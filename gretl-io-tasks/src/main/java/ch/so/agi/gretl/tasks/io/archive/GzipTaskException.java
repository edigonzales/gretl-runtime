package ch.so.agi.gretl.tasks.io.archive;

/** Task-specific failure of a Gzip operation. */
public final class GzipTaskException extends RuntimeException {
    private final String code;
    private final String phase;

    public GzipTaskException(String code, String message, String phase, Throwable cause) {
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
