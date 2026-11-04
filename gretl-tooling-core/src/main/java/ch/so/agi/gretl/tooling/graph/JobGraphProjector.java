package ch.so.agi.gretl.tooling.graph;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.edit.*;
import ch.so.agi.gretl.tooling.graph.JobGraph.*;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import ch.so.agi.gretl.tooling.source.TextSpan;
import java.net.URI;
import java.util.*;

/** Projects only captured semantics. It neither selects an executable plan nor invents runtime state. */
public final class JobGraphProjector {
    public record GraphOptions(boolean includeInnerInvocations,boolean requireOpenDocuments) {
        public static GraphOptions defaults(){return new GraphOptions(false,true);}
    }
    public JobGraph project(AnalysisSnapshot snapshot,GraphOptions options,CancellationToken cancellation) {
        Objects.requireNonNull(options);cancellation.check();
        var analysis=snapshot.analysis();
        var nodes=new ArrayList<Node>();var parameters=new ArrayList<Node>();var cleanup=new ArrayList<Node>();var edges=new ArrayList<Edge>();
        var symbols=new LinkedHashMap<SymbolId,String>();var containers=new LinkedHashMap<URI,String>();
        String project=analysis.capture().project().id().toString();
        for(var job:analysis.capture().project().jobs()) {
            cancellation.check();
            if(analysis.capture().project().projectMode()) {
                String id=id(snapshot,"node",nodes.size());containers.put(job.documentId(),id);
                nodes.add(new Node(id,key(project,job.scope().canonical(),"PROJECT"),null,new Anchor(job.documentId(),new TextSpan(0,0)),NodeKind.PROJECT,
                        job.scope().canonical(),null,null,Map.of(),List.of(),List.of(),List.of()));
            }
        }
        int next=nodes.size(),innerNext=0;
        for(var entry:analysis.symbols().entrySet())for(var symbol:entry.getValue()) {
            cancellation.check();
            String id=id(snapshot,"node",next++);
            if(entry.getValue().size()==1)symbols.put(symbol.id(),id);
            NodeKind kind=switch(symbol.declaration().kind()) {
                case TASK->NodeKind.TASK;case ACTION->NodeKind.ACTION;case PARAMETER->NodeKind.PARAMETER;case CLEANUP->NodeKind.CLEANUP;
                default->throw new IllegalStateException("Non-symbol declaration");
            };
            var capabilities=new LinkedHashMap<String,EditCapability>();
            var style=new EditStyle("    ",options.requireOpenDocuments());
            var editability=new EditabilityAnalyzer();
            capabilities.put("rename",editability.assess(snapshot,new SemanticEditIntent.RenameSymbol(symbol.id(),symbol.localName()),style));
            if(symbol.id().namespace()==Namespace.TASK) {
                capabilities.put("delete",editability.assess(snapshot,new SemanticEditIntent.DeleteTask(symbol.id()),style));
                capabilities.put("options",editability.assess(snapshot,new SemanticEditIntent.SetTaskOption(symbol.id(),"description",new EditableValue.Literal(null)),style));
                if(symbol.operation()!=null)for(var input:symbol.operation().inputs())capabilities.put("field:"+input.name(),editability.assess(snapshot,new SemanticEditIntent.SetField(symbol.id(),input.name(),new EditableValue.Literal(null)),style));
            }
            var operation=symbol.operation();
            var anchor=new Anchor(symbol.anchor().documentId(),symbol.declaration().statementSpan());
            var diagnostics=analysis.diagnostics().stream().filter(d->inside(anchor,d.anchor())).toList();
            var node=new Node(id,entry.getValue().size()==1?key(project,symbol.id().namespace().name(),symbol.id().qualifiedName(),kind.name()):null,
                    symbol.id(),anchor,kind,symbol.id().qualifiedName(),operation==null?null:operation.operationId(),containers.get(anchor.documentId()),capabilities,
                    operation==null?List.of():operation.inputs(),operation==null?List.of():operation.outputs(),diagnostics);
            if(kind==NodeKind.PARAMETER)parameters.add(node);else if(kind==NodeKind.CLEANUP)cleanup.add(node);else nodes.add(node);
            if(options.includeInnerInvocations())for(var inner:symbol.declaration().invocations()) {
                cancellation.check();
                String title=inner.operation() instanceof NameValue name?name.name():"Dynamic invocation";
                var innerOperation=analysis.capture().catalog().result().catalog().stream().flatMap(c->c.operations().stream()).filter(o->o.symbol().equals(title)).findFirst();
                // Explanatory children have no orchestrated symbol or independent edit capability.
                nodes.add(new Node(id(snapshot,"inner",innerNext++),null,null,new Anchor(anchor.documentId(),inner.span()),NodeKind.INNER_INVOCATION,title,
                        innerOperation.map(o->o.operationId()).orElse(null),id,Map.of(),innerOperation.map(o->o.inputs()).orElse(List.of()),innerOperation.map(o->o.outputs()).orElse(List.of()),List.of()));
            }
        }
        for(var document:analysis.capture().project().documents().values()) {
            cancellation.check();
            for(var declaration:document.declarations())if(declaration.name()==null&&declaration.kind()!=DeclarationKind.DEFAULTS&&declaration.kind()!=DeclarationKind.INCLUDE) {
                nodes.add(dynamic(snapshot,next++,document.source().stamp().documentId(),declaration.statementSpan(),containers));
            }
            for(var opaque:document.opaque())if(opaque.mayRegisterDeclarations())nodes.add(dynamic(snapshot,next++,document.source().stamp().documentId(),opaque.span(),containers));
        }
        for(var reference:analysis.references()) {
            cancellation.check();
            if(reference.resolution()!=Resolution.RESOLVED||reference.owner()==null)continue;
            String from=symbols.get(reference.target().orElseThrow()),to=symbols.get(reference.owner());
            if(from==null||to==null)continue;
            EdgeKind kind=switch(reference.kind()) {
                case DEPENDS_ON->EdgeKind.DEPENDS_ON;case AFTER->EdgeKind.AFTER;
                case RESULT,RESULT_OPTIONAL->EdgeKind.RESULT_BINDING;default->null;
            };
            if(kind==null)continue;
            String output=null,input=null;boolean known=false;
            if(kind==EdgeKind.RESULT_BINDING&&reference.kind()==ReferenceKind.RESULT) {
                var owner=analysis.unique(reference.owner()).orElseThrow();
                for(var property:owner.declaration().properties())if(property.receiver() instanceof CallValue call&&call.name().equals("result")&&call.span().contains(reference.anchor().span())
                        &&call.arguments().size()==1&&call.arguments().getFirst() instanceof LiteralValue literal&&literal.value() instanceof String) {
                    output=property.property();
                    for(var field:owner.declaration().fields())if(field.value().span().equals(property.span()))input=field.name();
                    var producer=analysis.unique(reference.target().orElseThrow()).orElseThrow();
                    String selectedOutput=output,selectedInput=input;
                    known=producer.operation()!=null&&owner.operation()!=null&&input!=null
                            &&producer.operation().outputs().stream().anyMatch(o->o.name().equals(selectedOutput))
                            &&owner.operation().inputs().stream().anyMatch(i->i.name().equals(selectedInput));
                }
            }
            edges.add(new Edge(id(snapshot,"edge",edges.size()),from,to,kind,reference.anchor(),output,input,known));
        }
        var capabilities=analysis.capabilities();
        Status status=analysis.diagnostics().stream().anyMatch(d->d.severity()==Severity.ERROR)?Status.INVALID:
                capabilities.bounded()&&capabilities.declarationsComplete()&&capabilities.referencesComplete()&&capabilities.graphComplete()
                        &&capabilities.catalogAvailable()&&capabilities.fieldAssignmentsComplete()&&capabilities.executionComplete()?Status.COMPLETE:Status.PARTIAL;
        return new JobGraph(snapshot.id(),EditPreconditions.capture(snapshot),status,nodes,edges,parameters,cleanup,analysis.diagnostics());
    }

    public Optional<Anchor> sourceFor(JobGraph graph,String elementId,UUID expectedSnapshot) {
        requireSnapshot(graph,expectedSnapshot);
        var node=allNodes(graph).filter(n->n.id().equals(elementId)).findFirst();
        if(node.isPresent())return Optional.of(node.get().anchor());
        return graph.edges().stream().filter(e->e.id().equals(elementId)).map(Edge::anchor).findFirst();
    }
    public List<String> elementsAt(JobGraph graph,URI document,int offset,UUID expectedSnapshot) {
        requireSnapshot(graph,expectedSnapshot);
        var result=new ArrayList<String>();
        allNodes(graph).filter(n->n.anchor().documentId().equals(document)&&n.anchor().span()!=null&&n.anchor().span().contains(offset)).forEach(n->result.add(n.id()));
        graph.edges().stream().filter(e->e.anchor().documentId().equals(document)&&e.anchor().span()!=null&&e.anchor().span().contains(offset)).forEach(e->result.add(e.id()));
        return List.copyOf(result);
    }
    private java.util.stream.Stream<Node> allNodes(JobGraph graph){return java.util.stream.Stream.of(graph.nodes(),graph.parameters(),graph.cleanup()).flatMap(List::stream);}
    private void requireSnapshot(JobGraph graph,UUID expected){if(!graph.snapshotId().equals(expected))throw new IllegalArgumentException("GRAPH_STALE_SNAPSHOT");}
    private Node dynamic(AnalysisSnapshot snapshot,int index,URI document,TextSpan span,Map<URI,String> containers) {
        return new Node(id(snapshot,"node",index),null,null,new Anchor(document,span),NodeKind.DYNAMIC_CODE,"Dynamic code",null,containers.get(document),Map.of(),List.of(),List.of(),List.of());
    }
    private boolean inside(Anchor parent,Anchor child){return parent.documentId().equals(child.documentId())&&parent.span()!=null&&child.span()!=null&&parent.span().contains(child.span());}
    private String id(AnalysisSnapshot snapshot,String kind,int index){return snapshot.id()+":"+kind+":"+index;}
    private String key(String...parts){var key=new StringBuilder();for(var part:parts)key.append(part.length()).append(':').append(part);return key.toString();}
}
