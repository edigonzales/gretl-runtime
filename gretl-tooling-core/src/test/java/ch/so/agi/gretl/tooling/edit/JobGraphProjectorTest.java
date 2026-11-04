package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.graph.*;
import ch.so.agi.gretl.tooling.graph.JobGraph.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.edit.EditVerifierTest.*;
import static org.junit.jupiter.api.Assertions.*;

class JobGraphProjectorTest {
    final JobGraphProjector projector=new JobGraphProjector();
    @Test void projectsAllDefinitionsSeparateEdgeKindsAndAuxiliaryDeclarations() {
        try(var fixture=new Fixture()) {
            new DependencySourceEditsTest().catalog(fixture);
            String source="parameter('arg',defaultValue:'x')\n"
                    +"task('p', Source) {}\ntask('c', Sink, dependsOn:'p',after:'p') { input=result('p').file }\n"
                    +"action('unused') { ctx.run(Echo) { input='inside' } }\ncleanup('close') {}\ndefaults 'p'\n";
            fixture.files.put(JOB,source);var snapshot=fixture.capture();
            var graph=projector.project(snapshot,new JobGraphProjector.GraphOptions(true,false),CancellationToken.NONE);
            assertEquals(3,graph.nodes().stream().filter(n->n.kind()==NodeKind.TASK||n.kind()==NodeKind.ACTION).count(),"Unselected action remains in the declared graph");
            assertEquals(1,graph.nodes().stream().filter(n->n.kind()==NodeKind.INNER_INVOCATION).count());
            assertEquals(1,graph.parameters().size());assertEquals(1,graph.cleanup().size());
            assertEquals(Set.of(EdgeKind.DEPENDS_ON,EdgeKind.AFTER,EdgeKind.RESULT_BINDING),new HashSet<>(graph.edges().stream().map(Edge::kind).toList()));
            var binding=graph.edges().stream().filter(e->e.kind()==EdgeKind.RESULT_BINDING).findFirst().orElseThrow();
            assertEquals("file",binding.outputField());assertEquals("input",binding.inputField());assertTrue(binding.schemaKnown());
            var inner=graph.nodes().stream().filter(n->n.kind()==NodeKind.INNER_INVOCATION).findFirst().orElseThrow();
            var compact=projector.project(snapshot,new JobGraphProjector.GraphOptions(false,false),CancellationToken.NONE);
            for(var node:compact.nodes())assertTrue(graph.nodes().stream().anyMatch(expanded->expanded.id().equals(node.id())&&Objects.equals(expanded.symbol(),node.symbol())),"Optional explanatory children must not change navigation IDs");
            assertTrue(inner.editCapabilities().isEmpty());assertNull(inner.symbol());
            assertTrue(graph.nodes().stream().anyMatch(n->n.id().equals(inner.parentId())&&n.kind()==NodeKind.ACTION));
            assertFalse(graph.edges().stream().anyMatch(e->e.source().equals(inner.id())||e.target().equals(inner.id())));
            assertEquals(graph.edges().size()+graph.nodes().size()+graph.parameters().size()+graph.cleanup().size(),
                    java.util.stream.Stream.concat(graph.edges().stream().map(Edge::id),java.util.stream.Stream.of(graph.nodes(),graph.parameters(),graph.cleanup()).flatMap(List::stream).map(Node::id)).distinct().count());
            assertEquals(binding.anchor(),projector.sourceFor(graph,binding.id(),snapshot.id()).orElseThrow());
            assertTrue(projector.elementsAt(graph,JOB,binding.anchor().span().startUtf16(),snapshot.id()).contains(binding.id()));
            assertThrows(IllegalArgumentException.class,()->projector.sourceFor(graph,binding.id(),UUID.randomUUID()));
            assertEquals(source,fixture.files.get(JOB));
        }
    }

    @Test void missingDataRightsRemainInvalidWithoutAnInventedDependencyAndRawPortsRemainUnknown() {
        try(var fixture=new Fixture()) {
            new DependencySourceEditsTest().catalog(fixture);
            fixture.files.put(JOB,"def p=task('p', Source) {}; task('c', Sink) { input=result('p').file }; action('raw',dependsOn:p) { println result(p).file }");
            var graph=projector.project(fixture.capture(),JobGraphProjector.GraphOptions.defaults(),CancellationToken.NONE);
            assertEquals(Status.INVALID,graph.status());
            assertEquals(1,graph.edges().stream().filter(e->e.kind()==EdgeKind.DEPENDS_ON).count());
            assertEquals(2,graph.edges().stream().filter(e->e.kind()==EdgeKind.RESULT_BINDING).count());
            assertEquals(1,graph.edges().stream().filter(e->e.kind()==EdgeKind.RESULT_BINDING&&!e.schemaKnown()&&e.outputField()==null).count());
            assertTrue(graph.nodes().stream().filter(n->n.kind()==NodeKind.TASK).allMatch(n->n.editCapabilities().get("options").state()==EditCapability.State.NEEDS_OPEN_DOCUMENTS));
        }
    }

    @Test void dynamicCodeIsVisibleAndPresentationIdentitySurvivesSnapshotChanges() {
        try(var fixture=new Fixture()) {
            fixture.files.put(JOB,"task('a', Echo) { input='old' }; def x=System.getProperty('name'); task(x,Echo) { input='dynamic' }");
            var first=fixture.capture();var graph=projector.project(first,JobGraphProjector.GraphOptions.defaults(),CancellationToken.NONE);
            assertEquals(Status.PARTIAL,graph.status());
            assertTrue(graph.nodes().stream().anyMatch(n->n.kind()==NodeKind.DYNAMIC_CODE&&n.editCapabilities().isEmpty()));
            var next=projector.project(fixture.capture(),JobGraphProjector.GraphOptions.defaults(),CancellationToken.NONE);
            var staticNode=graph.nodes().stream().filter(n->n.kind()==NodeKind.TASK).findFirst().orElseThrow();
            var nextNode=next.nodes().stream().filter(n->n.kind()==NodeKind.TASK).findFirst().orElseThrow();
            assertNotEquals(staticNode.id(),nextNode.id());assertEquals(staticNode.presentationKey(),nextNode.presentationKey());
            assertThrows(CancellationException.class,()->projector.project(first,JobGraphProjector.GraphOptions.defaults(),()->true));
        }
    }
}
