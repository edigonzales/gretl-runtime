package ch.so.agi.gretl.tooling.workspace;

import ch.so.agi.gretl.tooling.CancellationToken;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import static ch.so.agi.gretl.tooling.workspace.ProjectResolver.*;
import static ch.so.agi.gretl.tooling.workspace.WorkspaceManager.*;

/** Bounded owned executor. Captures/IO happen on workers, never in document notifications. */
public final class AnalysisScheduler<T> implements AutoCloseable {
    @FunctionalInterface public interface Analyzer<T>{T analyze(WorkspaceCapture capture,CancellationToken cancellation);}
    public enum Status { PUBLISHED, STALE, CANCELLED, LIMIT, FAILED }
    public record Outcome<T>(Status status,WorkspaceCapture capture,T value,String problemCode) { }
    public record Latest<T>(WorkspaceCapture capture,T value,boolean stale) { }
    private record Key(java.net.URI root,java.net.URI document,boolean singleJob) { }
    private final class Ticket {
        final Key key;final ProjectRequest request;final long sequence;final AtomicBoolean cancelled=new AtomicBoolean();final CompletableFuture<Outcome<T>> result=new CompletableFuture<>();
        volatile ProjectId project;FutureTask<Void> future;
        Ticket(Key key,ProjectRequest request,long sequence){this.key=key;this.request=request;this.sequence=sequence;}
    }
    private final Object lock=new Object();
    private final WorkspaceManager workspace;
    private final Analyzer<T> analyzer;
    private final BiConsumer<WorkspaceCapture,T> publisher;
    private final ThreadPoolExecutor executor;
    private final Map<Key,Ticket> pending=new LinkedHashMap<>();
    private final Map<ProjectId,Long> newest=new HashMap<>();
    private final LinkedHashMap<ProjectId,Latest<T>> latest=new LinkedHashMap<>(16,0.75f,true);
    private final int maximumSnapshots;
    private long sequence;
    private boolean closed;
    public AnalysisScheduler(WorkspaceManager workspace,Analyzer<T> analyzer,BiConsumer<WorkspaceCapture,T> publisher){this(workspace,analyzer,publisher,2,16,8);}
    public AnalysisScheduler(WorkspaceManager workspace,Analyzer<T> analyzer,BiConsumer<WorkspaceCapture,T> publisher,int workers,int queueSize,int maximumSnapshots){
        this.workspace=Objects.requireNonNull(workspace);this.analyzer=Objects.requireNonNull(analyzer);this.publisher=Objects.requireNonNull(publisher);
        if(workers<1||workers>4||queueSize<1||queueSize>64||maximumSnapshots<1||maximumSnapshots>16)throw new IllegalArgumentException("Invalid analysis budget");this.maximumSnapshots=maximumSnapshots;
        var ids=new java.util.concurrent.atomic.AtomicInteger();
        executor=new ThreadPoolExecutor(workers,workers,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(queueSize),r->{var thread=new Thread(r,"gretl-analysis-"+workspace.serverInstance()+"-"+ids.incrementAndGet());thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
        workspace.onInvalidation(this::invalidated);
    }
    public CompletableFuture<Outcome<T>> submit(ProjectRequest request){
        synchronized(lock){
            if(closed)throw new IllegalStateException("ANALYSIS_CLOSED");var key=new Key(request.workspaceRoot(),request.documentId(),request.singleJob());var old=pending.get(key);if(old!=null)cancel(old);
            var ticket=new Ticket(key,request,++sequence);ticket.future=new FutureTask<>(()->{run(ticket);return null;});pending.put(key,ticket);
            try{executor.execute(ticket.future);}catch(RejectedExecutionException full){pending.remove(key,ticket);ticket.result.complete(new Outcome<>(Status.LIMIT,null,null,"ANALYSIS_QUEUE_LIMIT"));}
            return ticket.result;
        }
    }
    private void run(Ticket ticket){
        WorkspaceCapture capture=null;
        CancellationToken cancellation=()->ticket.cancelled.get()||Thread.currentThread().isInterrupted();
        try{
            cancellation.check();capture=workspace.capture(ticket.request,cancellation);ticket.project=capture.project().id();
            synchronized(lock){
                if(closed||newest.getOrDefault(ticket.project,0L)>ticket.sequence)throw new CancellationException("Superseded project analysis");
                newest.put(ticket.project,ticket.sequence);
                for(var other:List.copyOf(pending.values()))if(other!=ticket&&ticket.project.equals(other.project)&&other.sequence<ticket.sequence)cancel(other);
            }
            T value=analyzer.analyze(capture,cancellation);cancellation.check();WorkspaceCapture resultCapture=capture;boolean[] accepted={false};
            boolean current=workspace.publishIfCurrent(capture,()->{
                synchronized(lock){
                    if(closed||ticket.cancelled.get()||newest.getOrDefault(ticket.project,0L)!=ticket.sequence)return;
                    publisher.accept(resultCapture,value);latest.put(ticket.project,new Latest<>(resultCapture,value,false));while(latest.size()>maximumSnapshots)latest.remove(latest.keySet().iterator().next());accepted[0]=true;
                }
            },cancellation);
            ticket.result.complete(new Outcome<>(current&&accepted[0]?Status.PUBLISHED:Status.STALE,capture,current&&accepted[0]?value:null,current&&accepted[0]?null:"STALE_ANALYSIS"));
        }catch(CancellationException cancelled){ticket.result.complete(new Outcome<>(ticket.cancelled.get()?Status.CANCELLED:Status.STALE,capture,null,"ANALYSIS_CANCELLED"));}
        catch(RuntimeException failed){ticket.result.complete(new Outcome<>(Status.FAILED,capture,null,"ANALYSIS_FAILED"));}
        finally{synchronized(lock){pending.remove(ticket.key,ticket);}}
    }
    public Optional<Latest<T>> latest(ProjectId id){Latest<T> result;synchronized(lock){result=latest.get(id);}return result==null?Optional.empty():Optional.of(new Latest<>(result.capture(),result.value(),!workspace.sameGeneration(result.capture())));}
    public void cancelProject(ProjectId id){boolean exists=workspace.containsProject(id);synchronized(lock){for(var ticket:List.copyOf(pending.values()))if(id.equals(ticket.project))cancel(ticket);if(!exists){latest.remove(id);newest.remove(id);}}}
    private void invalidated(Set<ProjectId> ids){
        List<Ticket> tickets;List<ProjectId> cached;
        synchronized(lock){tickets=List.copyOf(pending.values());cached=List.copyOf(newest.keySet());}
        for(var ticket:tickets){boolean rootExists=workspace.containsRoot(ticket.request.workspaceRoot());if(!rootExists||ticket.project!=null&&ids.contains(ticket.project))synchronized(lock){cancel(ticket);}}
        for(var id:cached)if(!workspace.containsProject(id))synchronized(lock){latest.remove(id);newest.remove(id);}
    }
    private void cancel(Ticket ticket){ticket.cancelled.set(true);ticket.future.cancel(true);executor.remove(ticket.future);pending.remove(ticket.key,ticket);ticket.result.complete(new Outcome<>(Status.CANCELLED,null,null,"ANALYSIS_CANCELLED"));}
    public int pendingCount(){synchronized(lock){return pending.size();}}
    public boolean isTerminated(){return executor.isTerminated();}
    @Override public void close(){
        synchronized(lock){if(closed)return;closed=true;for(var ticket:List.copyOf(pending.values()))cancel(ticket);latest.clear();newest.clear();}
        workspace.onInvalidation(ids->{});executor.shutdownNow();
        try{if(!executor.awaitTermination(5,TimeUnit.SECONDS))throw new IllegalStateException("ANALYSIS_SHUTDOWN_TIMEOUT");}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("ANALYSIS_SHUTDOWN_INTERRUPTED",interrupted);}
    }
}
