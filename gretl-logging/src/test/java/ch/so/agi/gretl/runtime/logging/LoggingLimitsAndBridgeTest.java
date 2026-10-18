package ch.so.agi.gretl.runtime.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.runner.FailureInfo;
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
import java.util.List;
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
                Map.of(), true, 4096, 1024 * 1024,
                java.time.Duration.ofSeconds(10), java.time.Duration.ofSeconds(30));
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
            pipeline.emit(new RunEvent("gretl.run.finished", "finished",
                    Instant.parse("2026-10-18T10:00:01Z"), LogLevel.INFO,
                    Optional.of(context.runId()), Optional.of(context.jobName()),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    "run", "SUCCEEDED", 0, Map.of(), Optional.empty()));
            assertTrue(pipeline.state().fileTruncated());
            assertTrue(Files.size(file) <= 1024 * 1024);
            assertTrue(Files.readString(file).contains("gretl.logging.truncated"));
            assertTrue(Files.readString(file).contains("gretl.run.finished"));
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
        try (LoggingPipeline pipeline = new LoggingPipeline(config, context,
                new Redactor(new SecretRegistry()), stream, tempDir.resolve("events.jsonl"),
                Clock.systemUTC())) {
            pipeline.emit(new RunEvent("gretl.run.started", "start", Instant.now(),
                    LogLevel.INFO, Optional.of(context.runId()),
                    Optional.of(context.jobName()), Optional.empty(), Optional.empty(),
                    Optional.empty(), "run", "", 0, Map.of(), Optional.empty()));
            assertTrue(pipeline.state().degraded());
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
                1024, 1024, java.time.Duration.ofSeconds(10),
                java.time.Duration.ofSeconds(30)));
        assertThrows(IllegalArgumentException.class, () -> new LoggingConfig(
                LoggingConfig.Format.TEXT, LogLevel.INFO, Map.of(), true,
                16384, 1024 * 1024, java.time.Duration.ofMillis(1),
                java.time.Duration.ofSeconds(30)));
    }

    private static LogContext context() {
        return new LogContext("command", UUID.randomUUID(), "job", "0.1.0-M6",
                Optional.empty());
    }
}
