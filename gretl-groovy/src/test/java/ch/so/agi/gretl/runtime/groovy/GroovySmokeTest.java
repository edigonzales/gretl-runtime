package ch.so.agi.gretl.runtime.groovy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.JobOutcome;
import ch.so.agi.gretl.runtime.runner.ResultSlot;
import ch.so.agi.gretl.runtime.runner.RunStatus;
import ch.so.agi.gretl.runtime.runner.TaskStatus;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroovySmokeTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsAndRunsTheCanonicalMapFirstScript() throws Exception {
        TestGroovyOperations.reset();
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobDefinition definition = harness.loadResource("basic.groovy");

        assertEquals(1, definition.parameters().size());
        assertEquals("eingabe", definition.parameters().get(0).name());
        assertTrue(definition.task("echo").isPresent());
        assertTrue(definition.task("melden").isPresent());
        assertEquals(1, definition.cleanups().size());

        JobOutcome outcome = harness.run(definition, Map.of("eingabe", "hello"));

        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals(TaskStatus.SUCCEEDED,
                outcome.task("echo").orElseThrow().status());
        assertEquals(TaskStatus.SUCCEEDED,
                outcome.task("melden").orElseThrow().status());
        assertEquals(new ResultSlot.Value("hello"),
                outcome.task("melden").orElseThrow().slot());
        assertEquals(1, TestGroovyOperations.BUILDER_CREATIONS.get());
        assertEquals(1, TestGroovyOperations.HANDLER_CALLS.get());

        assertTrue(harness.messages.messages().contains("echo=hello"));
        assertTrue(harness.messages.messages().contains("cleanup ran"));
        assertEquals(1, harness.events.count("gretl.cleanup.finished"));
        assertFalse(harness.messages.messages().contains("load-message"));
    }
}
