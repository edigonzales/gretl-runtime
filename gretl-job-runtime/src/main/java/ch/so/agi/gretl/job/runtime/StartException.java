package ch.so.agi.gretl.job.runtime;

/** CLI failure with a documented exit-code class and a human-readable diagnostic. */
public final class StartException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int code;
    private final String diagnosticCode;
    private final String field;
    private final String task;
    private final String source;

    public StartException(int code, String message) {
        this(code, message, null, "CLI_ERROR", null, null, null);
    }

    public StartException(int code, String message, Throwable cause) {
        this(code, message, cause, "CLI_ERROR", null, null, null);
    }

    public StartException(int code, String message, Throwable cause, String diagnosticCode,
                        String field, String task, String source) {
        super(message, cause);
        this.code = code;
        this.diagnosticCode = diagnosticCode == null || diagnosticCode.isBlank()
                ? "CLI_ERROR" : diagnosticCode;
        this.field = field;
        this.task = task;
        this.source = source;
    }

    public int code() {
        return code;
    }

    public String diagnosticCode() {
        return diagnosticCode;
    }

    public String field() {
        return field;
    }

    public String task() {
        return task;
    }

    public String source() {
        return source;
    }
}
