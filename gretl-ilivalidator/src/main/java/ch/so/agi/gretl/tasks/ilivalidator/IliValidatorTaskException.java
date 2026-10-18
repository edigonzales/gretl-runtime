package ch.so.agi.gretl.tasks.ilivalidator;

import ch.so.agi.gretl.runtime.api.DiagnosticCarrier;
import ch.so.agi.gretl.runtime.api.DiagnosticInfo;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Safe validator failure; detailed event text stays in the protected report artifact. */
public final class IliValidatorTaskException extends RuntimeException
        implements DiagnosticCarrier {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final String phase;
    private final int errors;
    private final long warnings;
    private final transient Path reportFile;

    public IliValidatorTaskException(String code, String message, String phase,
                                     int errors, long warnings,
                                     Optional<Path> reportFile, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.phase = phase;
        this.errors = errors;
        this.warnings = warnings;
        this.reportFile = reportFile.orElse(null);
    }

    public String code() { return code; }

    @Override
    public DiagnosticInfo diagnostic() {
        return new DiagnosticInfo(code, Optional.empty(), Optional.empty(),
                List.of(), Optional.empty());
    }

    public String phase() { return phase; }
    public int errors() { return errors; }
    public long warnings() { return warnings; }
    public Optional<Path> reportFile() { return Optional.ofNullable(reportFile); }
}
