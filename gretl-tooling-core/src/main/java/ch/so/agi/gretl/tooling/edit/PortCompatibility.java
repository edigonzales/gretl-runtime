package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.catalog.OperationCatalog.ValueShape;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.SymbolId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Data-schema compatibility only. Opaque element types and nullable outputs are not invented into proof. */
final class PortCompatibility {
    private static final ValueShape UNKNOWN=new ValueShape("Opaque","unknown",List.of());

    void require(AnalysisSnapshot snapshot,SymbolId producer,String outputField,ValueShape input) {
        var symbol=snapshot.analysis().unique(producer).orElseThrow(()->new IllegalArgumentException("EDIT_AMBIGUOUS_TARGET"));
        if(symbol.operation()==null||List.of("class","metaClass").contains(outputField))throw new IllegalArgumentException("EDIT_UNKNOWN_SCHEMA");
        var output=symbol.operation().outputs().stream().filter(f->f.name().equals(outputField)).findFirst()
                .orElseThrow(()->new IllegalArgumentException("EDIT_UNKNOWN_OUTPUT"));
        if(output.nullable()||!known(output.shape(),0)||!output.shape().equals(input))throw new IllegalArgumentException("EDIT_PORT_COMPATIBILITY");
    }

    Set<SymbolId> dependencies(AnalysisSnapshot snapshot,EditableValue value,ValueShape input) {
        var result=new LinkedHashSet<SymbolId>();
        visit(snapshot,value,input,result,new int[]{0},0);
        return java.util.Collections.unmodifiableSet(result);
    }

    private void visit(AnalysisSnapshot snapshot,EditableValue value,ValueShape input,Set<SymbolId> result,int[] visited,int depth) {
        if(++visited[0]>65_536||depth>16)throw new IllegalArgumentException("EDIT_VALUE_LIMIT");
        switch(value) {
            case EditableValue.ResultFieldRef reference -> {
                require(snapshot,reference.producer(),reference.outputField(),input);
                result.add(reference.producer());
            }
            case EditableValue.Sequence sequence -> {
                var element=input.kind().equals("ListOf")&&input.children().size()==1?input.children().getFirst():UNKNOWN;
                sequence.items().forEach(item->visit(snapshot,item,element,result,visited,depth+1));
            }
            case EditableValue.Mapping mapping -> {
                var element=input.kind().equals("MapOf")&&input.children().size()==2?input.children().get(1):UNKNOWN;
                mapping.entries().values().forEach(item->visit(snapshot,item,element,result,visited,depth+1));
            }
            default -> { }
        }
    }

    private boolean known(ValueShape shape,int depth) {
        if(depth>16)return false;
        if(List.of("Text","Integer","Decimal","Boolean","Path").contains(shape.kind()))return shape.children().isEmpty();
        return (shape.kind().equals("ListOf")&&shape.children().size()==1||shape.kind().equals("MapOf")&&shape.children().size()==2)
                &&shape.children().stream().allMatch(child->known(child,depth+1));
    }
}
