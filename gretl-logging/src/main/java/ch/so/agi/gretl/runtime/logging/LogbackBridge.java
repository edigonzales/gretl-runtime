package ch.so.agi.gretl.runtime.logging;

import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.runner.FailureInfo;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * Explicit single-provider Logback bridge for third-party library logs. GRETL
 * lifecycle/task logs do not depend on this bridge; they use RunEventSink.
 */
public final class LogbackBridge implements AutoCloseable {
    private final LoggerContext loggerContext;
    private final LibraryAppender appender;

    private LogbackBridge(LoggerContext loggerContext, LibraryAppender appender) {
        this.loggerContext = loggerContext;
        this.appender = appender;
    }

    public static LogbackBridge install(LoggingPipeline pipeline, LoggingConfig config) {
        java.util.Objects.requireNonNull(pipeline, "pipeline");
        java.util.Objects.requireNonNull(config, "config");
        Object factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext loggerContext)) {
            throw new IllegalStateException(
                    "Exactly one Logback provider must be active, found: "
                            + factory.getClass().getName());
        }
        loggerContext.reset();
        loggerContext.setName("gretl");
        ch.qos.logback.classic.Logger root =
                loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.WARN);
        for (var entry : config.libraryLevels().entrySet()) {
            loggerContext.getLogger(entry.getKey()).setLevel(toLogback(entry.getValue()));
        }
        LibraryAppender appender = new LibraryAppender(pipeline);
        appender.setContext(loggerContext);
        appender.setName("gretl-library");
        appender.start();
        root.addAppender(appender);
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        java.util.logging.Logger.getLogger("").addHandler(new OriginJulBridge(pipeline));
        return new LogbackBridge(loggerContext, appender);
    }

    /** A relayed JUL record cannot acquire the receiving thread's work identity. */
    private static final class OriginJulBridge extends SLF4JBridgeHandler {
        private final LoggingPipeline pipeline;
        OriginJulBridge(LoggingPipeline pipeline) { this.pipeline = pipeline; }
        @Override public void publish(java.util.logging.LogRecord record) {
            if (record != null && (record.getLongThreadID() != Thread.currentThread().threadId()
                    || !MdcExecutionScope.isCurrentJulRecord(record))) {
                LogContext context = pipeline.context();
                try (var scope = pipeline.openScope(new ch.so.agi.gretl.runtime.api.ExecutionIdentity(
                        context.runId(), context.jobName(), Optional.empty(), Optional.empty()), Optional.empty())) {
                    super.publish(record);
                }
            } else {
                super.publish(record);
            }
        }
    }

    @Override
    public void close() {
        try {
            SLF4JBridgeHandler.uninstall();
        } catch (RuntimeException ignored) {
            // Bridge may already be uninstalled by another owner.
        }
        appender.stop();
        ch.qos.logback.classic.Logger root =
                loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.detachAppender(appender);
    }

    static Level toLogback(LogLevel level) {
        return switch (level) {
            case TRACE -> Level.TRACE;
            case DEBUG -> Level.DEBUG;
            case INFO -> Level.INFO;
            case WARN -> Level.WARN;
            case ERROR -> Level.ERROR;
        };
    }

    static LogLevel fromLogback(Level level) {
        if (level == Level.TRACE) {
            return LogLevel.TRACE;
        }
        if (level == Level.DEBUG) {
            return LogLevel.DEBUG;
        }
        if (level == Level.WARN) {
            return LogLevel.WARN;
        }
        if (level == Level.ERROR) {
            return LogLevel.ERROR;
        }
        return LogLevel.INFO;
    }

    private static final class LibraryAppender extends AppenderBase<ILoggingEvent> {
        private final LoggingPipeline pipeline;

        private LibraryAppender(LoggingPipeline pipeline) {
            this.pipeline = pipeline;
        }

        @Override
        protected void append(ILoggingEvent event) {
            try {
                if (event == null) {
                    return;
                }
                pipeline.libraryEvent(fromLogback(event.getLevel()),
                        event.getLoggerName(), event.getFormattedMessage(),
                        failure(event.getThrowableProxy()),
                        LibraryIdentity.capture(event.getMDCPropertyMap(), pipeline.context()), event.getInstant());
            } catch (Throwable ignored) {
                // A library logging failure must never recurse into logging or fail the task.
            }
        }
    }

    static Optional<FailureInfo> failure(IThrowableProxy proxy) {
        if (proxy == null) {
            return Optional.empty();
        }
        String id = UUID.randomUUID().toString();
        return Optional.of(capture(proxy, id, 0));
    }

    private static FailureInfo capture(IThrowableProxy proxy, String id, int depth) {
        boolean truncated = depth >= 16;
        List<FailureInfo> causes = new ArrayList<>();
        if (!truncated && proxy.getCause() != null) {
            causes.add(capture(proxy.getCause(), id, depth + 1));
        }
        List<FailureInfo> suppressed = new ArrayList<>();
        IThrowableProxy[] suppressedRaw = proxy.getSuppressed();
        if (!truncated && suppressedRaw != null) {
            int limit = Math.min(suppressedRaw.length, 8);
            for (int index = 0; index < limit; index++) {
                suppressed.add(capture(suppressedRaw[index], id, depth + 1));
            }
            truncated = suppressedRaw.length > 8;
        }
        StackTraceElementProxy[] stack = proxy.getStackTraceElementProxyArray();
        int frameLimit = stack == null ? 0 : Math.min(stack.length, 64);
        List<FailureInfo.Frame> frames = new ArrayList<>(frameLimit);
        for (int index = 0; index < frameLimit; index++) {
            StackTraceElement element = stack[index].getStackTraceElement();
            frames.add(new FailureInfo.Frame(element.getClassName(), element.getMethodName(),
                    element.getFileName(), element.getLineNumber()));
        }
        boolean framesTruncated = stack != null && stack.length > 64;
        Optional<ch.so.agi.gretl.runtime.runner.SourceLocation> source = frames.stream()
                .filter(frame -> frame.fileName() != null && !frame.fileName().isBlank()
                        && frame.lineNumber() > 0)
                .findFirst()
                .map(frame -> new ch.so.agi.gretl.runtime.runner.SourceLocation(
                        frame.fileName(), frame.lineNumber(), 0));
        return new FailureInfo(id, proxy.getClassName(), proxy.getMessage(),
                causes, suppressed, truncated, frames, framesTruncated, source);
    }
}
