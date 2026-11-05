package ch.so.agi.gretl.runtime.groovy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** The original P0 red probe becomes the public positive contract in P3. */
class FinalizerBaselineTest {
    @TempDir Path root;

    @Test void finalizedByRunsAfterOwnerFailure() throws Exception {
        var harness = new GroovyHarness(root);
        var definition = harness.loadSource("finalizers.groovy", """
                action('owner', finalizedBy: 'release') { throw new IllegalStateException('owner failed') }
                action('release') { println 'released' }
                defaults 'owner'
                """);
        var outcome=harness.run(definition, java.util.Map.of());
        assertEquals(ch.so.agi.gretl.runtime.runner.RunStatus.FAILED,outcome.status());
        assertEquals("owner failed",outcome.primaryFailure().orElseThrow().message());
        assertEquals(ch.so.agi.gretl.runtime.runner.TaskStatus.SUCCEEDED,outcome.task("release").orElseThrow().status());
        assertEquals(java.util.List.of("released"),harness.messages.messages());
    }
}
