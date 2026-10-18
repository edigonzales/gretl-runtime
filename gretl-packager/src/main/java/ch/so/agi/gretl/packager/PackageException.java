package ch.so.agi.gretl.packager;

/** A safe, user-facing packager failure. */
public final class PackageException extends Exception {
    private static final long serialVersionUID = 1L;
    private final String code;

    public PackageException(String code, String message) {
        super(code + ": " + message);
        this.code = code;
    }

    public PackageException(String code, String message, Throwable cause) {
        super(code + ": " + message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
