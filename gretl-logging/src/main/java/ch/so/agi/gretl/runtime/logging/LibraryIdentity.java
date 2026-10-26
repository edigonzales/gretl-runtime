package ch.so.agi.gretl.runtime.logging;

import ch.so.agi.gretl.runtime.api.ExecutionIdentity;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Immutable identity captured from the originating event, never consumer-thread MDC. */
record LibraryIdentity(ExecutionIdentity identity, Optional<String> operationId) {
    static Optional<LibraryIdentity> capture(Map<String,String> mdc, LogContext context) {
        if (mdc == null) return Optional.empty();
        try {
            String rawRun = mdc.get(MdcExecutionScope.RUN);
            UUID run = UUID.fromString(rawRun);
            if (!run.toString().equals(rawRun) || !run.equals(context.runId())
                    || !context.jobName().equals(mdc.get(MdcExecutionScope.JOB))) return Optional.empty();
            Optional<String> task = value(mdc, MdcExecutionScope.TASK);
            Optional<String> invocation = value(mdc, MdcExecutionScope.INVOCATION);
            Optional<String> operation = value(mdc, MdcExecutionScope.OPERATION);
            if (invocation.isPresent() != operation.isPresent()) return Optional.empty();
            return Optional.of(new LibraryIdentity(new ExecutionIdentity(run, context.jobName(), task, invocation), operation));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    private static Optional<String> value(Map<String,String> mdc, String key) {
        String value = mdc.get(key);
        if (value != null && (value.isBlank() || value.chars().anyMatch(Character::isISOControl)))
            throw new IllegalArgumentException("Invalid reserved MDC identity");
        return Optional.ofNullable(value);
    }
}
