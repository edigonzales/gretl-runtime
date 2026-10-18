package ch.so.agi.gretl.runtime.logging;

import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.runner.FailureInfo;
import ch.so.agi.gretl.runtime.runner.SourceLocation;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Canonical event after sequencing and redaction. */
public record LogEvent(
        long sequence, String eventId, String commandId, Instant timestamp,
        LogLevel level, String eventName, String message,
        Optional<UUID> runId, Optional<String> jobName, Optional<String> taskId,
        Optional<String> invocationId, Optional<String> operationId,
        String phase, String outcome, long durationMillis,
        Map<String, String> attributes, Optional<FailureInfo> failure,
        Optional<SourceLocation> source) {

    public LogEvent {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        java.util.Objects.requireNonNull(eventId, "eventId");
        java.util.Objects.requireNonNull(commandId, "commandId");
        java.util.Objects.requireNonNull(timestamp, "timestamp");
        java.util.Objects.requireNonNull(level, "level");
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("eventName must not be blank");
        }
        message = message == null ? "" : message;
        java.util.Objects.requireNonNull(runId, "runId");
        java.util.Objects.requireNonNull(jobName, "jobName");
        java.util.Objects.requireNonNull(taskId, "taskId");
        java.util.Objects.requireNonNull(invocationId, "invocationId");
        java.util.Objects.requireNonNull(operationId, "operationId");
        phase = phase == null ? "" : phase;
        outcome = outcome == null ? "" : outcome;
        attributes = Map.copyOf(attributes);
        java.util.Objects.requireNonNull(failure, "failure");
        java.util.Objects.requireNonNull(source, "source");
    }
}
