package ch.so.agi.gretl.tasks.basic;

/** Task-specific failure of a CSV/JSON/tabular task. */
public final class TabularTaskException extends RuntimeException {
    private final String code;
    private final String phase;

    public TabularTaskException(String code, String message, String phase, Throwable cause) {
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
