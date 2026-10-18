package ch.so.agi.gretl.runtime.logging;

import ch.so.agi.gretl.runtime.api.LogLevel;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Text or one-line-JSON stderr console. */
final class ConsoleSink {
    private final PrintStream stream;
    private final LoggingConfig config;
    private final LogContext context;
    private final RecordEncoder encoder;
    private final TextFormatter text;
    private final Set<String> detailIds = new HashSet<>();
    private boolean degraded;

    ConsoleSink(PrintStream stream, LoggingConfig config, LogContext context) {
        this.stream = stream;
        this.config = config;
        this.context = context;
        this.encoder = new RecordEncoder(context);
        this.text = new TextFormatter(context.locale());
    }

    synchronized void write(LogEvent event) {
        if (degraded || event.level().ordinal() < config.level().ordinal()) {
            return;
        }
        boolean includeDetail = !detailIds.contains(event.failure()
                .map(ch.so.agi.gretl.runtime.runner.FailureInfo::id).orElse(""));
        try {
            if (config.format() == LoggingConfig.Format.JSON) {
                RecordEncoder.Encoded encoded = encoder.encode(
                        event, config.maxRecordBytes(), includeDetail);
                stream.write(encoded.bytes(), 0, encoded.bytes().length);
            } else {
                LogEvent effective = includeDetail ? event : withoutFailure(event);
                stream.println(text.format(effective));
            }
            stream.flush();
            if (stream.checkError()) {
                degraded = true;
            }
            if (includeDetail) {
                event.failure().ifPresent(failure -> detailIds.add(failure.id()));
            }
        } catch (RuntimeException exception) {
            degraded = true;
        }
    }

    synchronized boolean degraded() {
        return degraded;
    }

    private static LogEvent withoutFailure(LogEvent event) {
        return new LogEvent(event.sequence(), event.eventId(), event.commandId(),
                event.timestamp(), event.level(), event.eventName(), event.message(),
                event.runId(), event.jobName(), event.taskId(), event.invocationId(),
                event.operationId(), event.phase(), event.outcome(), event.durationMillis(),
                event.attributes(), java.util.Optional.empty(), event.source());
    }
}
