package ch.so.agi.gretl.runtime.runner;

/** Failed workspace creation or resource preparation before run.started. */
public final class RunStartException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public RunStartException(Throwable cause) {
        super("RUN_WORKSPACE_PREPARATION_FAILED: " + cause.getMessage(), cause);
    }
}
