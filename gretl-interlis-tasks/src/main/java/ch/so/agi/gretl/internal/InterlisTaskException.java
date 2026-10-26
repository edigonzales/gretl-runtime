package ch.so.agi.gretl.internal;

import java.nio.file.Path;
import java.util.Optional;

/** Failure of an INTERLIS format task. */
public final class InterlisTaskException extends RuntimeException implements ch.so.agi.gretl.runtime.api.DiagnosticCarrier {
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

    @Override public ch.so.agi.gretl.runtime.api.DiagnosticInfo diagnostic() {
        return new ch.so.agi.gretl.runtime.api.DiagnosticInfo(code, Optional.empty(), Optional.empty(), java.util.List.of(), Optional.empty());
    }

    public String code() { return code; }
    public String phase() { return phase; }
    public Optional<Path> report() { return report; }
}
