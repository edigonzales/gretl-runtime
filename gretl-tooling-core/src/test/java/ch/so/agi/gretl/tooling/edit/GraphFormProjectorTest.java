package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.graph.*;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.edit.EditVerifierTest.*;
import static org.junit.jupiter.api.Assertions.*;

class GraphFormProjectorTest {
    final GraphFormProjector projector=new GraphFormProjector();
    JobGraph graph(ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot snapshot){return new JobGraphProjector().project(snapshot,JobGraphProjector.GraphOptions.defaults(),CancellationToken.NONE);}
    @Test void projectsBoundReferencesAndExactNumericContainersWithoutReacquiringSources(){
        try(var fixture=new Fixture()){
            String source="parameter('src',description:'Source',defaultValue:'x')\n"
                    +"task('a', Echo) { input=parameter('src'); mode=[count:9223372036854775807L, nested:[file('ä data'),workPath('out'),secret('alias')]] }\n"
                    +"task('b', Echo, dependsOn:'a') { input=result('a').value }\ndefaults 'b'\n";
            fixture.files.put(JOB,source);var snapshot=fixture.capture();var graph=graph(snapshot);var projected=projector.project(snapshot,graph,CancellationToken.NONE);
            var a=graph.nodes().stream().filter(n->n.title().equals("a")).findFirst().orElseThrow();
            var fields=projected.nodes().get(a.id()).fields();assertEquals(new SymbolId(Namespace.PARAMETER,"src"),fields.get("input").value().symbol());
            var mode=fields.get("mode").value();assertEquals("Map",mode.kind());assertEquals("9223372036854775807",mode.entries().get("count").scalar());assertEquals("Long",mode.entries().get("count").kind());
            assertEquals(List.of("JobFileRef","WorkPathRef","SecretAliasRef"),mode.entries().get("nested").items().stream().map(GraphFormProjector.Value::kind).toList());
            var b=graph.nodes().stream().filter(n->n.title().equals("b")).findFirst().orElseThrow();var binding=projected.nodes().get(b.id()).fields().get("input").value();assertEquals("ResultFieldRef",binding.kind());assertEquals(new SymbolId(Namespace.TASK,"a"),binding.symbol());assertEquals("value",binding.field());
            assertEquals(JOB,projected.defaultsDocument());assertEquals(List.of(new SymbolId(Namespace.TASK,"b")),projected.defaults());assertEquals(source.length(),projected.insertions().getFirst().insertionOffset());assertEquals(EditCapability.State.NEEDS_OPEN_DOCUMENTS,projected.insertions().getFirst().addTask().state());assertEquals(source,fixture.files.get(JOB));
        }
    }
    @Test void opaqueAndDuplicateFieldsRemainExplicitlyUnavailableWhileSimpleNeighbourValuesSurvive(){
        try(var fixture=new Fixture()){
            String source="// untouched 😀\ntask('a', Echo) { input=\"${System.setProperty('gretl.form.executed','bad')}\"; mode='FAST' }\n";
            fixture.files.put(JOB,source);var snapshot=fixture.capture();var graph=graph(snapshot);var node=graph.nodes().getFirst();var fields=projector.project(snapshot,graph,CancellationToken.NONE).nodes().get(node.id()).fields();
            assertTrue(fields.get("input").present());assertNull(fields.get("input").value());assertEquals("EDIT_OPAQUE_VALUE",fields.get("input").reason());assertEquals("FAST",fields.get("mode").value().scalar());assertNull(System.getProperty("gretl.form.executed"));assertEquals(source,fixture.files.get(JOB));
            fixture.files.put(JOB,"task('a', Echo) { input='one'; input='two' }\n");snapshot=fixture.capture();graph=graph(snapshot);fields=projector.project(snapshot,graph,CancellationToken.NONE).nodes().get(graph.nodes().getFirst().id()).fields();assertEquals("EDIT_MULTIPLE_ASSIGNMENTS",fields.get("input").reason());assertFalse(fields.get("mode").present());
        }
    }
    @Test void staleGraphAndCancellationNeverProduceEditableDrafts(){
        try(var fixture=new Fixture()){
            var before=fixture.capture();var graph=graph(before);var later=fixture.capture();assertThrows(IllegalArgumentException.class,()->projector.project(later,graph,CancellationToken.NONE));assertThrows(CancellationException.class,()->projector.project(before,graph,()->true));
        }
    }
}
