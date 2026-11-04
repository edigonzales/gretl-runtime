package ch.so.agi.gretl.lsp;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.CatalogRepository;
import ch.so.agi.gretl.tooling.edit.PlannedEdit;
import ch.so.agi.gretl.tooling.semantic.*;
import ch.so.agi.gretl.tooling.workspace.*;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.*;

/** Bounded host scheduling, snapshot ownership and plan retention. Dispatch never performs disk IO. */
final class ServerWorkspace implements AutoCloseable {
    final DocumentStore documents=new DocumentStore();
    final CatalogRepository catalog;
    final WorkspaceManager manager;
    private final Set<URI> roots=new LinkedHashSet<>();
    private final LinkedHashMap<ProjectResolver.ProjectId,AnalysisSnapshot> snapshots=new LinkedHashMap<>(16,.75f,true);
    private record Retained(AnalysisSnapshot snapshot,PlannedEdit edit,Instant expires) { }
    private final LinkedHashMap<String,Retained> plans=new LinkedHashMap<>();
    private final ThreadPoolExecutor workers;
    private final Object acquisition=new Object();
    private final Set<CompletableFuture<?>> inFlight=ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService debounce;
    private ScheduledFuture<?> refresh;
    private final HostFileWatcher watcher;
    private final BiConsumer<AnalysisSnapshot,CancellationToken> publish;
    private final AnalysisScheduler.Analyzer<AnalysisSnapshot> analyzer;
    volatile ServerConfiguration configuration=ServerConfiguration.defaults();
    private volatile boolean closed;
    private long epoch;
    ServerWorkspace(CatalogRepository catalog,BiConsumer<AnalysisSnapshot,CancellationToken> publish) throws IOException {
        this(catalog,publish,(capture,cancel)->AnalysisSnapshot.of(new ProjectAnalyzer().analyze(capture,cancel)));
    }
    ServerWorkspace(CatalogRepository catalog,BiConsumer<AnalysisSnapshot,CancellationToken> publish,AnalysisScheduler.Analyzer<AnalysisSnapshot> analyzer) throws IOException {
        this.catalog=catalog;this.publish=publish;this.analyzer=analyzer;manager=new WorkspaceManager(documents,catalog);
        workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"gretl-lsp-worker");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        debounce=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"gretl-lsp-refresh");t.setDaemon(true);return t;});
        watcher=new HostFileWatcher(this::sourcesChanged);
    }
    void addRoot(URI id) throws IOException {
        id=SourceUris.directoryIdentity(local(id));synchronized(this){if(roots.contains(id))return;}
        manager.addRoot(id,new LspHostSourceRepository(id));synchronized(this){roots.add(id);epoch++;}
        manager.updateConfiguration(id,new WorkspaceManager.Configuration(configuration.language(),Map.of()));
        watcher.capture(List.of(SourceUris.child(id,"settings.gretl")));
    }
    void removeRoot(URI id){id=SourceUris.directoryIdentity(local(id));manager.removeRoot(id);synchronized(this){roots.remove(id);snapshots.clear();plans.clear();epoch++;}schedule();}
    /** Control-thread transaction: all fallible validation precedes changes to ownership or retained plans. */
    void updateRoots(List<URI> additions,List<URI> removals) throws IOException {
        if(additions.size()>16||removals.size()>16)throw new IllegalArgumentException("WORKSPACE_ROOT_LIMIT");
        var addIds=additions.stream().map(ServerWorkspace::local).map(SourceUris::directoryIdentity).toList();
        var removeIds=removals.stream().map(ServerWorkspace::local).map(SourceUris::directoryIdentity).toList();
        if(new HashSet<>(addIds).size()!=addIds.size()||new HashSet<>(removeIds).size()!=removeIds.size())throw new IllegalArgumentException("WORKSPACE_ROOT_DUPLICATE");
        Set<URI> previous;synchronized(this){if(closed)throw new IllegalStateException("WORKSPACE_CLOSED");previous=Set.copyOf(roots);}
        var next=new LinkedHashSet<>(previous);next.removeAll(removeIds);next.addAll(addIds);
        if(next.size()>16)throw new IllegalArgumentException("WORKSPACE_ROOT_LIMIT");
        var prepared=new LinkedHashMap<URI,LspHostSourceRepository>();
        for(var id:addIds)prepared.put(id,new LspHostSourceRepository(id));
        if(next.equals(previous)&&Collections.disjoint(removeIds,addIds))return;
        // New acquisitions cannot observe intermediate root ownership. Existing requests become obsolete first.
        synchronized(acquisition) {
            synchronized(this){if(closed)throw new IllegalStateException("WORKSPACE_CLOSED");if(!roots.equals(previous))throw new IllegalStateException("WORKSPACE_STATE_CHANGED");epoch++;}
            for(var id:removeIds)manager.removeRoot(id);
            var remaining=new HashSet<>(previous);remaining.removeAll(removeIds);
            for(var entry:prepared.entrySet())if(remaining.add(entry.getKey())) {
                manager.addRoot(entry.getKey(),entry.getValue());
                manager.updateConfiguration(entry.getKey(),new WorkspaceManager.Configuration(configuration.language(),Map.of()));
            }
            synchronized(this){roots.clear();roots.addAll(next);snapshots.clear();plans.clear();epoch++;}
            watcher.capture(addIds.stream().map(id->SourceUris.child(id,"settings.gretl")).toList());
        }
        schedule();
    }
    void configure(ServerConfiguration next) {
        List<URI> currentRoots;
        synchronized(this){configuration=next;epoch++;currentRoots=List.copyOf(roots);snapshots.clear();plans.clear();}
        for(var root:currentRoots)manager.updateConfiguration(root,new WorkspaceManager.Configuration(next.language(),Map.of()));
        schedule();
    }
    synchronized URI rootFor(URI document) {
        if(!"file".equals(document.getScheme())&&roots.size()!=1)throw error(ResponseErrorCode.InvalidParams,"UNSAVED_WORKSPACE_AMBIGUOUS");
        return roots.stream().filter(root->SourceUris.contains(root,document)||Set.of("untitled","memory").contains(document.getScheme()))
                .max(Comparator.comparingInt(root->root.toString().length())).orElseThrow(()->error(ResponseErrorCode.InvalidParams,"SOURCE_OUTSIDE_WORKSPACE"));
    }
    URI document(String value) {
        URI id=DocumentStore.identity(URI.create(GretlProtocol.required(value,8192)));
        if(!Set.of("file","untitled","memory").contains(id.getScheme()))throw error(ResponseErrorCode.InvalidParams,"SOURCE_SCHEME_UNSUPPORTED");
        rootFor(id);return id;
    }
    static URI local(URI id){id=DocumentStore.identity(id);if(!"file".equals(id.getScheme()))throw new IllegalArgumentException("LOCAL_WORKSPACE_REQUIRED");return id;}
    void sourcesChanged(Set<URI> ids){manager.onSourcesChanged(ids);synchronized(this){epoch++;}schedule();}
    void changed(){synchronized(this){epoch++;}schedule();}
    synchronized void schedule() {
        if(closed||!configuration.trusted())return;
        if(refresh!=null)refresh.cancel(false);
        refresh=debounce.schedule(()-> {
            for(var id:documents.capture().documents().keySet()) {
                String path=id.getPath();
                if(path!=null&&!path.endsWith(".sql"))request(id,List.of(),(snapshot,cancel)->null).exceptionally(failure->null);
            }
        },75,TimeUnit.MILLISECONDS);
    }
    <T> CompletableFuture<T> request(URI document,List<URI> extra,BiFunction<AnalysisSnapshot,CancellationToken,T> operation) {
        var result=new CompletableFuture<T>();var cancelled=new AtomicBoolean();
        inFlight.add(result);
        result.whenComplete((value,failure)->{inFlight.remove(result);if(result.isCancelled())cancelled.set(true);});
        final long revision=documents.capture().revision(),requestEpoch;
        synchronized(this){requestEpoch=epoch;}
        CancellationToken cancellation=()->cancelled.get()||closed||Thread.currentThread().isInterrupted()||documents.capture().revision()!=revision||epochChanged(requestEpoch);
        try {workers.execute(()-> {
            try {
                if(!configuration.trusted())throw error(ResponseErrorCode.RequestFailed,"WORKSPACE_UNTRUSTED");
                cancellation.check();AnalysisSnapshot snapshot;
                synchronized(acquisition) {
                cancellation.check();snapshot=null;
                List<AnalysisSnapshot> cachedSnapshots;synchronized(this){cachedSnapshots=List.copyOf(snapshots.values());}
                String path=document.getPath();
                var buffer=documents.get(document);
                boolean singleJob=(path==null||!path.endsWith("/job.groovy")&&!path.endsWith("/settings.gretl"))
                        &&(buffer.filter(open->!open.languageId().equals("sql")).isPresent()
                        ||buffer.isEmpty()&&cachedSnapshots.stream().anyMatch(cached->cached.analysis().capture().project().id().singleJob()
                            &&cached.analysis().capture().project().id().anchor().equals(document)));
                if(documents.get(document).isEmpty()&&cachedSnapshots.stream().noneMatch(cached->cached.sources().containsKey(document))
                        &&(path==null||!path.endsWith("/job.groovy")&&!path.endsWith("/settings.gretl")))throw error(ResponseErrorCode.InvalidParams,"SOURCE_NOT_KNOWN");
                for(var cached:cachedSnapshots)if(cached.sources().containsKey(document)&&extra.isEmpty()
                        &&cached.analysis().capture().project().id().singleJob()==singleJob
                        &&(!singleJob||cached.analysis().capture().project().id().anchor().equals(document))
                        &&manager.sameGeneration(cached.analysis().capture())){snapshot=cached;break;}
                if(snapshot!=null&&!manager.isCurrent(snapshot.analysis().capture(),cancellation))snapshot=null;
                if(snapshot==null) {
                    var capture=manager.capture(new ProjectResolver.ProjectRequest(rootFor(document),document,singleJob,List.of(),extra),cancellation);
                    snapshot=analyzer.analyze(capture,cancellation);
                    watcher.capture(capture.project().sources().versions().keySet());
                    var analyzed=snapshot;
                    if(!manager.publishIfCurrent(capture,()->{synchronized(this){snapshots.put(capture.project().id(),analyzed);while(snapshots.size()>8)snapshots.remove(snapshots.keySet().iterator().next());}publish.accept(analyzed,cancellation);},cancellation))throw new CancellationException();
                }
                }
                cancellation.check();T value=operation.apply(snapshot,cancellation);cancellation.check();
                if(!manager.publishIfCurrent(snapshot.analysis().capture(),()->result.complete(value),cancellation))throw new CancellationException();
            } catch(CancellationException obsolete){result.completeExceptionally(error(ResponseErrorCode.ContentModified,"STALE_OR_CANCELLED_ANALYSIS"));}
            catch(ResponseErrorException failure){result.completeExceptionally(failure);}
            catch(IllegalArgumentException|NullPointerException failure){result.completeExceptionally(error(ResponseErrorCode.InvalidParams,code(failure)));}
            catch(RuntimeException failure){result.completeExceptionally(error(ResponseErrorCode.InternalError,"ANALYSIS_FAILED"));}
        });}catch(RejectedExecutionException full){result.completeExceptionally(error(ResponseErrorCode.RequestFailed,"REQUEST_QUEUE_LIMIT"));}
        return result;
    }
    private synchronized boolean epochChanged(long expected){return expected!=epoch;}
    synchronized void retain(AnalysisSnapshot snapshot,PlannedEdit edit){plans.entrySet().removeIf(e->e.getValue().expires().isBefore(Instant.now()));plans.put(edit.id().toString(),new Retained(snapshot,edit,Instant.now().plusSeconds(60)));while(plans.size()>32)plans.remove(plans.keySet().iterator().next());}
    synchronized AnalysisSnapshot planSnapshot(String id){var plan=plans.get(id);if(plan==null||plan.expires().isBefore(Instant.now()))throw error(ResponseErrorCode.InvalidParams,"EDIT_PLAN_UNKNOWN_OR_EXPIRED");return plan.snapshot();}
    synchronized PlannedEdit plan(String id){planSnapshot(id);return plans.get(id).edit();}
    void requireWritableTargets(PlannedEdit edit) {
        for(var document:edit.changes().keySet())if(!LspHostSourceRepository.writable(document,rootFor(document)))throw error(ResponseErrorCode.RequestFailed,"EDIT_READONLY");
    }
    static ResponseErrorException error(ResponseErrorCode code,String message){return new ResponseErrorException(new ResponseError(code,message,null));}
    static String code(Throwable failure){String message=failure.getMessage();return message!=null&&message.matches("[A-Z][A-Z0-9_]{0,127}")?message:"INVALID_REQUEST";}
    public void close() {
        synchronized(this){if(closed)return;closed=true;epoch++;plans.clear();snapshots.clear();}
        debounce.shutdownNow();workers.shutdownNow();try{watcher.close();}catch(IOException ignored){ }
        for(var future:inFlight)future.cancel(false);
        try{workers.awaitTermination(5,TimeUnit.SECONDS);debounce.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        manager.close();
    }
}
