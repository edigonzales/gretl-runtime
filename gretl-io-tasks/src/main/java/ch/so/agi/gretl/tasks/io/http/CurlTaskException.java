package ch.so.agi.gretl.tasks.io.http;

/** Task-specific failure of a Curl HTTP request. */
public final class CurlTaskException extends RuntimeException {
    private final String code;
    private final int status;
    private final long bytes;
    private final String phase;

    public CurlTaskException(String code, String message, int status, long bytes,
                             String phase, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
        this.bytes = bytes;
        this.phase = phase;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public long bytes() {
        return bytes;
    }

    public String phase() {
        return phase;
    }
}
