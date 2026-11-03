package ch.so.agi.gretl.tooling.workspace;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import static ch.so.agi.gretl.tooling.workspace.ProjectResolver.*;
import static ch.so.agi.gretl.tooling.workspace.SourceRepository.*;

/** Owns project generations and immutable acquisitions, not an executable runtime or a query thread pool. */
public final class WorkspaceManager implements AutoCloseable {
    public record Configuration(String language,Map<String,String> options){
        public Configuration { language=language==null?"en":language;options=Map.copyOf(options); }
        public static Configuration defaults(){return new Configuration("en",Map.of());}
    }
    public record WorkspaceCapture(UUID serverInstance,long generation,long rootConfigurationGeneration,String configurationDigest,Configuration configuration,CatalogRepository.CatalogSnapshot catalog,ResolvedProject project,ReadPolicy readPolicy) { }
    private static final class RootState {
        final URI id;final SourceRepository repository;Configuration configuration=Configuration.defaults();long configurationGeneration,sourceEpoch;
        RootState(URI id,SourceRepository repository){this.id=id;this.repository=repository;}
    }
    private static final class ProjectState {
        final ProjectId id;ProjectRequest request;long generation;Set<URI> dependencies=Set.of();
        ProjectState(ProjectId id,ProjectRequest request,long generation){this.id=id;this.request=request;this.generation=generation;}
    }
    private final UUID serverInstance=UUID.randomUUID();
    private final DocumentStore documents;
    private final CatalogRepository catalogs;
    private final Map<URI,RootState> roots=new LinkedHashMap<>();
    private final LinkedHashMap<ProjectId,ProjectState> projects=new LinkedHashMap<>(16,0.75f,true);
    private long clock;
    private boolean closed;
    private volatile Consumer<Set<ProjectId>> invalidated=ids->{};
    public WorkspaceManager(DocumentStore documents,CatalogRepository catalogs){this.documents=Objects.requireNonNull(documents);this.catalogs=Objects.requireNonNull(catalogs);documents.onChange(id->onSourcesChanged(Set.of(id)));}
    public DocumentStore documents(){return documents;}
    public UUID serverInstance(){return serverInstance;}
    public void onInvalidation(Consumer<Set<ProjectId>> listener){invalidated=Objects.requireNonNull(listener);}
    public void addRoot(URI id,SourceRepository repository){
        Set<ProjectId> affected;
        synchronized(this){checkOpen();id=SourceUris.directoryIdentity(id);if(roots.containsKey(id))throw new IllegalArgumentException("ROOT_ALREADY_REGISTERED");if(roots.size()>=16)throw new IllegalStateException("WORKSPACE_ROOT_LIMIT");var root=new RootState(id,Objects.requireNonNull(repository));root.configurationGeneration=++clock;roots.put(id,root);affected=invalidateOwnership();}
        invalidated.accept(affected);
    }
    public void removeRoot(URI id){
        var affected=new LinkedHashSet<ProjectId>();
        synchronized(this){id=SourceUris.directoryIdentity(id);if(roots.remove(id)==null)return;URI removed=id;projects.entrySet().removeIf(e->{if(e.getKey().workspaceRoot().equals(removed)){affected.add(e.getKey());return true;}return false;});affected.addAll(invalidateOwnership());}
        invalidated.accept(Set.copyOf(affected));
    }
    public void updateConfiguration(URI id,Configuration configuration){
        var affected=new LinkedHashSet<ProjectId>();synchronized(this){checkOpen();var root=requireRoot(id);root.configuration=Objects.requireNonNull(configuration);root.configurationGeneration=++clock;for(var p:projects.values())if(p.id.workspaceRoot().equals(root.id)){p.generation=++clock;affected.add(p.id);}}
        invalidated.accept(Set.copyOf(affected));
    }
    public void onCatalogChanged(){Set<ProjectId> affected;synchronized(this){checkOpen();affected=new LinkedHashSet<>(projects.keySet());for(var p:projects.values())p.generation=++clock;}invalidated.accept(Set.copyOf(affected));}
    public void onSourcesChanged(Set<URI> changed){
        var affected=new LinkedHashSet<ProjectId>();
        synchronized(this){if(closed)return;for(var root:roots.values()){
            var policy=policy(root,null);var relevant=changed.stream().map(DocumentStore::identity).filter(id->policy.permits(id)||projects.values().stream().anyMatch(p->p.id.workspaceRoot().equals(root.id)&&p.request.documentId().equals(id))).toList();if(relevant.isEmpty())continue;root.sourceEpoch++;
            for(var project:projects.values())if(project.id.workspaceRoot().equals(root.id)&&(project.dependencies.isEmpty()||relevant.stream().anyMatch(id->project.dependencies.contains(id)||project.request.documentId().equals(id)))){project.generation=++clock;affected.add(project.id);}
        }}
        invalidated.accept(Set.copyOf(affected));
    }
    public WorkspaceCapture capture(ProjectRequest original,CancellationToken cancellation){
        RootState root;long rootGeneration,sourceEpoch;Configuration configuration;ReadPolicy policy;CatalogRepository.CatalogSnapshot catalog;
        synchronized(this){checkOpen();root=requireRoot(original.workspaceRoot());rootGeneration=root.configurationGeneration;sourceEpoch=root.sourceEpoch;configuration=root.configuration;policy=policy(root,original.documentId());catalog=catalogs.snapshot();}
        var request=new ProjectRequest(root.id,original.documentId(),original.singleJob(),policy.excludedRoots(),original.requestedSources());var overlay=documents.capture();
        var resolved=new ProjectResolver().resolve(request,root.repository,overlay,cancellation);
        if(!sourcesCurrent(resolved.sources(),policy,root.repository,cancellation))throw new java.util.concurrent.CancellationException("STALE_CAPTURE");
        WorkspaceCapture[] captured={null};var evicted=new LinkedHashSet<ProjectId>();
        synchronized(this){
            checkOpen();if(roots.get(root.id)!=root||root.configurationGeneration!=rootGeneration||root.sourceEpoch!=sourceEpoch||catalogs.snapshot().generation()!=catalog.generation())throw new java.util.concurrent.CancellationException("STALE_CAPTURE");
            boolean current=documents.ifUnchanged(resolved.sources().buffers(),id->policy.permits(id)&&resolved.sources().versions().containsKey(id),()->{
                var canonicalRequest=new ProjectRequest(root.id,resolved.id().anchor(),request.singleJob(),policy.excludedRoots(),request.requestedSources());
                var state=projects.computeIfAbsent(resolved.id(),id->new ProjectState(id,canonicalRequest,++clock));state.request=canonicalRequest;state.dependencies=Set.copyOf(resolved.sources().versions().keySet());
                captured[0]=new WorkspaceCapture(serverInstance,state.generation,rootGeneration,digest(configuration,policy),configuration,catalog,resolved,policy);
                while(projects.size()>64){var oldest=projects.keySet().iterator().next();projects.remove(oldest);evicted.add(oldest);}
            });
            if(!current)throw new java.util.concurrent.CancellationException("STALE_CAPTURE");
        }
        if(!evicted.isEmpty())invalidated.accept(Set.copyOf(evicted));return captured[0];
    }
    public WorkspaceCapture capture(ProjectId id,CancellationToken cancellation){ProjectRequest request;synchronized(this){checkOpen();request=Objects.requireNonNull(projects.get(id),"PROJECT_NOT_CAPTURED").request;}return capture(request,cancellation);}
    public boolean isCurrent(WorkspaceCapture capture,CancellationToken cancellation){
        RootState root;synchronized(this){if(!sameGeneration(capture))return false;root=roots.get(capture.project().id().workspaceRoot());}
        if(!sourcesCurrent(capture.project().sources(),capture.readPolicy(),root.repository,cancellation))return false;
        synchronized(this){return sameGeneration(capture)&&documents.ifUnchanged(capture.project().sources().buffers(),id->capture.readPolicy().permits(id)&&capture.project().sources().versions().containsKey(id),()->{});}
    }
    public boolean publishIfCurrent(WorkspaceCapture capture,Runnable publish,CancellationToken cancellation){
        if(!isCurrent(capture,cancellation))return false;
        synchronized(this){return sameGeneration(capture)&&documents.ifUnchanged(capture.project().sources().buffers(),id->capture.readPolicy().permits(id)&&capture.project().sources().versions().containsKey(id),publish);}
    }
    public synchronized boolean sameGeneration(WorkspaceCapture capture){
        if(closed||!serverInstance.equals(capture.serverInstance()))return false;var root=roots.get(capture.project().id().workspaceRoot());var project=projects.get(capture.project().id());
        return root!=null&&project!=null&&project.generation==capture.generation()&&root.configurationGeneration==capture.rootConfigurationGeneration()&&catalogs.snapshot().generation()==capture.catalog().generation()&&digest(root.configuration,policy(root,project.request.documentId())).equals(capture.configurationDigest())&&documents.ifUnchanged(capture.project().sources().buffers(),id->capture.readPolicy().permits(id)&&capture.project().sources().versions().containsKey(id),()->{});
    }
    public synchronized boolean containsProject(ProjectId id){return !closed&&projects.containsKey(id);}
    public synchronized boolean containsRoot(URI id){return !closed&&roots.containsKey(SourceUris.directoryIdentity(id));}
    private boolean sourcesCurrent(SourceView.SnapshotSources expected,ReadPolicy policy,SourceRepository repository,CancellationToken cancellation){
        var current=new SourceView(repository,policy,documents.capture());
        for(var entry:expected.versions().entrySet()){
            cancellation.check();current.read(entry.getKey(),cancellation);var actual=current.version(entry.getKey());var previous=entry.getValue();
            if(actual==null||!Objects.equals(previous.stamp(),actual.stamp())||!Objects.equals(previous.bufferIncarnation(),actual.bufferIncarnation()))return false;
            if(previous.stamp()==null&&!Objects.equals(previous.unavailableCode(),actual.unavailableCode()))return false;
        }
        return true;
    }
    private ReadPolicy policy(RootState root,URI active){
        var excluded=roots.keySet().stream().filter(id->!id.equals(root.id)&&SourceUris.contains(root.id,id)).toList();
        var explicit=active!=null&&Set.of("untitled","memory").contains(active.getScheme())?Set.of(active):Set.<URI>of();return new ReadPolicy(root.id,1_048_576,true,excluded,explicit);
    }
    private Set<ProjectId> invalidateOwnership(){var ids=new LinkedHashSet<ProjectId>();for(var root:roots.values())root.configurationGeneration=++clock;for(var project:projects.values()){project.generation=++clock;ids.add(project.id);}return Set.copyOf(ids);}
    private RootState requireRoot(URI id){var root=roots.get(SourceUris.directoryIdentity(id));if(root==null)throw new IllegalArgumentException("WORKSPACE_ROOT_NOT_REGISTERED");return root;}
    private static String digest(Configuration configuration,ReadPolicy policy){
        var text=new StringBuilder();append(text,configuration.language());new TreeMap<>(configuration.options()).forEach((k,v)->{append(text,k);append(text,v);});policy.excludedRoots().stream().map(URI::toString).sorted().forEach(v->append(text,v));return CatalogJsonCodec.hash(text.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static void append(StringBuilder text,String value){text.append(value.length()).append(':').append(value);}
    private void checkOpen(){if(closed)throw new IllegalStateException("WORKSPACE_CLOSED");}
    @Override public void close(){Set<ProjectId> affected;synchronized(this){if(closed)return;affected=Set.copyOf(projects.keySet());closed=true;projects.clear();roots.clear();}documents.onChange(id->{});for(var id:documents.capture().documents().keySet())documents.close(id);invalidated.accept(affected);invalidated=ids->{};}
}
