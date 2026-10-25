package ch.so.agi.gretl.runtime.logging;

import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.runner.FailureInfo;
import ch.so.agi.gretl.runtime.runner.RunEvent;
import ch.so.agi.gretl.runtime.runner.RunEventSink;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Creates the canonical event once, redacts it, assigns sequence/event-id and
 * fans it out to the console and optional JSONL file. Sink failures degrade
 * observability, never task status.
 */
public final class LoggingPipeline implements RunEventSink, AutoCloseable {
    private final LoggingConfig config;
    private final LogContext context;
    private final Redactor redactor;
    private final Clock clock;
    private final ConsoleSink console;
    private final EventFileSink file;
    private final AtomicLong sequence = new AtomicLong();
    private boolean degradedNotified;

    public LoggingPipeline(LoggingConfig config, LogContext context, Redactor redactor,
                           PrintStream stderr, Path eventFile, Clock clock)
            throws IOException {
        this.config = java.util.Objects.requireNonNull(config, "config");
        this.context = java.util.Objects.requireNonNull(context, "context");
        this.redactor = java.util.Objects.requireNonNull(redactor, "redactor");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.console = new ConsoleSink(java.util.Objects.requireNonNull(stderr, "stderr"),
                config, context);
        this.file = config.eventFile() && eventFile != null
                ? new EventFileSink(eventFile, config, context) : null;
    }

    @Override
    public synchronized void emit(RunEvent event) {
        java.util.Objects.requireNonNull(event, "event");
        long number = sequence.incrementAndGet();
        LogEvent canonical = new LogEvent(
                number,
                context.commandId() + ":" + number,
                context.commandId(),
                event.timestamp(),
                event.level(),
                event.eventName(),
                redactor.redact(event.message()),
                event.runId().or(() -> Optional.of(context.runId())),
                event.jobName().or(() -> Optional.of(context.jobName())),
                event.taskId(), event.invocationId(), event.operationId(),
                event.phase(), event.outcome(), event.durationMillis(),
                contextualFields(event.attributes()),
                event.failure().map(redactor::redact),
                event.failure().flatMap(FailureInfo::source));
        console.write(canonical);
        if (file != null) {
            file.write(canonical);
        }
        if ((console.degraded() || (file != null && file.snapshot().degraded()))
                && !degradedNotified) {
            degradedNotified = true;
            LogEvent warning = new LogEvent(
                    sequence.incrementAndGet(),
                    context.commandId() + ":" + sequence.get(),
                    context.commandId(), clock.instant(), LogLevel.WARN,
                    "gretl.logging.degraded",
                    "Eine verwaltete Logsenke ist ausgefallen; der fachliche Runstatus "
                            + "bleibt davon unberuehrt.",
                    Optional.of(context.runId()), Optional.of(context.jobName()),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    "logging", "", 0, contextualFields(Map.of()), Optional.empty(), Optional.empty());
            // Write only to the remaining sink(s), avoiding recursive degradation loops.
            if (!console.degraded()) {
                console.write(warning);
            }
            if (file != null && !file.snapshot().degraded()) {
                file.write(warning);
            }
        }
    }

    public synchronized void scriptMessage(String message) {
        Instant now = clock.instant();
        emit(new RunEvent("gretl.script.message", message, now, LogLevel.INFO,
                Optional.of(context.runId()), Optional.of(context.jobName()),
                Optional.empty(), Optional.empty(), Optional.empty(),
                "script", "", 0, Map.of(), Optional.empty()));
    }

    private Map<String, String> contextualFields(Map<String, String> fields) {
        Map<String, String> result = new java.util.LinkedHashMap<>(fields);
        result.remove("correlation.id");
        context.correlationId().ifPresent(value -> result.put("correlation.id", value));
        return redactor.redactFields(result);
    }

    public synchronized void libraryMessage(
            LogLevel level, String logger, String message, Throwable cause) {
        libraryEvent(level, logger, message,
                Optional.ofNullable(cause).map(FailureInfo::from));
    }

    public synchronized void libraryEvent(
            LogLevel level, String logger, String message,
            Optional<FailureInfo> failure) {
        emit(new RunEvent("gretl.library.log", message, clock.instant(), level,
                Optional.of(context.runId()), Optional.of(context.jobName()),
                Optional.empty(), Optional.empty(), Optional.empty(),
                "library", "", 0, Map.of("logger", logger), failure));
    }

    public synchronized LoggingState state() {
        EventFileSink.Snapshot snapshot = file == null ? null : file.snapshot();
        return new LoggingState(
                config.format(), config.eventFile(),
                snapshot == null ? Optional.empty() : Optional.of(snapshot.path()),
                console.degraded() || (snapshot != null && snapshot.degraded()),
                snapshot != null && snapshot.truncated(),
                snapshot != null && snapshot.lost(),
                snapshot == null ? 0 : snapshot.recordTruncations());
    }

    public LoggingConfig config() {
        return config;
    }

    public LogContext context() {
        return context;
    }

    @Override
    public synchronized void close() {
        if (file != null) {
            file.close();
        }
    }

    public static RunEvent scriptEvent(String message, UUID runId, String jobName,
                                       Instant timestamp) {
        return new RunEvent("gretl.script.message", message, timestamp, LogLevel.INFO,
                Optional.of(runId), Optional.of(jobName), Optional.empty(),
                Optional.empty(), Optional.empty(), "script", "", 0, Map.of(),
                Optional.empty());
    }
}
