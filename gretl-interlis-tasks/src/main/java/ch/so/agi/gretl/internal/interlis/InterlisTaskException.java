package ch.so.agi.gretl.internal.interlis;

import java.nio.file.Path;
import java.util.Optional;

/** Failure of an INTERLIS format task. */
public final class InterlisTaskException extends RuntimeException {
    private final String code;
    private final String phase;
    private final Optional<Path> report;

    public InterlisTaskException(String code, String message, String phase,
                                 Optional<Path> report, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.phase = phase;
        this.report = report == null ? Optional.empty() : report;
    }

    public String code() { return code; }
    public String phase() { return phase; }
    public Optional<Path> report() { return report; }
}
