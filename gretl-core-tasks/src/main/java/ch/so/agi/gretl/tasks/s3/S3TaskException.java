package ch.so.agi.gretl.tasks.s3;

/** Task-specific failure of an S3 operation. */
public final class S3TaskException extends RuntimeException {
    private final String code;
    private final String phase;

    public S3TaskException(String code, String message, String phase, Throwable cause) {
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
