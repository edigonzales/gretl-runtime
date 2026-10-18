package ch.so.agi.gretl.runtime.logging;

import java.nio.file.Path;
import java.util.Optional;

/** Bounded observability state exposed to the run report. */
public record LoggingState(
        LoggingConfig.Format format, boolean eventFileEnabled,
        Optional<Path> eventFile, boolean degraded, boolean fileTruncated,
        boolean fileLost, long recordTruncations) {
}
