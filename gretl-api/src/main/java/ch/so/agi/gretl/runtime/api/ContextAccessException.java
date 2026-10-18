package ch.so.agi.gretl.runtime.api;

/** Exception for a documented, non-defect context access failure. */
public final class ContextAccessException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
    private final String code;

    public ContextAccessException(String code, String message) {
        super(message);
        this.code = ApiChecks.text(code, "code");
    }

    public String code() {
        return code;
    }
}
