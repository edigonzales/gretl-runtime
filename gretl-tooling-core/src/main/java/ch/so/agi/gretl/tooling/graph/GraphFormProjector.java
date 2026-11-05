package ch.so.agi.gretl.tooling.graph;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.edit.*;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import java.net.URI;
import java.util.*;

/** Read-only structured form values from the already bound Core snapshot, never source evaluation. */
public final class GraphFormProjector {
    public record Value(String kind,String scalar,List<Value> items,Map<String,Value> entries,SymbolId symbol,String field) { }
    public record Field(boolean present,Value value,String reason) { }
    public record NodeValues(Map<String,Field> fields,Map<String,Field> options) {
        public NodeValues {fields=Map.copyOf(fields);options=Map.copyOf(options);}
    }
    public record Insertion(URI documentId,int insertionOffset,EditCapability addTask,EditCapability addAction,EditCapability addParameter) { }
    public record Projection(Map<String,NodeValues> nodes,List<Insertion> insertions,URI defaultsDocument,List<SymbolId> defaults) {
        public Projection {nodes=Map.copyOf(nodes);insertions=List.copyOf(insertions);defaults=List.copyOf(defaults);}
    }
    public Projection project(AnalysisSnapshot snapshot,JobGraph graph,CancellationToken cancellation) {
        if(!graph.snapshotId().equals(snapshot.id()))throw new IllegalArgumentException("GRAPH_STALE_SNAPSHOT");
        var values=new LinkedHashMap<String,NodeValues>();
        var all=new ArrayList<>(graph.nodes());all.addAll(graph.parameters());all.addAll(graph.cleanup());
        for(var node:all){
            cancellation.check();if(node.symbol()==null)continue;
            var symbol=snapshot.analysis().unique(node.symbol()).orElse(null);if(symbol==null)continue;
            var fields=new LinkedHashMap<String,Field>();
            if(symbol.operation()!=null)for(var input:symbol.operation().inputs()){
                var assignments=symbol.declaration().fields().stream().filter(f->f.name().equals(input.name())).toList();
                fields.put(input.name(),assignments.isEmpty()?new Field(false,null,null):assignments.size()>1?new Field(true,null,"EDIT_MULTIPLE_ASSIGNMENTS"):
                        field(snapshot,symbol,assignments.getFirst().value(),cancellation));
            }
            var options=new LinkedHashMap<String,Field>();
            for(var option:symbol.declaration().options())if(option.key() instanceof LiteralValue literal&&literal.value() instanceof String name){
                if(name.length()>1024||!options.containsKey(name)&&options.size()>=128)continue;
                if(options.containsKey(name))options.put(name,new Field(true,null,"EDIT_MULTIPLE_OPTIONS"));
                else options.put(name,field(snapshot,symbol,option.value(),cancellation));
            }
            values.put(node.id(),new NodeValues(fields,options));
        }
        var insertions=new ArrayList<Insertion>();var analyzer=new EditabilityAnalyzer();var style=EditStyle.openDocuments();
        var project=snapshot.analysis().capture().project();
        for(var application:project.includeClosure().applications().stream().filter(a->snapshot.analysis().capture().ownership().exclusive(a.id().sourceDocument())).toList()){
            cancellation.check();var source=snapshot.source(application.id().sourceDocument()).orElse(null);if(source==null)continue;
            int end=source.text().length();var uri=application.id().sourceDocument();
            insertions.add(new Insertion(uri,end,
                    analyzer.assess(snapshot,new SemanticEditIntent.AddTask(uri,end,"draft","",Map.of()),style),
                    analyzer.assess(snapshot,new SemanticEditIntent.AddAction(uri,end,"draft"),style),
                    analyzer.assess(snapshot,new SemanticEditIntent.AddParameter(uri,end,new SemanticEditIntent.ParameterDeclaration("draft","","text",false,null)),style)));
        }
        URI defaultsDocument=project.projectMode()?project.settingsId():project.id().anchor();
        return new Projection(values,insertions,defaultsDocument,snapshot.analysis().defaultTargets().stream().map(name->new SymbolId(Namespace.TASK,name)).toList());
    }
    private Field field(AnalysisSnapshot snapshot,Symbol owner,ValueExpression expression,CancellationToken cancellation){
        try{return new Field(true,value(snapshot,owner,expression,0,new int[]{0},cancellation),null);}
        catch(IllegalArgumentException opaque){return new Field(true,null,opaque.getMessage());}
    }
    private Value value(AnalysisSnapshot snapshot,Symbol owner,ValueExpression expression,int depth,int[] budget,CancellationToken cancellation){
        cancellation.check();if(depth>16||++budget[0]>4096)throw new IllegalArgumentException("EDIT_VALUE_LIMIT");
        if(expression instanceof LiteralValue literal){
            Object v=literal.value();if(v==null)return scalar("Null",null);
            String text=v.toString();if(text.length()>65536)throw new IllegalArgumentException("EDIT_VALUE_LIMIT");
            String kind=v instanceof String?"Text":v instanceof Boolean?"Boolean":v instanceof Integer||v instanceof Short||v instanceof Byte?"Integer":
                    v instanceof Long?"Long":v instanceof java.math.BigInteger?"BigInteger":v instanceof java.math.BigDecimal?"Decimal":null;
            if(kind==null)throw new IllegalArgumentException("EDIT_OPAQUE_VALUE");return scalar(kind,text);
        }
        if(expression instanceof ListValue list){
            if(list.items().size()>1024)throw new IllegalArgumentException("EDIT_VALUE_LIMIT");
            return new Value("List",null,list.items().stream().map(item->value(snapshot,owner,item,depth+1,budget,cancellation)).toList(),null,null,null);
        }
        if(expression instanceof MapValue map){
            if(map.entries().size()>1024)throw new IllegalArgumentException("EDIT_VALUE_LIMIT");var entries=new LinkedHashMap<String,Value>();
            for(var entry:map.entries()){
                if(!(entry.key() instanceof LiteralValue literal)||!(literal.value() instanceof String key)||key.length()>65536||entries.containsKey(key))throw new IllegalArgumentException("EDIT_OPAQUE_VALUE");
                entries.put(key,value(snapshot,owner,entry.value(),depth+1,budget,cancellation));
            }
            return new Value("Map",null,null,Collections.unmodifiableMap(entries),null,null);
        }
        if(expression instanceof CallValue call&&call.arguments().size()==1){
            if(Set.of("file","workPath","secret").contains(call.name())&&call.arguments().getFirst() instanceof LiteralValue literal&&literal.value() instanceof String text){
                if(text.length()>(call.name().equals("secret")?1024:4096))throw new IllegalArgumentException("EDIT_VALUE_LIMIT");
                return scalar(switch(call.name()){case "file"->"JobFileRef";case "workPath"->"WorkPathRef";default->"SecretAliasRef";},text);
            }
            if(call.name().equals("parameter"))return new Value("ParameterRef",null,null,null,resolved(snapshot,owner,call,ReferenceKind.PARAMETER),null);
        }
        if(expression instanceof PropertyValue property&&property.receiver() instanceof CallValue call&&call.name().equals("result")&&call.arguments().size()==1)
            return new Value("ResultFieldRef",null,null,null,resolved(snapshot,owner,call,ReferenceKind.RESULT),property.property());
        throw new IllegalArgumentException("EDIT_OPAQUE_VALUE");
    }
    private SymbolId resolved(AnalysisSnapshot snapshot,Symbol owner,CallValue call,ReferenceKind kind){
        var targets=snapshot.analysis().references().stream().filter(r->r.kind()==kind&&owner.id().equals(r.owner())&&owner.anchor().documentId().equals(r.anchor().documentId())&&call.span().contains(r.anchor().span()))
                .map(Reference::target).flatMap(Optional::stream).distinct().toList();
        if(targets.size()!=1)throw new IllegalArgumentException("EDIT_UNRESOLVED_VALUE");return targets.getFirst();
    }
    private Value scalar(String kind,String scalar){return new Value(kind,scalar,null,null,null,null);}
}
