package ch.so.agi.gretl.runtime.logging;

import java.util.Optional;
import java.util.UUID;

/** Stable process/run identity shared by all managed sinks. */
public record LogContext(
        String commandId, UUID runId, String jobName, String runtimeVersion,
        Optional<String> deploymentEnvironment) {

    public LogContext {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("commandId must not be blank");
        }
        java.util.Objects.requireNonNull(runId, "runId");
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalArgumentException("jobName must not be blank");
        }
        if (runtimeVersion == null || runtimeVersion.isBlank()) {
            throw new IllegalArgumentException("runtimeVersion must not be blank");
        }
        java.util.Objects.requireNonNull(deploymentEnvironment, "deploymentEnvironment");
    }
}
