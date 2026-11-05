package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.graph.*;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.edit.EditVerifierTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplyPartialCoverageTest {
    @Test void applyAloneMakesOtherwiseClosedGraphPartial() {
        try (var fixture = new Fixture()) {
            String source = "task('a', Echo) { input='value' }\ndefaults 'a'\n";
            fixture.files.put(JOB, source);
            var projector = new JobGraphProjector();
            var full = projector.project(fixture.capture(), JobGraphProjector.GraphOptions.defaults(), CancellationToken.NONE);
            assertEquals(JobGraph.Status.COMPLETE, full.status());
            fixture.files.put(JOB, "apply from: 'tasks/fragment.groovy'\n" + source);
            var partial = projector.project(fixture.capture(), JobGraphProjector.GraphOptions.defaults(), CancellationToken.NONE);
            assertEquals(JobGraph.Status.PARTIAL, partial.status());
            assertTrue(partial.nodes().stream().anyMatch(n -> n.kind() == JobGraph.NodeKind.DYNAMIC_CODE));
        }
    }
}
