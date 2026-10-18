package ch.so.agi.gretl.tasks.basic;

/** Safe archive failure. Entry names/content are not copied into the message. */
public final class ExtractTaskException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExtractTaskException(String code, String message, Throwable cause) {
        super(code + ": " + message, cause);
    }
}
