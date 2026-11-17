package ch.so.agi.gretl.runtime.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.runner.RunEvent;
import ch.so.agi.gretl.runtime.runner.SecretRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class LoggingLimitsAndBridgeTest {
    @TempDir
    Path tempDir;

    @Test
    void eventFileIsBoundedButKeepsTerminalReserve() throws Exception {
        Path file = tempDir.resolve("events.jsonl");
        LoggingConfig config = new LoggingConfig(LoggingConfig.Format.JSON, LogLevel.INFO,
                Map.of(), true, 4096, 1024 * 1024);
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        LogContext context = context();
        try (LoggingPipeline pipeline = new LoggingPipeline(config, context,
                new Redactor(new SecretRegistry()),
                new PrintStream(console, true, StandardCharsets.UTF_8), file,
                Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneOffset.UTC))) {
            for (int index = 0; index < 2000; index++) {
                pipeline.emit(new RunEvent("gretl.task.message", "x".repeat(1000),
                        Instant.parse("2026-10-18T10:00:00Z"), LogLevel.INFO,
                        Optional.of(context.runId()), Optional.of(context.jobName()),
                        Optional.of("task"), Optional.empty(), Optional.empty(),
                        "task", "", 0, Map.of(), Optional.empty()));
            }
            // Priority events may consume the reserve except for the final run event.
            for (int index = 0; index < 200; index++) {
                pipeline.emit(new RunEvent("gretl.task.failed", "x".repeat(3000),
                        Instant.parse("2026-10-18T10:00:00Z"), LogLevel.ERROR,
                        Optional.empty(), Optional.empty(), Optional.of("task"),
                        Optional.empty(), Optional.empty(), "task", "FAILED", 0,
                        Map.of(), Optional.empty()));
            }
            pipeline.emit(new RunEvent("gretl.run.finished", "finished",
                    Instant.parse("2026-10-18T10:00:01Z"), LogLevel.INFO,
                    Optional.of(context.runId()), Optional.of(context.jobName()),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    "run", "SUCCEEDED", 0, Map.of(), Optional.empty()));
            assertTrue(pipeline.state().fileTruncated());
            assertTrue(Files.size(file) <= 1024 * 1024);
            var byId = new HashMap<>();
            long sequence = 0;
            for (String line : console.toString(StandardCharsets.UTF_8).lines().toList()) {
                var event = (Map<?, ?>) Json.parse(line);
                assertEquals(++sequence, event.get("sequence"));
                assertEquals(null, byId.put(event.get("event_id"), line));
            }
            var lines = Files.readAllLines(file);
            sequence = 0;
            for (String line : lines) {
                var event = (Map<?, ?>) Json.parse(line);
                long next = (long) event.get("sequence");
                assertTrue(next > sequence);
                sequence = next;
                assertEquals(line, byId.get(event.get("event_id")));
            }
            var terminal = (Map<?, ?>) Json.parse(lines.getLast());
            assertEquals("gretl.run.finished", terminal.get("event_name"));
        }
    }

    @Test
    void brokenConsoleMarksDegradedWithoutFailingTheRun() throws Exception {
        LoggingConfig config = LoggingConfig.defaults();
        LogContext context = context();
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("broken pipe");
            }
        };
        PrintStream stream = new PrintStream(broken, true, StandardCharsets.UTF_8);
        Path file = tempDir.resolve("events.jsonl");
        Instant now = Instant.parse("2026-10-18T10:00:00Z");
        try (LoggingPipeline pipeline = new LoggingPipeline(config, context,
                new Redactor(new SecretRegistry()), stream, file,
                Clock.fixed(now, ZoneOffset.UTC))) {
            pipeline.emit(new RunEvent("gretl.run.started", "start", Instant.now(),
                    LogLevel.INFO, Optional.of(context.runId()),
                    Optional.of(context.jobName()), Optional.empty(), Optional.empty(),
                    Optional.empty(), "run", "", 0, Map.of(), Optional.empty()));
            pipeline.scriptMessage("after console failure");
            assertTrue(pipeline.state().degraded());
            var events = Files.readAllLines(file).stream().map(Json::parse)
                    .map(value -> (Map<?, ?>) value).toList();
            assertEquals(3, events.size());
            var warning = events.get(1);
            assertEquals("gretl.logging.degraded", warning.get("event_name"));
            assertEquals("command:2", warning.get("event_id"));
            assertEquals(now.toString(), warning.get("timestamp"));
            assertEquals("after console failure", events.getLast().get("message"));
        }
    }

    @Test
    void logbackBridgeForwardsOneLibraryEventWithCause() throws Exception {
        Path file = tempDir.resolve("library.jsonl");
        LogContext context = context();
        try (LoggingPipeline pipeline = new LoggingPipeline(
                LoggingConfig.defaults().withLibraryLevels(Map.of("fixture", LogLevel.DEBUG)),
                context, new Redactor(new SecretRegistry()),
                new PrintStream(new ByteArrayOutputStream()), file, Clock.systemUTC());
             LogbackBridge bridge = LogbackBridge.install(pipeline, pipeline.config())) {
            assertTrue(bridge != null);
            LoggerFactory.getLogger("fixture").warn("library warning",
                    new IllegalStateException("library cause"));
        }
        String content = Files.readString(file);
        assertTrue(content.contains("gretl.library.log"));
        assertTrue(content.contains("library warning"));
        assertTrue(content.contains("library cause"));
        assertFalse(content.contains("\n\n"));
    }

    @Test
    void configAndJsonValidationRejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> new LoggingConfig(
                LoggingConfig.Format.TEXT, LogLevel.INFO, Map.of(), true,
                1024, 1024));
        assertThrows(IllegalArgumentException.class, () -> new LoggingConfig(
                LoggingConfig.Format.TEXT, LogLevel.INFO, Map.of(), true,
                16384, 1024));
    }

    private static LogContext context() {
        return new LogContext("command", UUID.randomUUID(), "job", "6.0.0-SNAPSHOT",
                Optional.empty());
    }
}
