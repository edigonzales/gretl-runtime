package ch.so.agi.gretl.runtime.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.so.agi.gretl.runtime.api.DiagnosticInfo;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.runner.FailureInfo;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventJsonDiagnosticTest {
    @Test
    void diagnosticMetadataIsPartOfTheStableJsonErrorEnvelope() {
        FailureInfo failure = new FailureInfo("failure-1",
                "ch.so.agi.gretl.runtime.groovy.GroovyDslException",
                "Unknown configuration field unknownSetting",
                List.of(), List.of(), false, List.of(), false,
                Optional.empty(),
                Optional.of(new DiagnosticInfo("UNKNOWN_CONFIGURATION_FIELD",
                        Optional.of("bad"), Optional.of("unknownSetting"),
                        List.of("url", "output"), Optional.of("use name = value"))));
        LogEvent event = new LogEvent(3, "command-1:3", "command-1",
                Instant.parse("2026-10-18T16:00:00Z"), LogLevel.ERROR,
                "gretl.task.failed", "", Optional.of(UUID.randomUUID()),
                Optional.of("job"), Optional.of("bad"), Optional.empty(),
                Optional.of("gretl.http-download"), "task", "FAILED", 5,
                Map.of(), Optional.of(failure), Optional.empty());

        Map<String, Object> json = EventJson.encode(event,
                new LogContext("command-1", UUID.randomUUID(), "job",
                        "0.1.0-SNAPSHOT", Optional.empty()), true);
        Map<?, ?> error = (Map<?, ?>) json.get("error");

        assertEquals("UNKNOWN_CONFIGURATION_FIELD", error.get("code"));
        assertEquals("bad", error.get("task_id"));
        assertEquals("unknownSetting", error.get("field"));
        assertEquals(List.of("url", "output"), error.get("allowed"));
        assertEquals("use name = value", error.get("hint"));
    }
}
