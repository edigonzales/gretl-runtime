package ch.so.agi.gretl.tooling.workspace;

import java.net.URI;
import java.util.*;
import static ch.so.agi.gretl.tooling.workspace.IncludeModel.*;
import ch.so.agi.gretl.tooling.workspace.WorkspaceManager.WorkspaceCapture;

/** Data-only confirmed context selection. Panel tokens resolve without changing the editor selection. */
public final class SourceContextService {
    public record SourceContext(String token,SourceApplication application) { }
    public record SourceContexts(URI source,List<SourceContext> contexts,boolean ownersComplete,long ownerGeneration,
                                 long contextGeneration,Long incarnation,String selectedToken,boolean selectionRequired) {
        public SourceContexts { contexts=List.copyOf(contexts); }
    }
    private record State(UUID server,long ownerGeneration,Long incarnation,List<SourceContext> contexts,String selected,long generation) { }
    private final LinkedHashMap<URI,State> states=new LinkedHashMap<>(16,.75f,true);
    private long generation;
    public synchronized SourceContexts contextsFor(WorkspaceCapture capture,URI source) {
        source=DocumentStore.identity(source);
        if(!capture.readPolicy().permits(source))throw new IllegalArgumentException("SOURCE_OUTSIDE_POLICY");
        var version=capture.project().sources().versions().get(source);Long incarnation=version==null?null:version.bufferIncarnation();
        var applications=capture.ownership().contexts(source).stream().sorted(Comparator.comparing(a->a.id().toString())).toList();
        var state=states.get(source);
        if(state==null||!state.server().equals(capture.serverInstance())||state.ownerGeneration()!=capture.ownership().generation()
                ||!Objects.equals(state.incarnation(),incarnation)||!state.contexts().stream().map(SourceContext::application).toList().equals(applications)){
            ApplicationId previousSelection=null;
            if(state!=null&&state.server().equals(capture.serverInstance())&&Objects.equals(state.incarnation(),incarnation)
                    &&state.contexts().stream().map(c->c.application().id()).collect(java.util.stream.Collectors.toSet())
                        .equals(applications.stream().map(SourceApplication::id).collect(java.util.stream.Collectors.toSet())))
                {
                    String selectedToken=state.selected();
                    previousSelection=state.contexts().stream().filter(c->c.token().equals(selectedToken)).map(c->c.application().id()).findFirst().orElse(null);
                }
            var contexts=applications.stream().limit(512).map(a->new SourceContext(UUID.randomUUID().toString(),a)).toList();
            String selected=contexts.size()==1?contexts.getFirst().token():null;
            if(selected==null&&previousSelection!=null)for(var context:contexts)if(context.application().id().equals(previousSelection))selected=context.token();
            state=new State(capture.serverInstance(),capture.ownership().generation(),incarnation,contexts,selected,++generation);
            states.put(source,state);while(states.size()>128)states.remove(states.keySet().iterator().next());
        }
        return new SourceContexts(source,state.contexts(),capture.ownership().complete()&&applications.size()<=512,state.ownerGeneration(),state.generation(),incarnation,state.selected(),state.contexts().size()>1&&state.selected()==null);
    }
    public synchronized SourceContexts select(WorkspaceCapture capture,URI source,String token,Long incarnation,long ownerGeneration) {
        var contexts=contextsFor(capture,source);
        if(ownerGeneration!=contexts.ownerGeneration()||!Objects.equals(incarnation,contexts.incarnation())
                ||contexts.contexts().stream().noneMatch(c->c.token().equals(token)))throw new IllegalArgumentException("SOURCE_CONTEXT_STALE");
        var previous=states.get(contexts.source());
        if(!Objects.equals(previous.selected(),token))states.put(contexts.source(),new State(previous.server(),previous.ownerGeneration(),incarnation,previous.contexts(),token,++generation));
        return contextsFor(capture,source);
    }
    public synchronized SourceContext resolveForQuery(WorkspaceCapture capture,URI source,String panelToken) {
        var contexts=contextsFor(capture,source);String token=panelToken==null?contexts.selectedToken():panelToken;
        if(token==null)throw new IllegalArgumentException(contexts.contexts().isEmpty()?"SOURCE_CONTEXT_UNKNOWN":"SOURCE_CONTEXT_SELECTION_REQUIRED");
        return contexts.contexts().stream().filter(c->c.token().equals(token)).findFirst().orElseThrow(()->new IllegalArgumentException("SOURCE_CONTEXT_STALE"));
    }
    public synchronized void clear(){states.clear();generation++;}
}
