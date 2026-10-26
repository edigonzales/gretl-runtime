package ch.so.agi.gretl.runtime.logging;

import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.runner.RunEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Bounded append-only JSONL event file with a terminal-event reserve. */
final class EventFileSink implements AutoCloseable {
    private static final long RESERVE_BYTES = 256L * 1024;

    private final Path path;
    private final OutputStream output;
    private final LoggingConfig config;
    private final LogContext context;
    private final RecordEncoder encoder;
    private final long normalLimit;
    private final Set<String> detailIds = new java.util.HashSet<>();
    private long bytesWritten;
    private boolean truncated;
    private boolean lost;
    private boolean degraded;
    private boolean markerWritten;
    private long recordTruncations;

    EventFileSink(Path path, LoggingConfig config, LogContext context) throws IOException {
        this.path = path;
        this.config = config;
        this.context = context;
        this.encoder = new RecordEncoder(context);
        PrivateDiagnosticFiles.createParents(path.toAbsolutePath().normalize().getParent());
        this.output = PrivateDiagnosticFiles.newEventFile(path);
        this.normalLimit = Math.max(1, config.maxEventFileBytes()
                - Math.min(RESERVE_BYTES, config.maxEventFileBytes() / 8));
    }

    synchronized void write(LogEvent event) {
        if (lost || degraded) {
            return;
        }
        boolean terminal = terminal(event.eventName());
        boolean includeDetail = !detailIds.contains(event.failure()
                .map(ch.so.agi.gretl.runtime.runner.FailureInfo::id).orElse(""));
        RecordEncoder.Encoded encoded = encoder.encode(
                event, config.maxRecordBytes(), includeDetail);
        recordTruncations += encoded.truncated() ? 1 : 0;
        long limit = terminal ? config.maxEventFileBytes() : normalLimit;
        if (!terminal && bytesWritten + encoded.bytes().length > normalLimit) {
            truncated = true;
            if (!markerWritten) {
                writeMarker(event);
            }
            return;
        }
        if (bytesWritten + encoded.bytes().length > limit) {
            lost = true;
            return;
        }
        writeBytes(encoded.bytes());
        if (includeDetail) {
            event.failure().ifPresent(failure -> detailIds.add(failure.id()));
        }
    }

    private void writeMarker(LogEvent trigger) {
        markerWritten = true;
        LogEvent marker = new LogEvent(
                trigger.sequence(), context.commandId() + ":" + trigger.sequence(),
                context.commandId(),
                Instant.now(), LogLevel.WARN, "gretl.logging.truncated",
                "Ereignisdatei hat das allgemeine Limit erreicht; weitere normale Ereignisse "
                        + "werden nicht mehr geschrieben.",
                Optional.of(context.runId()), Optional.of(context.jobName()),
                Optional.empty(), Optional.empty(), Optional.empty(),
                "logging", "", 0,
                Map.of("max_event_file_bytes",
                        Long.toString(config.maxEventFileBytes())),
                Optional.empty(), Optional.empty());
        // The marker intentionally bypasses the normal limit and uses the reserve.
        RecordEncoder.Encoded encoded = encoder.encode(marker, config.maxRecordBytes(), true);
        if (bytesWritten + encoded.bytes().length <= config.maxEventFileBytes()) {
            writeBytes(encoded.bytes());
        } else {
            lost = true;
        }
    }

    private void writeBytes(byte[] bytes) {
        try {
            output.write(bytes);
            output.flush();
            bytesWritten += bytes.length;
        } catch (IOException exception) {
            degraded = true;
        }
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(path, degraded, truncated, lost, recordTruncations);
    }

    @Override
    public synchronized void close() {
        try {
            output.close();
        } catch (IOException ignored) {
            degraded = true;
        }
    }

    private static boolean terminal(String eventName) {
        return eventName.equals("gretl.run.finished")
                || eventName.equals("gretl.task.failed")
                || eventName.equals("gretl.cleanup.failed")
                || eventName.equals("gretl.logging.truncated")
                || eventName.equals("gretl.logging.degraded");
    }

    record Snapshot(Path path, boolean degraded, boolean truncated, boolean lost,
                    long recordTruncations) {
    }
}
