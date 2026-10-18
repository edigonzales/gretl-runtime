package ch.so.agi.gretl.runtime.runner;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Terminal immutable summary of one synchronous operation invocation. */
public record InvocationOutcome(
        String invocationId, String parentTaskId, String operationId,
        InvocationStatus status, String phase,
        Optional<Instant> startedAt, Optional<Instant> finishedAt,
        long durationMillis, Optional<FailureInfo> failure,
        List<FailureInfo> secondaryFailures) {

    public InvocationOutcome {
        text(invocationId, "invocationId");
        text(parentTaskId, "parentTaskId");
        text(operationId, "operationId");
        java.util.Objects.requireNonNull(status, "status");
        text(phase, "phase");
        java.util.Objects.requireNonNull(startedAt, "startedAt");
        java.util.Objects.requireNonNull(finishedAt, "finishedAt");
        java.util.Objects.requireNonNull(failure, "failure");
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must not be negative");
        }
        if (status == InvocationStatus.FAILED && failure.isEmpty()) {
            throw new IllegalArgumentException("FAILED invocation requires a failure");
        }
        secondaryFailures = List.copyOf(secondaryFailures);
    }

    private static void text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
