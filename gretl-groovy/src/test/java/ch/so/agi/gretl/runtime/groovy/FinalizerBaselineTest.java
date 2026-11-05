package ch.so.agi.gretl.runtime.groovy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** P0 capability probe: replace with the positive public contract in P3. */
class FinalizerBaselineTest {
    @TempDir Path root;

    @Test void finalizedByIsNotYetSupportedAndExecutesNoBody() throws Exception {
        var harness = new GroovyHarness(root);
        var error = assertThrows(GroovyDslException.class, () -> harness.loadSource("finalizers.groovy", """
                action('owner', finalizedBy: 'release') { throw new AssertionError('owner executed') }
                action('release') { throw new AssertionError('finalizer executed') }
                defaults 'owner'
                """));
        assertEquals("UNKNOWN_OPTION", error.code());
        assertTrue(error.getMessage().contains("finalizedBy"));
        assertNotNull(error.source());
        assertEquals(1, error.source().line());
    }
}
