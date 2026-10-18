package ch.so.agi.gretl.runtime.logging;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.runner.FailureInfo;
import ch.so.agi.gretl.runtime.runner.SourceLocation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TextFormatterTest {
    private static final Instant TIME = Instant.parse("2026-10-18T16:00:00Z");

    @Test
    void earlyProblemUsesTheSameHeaderAndFieldLayoutAsRuntimeErrors() {
        String text = new TextFormatter().formatProblem(TIME, "PARAMETER_UNKNOWN", null,
                "Der Job kennt den Parameter quell nicht. Meinten Sie quelle? (CLI).",
                Map.of("Feld", "quell"));

        assertTrue(text.startsWith("2026-10-18T16:00:00.000Z ERROR Parameter unbekannt "
                + "[PARAMETER_UNKNOWN]"));
        assertTrue(text.contains("\n  Meldung: Der Job kennt den Parameter quell nicht."));
        assertTrue(text.contains("\n  Feld: quell"));
    }

    @Test
    void runtimeErrorUsesHumanTitleAndIndentedFields() {
        FailureInfo failure = new FailureInfo("failure-1",
                "ch.so.agi.gretl.runtime.groovy.GroovyDslException",
                "Unbekanntes Feld unknownSetting [task=bad] [field=unknownSetting] [bad-config.groovy:2]",
                List.of(), List.of(), false, List.of(), false,
                Optional.of(new SourceLocation("bad-config.groovy", 2, 0)));
        LogEvent event = new LogEvent(1, "event-1", "command-1", TIME, LogLevel.ERROR,
                "gretl.task.failed", "", Optional.of(UUID.randomUUID()),
                Optional.of("job"), Optional.of("bad"), Optional.empty(),
                Optional.of("gretl.http-download"), "", "FAILED", 0,
                Map.of(), Optional.of(failure), Optional.empty());

        String text = new TextFormatter().format(event);

        assertTrue(text.startsWith("2026-10-18T16:00:00.000Z ERROR Task fehlgeschlagen "
                + "[gretl.task.failed]"));
        assertTrue(text.contains("\n  Task: bad"));
        assertTrue(text.contains("\n  Meldung: Unbekanntes Feld unknownSetting"));
        assertTrue(text.contains("\n  Feld: unknownSetting"));
        assertTrue(text.contains("\n  Quelle: bad-config.groovy:2"));
    }

    @Test
    void infoEventKeepsTechnicalEventNameForCorrelation() {
        LogEvent event = new LogEvent(1, "event-1", "command-1", TIME, LogLevel.INFO,
                "gretl.run.started", "start", Optional.of(UUID.randomUUID()),
                Optional.of("job"), Optional.empty(), Optional.empty(), Optional.empty(),
                "run", "RUNNING", 0, Map.of("targets", "run"),
                Optional.empty(), Optional.empty());

        String text = new TextFormatter().format(event);

        assertTrue(text.startsWith("2026-10-18T16:00:00.000Z INFO Lauf gestartet "
                + "[gretl.run.started]"));
        assertTrue(text.contains("targets=run"));
    }
}
