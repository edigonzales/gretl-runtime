package ch.so.agi.gretl.runtime.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.runner.FailureInfo;
import ch.so.agi.gretl.runtime.runner.RunEvent;
import ch.so.agi.gretl.runtime.runner.SecretRegistry;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoggingPipelineTest {
    @TempDir
    Path tempDir;

    @Test
    void jsonConsoleAndFileShareSequenceIdentityAndOneObjectPerLine() throws Exception {
        SecretRegistry secrets = new SecretRegistry();
        secrets.register("secret-value");
        LogContext context = context();
        Path eventFile = tempDir.resolve("run.events.jsonl");
        ByteArrayOutputStream consoleBytes = new ByteArrayOutputStream();
        PrintStream console = new PrintStream(consoleBytes, true, StandardCharsets.UTF_8);
        LoggingConfig config = new LoggingConfig(
                LoggingConfig.Format.JSON, LogLevel.INFO, Map.of(), true,
                16384, 1024 * 1024, java.time.Duration.ofSeconds(10),
                java.time.Duration.ofSeconds(30));
        try (LoggingPipeline pipeline = new LoggingPipeline(config, context,
                new Redactor(secrets), console, eventFile,
                Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneOffset.UTC))) {
            RunEvent started = new RunEvent("gretl.run.started", "start secret-value",
                    Instant.parse("2026-10-18T10:00:00Z"), LogLevel.INFO,
                    Optional.of(context.runId()), Optional.of(context.jobName()),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    "run", "", 0, Map.of("safe", "value"), Optional.empty());
            pipeline.emit(started);
            pipeline.scriptMessage("script line");
            pipeline.emit(new RunEvent("gretl.task.failed", "failed secret-value",
                    Instant.parse("2026-10-18T10:00:01Z"), LogLevel.ERROR,
                    Optional.of(context.runId()), Optional.of(context.jobName()),
                    Optional.of("task"), Optional.of("i000001"), Optional.of("test.op"),
                    "task", "FAILED", 50, Map.of("password", "secret-value"),
                    Optional.of(FailureInfo.from(new IllegalStateException(
                            "cause secret-value")))));
        }

        List<String> lines = Files.readAllLines(eventFile, StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        for (int index = 0; index < lines.size(); index++) {
            Map<?, ?> event = (Map<?, ?>) Json.parse(lines.get(index));
            assertEquals((long) index + 1, event.get("sequence"));
            assertEquals(context.commandId() + ":" + (index + 1), event.get("event_id"));
            assertEquals("gretl", ((Map<?, ?>) event.get("resource")).get("service.name"));
        }
        assertFalse(lines.get(0).contains("secret-value"));
        assertTrue(lines.get(0).contains("[REDACTED]"));
        assertTrue(lines.get(2).contains("\"password\":\"[REDACTED]\""));
        assertTrue(lines.get(2).contains("cause [REDACTED]"));

        List<String> consoleLines = consoleBytes.toString(StandardCharsets.UTF_8)
                .lines().toList();
        assertEquals(3, consoleLines.size());
        Map<?, ?> first = (Map<?, ?>) Json.parse(consoleLines.get(0));
        assertEquals("gretl.run.started", first.get("event_name"));
        assertEquals("cc35045e-8a2f-4f5e-935e-d59adad4b483:1", first.get("event_id"));
    }

    @Test
    void textFormatKeepsLifecycleOnStderr() throws Exception {
        SecretRegistry secrets = new SecretRegistry();
        LogContext context = context();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream console = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        try (LoggingPipeline pipeline = new LoggingPipeline(LoggingConfig.defaults(),
                context, new Redactor(secrets), console, tempDir.resolve("events.jsonl"),
                Clock.systemUTC())) {
            pipeline.emit(new RunEvent("gretl.task.finished", "done",
                    Instant.now(), LogLevel.INFO, Optional.of(context.runId()),
                    Optional.of(context.jobName()), Optional.of("task"),
                    Optional.empty(), Optional.empty(), "task", "SUCCEEDED", 12,
                    Map.of(), Optional.empty()));
        }
        String text = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("gretl.task.finished"));
        assertTrue(text.contains("task=task"));
        assertTrue(text.contains("done"));
    }

    private static LogContext context() {
        return new LogContext("cc35045e-8a2f-4f5e-935e-d59adad4b483",
                UUID.fromString("230305b8-bd98-4b23-a5b8-6c7e8b8ab4a7"),
                "job", "0.1.0-M6", Optional.empty());
    }
}
