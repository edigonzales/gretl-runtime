package ch.so.agi.gretl.tasks.database;

/** Task-specific failure of a Db2Db transfer. */
public final class Db2DbTaskException extends RuntimeException {
    private final String code;
    private final String phase;

    public Db2DbTaskException(String code, String message, String phase, Throwable cause) {
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
