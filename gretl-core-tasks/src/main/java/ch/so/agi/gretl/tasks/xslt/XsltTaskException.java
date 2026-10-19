package ch.so.agi.gretl.tasks.xslt;

/** Task-specific failure of an XSLT transformation. */
public final class XsltTaskException extends RuntimeException {
    private final String code;
    private final String phase;

    public XsltTaskException(String code, String message, String phase, Throwable cause) {
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
