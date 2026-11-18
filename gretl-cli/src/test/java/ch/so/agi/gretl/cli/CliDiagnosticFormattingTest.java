package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.logging.Json;
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
    void unknownParameterMessageFollowsTheRequestedLocale() throws Exception {
        Path script = tempDir.resolve("parameter-en.groovy");
        Files.writeString(script, """
                parameter('quelle', required: true, description: 'Quelle')
                action('run') { return parameter('quelle') }
                defaults 'run'
                """);

        Result result = run("run", script.toString(), "--lang", "en",
                "--param", "quell=x");

        assertEquals(2, result.code());
        assertTrue(result.err().contains("ERROR Unknown parameter [UNKNOWN_PARAMETER]"));
        assertTrue(result.err().contains(
                "Message: The job does not know parameter quell. Did you mean quelle? (CLI)."));
    }

    @Test
    void groovyDefinitionErrorsFollowTheRequestedLocale() throws Exception {
        Path script = tempDir.resolve("bad-option-en.groovy");
        Files.writeString(script, """
                parameter('quelle', description: 'Quelle', bogus: 1)
                action('run') { return parameter('quelle') }
                defaults 'run'
                """);

        Result result = run("run", script.toString(), "--lang", "en");

        assertEquals(2, result.code());
        assertTrue(result.err().contains("Unknown option bogus for parameter quelle"),
                result.err());
    }

    @Test
    void jsonErrorFormatStaysMachineReadable() throws Exception {
        Path script = tempDir.resolve("parameter-json.groovy");
        Files.writeString(script, """
                parameter('quelle', required: true, description: 'Quelle')
                action('run') { return parameter('quelle') }
                defaults 'run'
                """);

        Path config = tempDir.resolve("json-config.json");
        Files.writeString(config, """
                {"schemaVersion":1,"logging":{"format":"json"}}
                """);
        Result result = run("run", script.toString(), "--param", "quell=x", "--config", config.toString());

        assertEquals(2, result.code());
        var event = (Map<?, ?>) Json.parse(result.err().lines().findFirst().orElseThrow());
        assertEquals("gretl.cli.error", event.get("event_name"));
        assertEquals(2L, event.get("exit_code"));
        assertEquals("UNKNOWN_PARAMETER", event.get("diagnostic_code"));
        assertEquals("quell", event.get("field"));
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
        assertTrue(result.err().contains("\n  Code: UNKNOWN_CONFIGURATION_FIELD"));
        assertTrue(result.err().contains("\n  Task: bad"));
        assertTrue(result.err().contains("\n  Feld: unknownSetting"));
        assertTrue(result.err().contains("\n  Erlaubte Felder:"));
        assertTrue(result.err().contains("\n  Meldung:"));
        assertTrue(result.err().contains("\n  Quelle: " + script + ":2"));
        assertFalse(result.err().contains("Operation fehlgeschlagen"));
        assertFalse(result.err().contains("FEHLER:"));
        assertFalse(result.err().contains("\tat "));
    }

    @Test
    void languageOptionSelectsEnglishTextLabels() throws Exception {
        Path script = tempDir.resolve("bad-config-en.groovy");
        Files.writeString(script, """
                task('bad', HttpDownload) {
                    unknownSetting = 'x'
                }
                defaults 'bad'
                """);

        Result result = run("run", script.toString(), "--lang", "en",
                "--work-dir", tempDir.resolve("work-en").toString());

        assertEquals(10, result.code());
        assertTrue(result.err().contains("ERROR Task failed [gretl.task.failed]"));
        assertTrue(result.err().contains("\n  Code: UNKNOWN_CONFIGURATION_FIELD"));
        assertTrue(result.err().contains("\n  Field: unknownSetting"));
        assertTrue(result.err().contains("\n  Message: Unknown configuration field"), result.err());
        assertFalse(result.err().contains("Task fehlgeschlagen"));
    }

    @Test
    void runtimeConfigurationCanSelectEnglish() throws Exception {
        Path script = tempDir.resolve("bad-config-config-en.groovy");
        Files.writeString(script, """
                task('bad', HttpDownload) {
                    unknownSetting = 'x'
                }
                defaults 'bad'
                """);
        Path config = tempDir.resolve("runtime-en.json");
        Files.writeString(config, "{\"schemaVersion\":1,\"language\":\"en\"}");

        Result result = run(Map.of(), "run", script.toString(), "--config", config.toString(),
                "--work-dir", tempDir.resolve("work-config-en").toString());

        assertEquals(10, result.code());
        assertTrue(result.err().contains("ERROR Task failed [gretl.task.failed]"));
        assertTrue(result.err().contains("Message: Unknown configuration field"));
    }

    @Test
    void runtimeConfigurationSelectsTheDefinitionLocaleOnSuccessiveCalls() throws Exception {
        Path script = tempDir.resolve("bad-definition.groovy");
        Files.writeString(script, "parameter('name', description: 'Name', bogus: 1)");
        Path config = tempDir.resolve("runtime.json");
        for (String language : java.util.List.of("de", "en")) {
            Files.writeString(config, Json.write(Map.of("schemaVersion", 1, "language", language)));
            Result result = run(Map.of(), "check", script.toString(), "--config", config.toString());
            assertEquals(2, result.code());
            assertTrue(result.err().contains(language.equals("en")
                    ? "Unknown option bogus" : "Unbekannte Option bogus"), result.err());
        }
    }

    private Result run(String... args) {
        return run(Map.of("GRETL_LANGUAGE", "de"), args);
    }

    private Result run(Map<String, String> environment, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = new GretlCli(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8), environment, tempDir,
                getClass().getClassLoader(),
                Clock.fixed(Instant.parse("2026-10-18T16:00:00Z"), ZoneOffset.UTC)).run(args);
        return new Result(code, out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int code, String out, String err) {
    }
}
