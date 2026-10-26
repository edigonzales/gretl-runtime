package ch.so.agi.gretl.runtime.logging;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Stable process/run identity shared by all managed sinks. */
public record LogContext(
        String commandId, UUID runId, String jobName, String runtimeVersion,
        Optional<String> deploymentEnvironment, Locale locale,
        Optional<String> correlationId) {

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
        java.util.Objects.requireNonNull(locale, "locale");
        java.util.Objects.requireNonNull(correlationId, "correlationId");
        correlationId.ifPresent(value -> {
            if (value.isBlank() || value.length() > 256
                    || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid correlationId");
            }
        });
    }

    public LogContext(String commandId, UUID runId, String jobName,
                      String runtimeVersion, Optional<String> deploymentEnvironment,
                      Locale locale) {
        this(commandId, runId, jobName, runtimeVersion, deploymentEnvironment,
                locale, Optional.empty());
    }

    public LogContext(String commandId, UUID runId, String jobName,
                      String runtimeVersion, Optional<String> deploymentEnvironment) {
        this(commandId, runId, jobName, runtimeVersion, deploymentEnvironment,
                Locale.GERMAN);
    }
}
