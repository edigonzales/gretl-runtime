package ch.so.agi.gretl.tooling.graph;

import ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.graph.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace.*;
import static org.junit.jupiter.api.Assertions.*;

class TargetPreviewProjectorTest {
    final TargetPreviewProjector projector=new TargetPreviewProjector();
    @Test void targetPreviewUsesDataClosureWithoutChangingTheDefinitionGraphOrActivatingAfterOnlyNodes() {
        try(var fixture=new EditTestWorkspace()) {
            String source="action('afterOnly') {}\naction('producer') {}\naction('consumer',dependsOn:'producer',after:'afterOnly') {}\naction('unused') {}\ndefaults 'consumer'\n";
            fixture.files.put(JOB,source);var snapshot=fixture.capture();
            var graph=new JobGraphProjector().project(snapshot,JobGraphProjector.GraphOptions.defaults(),CancellationToken.NONE);
            var preview=projector.project(snapshot,List.of(),CancellationToken.NONE);
            assertTrue(preview.available(),preview.reason());assertEquals(List.of("consumer"),preview.targets());
            assertEquals(List.of("producer","consumer"),preview.orderedSymbols());assertEquals(4,graph.nodes().size());
            assertEquals(List.of("unused"),projector.project(snapshot,List.of("unused"),CancellationToken.NONE).orderedSymbols());
            assertEquals("UNKNOWN_TARGET",projector.project(snapshot,List.of("missing"),CancellationToken.NONE).reason());
            assertEquals(source,fixture.files.get(JOB));
        }
    }
    @Test void incompleteSourcesAndCancellationNeverBecomeAnExecutableLookingPreview() {
        try(var fixture=new EditTestWorkspace()) {
            fixture.files.put(JOB,"def name=System.getProperty('name'); action(name) {}; action('known') {}\n");var snapshot=fixture.capture();
            var preview=projector.project(snapshot,List.of("known"),CancellationToken.NONE);
            assertFalse(preview.available());assertEquals("GRAPH_INCOMPLETE",preview.reason());assertTrue(preview.orderedSymbols().isEmpty());
            assertThrows(CancellationException.class,()->projector.project(snapshot,List.of(),()->true));
        }
    }
}
