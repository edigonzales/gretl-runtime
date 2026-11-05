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
    public record WorkspaceCapture(UUID serverInstance,long generation,long rootConfigurationGeneration,String configurationDigest,Configuration configuration,CatalogRepository.CatalogSnapshot catalog,ResolvedProject project,ReadPolicy readPolicy,SourceOwnershipIndex.OwnershipSnapshot ownership) {
        public WorkspaceCapture(UUID serverInstance,long generation,long rootConfigurationGeneration,String configurationDigest,Configuration configuration,CatalogRepository.CatalogSnapshot catalog,ResolvedProject project,ReadPolicy readPolicy){this(serverInstance,generation,rootConfigurationGeneration,configurationDigest,configuration,catalog,project,readPolicy,SourceOwnershipIndex.OwnershipSnapshot.unknown());}
    }
    private static final class RootState {
        final URI id;final SourceRepository repository;final Set<SourceOwnershipIndex.EntryRegistration> explicitEntries=new LinkedHashSet<>();Configuration configuration=Configuration.defaults();long configurationGeneration,sourceEpoch,ownerGeneration;String ownerFingerprint;
        RootState(URI id,SourceRepository repository){this.id=id;this.repository=repository;}
    }
    private static final class ProjectState {
        final ProjectId id;ProjectRequest request;boolean sourceQuery;long generation;Set<URI> dependencies=Set.of();
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
        synchronized(this){checkOpen();id=SourceUris.directoryIdentity(id);if(roots.containsKey(id))throw new IllegalArgumentException("ROOT_ALREADY_REGISTERED");if(roots.size()>=16)throw new IllegalStateException("WORKSPACE_ROOT_LIMIT");var root=new RootState(id,Objects.requireNonNull(repository));root.configurationGeneration=++clock;root.ownerGeneration=++clock;roots.put(id,root);affected=invalidateOwnership();}
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
    public void registerEntry(URI rootId,URI entry,boolean singleJob) {
        Set<ProjectId> affected;
        synchronized(this){checkOpen();var root=requireRoot(rootId);entry=DocumentStore.identity(entry);
            if(!policy(root,entry).permits(entry))throw new IllegalArgumentException("SOURCE_OUTSIDE_POLICY");
            if(root.explicitEntries.size()>=64)throw new IllegalArgumentException("ENTRY_REGISTRATION_LIMIT");
            if(!root.explicitEntries.add(new SourceOwnershipIndex.EntryRegistration(entry,singleJob)))return;
            root.sourceEpoch++;root.ownerGeneration=++clock;root.ownerFingerprint=null;affected=new LinkedHashSet<>();
            for(var p:projects.values())if(p.id.workspaceRoot().equals(root.id)){p.generation=++clock;affected.add(p.id);}
        }
        invalidated.accept(Set.copyOf(affected));
    }
    public synchronized boolean isRegisteredEntry(URI source){source=DocumentStore.identity(source);URI id=source;return roots.values().stream().anyMatch(root->root.explicitEntries.stream().anyMatch(entry->entry.documentId().equals(id)));}
    public void onCatalogChanged(){Set<ProjectId> affected;synchronized(this){checkOpen();affected=new LinkedHashSet<>(projects.keySet());for(var p:projects.values())p.generation=++clock;}invalidated.accept(Set.copyOf(affected));}
    public void onSourcesChanged(Set<URI> changed){
        var affected=new LinkedHashSet<ProjectId>();
        synchronized(this){if(closed)return;for(var root:roots.values()){
            var policy=policy(root,null);var relevant=changed.stream().map(DocumentStore::identity).filter(id->policy.permits(id)||projects.values().stream().anyMatch(p->p.id.workspaceRoot().equals(root.id)&&p.request.documentId().equals(id))).toList();if(relevant.isEmpty())continue;root.sourceEpoch++;
            boolean inventory=relevant.stream().anyMatch(id->conventionalEntry(id)||root.explicitEntries.stream().anyMatch(e->e.documentId().equals(id)));
            boolean known=inventory||projects.values().stream().anyMatch(p->p.id.workspaceRoot().equals(root.id)&&(p.dependencies.isEmpty()||relevant.stream().anyMatch(id->p.dependencies.contains(id)||p.request.documentId().equals(id))));
            if(!known)continue;root.ownerGeneration=++clock;root.ownerFingerprint=null;
            for(var project:projects.values())if(project.id.workspaceRoot().equals(root.id)&&(inventory||project.dependencies.isEmpty()||relevant.stream().anyMatch(id->project.dependencies.contains(id)||project.request.documentId().equals(id)))){project.generation=++clock;affected.add(project.id);}
        }}
        invalidated.accept(Set.copyOf(affected));
    }
    public WorkspaceCapture capture(ProjectRequest original,CancellationToken cancellation){return capture(original,false,cancellation);}
    public WorkspaceCapture captureSource(ProjectRequest original,CancellationToken cancellation){return capture(original,true,cancellation);}
    private WorkspaceCapture capture(ProjectRequest original,boolean sourceQuery,CancellationToken cancellation){
        RootState root;long rootGeneration,sourceEpoch;Set<SourceOwnershipIndex.EntryRegistration> explicitEntries;Configuration configuration;ReadPolicy policy;CatalogRepository.CatalogSnapshot catalog;
        synchronized(this){checkOpen();root=requireRoot(original.workspaceRoot());rootGeneration=root.configurationGeneration;sourceEpoch=root.sourceEpoch;explicitEntries=Set.copyOf(root.explicitEntries);configuration=root.configuration;policy=policy(root,original.documentId());catalog=catalogs.snapshot();}
        var request=new ProjectRequest(root.id,original.documentId(),original.singleJob(),policy.excludedRoots(),original.requestedSources());var overlay=documents.capture();
        var view=new SourceView(root.repository,policy,overlay);
        ProjectRequest resolutionRequest=request;
        if(sourceQuery&&!conventionalEntry(request.documentId())&&explicitEntries.stream().noneMatch(e->e.documentId().equals(request.documentId()))){
            var listing=view.listProjectEntries(root.id,ScanBudget.defaults(),cancellation);
            var anchor=listing.entries().stream().filter(WorkspaceManager::conventionalEntry)
                    .sorted(Comparator.comparing((URI id)->!id.getPath().endsWith("/settings.gretl")).thenComparing(URI::toString)).findFirst();
            if(anchor.isPresent())resolutionRequest=new ProjectRequest(root.id,anchor.get(),false,policy.excludedRoots(),request.requestedSources());
            else if(!explicitEntries.isEmpty()){
                var entry=explicitEntries.stream().sorted(Comparator.comparing(e->e.documentId().toString())).findFirst().orElseThrow();
                resolutionRequest=new ProjectRequest(root.id,entry.documentId(),entry.singleJob(),policy.excludedRoots(),request.requestedSources());
            } else resolutionRequest=null;
        } else if(sourceQuery&&explicitEntries.stream().anyMatch(e->e.documentId().equals(request.documentId())&&e.singleJob()))
            resolutionRequest=new ProjectRequest(root.id,request.documentId(),true,policy.excludedRoots(),request.requestedSources());
        var active=resolutionRequest==null?syntaxOnly(request,view,cancellation):new ProjectResolver().resolve(resolutionRequest,view,cancellation);
        if(sourceQuery)view.read(original.documentId(),cancellation);
        var acquiredOwnership=new SourceOwnershipIndex().captureOwners(request,active,view,explicitEntries,sourceEpoch,cancellation);
        var resolved=active.withSources(view.freeze());
        if(!sourcesCurrent(resolved.sources(),policy,root.repository,cancellation))throw new java.util.concurrent.CancellationException("STALE_CAPTURE");
        WorkspaceCapture[] captured={null};var evicted=new LinkedHashSet<ProjectId>();
        synchronized(this){
            checkOpen();if(roots.get(root.id)!=root||root.configurationGeneration!=rootGeneration||root.sourceEpoch!=sourceEpoch||catalogs.snapshot().generation()!=catalog.generation())throw new java.util.concurrent.CancellationException("STALE_CAPTURE");
            String fingerprint=ownershipFingerprint(acquiredOwnership,resolved.sources());
            if(root.ownerFingerprint!=null&&!root.ownerFingerprint.equals(fingerprint)){
                root.ownerGeneration=++clock;
                for(var project:projects.values())if(project.id.workspaceRoot().equals(root.id)){project.generation=++clock;evicted.add(project.id);}
            }
            root.ownerFingerprint=fingerprint;var ownership=acquiredOwnership.withGeneration(root.ownerGeneration);
            boolean current=documents.ifUnchanged(resolved.sources().buffers(),id->policy.permits(id)&&resolved.sources().versions().containsKey(id),()->{
                var canonicalRequest=new ProjectRequest(root.id,resolved.id().anchor(),resolved.id().singleJob(),policy.excludedRoots(),request.requestedSources());
                var state=projects.computeIfAbsent(resolved.id(),id->new ProjectState(id,canonicalRequest,++clock));state.request=canonicalRequest;state.sourceQuery=sourceQuery;state.dependencies=Set.copyOf(resolved.sources().versions().keySet());
                captured[0]=new WorkspaceCapture(serverInstance,state.generation,rootGeneration,digest(configuration,policy),configuration,catalog,resolved,policy,ownership);
                while(projects.size()>64){var oldest=projects.keySet().iterator().next();projects.remove(oldest);evicted.add(oldest);}
            });
            if(!current)throw new java.util.concurrent.CancellationException("STALE_CAPTURE");
        }
        if(!evicted.isEmpty())invalidated.accept(Set.copyOf(evicted));return captured[0];
    }
    public WorkspaceCapture inContext(WorkspaceCapture capture,ProjectId id){
        var evicted=new LinkedHashSet<ProjectId>();WorkspaceCapture selected;
        synchronized(this){
            if(!sameGeneration(capture))throw new java.util.concurrent.CancellationException("STALE_CAPTURE");
            var project=capture.ownership().projects().get(id);if(project==null)throw new IllegalArgumentException("SOURCE_CONTEXT_UNKNOWN");
            var request=new ProjectRequest(id.workspaceRoot(),id.anchor(),id.singleJob(),capture.readPolicy().excludedRoots());
            var state=projects.computeIfAbsent(id,key->new ProjectState(key,request,++clock));state.sourceQuery=true;state.dependencies=Set.copyOf(capture.project().sources().versions().keySet());
            selected=new WorkspaceCapture(capture.serverInstance(),state.generation,capture.rootConfigurationGeneration(),capture.configurationDigest(),
                    capture.configuration(),capture.catalog(),project.withSources(capture.project().sources()),capture.readPolicy(),capture.ownership());
            while(projects.size()>64){var oldest=projects.keySet().iterator().next();projects.remove(oldest);evicted.add(oldest);}
        }
        if(!evicted.isEmpty())invalidated.accept(Set.copyOf(evicted));return selected;
    }
    public WorkspaceCapture syntaxOnly(WorkspaceCapture capture,URI source,CancellationToken cancellation){
        var original=capture.project().sources();
        var view=new SourceView(original,capture.readPolicy(),new DocumentStore.BufferCapture(0,original.buffers()));
        var request=new ProjectRequest(capture.project().id().workspaceRoot(),source,false,capture.readPolicy().excludedRoots());
        var project=syntaxOnly(request,view,cancellation).withSources(original);
        synchronized(this){var state=projects.computeIfAbsent(project.id(),id->new ProjectState(id,request,++clock));state.sourceQuery=true;state.dependencies=Set.copyOf(original.versions().keySet());
            return new WorkspaceCapture(capture.serverInstance(),state.generation,capture.rootConfigurationGeneration(),capture.configurationDigest(),capture.configuration(),capture.catalog(),project,capture.readPolicy(),capture.ownership());}
    }
    private static ResolvedProject syntaxOnly(ProjectRequest request,SourceView view,CancellationToken cancellation){
        var read=view.read(request.documentId(),cancellation);var documents=new LinkedHashMap<URI,ch.so.agi.gretl.tooling.syntax.SyntaxModel.ParsedDocument>();
        read.source().ifPresent(source->documents.put(request.documentId(),new ch.so.agi.gretl.tooling.syntax.GretlDocumentParser().parse(source)));
        return new ResolvedProject(new ProjectId(request.workspaceRoot(),request.documentId()),SourceUris.parent(request.documentId()),null,false,List.of(),List.of(),List.of(),documents,view.freeze(),false,null,IncludeModel.IncludeClosure.empty());
    }
    private static boolean conventionalEntry(URI id){return id.getPath()!=null&&(id.getPath().endsWith("/job.groovy")||id.getPath().endsWith("/settings.gretl"));}
    public WorkspaceCapture capture(ProjectId id,CancellationToken cancellation){ProjectRequest request;boolean sourceQuery;synchronized(this){checkOpen();var state=Objects.requireNonNull(projects.get(id),"PROJECT_NOT_CAPTURED");request=state.request;sourceQuery=state.sourceQuery;}return capture(request,sourceQuery,cancellation);}
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
        return root!=null&&project!=null&&project.generation==capture.generation()&&root.configurationGeneration==capture.rootConfigurationGeneration()&&(capture.ownership().generation()<0||root.ownerGeneration==capture.ownership().generation())&&catalogs.snapshot().generation()==capture.catalog().generation()&&digest(root.configuration,policy(root,project.request.documentId())).equals(capture.configurationDigest())&&documents.ifUnchanged(capture.project().sources().buffers(),id->capture.readPolicy().permits(id)&&capture.project().sources().versions().containsKey(id),()->{});
    }
    public synchronized boolean containsProject(ProjectId id){return !closed&&projects.containsKey(id);}
    /** Metadata-only validity for host state that must not retain whole captures. */
    public synchronized boolean isProjectGenerationCurrent(ProjectId id,long generation){var project=projects.get(id);return !closed&&project!=null&&project.generation==generation;}
    public synchronized boolean containsRoot(URI id){return !closed&&roots.containsKey(SourceUris.directoryIdentity(id));}
    private boolean sourcesCurrent(SourceView.SnapshotSources expected,ReadPolicy policy,SourceRepository repository,CancellationToken cancellation){
        var current=new SourceView(repository,policy,documents.capture());
        for(var entry:expected.versions().entrySet()){
            cancellation.check();current.read(entry.getKey(),cancellation);var actual=current.version(entry.getKey());var previous=entry.getValue();
            if(actual==null||!Objects.equals(previous.stamp(),actual.stamp())||!Objects.equals(previous.bufferIncarnation(),actual.bufferIncarnation()))return false;
            if(previous.stamp()==null&&!Objects.equals(previous.unavailableCode(),actual.unavailableCode()))return false;
        }
        for(var listing:expected.listings().entrySet())
            if(!listing.getValue().equals(current.listProjectEntries(listing.getKey().root(),listing.getKey().budget(),cancellation)))return false;
        for(var traversal:expected.traversals().entrySet())
            if(!traversal.getValue().equals(current.checkIncludeTraversal(traversal.getKey(),cancellation)))return false;
        return true;
    }
    private ReadPolicy policy(RootState root,URI active){
        var excluded=roots.keySet().stream().filter(id->!id.equals(root.id)&&SourceUris.contains(root.id,id)).toList();
        var explicit=active!=null&&Set.of("untitled","memory").contains(active.getScheme())?Set.of(active):Set.<URI>of();return new ReadPolicy(root.id,1_048_576,true,excluded,explicit);
    }
    private Set<ProjectId> invalidateOwnership(){var ids=new LinkedHashSet<ProjectId>();for(var root:roots.values())root.configurationGeneration=++clock;for(var project:projects.values()){project.generation=++clock;ids.add(project.id);}return Set.copyOf(ids);}
    private RootState requireRoot(URI id){var root=roots.get(SourceUris.directoryIdentity(id));if(root==null)throw new IllegalArgumentException("WORKSPACE_ROOT_NOT_REGISTERED");return root;}
    private static String ownershipFingerprint(SourceOwnershipIndex.OwnershipSnapshot ownership,SourceView.SnapshotSources sources){
        var text=new StringBuilder(Boolean.toString(ownership.complete()));
        ownership.applications().values().stream().flatMap(List::stream).map(a->a.id().toString()).sorted().forEach(s->append(text,s));
        sources.versions().entrySet().stream().filter(e->ownership.applications().containsKey(e.getKey())||ownership.projects().values().stream().anyMatch(p->e.getKey().equals(p.settingsId()))).sorted(Map.Entry.comparingByKey()).forEach(e->{append(text,e.getKey().toString());append(text,e.getValue().toString());});
        append(text,ownership.inventory().toString());return CatalogJsonCodec.hash(text.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static String digest(Configuration configuration,ReadPolicy policy){
        var text=new StringBuilder();append(text,configuration.language());new TreeMap<>(configuration.options()).forEach((k,v)->{append(text,k);append(text,v);});policy.excludedRoots().stream().map(URI::toString).sorted().forEach(v->append(text,v));return CatalogJsonCodec.hash(text.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static void append(StringBuilder text,String value){text.append(value.length()).append(':').append(value);}
    private void checkOpen(){if(closed)throw new IllegalStateException("WORKSPACE_CLOSED");}
    @Override public void close(){Set<ProjectId> affected;synchronized(this){if(closed)return;affected=Set.copyOf(projects.keySet());closed=true;projects.clear();roots.clear();}documents.onChange(id->{});for(var id:documents.capture().documents().keySet())documents.close(id);invalidated.accept(affected);invalidated=ids->{};}
}
