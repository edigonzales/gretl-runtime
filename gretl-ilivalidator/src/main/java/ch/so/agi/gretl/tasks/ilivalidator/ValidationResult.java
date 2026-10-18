package ch.so.agi.gretl.tasks.ilivalidator;

import java.nio.file.Path;

/** Successful local INTERLIS validation. */
public record ValidationResult(Path file, Path reportFile, long warnings) {
    public ValidationResult {
        java.util.Objects.requireNonNull(file, "file");
        java.util.Objects.requireNonNull(reportFile, "reportFile");
        if (warnings < 0) {
            throw new IllegalArgumentException("warnings must not be negative");
        }
    }
}
