package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.runtime.api.DiagnosticCarrier;
import ch.so.agi.gretl.runtime.api.DiagnosticInfo;
import java.util.List;
import java.util.Optional;

/** Safe archive failure. Entry names/content are not copied into the message. */
public final class ExtractTaskException extends RuntimeException
        implements DiagnosticCarrier {
    private static final long serialVersionUID = 1L;

    private final String code;

    public ExtractTaskException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }

    @Override
    public DiagnosticInfo diagnostic() {
        return new DiagnosticInfo(code, Optional.empty(), Optional.empty(),
                List.of(), Optional.empty());
    }
}
