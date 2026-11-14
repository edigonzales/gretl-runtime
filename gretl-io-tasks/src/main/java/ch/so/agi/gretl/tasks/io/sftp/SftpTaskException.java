package ch.so.agi.gretl.tasks.io.sftp;

import ch.so.agi.gretl.runtime.api.DiagnosticCarrier;
import ch.so.agi.gretl.runtime.api.DiagnosticInfo;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

final class SftpTaskException extends IOException implements DiagnosticCarrier {
    private static final long serialVersionUID = 1L;
    private final String code;

    SftpTaskException(String code, String message) {
        super(message);
        this.code = code;
    }

    @Override
    public DiagnosticInfo diagnostic() {
        return new DiagnosticInfo(code, Optional.empty(), Optional.empty(), List.of(), Optional.empty());
    }
}
