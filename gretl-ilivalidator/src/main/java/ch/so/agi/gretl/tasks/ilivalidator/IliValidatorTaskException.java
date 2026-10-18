package ch.so.agi.gretl.tasks.ilivalidator;

import java.nio.file.Path;
import java.util.Optional;

/** Safe validator failure; detailed event text stays in the protected report artifact. */
public final class IliValidatorTaskException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String phase;
    private final int errors;
    private final long warnings;
    private final transient Path reportFile;

    public IliValidatorTaskException(String code, String message, String phase,
                                     int errors, long warnings,
                                     Optional<Path> reportFile, Throwable cause) {
        super(code + ": " + message + " [phase=" + phase + ", errors=" + errors
                + ", warnings=" + warnings
                + (reportFile.isPresent() ? ", report=" + reportFile.orElseThrow() : "")
                + "]", cause);
        this.phase = phase;
        this.errors = errors;
        this.warnings = warnings;
        this.reportFile = reportFile.orElse(null);
    }

    public String phase() { return phase; }
    public int errors() { return errors; }
    public long warnings() { return warnings; }
    public Optional<Path> reportFile() { return Optional.ofNullable(reportFile); }
}
