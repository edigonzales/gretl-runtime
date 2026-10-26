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

class CliFailureModesTest {
    @TempDir
    Path tempDir;

    @Test
    void eventFileCanBeDisabledButReportRemains() throws Exception {
        Path script = actionJob("ok");
        Path config = tempDir.resolve("runtime.json");
        Files.writeString(config, "{\"schemaVersion\":1,\"logging\":{\"eventFile\":false},"
                + "\"runtime\":{\"workRoot\":\"" + tempDir.resolve("work")
                .toString().replace("\\", "\\\\") + "\",\"reportRoot\":\""
                + tempDir.resolve("reports").toString().replace("\\", "\\\\") + "\"}}");
        Path report = tempDir.resolve("report.json");
        Result result = run("run", script.toString(), "--config", config.toString(),
                "--report", report.toString());
        assertEquals(0, result.code(), result.err());
        assertTrue(Files.isRegularFile(report));
        try (Stream<Path> files = Files.list(tempDir.resolve("reports"))) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString()
                    .endsWith(".events.jsonl")));
        }
    }

    @Test
    void reportWriteFailureAfterWorkReturnsTen() throws Exception {
        Path script = actionJob("ok");
        Path reportDirectory = Files.createDirectories(tempDir.resolve("report-target"));
        Files.writeString(reportDirectory.resolve("occupied"), "x");
        Result result = run("run", script.toString(),
                "--lang", "de",
                "--work-dir", tempDir.resolve("work").toString(),
                "--report", reportDirectory.toString());
        assertEquals(10, result.code(), result.err());
        assertTrue(result.err().contains("Runbericht"));
    }

    @Test
    void invalidWorkRootFailsBeforeTaskAndParameterFileIsStrict() throws Exception {
        Path script = actionJob("ok");
        Path file = tempDir.resolve("not-a-directory");
        Files.writeString(file, "x");
        Path config = tempDir.resolve("runtime.json");
        Files.writeString(config, "{\"schemaVersion\":1,\"runtime\":{\"workRoot\":\""
                + file.toString().replace("\\", "\\\\") + "\"}}");
        Result work = run("run", script.toString(), "--config", config.toString(),
                "--report", tempDir.resolve("r.json").toString());
        assertEquals(2, work.code());

        Path parameterJob = tempDir.resolve("parameter.groovy");
        Files.writeString(parameterJob, """
                parameter('x', description: 'X')
                action('run') { return parameter('x') }
                defaults 'run'
                """);
        Path nonString = tempDir.resolve("params.json");
        Files.writeString(nonString, "{\"x\":1}");
        Result nonStringResult = run("run", parameterJob.toString(),
                "--params-file", nonString.toString());
        assertEquals(2, nonStringResult.code());

        Path duplicate = tempDir.resolve("duplicate.json");
        Files.writeString(duplicate, "{\"x\":\"a\",\"x\":\"b\"}");
        Result duplicateResult = run("run", parameterJob.toString(),
                "--params-file", duplicate.toString());
        assertEquals(2, duplicateResult.code());

        Result empty = run("run", parameterJob.toString(), "--param", "x=");
        // Optional text accepts an explicit empty value.
        assertEquals(0, empty.code(), empty.err());
    }

    @Test
    void missingSecretSourceFailsTaskWithoutLeakingAValue() throws Exception {
        Path script = tempDir.resolve("secret.groovy");
        Files.writeString(script, """
                action('run') {
                    return secret('missing').reveal()
                }
                defaults 'run'
                """);
        Path config = tempDir.resolve("runtime.json");
        Files.writeString(config, "{\"schemaVersion\":1,\"runtime\":{\"workRoot\":\""
                + tempDir.resolve("work").toString().replace("\\", "\\\\")
                + "\",\"reportRoot\":\"" + tempDir.resolve("reports")
                .toString().replace("\\", "\\\\") + "\"},"
                + "\"secrets\":{\"missing\":{\"environment\":\"GRETL_NOT_SET_ANYWHERE\"}}}");
        Path report = tempDir.resolve("secret-report.json");
        Result result = run("run", script.toString(), "--config", config.toString(),
                "--report", report.toString());
        assertEquals(10, result.code());
        String json = Files.readString(report);
        assertTrue(json.contains("missing"));
        assertTrue(json.contains("GRETL_NOT_SET_ANYWHERE"));
        assertTrue(json.toLowerCase().contains("secret"));
    }

    private Path actionJob(String value) throws Exception {
        Path script = tempDir.resolve("action-" + value + ".groovy");
        Files.writeString(script, """
                action('run') { return '%s' }
                defaults 'run'
                """.formatted(value));
        return script;
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
