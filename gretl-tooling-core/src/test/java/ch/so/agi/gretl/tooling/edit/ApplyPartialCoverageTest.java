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
            // P2 now reads the target: this remains the positive static fixture until P3 binding.
            fixture.files.put(java.net.URI.create("file:/work/tasks/fragment.groovy"),"task('child',Echo){ input='child' }");
            fixture.files.put(JOB, "apply from: 'tasks/fragment.groovy'\n" + source);
            var partial = projector.project(fixture.capture(), JobGraphProjector.GraphOptions.defaults(), CancellationToken.NONE);
            assertEquals(JobGraph.Status.PARTIAL, partial.status());
            assertTrue(partial.nodes().stream().anyMatch(n -> n.kind() == JobGraph.NodeKind.DYNAMIC_CODE));
        }
    }
    @Test void missingIncludeIsDiagnosedAndNeverComplete() {
        try(var fixture=new Fixture()){
            fixture.files.put(JOB,"apply from:'missing.groovy'\ntask('a',Echo){input='value'}");
            var snapshot=fixture.capture();var graph=new JobGraphProjector().project(snapshot,JobGraphProjector.GraphOptions.defaults(),CancellationToken.NONE);
            assertEquals(JobGraph.Status.INVALID,graph.status());assertFalse(snapshot.analysis().capabilities().declarationsComplete());
            assertTrue(snapshot.analysis().diagnostics().stream().anyMatch(d->d.code().equals("APPLY_NOT_FOUND")));
        }
    }

}
