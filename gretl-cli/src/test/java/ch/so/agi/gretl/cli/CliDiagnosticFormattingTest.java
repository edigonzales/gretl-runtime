package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliDiagnosticFormattingTest {
    @TempDir
    Path tempDir;

    @Test
    void unknownParameterUsesTheUnifiedErrorBlock() throws Exception {
        Path script = tempDir.resolve("parameter.groovy");
        Files.writeString(script, """
                parameter('quelle', required: true, description: 'Quelle')
                action('run') { return parameter('quelle') }
                defaults 'run'
                """);

        Result result = run("run", script.toString(), "--param", "quell=x");

        assertEquals(2, result.code());
        assertTrue(result.err().contains("ERROR Parameter unbekannt [UNKNOWN_PARAMETER]"), result.err());
        assertTrue(result.err().contains("\n  Meldung: Der Job kennt den Parameter quell nicht."));
        assertTrue(result.err().contains("\n  Feld: quell"));
        assertFalse(result.err().contains("FEHLER:"));
        assertFalse(result.err().contains("\tat "));
    }

    @Test
    void jsonErrorFormatStaysMachineReadable() throws Exception {
        Path script = tempDir.resolve("parameter-json.groovy");
        Files.writeString(script, """
                parameter('quelle', required: true, description: 'Quelle')
                action('run') { return parameter('quelle') }
                defaults 'run'
                """);

        Result result = run("run", script.toString(), "--param", "quell=x",
                "--log-format", "json");

        assertEquals(2, result.code());
        assertTrue(result.err().contains("\"event_name\":\"gretl.cli.error\""));
        assertTrue(result.err().contains("\"exit_code\":2"));
        assertFalse(result.err().contains("ERROR Parameter unbekannt"));
    }

    @Test
    void runtimeFieldErrorUsesTheUnifiedErrorBlock() throws Exception {
        Path script = tempDir.resolve("bad-config.groovy");
        Files.writeString(script, """
                task('bad', HttpDownload) {
                    unknownSetting = 'x'
                }
                defaults 'bad'
                """);

        Result result = run("run", script.toString(),
                "--work-dir", tempDir.resolve("work").toString());

        assertEquals(10, result.code());
        assertTrue(result.err().contains("ERROR Task fehlgeschlagen [gretl.task.failed]"));
        assertTrue(result.err().contains("\n  Task: bad"));
        assertTrue(result.err().contains("\n  Meldung:"));
        assertTrue(result.err().contains("\n  Quelle: bad-config.groovy:2"));
        assertFalse(result.err().contains("FEHLER:"));
        assertFalse(result.err().contains("\tat "));
    }

    private Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = new GretlCli(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8), Map.of(), tempDir,
                getClass().getClassLoader(),
                Clock.fixed(Instant.parse("2026-10-18T16:00:00Z"), ZoneOffset.UTC)).run(args);
        return new Result(code, out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int code, String out, String err) {
    }
}
