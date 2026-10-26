package ch.so.agi.gretl.runtime.logging;

import ch.so.agi.gretl.runtime.api.ExecutionIdentity;
import ch.so.agi.gretl.runtime.runner.RunEventSink;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.MDC;

/** Thread-owned binding. Only GRETL's reserved keys belong to this scope. */
final class MdcExecutionScope implements RunEventSink.Scope {
    static final String RUN = "gretl.run_id", JOB = "gretl.job_name", TASK = "gretl.task_id",
            INVOCATION = "gretl.invocation_id", OPERATION = "gretl.operation_id";
    static final List<String> KEYS = List.of(RUN, JOB, TASK, INVOCATION, OPERATION);
    // Ordering only, no identity or inherited context. LogRecord assigns a JDK-wide
    // creation sequence; records predating the current scope interval are untrusted.
    private static final ThreadLocal<Long> JUL_BOUNDARY = new ThreadLocal<>();
    private final Map<String,String> previous = new LinkedHashMap<>();
    private final Long previousJulBoundary = JUL_BOUNDARY.get();
    private final Thread owner = Thread.currentThread();
    private final Runnable failure;
    private boolean closed;

    MdcExecutionScope(ExecutionIdentity identity, Optional<String> operationId, Runnable failure) {
        this.failure = failure;
        for (String key : KEYS) previous.put(key, MDC.get(key));
        try {
            set(RUN, identity.runId().toString()); set(JOB, identity.jobName());
            set(TASK, identity.taskId().orElse(null));
            set(INVOCATION, identity.invocationId().orElse(null));
            set(OPERATION, operationId.orElse(null));
            JUL_BOUNDARY.set(newJulBoundary());
        } catch (RuntimeException exception) {
            restore();
            throw exception;
        }
    }

    private static void set(String key, String value) {
        if (value == null) MDC.remove(key); else MDC.put(key, value);
    }

    private void restore() {
        boolean failed = false;
        for (var entry : previous.entrySet()) {
            try { set(entry.getKey(), entry.getValue()); }
            catch (RuntimeException exception) { failed = true; }
        }
        // A resumed parent is a new interval too: an inner record must not acquire
        // the restored parent's identity when published after invocation close.
        if (previousJulBoundary == null) JUL_BOUNDARY.remove();
        else JUL_BOUNDARY.set(newJulBoundary());
        if (failed) failure.run();
    }

    private static long newJulBoundary() {
        return new java.util.logging.LogRecord(java.util.logging.Level.OFF, "").getSequenceNumber();
    }

    static boolean isCurrentJulRecord(java.util.logging.LogRecord record) {
        Long boundary = JUL_BOUNDARY.get();
        return boundary != null && record.getSequenceNumber() >= boundary;
    }

    @Override public void close() {
        if (closed) return;
        if (Thread.currentThread() != owner) { failure.run(); return; }
        closed = true;
        restore();
    }
}
