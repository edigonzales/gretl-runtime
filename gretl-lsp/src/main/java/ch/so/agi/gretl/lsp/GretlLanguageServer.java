package ch.so.agi.gretl.lsp;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.CatalogRepository;
import ch.so.agi.gretl.tooling.edit.*;
import ch.so.agi.gretl.tooling.graph.*;
import ch.so.agi.gretl.tooling.query.*;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.*;
import static ch.so.agi.gretl.lsp.GretlProtocol.*;

public final class GretlLanguageServer implements GretlLanguageServerApi,AutoCloseable {
    enum State { NEW, INITIALIZED, SHUTTING_DOWN, CLOSED }
    private volatile State state=State.NEW;
    private volatile boolean ready;
    private volatile GretlLanguageClient client;
    private final ServerWorkspace workspace;
    private final DiagnosticPublicationState diagnosticState=new DiagnosticPublicationState(64);
    private final GretlTextDocumentService text;
    private final GretlWorkspaceService folders;
    private final CompletableFuture<Integer> exit=new CompletableFuture<>();
    private final ThreadPoolExecutor control=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{var thread=new Thread(r,"gretl-lsp-control");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    private final java.util.concurrent.atomic.AtomicBoolean initializeRequested=new java.util.concurrent.atomic.AtomicBoolean();
    private final Object order=new Object();
    private CompletableFuture<Void> controlTail=CompletableFuture.completedFuture(null);
    private long controlSequence;
    private volatile boolean controlOverloaded;
    private volatile boolean documentChanges,markdown,related,hierarchicalSymbols,lineFoldingOnly,codeActionLiterals;
    public GretlLanguageServer(CatalogRepository catalog) throws IOException {
        workspace=new ServerWorkspace(catalog,this::publish);text=new GretlTextDocumentService(this);folders=new GretlWorkspaceService(this);
        workspace.manager.onInvalidation(ids->{synchronized(workspace.manager){emitDiagnostics(diagnosticState.invalidate(ids,workspace.manager::isProjectGenerationCurrent));}});
    }
    public void connect(GretlLanguageClient client){this.client=Objects.requireNonNull(client);}
    public CompletableFuture<Integer> exitSignal(){return exit;}
    ServerWorkspace workspace(){return workspace;}
    boolean markdown(){return markdown;}
    boolean hierarchicalSymbols(){return hierarchicalSymbols;}
    boolean lineFoldingOnly(){return lineFoldingOnly;}
    boolean codeActionLiterals(){return codeActionLiterals;}
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        if(!initializeRequested.compareAndSet(false,true))return CompletableFuture.failedFuture(ServerWorkspace.error(ResponseErrorCode.InvalidRequest,"ALREADY_INITIALIZED"));
        var response=new CompletableFuture<InitializeResult>();
        enqueue(()->initializeNow(params).whenComplete((value,failure)->{if(failure==null)response.complete(value);else response.completeExceptionally(failure);}),response);
        return response;
    }
    private synchronized CompletableFuture<InitializeResult> initializeNow(InitializeParams params) {
        if(state!=State.NEW)return CompletableFuture.failedFuture(ServerWorkspace.error(ResponseErrorCode.InvalidRequest,"ALREADY_INITIALIZED"));
        try {
            if(params.getCapabilities()!=null) {
                var capabilities=params.getCapabilities();
                var experimental=new Gson().toJsonTree(capabilities.getExperimental());
                workspace.includeTooling=experimental.isJsonObject()&&experimental.getAsJsonObject().has("gretlRuntime")
                        &&experimental.getAsJsonObject().get("gretlRuntime").isJsonObject()
                        &&experimental.getAsJsonObject().getAsJsonObject("gretlRuntime").has("includeToolingVersion")
                        &&experimental.getAsJsonObject().getAsJsonObject("gretlRuntime").get("includeToolingVersion").toString().equals("1");
                var general=capabilities.getGeneral();if(general!=null&&general.getPositionEncodings()!=null&&!general.getPositionEncodings().isEmpty()&&!general.getPositionEncodings().contains("utf-16"))throw new IllegalArgumentException("UTF16_REQUIRED");
                var ws=capabilities.getWorkspace();documentChanges=ws!=null&&ws.getWorkspaceEdit()!=null&&Boolean.TRUE.equals(ws.getWorkspaceEdit().getDocumentChanges());
                var td=capabilities.getTextDocument();
                markdown=td!=null&&td.getHover()!=null&&td.getHover().getContentFormat()!=null&&td.getHover().getContentFormat().contains(MarkupKind.MARKDOWN);
                related=td!=null&&td.getPublishDiagnostics()!=null&&Boolean.TRUE.equals(td.getPublishDiagnostics().getRelatedInformation());
                hierarchicalSymbols=td!=null&&td.getDocumentSymbol()!=null&&Boolean.TRUE.equals(td.getDocumentSymbol().getHierarchicalDocumentSymbolSupport());
                lineFoldingOnly=td!=null&&td.getFoldingRange()!=null&&Boolean.TRUE.equals(td.getFoldingRange().getLineFoldingOnly());
                codeActionLiterals=td!=null&&td.getCodeAction()!=null&&td.getCodeAction().getCodeActionLiteralSupport()!=null;
            }
            String language=params.getLocale()!=null&&params.getLocale().toLowerCase(Locale.ROOT).startsWith("de")?"de":"en";
            var configuration=ServerConfiguration.decode(new Gson().toJsonTree(params.getInitializationOptions()),new ServerConfiguration(language,false));
            workspace.configure(configuration);
            if(params.getWorkspaceFolders()!=null)for(var folder:params.getWorkspaceFolders())workspace.addRoot(URI.create(folder.getUri()));
            else if(params.getRootUri()!=null)workspace.addRoot(URI.create(params.getRootUri()));
            var capabilities=new ServerCapabilities();capabilities.setPositionEncoding("utf-16");
            var sync=new TextDocumentSyncOptions();sync.setOpenClose(true);sync.setChange(TextDocumentSyncKind.Incremental);sync.setSave(new SaveOptions(false));capabilities.setTextDocumentSync(sync);
            capabilities.setCompletionProvider(new CompletionOptions(false,List.of(".","'","\"",":",",")));
            capabilities.setHoverProvider(true);capabilities.setSignatureHelpProvider(new SignatureHelpOptions(List.of("(",",")));
            capabilities.setDefinitionProvider(true);capabilities.setReferencesProvider(true);capabilities.setDocumentSymbolProvider(true);
            capabilities.setDocumentLinkProvider(new DocumentLinkOptions(false));capabilities.setFoldingRangeProvider(true);
            if(documentChanges){var rename=new RenameOptions();rename.setPrepareProvider(true);capabilities.setRenameProvider(rename);if(codeActionLiterals)capabilities.setCodeActionProvider(new CodeActionOptions(List.of(CodeActionKind.QuickFix)));}
            var workspaceCapabilities=new WorkspaceServerCapabilities();var options=new WorkspaceFoldersOptions();options.setSupported(true);options.setChangeNotifications(true);workspaceCapabilities.setWorkspaceFolders(options);capabilities.setWorkspace(workspaceCapabilities);
            capabilities.setExperimental(Map.of("gretlRuntime",Map.of("protocolVersion",1,"catalogSchemaVersion",1,"graphSchemaVersion",2,"graph",true,"editPlanning",documentChanges,"navigation",true,"analysisNotifications",true,"includeToolingVersion",1,"taskFinalizersVersion",1)));
            state=State.INITIALIZED;
            var result=new InitializeResult();result.setCapabilities(capabilities);result.setServerInfo(new ServerInfo("gretl-runtime-lsp","6.0.0-SNAPSHOT"));return CompletableFuture.completedFuture(result);
        }catch(IOException|IllegalArgumentException invalid){return CompletableFuture.failedFuture(ServerWorkspace.error(ResponseErrorCode.InvalidParams,Objects.requireNonNullElse(invalid.getMessage(),"INITIALIZATION_FAILED")));}
    }
    public void initialized(InitializedParams params){requireActive();ready=true;workspace.schedule();}
    public CompletableFuture<Object> shutdown() {
        synchronized(this){if(state!=State.INITIALIZED)return CompletableFuture.failedFuture(ServerWorkspace.error(ResponseErrorCode.InvalidRequest,"SHUTDOWN_STATE"));state=State.SHUTTING_DOWN;}
        CompletableFuture<Void> previous;synchronized(order){previous=controlTail;}
        return previous.thenApplyAsync(ignored->{workspace.close();return null;});
    }
    public void exit(){int code=state==State.SHUTTING_DOWN?0:1;state=State.CLOSED;exit.complete(code);}
    public TextDocumentService getTextDocumentService(){return text;}
    public WorkspaceService getWorkspaceService(){return folders;}
    void requireActive(){if(state==State.NEW)throw ServerWorkspace.error(ResponseErrorCode.ServerNotInitialized,"SERVER_NOT_INITIALIZED");if(state!=State.INITIALIZED)throw ServerWorkspace.error(ResponseErrorCode.InvalidRequest,"SERVER_SHUTTING_DOWN");if(controlOverloaded)throw ServerWorkspace.error(ResponseErrorCode.RequestFailed,"CONTROL_QUEUE_LIMIT_RESTART_REQUIRED");}
    <T> CompletableFuture<T> query(String document,BiFunction<AnalysisSnapshot,CancellationToken,T> operation) {
        return deferred(()->workspace.request(workspace.document(document),List.of(),operation));
    }
    <T> CompletableFuture<T> physicalQuery(String document,BiFunction<AnalysisSnapshot,CancellationToken,T> operation){
        return deferred(()->workspace.request(workspace.document(document),List.of(),null,true,operation));
    }
    private void enqueue(Runnable action,CompletableFuture<?> failureTarget) {
        synchronized(order) {
            var done=new CompletableFuture<Void>();controlSequence++;
            try {control.execute(()->{try{action.run();}catch(RuntimeException failure){if(failureTarget!=null)failureTarget.completeExceptionally(failure);else log(ServerWorkspace.code(failure));}finally{done.complete(null);}});controlTail=done;}
            catch(RejectedExecutionException full){controlOverloaded=true;if(failureTarget!=null)failureTarget.completeExceptionally(ServerWorkspace.error(ResponseErrorCode.RequestFailed,"CONTROL_QUEUE_LIMIT_RESTART_REQUIRED"));else log("CONTROL_QUEUE_LIMIT_RESTART_REQUIRED");done.complete(null);}
        }
    }
    private <T> CompletableFuture<T> deferred(java.util.function.Supplier<CompletableFuture<T>> operation) {
        try{requireActive();}catch(RuntimeException stateError){return CompletableFuture.failedFuture(stateError);}
        var response=new CompletableFuture<T>();var active=new java.util.concurrent.atomic.AtomicReference<CompletableFuture<T>>();
        response.whenComplete((value,failure)->{if(response.isCancelled()&&active.get()!=null)active.get().cancel(false);});
        CompletableFuture<Void> barrier;long sequence;synchronized(order){barrier=controlTail;sequence=controlSequence;}
        barrier.thenRun(()-> {
            if(response.isDone())return;
            synchronized(order){if(sequence!=controlSequence){response.completeExceptionally(ServerWorkspace.error(ResponseErrorCode.ContentModified,"CONTROL_STATE_CHANGED"));return;}}
            try {
                requireActive();var future=operation.get();active.set(future);if(response.isCancelled())future.cancel(false);
                future.whenComplete((value,failure)-> {
                    synchronized(order){if(sequence!=controlSequence){response.completeExceptionally(ServerWorkspace.error(ResponseErrorCode.ContentModified,"CONTROL_STATE_CHANGED"));return;}}
                    if(controlOverloaded)response.completeExceptionally(ServerWorkspace.error(ResponseErrorCode.RequestFailed,"CONTROL_QUEUE_LIMIT_RESTART_REQUIRED"));
                    else if(failure==null)response.complete(value);else response.completeExceptionally(failure);
                });
            }catch(RuntimeException invalid){response.completeExceptionally(invalid instanceof org.eclipse.lsp4j.jsonrpc.ResponseErrorException?invalid:ServerWorkspace.error(ResponseErrorCode.InvalidParams,ServerWorkspace.code(invalid)));}
        });return response;
    }
    void notification(Runnable action){try{requireActive();enqueue(action,null);}catch(RuntimeException invalid){log(ServerWorkspace.code(invalid));}}
    void log(String code){if(client!=null)client.logMessage(new MessageParams(MessageType.Warning,code));}
    void ensureEdits(){if(!documentChanges)throw ServerWorkspace.error(ResponseErrorCode.RequestFailed,"CLIENT_DOCUMENT_CHANGES_REQUIRED");}
    private void publish(AnalysisSnapshot snapshot,CancellationToken cancellation) {
        if(!ready||client==null||state!=State.INITIALIZED)return;
        cancellation.check();
        emitDiagnostics(diagnosticState.replace(snapshot.analysis().capture().project().id(),snapshot.analysis().capture().generation(),LspDiagnosticMapper.map(snapshot,related)));
        var membership=workspace.membershipUpdate(snapshot);if(membership!=null)client.sourceMembershipChanged(membership);
        client.analysisReady(new AnalysisReady(responseContext(snapshot)));
    }
    private void emitDiagnostics(DiagnosticPublicationState.Batch batch) {
        if(!ready||client==null||state!=State.INITIALIZED)return;
        for(var diagnostics:batch.changes()) {
            if(diagnostics.getDiagnostics().isEmpty())workspace.documents.get(URI.create(diagnostics.getUri())).ifPresent(document->diagnostics.setVersion(document.source().stamp().bufferVersion()));
            client.publishDiagnostics(diagnostics);
        }
        if(batch.limited())log("DIAGNOSTIC_PROJECT_LIMIT");
    }
    private void includeFeature(int protocol,int feature){version(protocol);if(feature!=1||!workspace.includeTooling)throw new IllegalArgumentException("SOURCE_CONTEXT_CAPABILITY_REQUIRED");}
    private SourceContextsResponse sourceContextsResponse(ch.so.agi.gretl.tooling.workspace.SourceContextService.SourceContexts contexts){
        return new SourceContextsResponse(1,1,workspace.manager.serverInstance().toString(),contexts.source().toString(),
                Long.toString(contexts.ownerGeneration()),Long.toString(contexts.contextGeneration()),contexts.incarnation()==null?null:contexts.incarnation().toString(),
                contexts.ownersComplete(),contexts.selectionRequired(),contexts.selectedToken(),contexts.contexts().stream().map(c->{var a=c.application();return new SourceContextInfo(c.token(),a.id().project().toString(),a.id().entryDocument().toString(),a.id().ownerScope(),a.resourceBase()==null?null:a.resourceBase().toString(),a.role().name());}).toList());
    }
    public CompletableFuture<SourceContextsResponse> sourceContexts(SourceContextsRequest request){
        return deferred(()->{
            includeFeature(request.protocolVersion(),request.includeToolingVersion());var document=workspace.document(request.documentId());
            if(request.registerAsEntry()){workspace.manager.registerEntry(workspace.rootFor(document),document,true);workspace.contextChanged();}
            return workspace.request(document,List.of(),null,true,(snapshot,cancel)->{
                var capture=snapshot.analysis().capture();var contexts=workspace.sourceContexts.contextsFor(capture,document);
                if(request.registerAsEntry()){
                    var standalone=contexts.contexts().stream().filter(c->c.application().id().project().singleJob()&&c.application().id().entryDocument().equals(document)).toList();
                    if(standalone.size()==1)contexts=workspace.sourceContexts.select(capture,document,standalone.getFirst().token(),contexts.incarnation(),contexts.ownerGeneration());
                }
                return sourceContextsResponse(contexts);
            });
        });
    }
    public CompletableFuture<SourceContextsResponse> selectSourceContext(SelectSourceContextRequest request){
        return deferred(()->{
            includeFeature(request.protocolVersion(),request.includeToolingVersion());var document=workspace.document(request.documentId());
            return workspace.request(document,List.of(),null,true,(snapshot,cancel)->sourceContextsResponse(workspace.sourceContexts.select(snapshot.analysis().capture(),document,
                    required(request.contextToken(),64),request.incarnation()==null?null:Long.valueOf(required(request.incarnation(),20)),Long.parseLong(required(request.ownerGeneration(),20)))))
                    .thenApply(selected->{workspace.contextChanged();return selected;});
        });
    }
    public CompletableFuture<ConfirmSourceMembershipResponse> confirmSourceMembership(ConfirmSourceMembershipRequest request){
        return deferred(()->{
            includeFeature(request.protocolVersion(),request.includeToolingVersion());var document=workspace.document(request.documentId());
            return workspace.request(document,List.of(),null,true,(snapshot,cancel)->workspace.confirmMembership(snapshot,request));
        });
    }
    private Context responseContext(AnalysisSnapshot snapshot){
        var context=context(snapshot);
        return !workspace.includeTooling||workspace.membershipReady(snapshot)?context:new Context(context.protocolVersion(),context.schemaVersion(),context.serverInstanceId(),context.projectId(),context.workspaceGeneration(),context.snapshotId(),context.catalogId(),context.sourceStamps(),context.completeness(),List.of("INCLUDE_MEMBERSHIP_PENDING"),context.ownerGeneration(),context.contextGeneration(),context.contextDocumentId(),context.contextToken());
    }
    public CompletableFuture<CatalogResponse> catalog(CatalogRequest request) {
        try{requireActive();version(request.protocolVersion());if(request.catalogSchemaVersion()!=1)throw new IllegalArgumentException("CATALOG_SCHEMA_MISMATCH");var loaded=workspace.catalog.snapshot().result();return CompletableFuture.completedFuture(new CatalogResponse(1,1,workspace.manager.serverInstance().toString(),loaded.state().name(),loaded.catalog().orElse(null),loaded.problems()));}
        catch(RuntimeException invalid){return CompletableFuture.failedFuture(invalid instanceof org.eclipse.lsp4j.jsonrpc.ResponseErrorException?invalid:ServerWorkspace.error(ResponseErrorCode.InvalidParams,ServerWorkspace.code(invalid)));}
    }
    public CompletableFuture<GraphResponse> graph(GraphRequest request) {
        return deferred(()-> {
        requireActive();version(request.protocolVersion());var document=workspace.document(request.documentId());
        var sources=request.requestedSources()==null?List.<String>of():request.requestedSources();
        if(sources.size()>32)throw new IllegalArgumentException("SOURCE_REQUEST_LIMIT");
        var extra=sources.stream().map(workspace::document).toList();
        return workspace.request(document,extra,request.contextToken(),false,(snapshot,cancel)-> {
            version(request.protocolVersion());expected(snapshot,request.expectedSnapshotId(),false);
            var graph=new JobGraphProjector().project(snapshot,new JobGraphProjector.GraphOptions(request.includeInnerInvocations(),true),cancel);
            var preview=request.previewTargets()==null?null:new ch.so.agi.gretl.tooling.graph.TargetPreviewProjector().project(snapshot,request.previewTargets(),cancel);
            var forms=new ch.so.agi.gretl.tooling.graph.GraphFormProjector().project(snapshot,graph,cancel);
            return new GraphResponse(responseContext(snapshot),new GraphPayload(workspace.includeTooling&&!workspace.membershipReady(snapshot)&&graph.status()==JobGraph.Status.COMPLETE?JobGraph.Status.PARTIAL:graph.status(),graph.nodes(),graph.edges(),graph.parameters(),graph.cleanup(),graph.diagnostics(),preview,forms));
        });
        });
    }
    public CompletableFuture<EditResponse> planEdit(EditRequest request) {
        return deferred(()-> {
            requireActive();ensureEdits();version(request.protocolVersion());
            var document=workspace.document(request.documentId());var intent=Objects.requireNonNull(request.intent());
            return workspace.request(document,List.of(),request.contextToken(),false,(snapshot,cancel)-> {
                workspace.requireMembershipReady(snapshot);expected(snapshot,request.expectedSnapshotId(),true);
                var planner=new SemanticEditPlanner();var style=new EditStyle("    ",request.requireOpenDocuments());
                var destination="SetDefaults".equals(intent.kind())?(snapshot.analysis().capture().project().projectMode()?snapshot.analysis().capture().project().settingsId():snapshot.analysis().capture().project().id().anchor()):document;
                var result="ConfigureTask".equals(intent.kind())?planner.planConfiguration(snapshot,Objects.requireNonNull(intent.target()).core(),values(intent.fields()),intent.removedFields()==null?Set.of():intent.removedFields(),values(intent.options()),style,cancel):planner.plan(snapshot,intent.core(destination),style,cancel);
                var plan=result.plan().orElse(null);if(plan!=null){workspace.requireWritableTargets(plan);workspace.retain(snapshot,plan);}
                return new EditResponse(context(snapshot),result.capability(),plan==null?null:plan.id().toString(),plan==null?null:new LspQueryMapper(snapshot).edit(plan),plan==null?List.of():plan.notices());
            });
        });
    }
    public CompletableFuture<ValidationResponse> validateEdit(ValidationRequest request) {
        return deferred(()-> {
            requireActive();version(request.protocolVersion());var original=workspace.planSnapshot(required(request.planId(),64));
            var selection=original.contextSelection();var document=selection==null?original.analysis().capture().project().id().anchor():selection.application().sourceDocument();
            return workspace.request(document,List.of(),selection==null?null:selection.token(),false,(current,cancel)-> {
                var plan=workspace.plan(request.planId());workspace.requireWritableTargets(plan);boolean valid=current.id().equals(original.id())&&Objects.equals(request.expectedSnapshotId(),original.id().toString())&&plan.isCurrent(current);
                return new ValidationResponse(1,valid,valid?List.of():List.of("EDIT_STALE_SNAPSHOT"));
            }).exceptionally(failure->new ValidationResponse(1,false,List.of("EDIT_STALE_SNAPSHOT")));
        });
    }
    public CompletableFuture<NavigationResponse> navigate(NavigationRequest request) {
        return deferred(()->workspace.request(workspace.document(request.documentId()),List.of(),request.contextToken(),false,(snapshot,cancel)-> {
            version(request.protocolVersion());expected(snapshot,request.expectedSnapshotId(),true);
            var projector=new JobGraphProjector();var graph=projector.project(snapshot,new JobGraphProjector.GraphOptions(true,true),cancel);
            var mapper=new LspQueryMapper(snapshot);
            if(request.elementId()!=null){
                String element=required(request.elementId(),128);var location=projector.sourceFor(graph,element,snapshot.id()).flatMap(mapper::location).orElse(null);
                var owner=graph.edges().stream().filter(e->e.id().equals(element)).map(e->e.target()).findFirst().orElse(element);
                var application=java.util.stream.Stream.of(graph.nodes(),graph.parameters(),graph.cleanup()).flatMap(List::stream)
                        .filter(n->n.id().equals(owner)&&n.symbol()!=null).map(n->snapshot.analysis().unique(n.symbol())).flatMap(Optional::stream).map(s->s.application()).findFirst().orElse(null);
                SourceContextInfo selected=null;
                if(workspace.includeTooling&&application!=null&&location!=null){
                    var choices=workspace.sourceContexts.contextsFor(snapshot.analysis().capture(),URI.create(location.getUri()));
                    var wanted=application.id();var choice=choices.contexts().stream().filter(c->c.application().id().equals(wanted)).findFirst();
                    if(choice.isPresent())selected=new SourceContextInfo(choice.get().token(),wanted.project().toString(),wanted.entryDocument().toString(),wanted.ownerScope(),application.resourceBase()==null?null:application.resourceBase().toString(),application.role().name());
                }
                return new NavigationResponse(context(snapshot),location,List.of(),selected);
            }
            return new NavigationResponse(context(snapshot),null,projector.elementsAt(snapshot,graph,workspace.document(request.documentId()),Objects.requireNonNull(request.offsetUtf16()),snapshot.id(),workspace.includeTooling?workspace.sourceContexts.resolveForQuery(snapshot.analysis().capture(),workspace.document(request.documentId()),request.contextToken()).application().id():null));
        }));
    }
    private void expected(AnalysisSnapshot snapshot,String expected,boolean required){if(required&&expected==null||expected!=null&&!snapshot.id().toString().equals(expected))throw ServerWorkspace.error(ResponseErrorCode.ContentModified,"SNAPSHOT_MISMATCH");}
    public void close(){state=State.CLOSED;control.shutdownNow();workspace.close();try{control.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}
}
