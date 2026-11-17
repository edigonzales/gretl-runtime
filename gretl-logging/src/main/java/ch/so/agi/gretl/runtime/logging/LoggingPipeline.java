package ch.so.agi.gretl.runtime.logging;

import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.ExecutionIdentity;
import ch.so.agi.gretl.runtime.runner.FailureInfo;
import ch.so.agi.gretl.runtime.runner.RunEvent;
import ch.so.agi.gretl.runtime.runner.RunEventSink;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

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
    private long sequence;
    private boolean degradedNotified;
    private boolean scopeDegraded;

    public LoggingPipeline(LoggingConfig config, LogContext context, Redactor redactor,
                           PrintStream stderr, Path eventFile, Clock clock) {
        this.config = java.util.Objects.requireNonNull(config, "config");
        this.context = java.util.Objects.requireNonNull(context, "context");
        this.redactor = java.util.Objects.requireNonNull(redactor, "redactor");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.console = new ConsoleSink(java.util.Objects.requireNonNull(stderr, "stderr"),
                config, context);
        this.file = config.eventFile() && eventFile != null
                ? new EventFileSink(eventFile, config) : null;
    }

    @Override
    public synchronized void emit(RunEvent event) {
        write(event);
        if (degraded()) {
            notifyDegraded("Die Beobachtbarkeit ist beeintraechtigt; der fachliche Runstatus "
                    + "bleibt davon unberuehrt.");
        }
    }

    private void write(RunEvent event) {
        long number = nextSequence();
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
        boolean toConsole = console.accepts(canonical);
        boolean toFile = file != null && file.accepts(canonical);
        byte[] jsonLine = null;
        if (toFile || (toConsole && config.format() == LoggingConfig.Format.JSON)) {
            try {
                jsonLine = RecordEncoder.encode(canonical, context, config.maxRecordBytes());
            } catch (RuntimeException ignored) {
                // A failed JSON record cannot prevent text output or change the run status.
            }
        }
        if (toConsole) {
            console.write(canonical, jsonLine);
        }
        if (toFile) {
            file.write(canonical, jsonLine);
        }
    }

    private boolean degraded() {
        return scopeDegraded || console.degraded() || (file != null && file.degraded());
    }

    private void notifyDegraded(String message) {
        if (!degradedNotified) {
            degradedNotified = true;
            write(new RunEvent("gretl.logging.degraded", message, clock.instant(), LogLevel.WARN,
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), "logging", "", 0, Map.of(), Optional.empty()));
        }
    }

    @Override
    public synchronized RunEventSink.Scope openScope(ExecutionIdentity identity, Optional<String> operationId) {
        try {
            return new MdcExecutionScope(identity, operationId, this::scopeFailed);
        } catch (RuntimeException failure) {
            scopeFailed();
            return RunEventSink.Scope.NO_OP;
        }
    }

    private synchronized void scopeFailed() {
        scopeDegraded = true;
        notifyDegraded("Logging-Scope konnte nicht sicher wiederhergestellt werden.");
    }

    public void scriptMessage(String message) {
        emit(new RunEvent("gretl.script.message", message, clock.instant(), LogLevel.INFO,
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

    synchronized void libraryEvent(LogLevel level, String logger, String message,
                                  Optional<FailureInfo> failure, Optional<LibraryIdentity> snapshot,
                                  Instant timestamp) {
        Optional<LibraryIdentity> trusted = scopeDegraded ? Optional.empty() : snapshot
                .filter(s -> s.identity().runId().equals(context.runId())
                        && s.identity().jobName().equals(context.jobName()));
        emit(new RunEvent("gretl.library.log", message, timestamp, level,
                Optional.of(context.runId()), Optional.of(context.jobName()),
                trusted.flatMap(s -> s.identity().taskId()), trusted.flatMap(s -> s.identity().invocationId()),
                trusted.flatMap(LibraryIdentity::operationId),
                "library", "", 0, Map.of("logger", logger), failure));
    }

    public synchronized LoggingState state() {
        return new LoggingState(config.format(), config.eventFile(),
                file == null ? Optional.empty() : file.path(), degraded(),
                file != null && file.truncated());
    }

    public LoggingConfig config() {
        return config;
    }

    public LogContext context() {
        return context;
    }

    /** Reserves an identity for a diagnostic written directly to stderr. */
    public synchronized long nextSequence() {
        return ++sequence;
    }

    @Override
    public synchronized void close() {
        if (file != null) {
            file.close();
        }
    }
}
