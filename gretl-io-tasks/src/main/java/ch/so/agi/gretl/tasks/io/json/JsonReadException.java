package ch.so.agi.gretl.tasks.io.json;

import ch.so.agi.gretl.runtime.api.DiagnosticCarrier;
import ch.so.agi.gretl.runtime.api.DiagnosticInfo;
import java.util.List;
import java.util.Optional;

/** Structured failure for a JSON read without exposing input data. */
public final class JsonReadException extends RuntimeException implements DiagnosticCarrier {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final String phase;

    JsonReadException(String code, String message, String phase) {
        super(message);
        this.code = code;
        this.phase = phase;
    }

    public String code() {
        return code;
    }

    @Override
    public DiagnosticInfo diagnostic() {
        return new DiagnosticInfo(code, Optional.empty(), Optional.empty(), List.of(), Optional.empty());
    }

    public String phase() {
        return phase;
    }
}
