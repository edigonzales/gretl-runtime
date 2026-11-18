package ch.so.agi.gretl.runtime.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Optional;

/** Bounded JSONL file. The pipeline serializes access to both sinks. */
final class EventFileSink implements AutoCloseable {
    private static final long RESERVE_BYTES = 256L * 1024;

    private final Path path;
    private final OutputStream output;
    private final LoggingConfig config;
    private final long normalLimit;
    private long bytesWritten;
    private boolean truncated;
    private boolean degraded;

    EventFileSink(Path path, LoggingConfig config) {
        this.path = path;
        this.config = config;
        normalLimit = config.maxEventFileBytes()
                - Math.min(RESERVE_BYTES, config.maxEventFileBytes() / 8);
        OutputStream opened = null;
        try {
            PrivateDiagnosticFiles.createParents(path.toAbsolutePath().normalize().getParent());
            opened = PrivateDiagnosticFiles.newEventFile(path);
        } catch (IOException | SecurityException exception) {
            degraded = true;
        }
        output = opened;
    }

    boolean accepts(LogEvent event) {
        return !degraded && (!truncated || limit(event) > normalLimit);
    }

    void write(LogEvent event, byte[] bytes) {
        if (bytes == null || bytes.length > limit(event) - bytesWritten) {
            truncated = true;
            return;
        }
        try {
            output.write(bytes);
            bytesWritten += bytes.length;
        } catch (IOException | RuntimeException exception) {
            degraded = true;
        }
    }

    private long limit(LogEvent event) {
        return switch (event.eventName()) {
            case "gretl.run.finished" -> config.maxEventFileBytes();
            case "gretl.task.failed", "gretl.cleanup.failed",
                    "gretl.logging.degraded", "gretl.report.write_failed" ->
                    config.maxEventFileBytes() - config.maxRecordBytes();
            default -> normalLimit;
        };
    }

    Optional<Path> path() {
        return output == null ? Optional.empty() : Optional.of(path);
    }

    boolean truncated() {
        return truncated;
    }

    boolean degraded() {
        return degraded;
    }

    @Override
    public void close() {
        if (output != null) {
            try {
                output.close();
            } catch (IOException | RuntimeException exception) {
                degraded = true;
            }
        }
    }
}
