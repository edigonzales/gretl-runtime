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
        assertTrue(events(first).anyMatch(event -> "configured".equals(event.get("correlation_id"))), first);
        assertFalse(first.contains("environment"));
        var identity = identity("first.json");
        assertEquals("configured", identity.get("correlationId"));
        assertEquals("configured-job", identity.get("jobName"));
        String second = run(Map.of("GRETL_CORRELATION_ID", "second", "GRETL_JOB_NAME", "second-job"),
                "second.json", false, 0);
        assertTrue(events(second).anyMatch(event -> "second".equals(event.get("correlation_id"))), second);
        assertFalse(second.contains("configured"));
        String third = run(Map.of(), "third.json", false, 0);
        assertTrue(events(third).noneMatch(event -> event.containsKey("correlation_id")));
        assertFalse(identity("third.json").containsKey("correlationId"));
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

    private java.util.stream.Stream<Map<?, ?>> events(String stderr) {
        return stderr.lines().map(Json::parse).map(value -> (Map<?, ?>) value);
    }

    private Map<?, ?> identity(String report) throws Exception {
        return (Map<?, ?>) ((Map<?, ?>) Json.parseFile(root.resolve(report), 1L << 20)).get("identity");
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
