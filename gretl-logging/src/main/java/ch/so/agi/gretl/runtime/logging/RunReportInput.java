package ch.so.agi.gretl.runtime.logging;

import ch.so.agi.gretl.runtime.runner.ExecutionPlan;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.JobOutcome;
import java.nio.file.Path;
import java.util.Optional;

/** All safe inputs needed for one schema-1 run report. */
public record RunReportInput(
        LogContext context, JobOutcome outcome, JobDefinition definition,
        ExecutionPlan plan, Path scriptFile, String scriptSha256,
        Optional<String> jobRevision, LoggingState logging) {

    public RunReportInput {
        java.util.Objects.requireNonNull(context, "context");
        java.util.Objects.requireNonNull(outcome, "outcome");
        java.util.Objects.requireNonNull(definition, "definition");
        java.util.Objects.requireNonNull(plan, "plan");
        java.util.Objects.requireNonNull(scriptFile, "scriptFile");
        if (scriptSha256 == null || scriptSha256.isBlank()) {
            throw new IllegalArgumentException("scriptSha256 must not be blank");
        }
        java.util.Objects.requireNonNull(jobRevision, "jobRevision");
        java.util.Objects.requireNonNull(logging, "logging");
    }
}
