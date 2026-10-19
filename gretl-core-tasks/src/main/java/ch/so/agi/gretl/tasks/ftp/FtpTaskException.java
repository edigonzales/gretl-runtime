package ch.so.agi.gretl.tasks.ftp;

/** Task-specific failure of an FTP operation. */
public final class FtpTaskException extends RuntimeException {
    private final String code;
    private final String phase;

    public FtpTaskException(String code, String message, String phase, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.phase = phase;
    }

    public String code() {
        return code;
    }

    public String phase() {
        return phase;
    }
}
