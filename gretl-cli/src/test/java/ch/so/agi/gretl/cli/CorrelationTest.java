package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.logging.Json;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorrelationTest {
    @TempDir Path root;

    @Test void explicitIdentityWinsAndDoesNotLeakToNextRun() throws Exception {
        Files.writeString(root.resolve("job.groovy"), "action('run') { log().info('hello') }; defaults 'run'");
        Files.writeString(root.resolve("runtime.json"), Json.write(Map.of("schemaVersion", 1,
                "runtime", Map.of("correlationId", "configured", "jobName", "configured-job"))));
        String first = run(Map.of("GRETL_CORRELATION_ID", "environment", "GRETL_JOB_NAME", "environment-job"),
                "first.json", true, 0);
        assertTrue(first.contains("\"correlation_id\":\"configured\""), first);
        assertFalse(first.contains("environment"));
        String report = Files.readString(root.resolve("first.json"));
        assertTrue(report.contains("\"correlationId\":\"configured\""));
        assertTrue(report.contains("\"jobName\":\"configured-job\""));
        String second = run(Map.of("GRETL_CORRELATION_ID", "second", "GRETL_JOB_NAME", "second-job"),
                "second.json", false, 0);
        assertTrue(second.contains("\"correlation_id\":\"second\""));
        assertFalse(second.contains("configured"));
        String third = run(Map.of(), "third.json", false, 0);
        assertFalse(third.contains("correlation_id"));
        assertFalse(Files.readString(root.resolve("third.json")).contains("correlationId"));
    }

    @Test void rejectsControlCharactersAndOverlongEnvironmentBeforeTaskWork() throws Exception {
        Files.writeString(root.resolve("job.groovy"), "action('run') { throw new AssertionError('must not run') }; defaults 'run'");
        for (String value : java.util.List.of("bad\nidentity", "x".repeat(257), " ")) {
            String error = run(Map.of("GRETL_CORRELATION_ID", value), "invalid.json", false, 2);
            assertTrue(error.contains("correlation"), error);
            assertFalse(Files.exists(root.resolve("invalid.json")));
        }
        run(Map.of("GRETL_CORRELATION_ID", "x".repeat(256)), "boundary.json", false, 10);
    }

    private String run(Map<String, String> env, String report, boolean config, int expected) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var cli = new GretlCli(new PrintStream(output), new PrintStream(output), env, root,
                getClass().getClassLoader());
        var args = new java.util.ArrayList<>(java.util.List.of("run", "job.groovy", "--report", report, "--log-format", "json"));
        if (config) args.addAll(java.util.List.of("--config", "runtime.json"));
        assertEquals(expected, cli.run(args.toArray(String[]::new)), output.toString());
        return output.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
