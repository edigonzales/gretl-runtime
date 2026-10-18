package ch.so.agi.gretl.tasks.basic;

/** Safe SQL failure details; SQL text and bound values are never included. */
public final class SqlTaskException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String databaseAlias;
    private final String scriptName;
    private final int entryIndex;
    private final String phase;
    private final String sqlState;
    private final int vendorCode;

    public SqlTaskException(String code, String message, String databaseAlias,
                            String scriptName, int entryIndex, String phase,
                            String sqlState, int vendorCode, Throwable cause) {
        super(code + ": " + message + " [database=" + databaseAlias
                + (scriptName == null ? "" : ", script=" + scriptName)
                + ", entry=" + entryIndex + ", phase=" + phase
                + (sqlState == null ? "" : ", SQLSTATE=" + sqlState)
                + (vendorCode == 0 ? "" : ", vendor=" + vendorCode) + "]", cause);
        this.databaseAlias = databaseAlias;
        this.scriptName = scriptName;
        this.entryIndex = entryIndex;
        this.phase = phase;
        this.sqlState = sqlState;
        this.vendorCode = vendorCode;
    }

    public String databaseAlias() { return databaseAlias; }
    public String scriptName() { return scriptName; }
    public int entryIndex() { return entryIndex; }
    public String phase() { return phase; }
    public String sqlState() { return sqlState; }
    public int vendorCode() { return vendorCode; }
}
