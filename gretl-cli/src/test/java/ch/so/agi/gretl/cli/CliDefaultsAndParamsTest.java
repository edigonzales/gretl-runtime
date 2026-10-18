package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliDefaultsAndParamsTest {
    @TempDir
    Path tempDir;

    @Test
    void defaultReportRootIsUnderStartCwd() throws Exception {
        Path script = tempDir.resolve("job.groovy");
        Files.writeString(script, """
                action('run') { return 'ok' }
                defaults 'run'
                """);
        Result result = run("run", script.toString());
        assertEquals(0, result.code(), result.err());
        Path reports = tempDir.resolve("gretl-reports");
        assertTrue(Files.isDirectory(reports));
        try (Stream<Path> files = Files.list(reports)) {
            var list = files.toList();
            assertTrue(list.stream().anyMatch(path -> path.toString().endsWith(".json")));
            assertTrue(list.stream().anyMatch(path -> path.toString().endsWith(".events.jsonl")));
        }
    }

    @Test
    void undeclaredRuntimeParamIsIgnoredButExplicitFileParamFails() throws Exception {
        Path script = tempDir.resolve("job.groovy");
        Files.writeString(script, """
                parameter('x', defaultValue: 'default', description: 'X')
                action('run') {
                    assert parameter('x') == 'default'
                    return parameter('x')
                }
                defaults 'run'
                """);
        Path config = tempDir.resolve("runtime.json");
        Files.writeString(config, "{\"schemaVersion\":1,\"params\":{\"unused\":\"ignored\"},"
                + "\"runtime\":{\"workRoot\":\"" + tempDir.resolve("work")
                .toString().replace("\\", "\\\\") + "\",\"reportRoot\":\""
                + tempDir.resolve("reports").toString().replace("\\", "\\\\") + "\"}}");
        Result valid = run("run", script.toString(), "--config", config.toString());
        assertEquals(0, valid.code(), valid.err());

        Path params = tempDir.resolve("params.json");
        Files.writeString(params, "{\"unknown\":\"x\"}");
        Result unknown = run("run", script.toString(), "--config", config.toString(),
                "--params-file", params.toString());
        assertEquals(2, unknown.code());
    }

    @Test
    void optionalDefaultAndExplicitEmptyTextRemainDistinct() throws Exception {
        Path script = tempDir.resolve("job.groovy");
        Files.writeString(script, """
                parameter('x', defaultValue: 'default', description: 'X')
                action('run') {
                    assert parameter('x') == ''
                    return parameter('x')
                }
                defaults 'run'
                """);
        assertEquals(0, run("run", script.toString(), "--param", "x=").code());

        Path missing = tempDir.resolve("missing.groovy");
        Files.writeString(missing, """
                parameter('x', defaultValue: 'default', description: 'X')
                action('run') {
                    assert parameter('x') == 'default'
                    return parameter('x')
                }
                defaults 'run'
                """);
        assertEquals(0, run("run", missing.toString()).code());
    }

    @Test
    void configuredTerminationMessageIsSmallJsonWithoutSecretValues() throws Exception {
        Path script = tempDir.resolve("term.groovy");
        Files.writeString(script, """
                action('run') { return 'ok' }
                defaults 'run'
                """);
        Path termination = tempDir.resolve("termination.json");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = new GretlCli(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                Map.of("GRETL_TERMINATION_MESSAGE_PATH", termination.toString()),
                tempDir, getClass().getClassLoader()).run(new String[]{"run", script.toString()});
        assertEquals(0, code);
        assertTrue(Files.isRegularFile(termination));
        String text = Files.readString(termination);
        assertTrue(text.contains("\"status\":\"SUCCEEDED\""));
        assertTrue(text.length() < 3072);
    }

    @Test
    void invalidBooleanIsRejectedByCheckAndRunBeforeWork() throws Exception {
        Path script = tempDir.resolve("bool.groovy");
        Files.writeString(script, """
                parameter('flag', type: 'boolean', required: true, description: 'Flag')
                action('run') { return parameter('flag') }
                defaults 'run'
                """);
        Result check = run("check", script.toString(), "--param", "flag=yes");
        assertEquals(2, check.code());
        Result run = run("run", script.toString(), "--param", "flag=yes",
                "--work-dir", tempDir.resolve("work").toString());
        assertEquals(2, run.code());
        assertFalse(Files.exists(tempDir.resolve("work")));
    }

    private Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = new GretlCli(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8), Map.of(), tempDir,
                getClass().getClassLoader()).run(args);
        return new Result(code, out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int code, String out, String err) {
    }
}
