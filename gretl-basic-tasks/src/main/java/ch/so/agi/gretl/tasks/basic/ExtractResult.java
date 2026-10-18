package ch.so.agi.gretl.tasks.basic;

import java.nio.file.Path;
import java.util.List;

/** Completed ZIP/GZIP extraction. */
public record ExtractResult(Path file, List<Path> files, Path directory, long bytes) {
    public ExtractResult {
        java.util.Objects.requireNonNull(files, "files");
        files = List.copyOf(files);
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes must not be negative");
        }
    }
}
