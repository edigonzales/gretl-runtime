package ch.so.agi.gretl.runtime.logging;

import ch.so.agi.gretl.runtime.api.Diagnostic;
import ch.so.agi.gretl.runtime.api.DiagnosticArtifact;
import ch.so.agi.gretl.runtime.runner.ExecutionPlan;
import ch.so.agi.gretl.runtime.runner.FailureInfo;
import ch.so.agi.gretl.runtime.runner.InvocationOutcome;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.JobOutcome;
import ch.so.agi.gretl.runtime.runner.ResultSlot;
import ch.so.agi.gretl.runtime.runner.TaskDefinition;
import ch.so.agi.gretl.runtime.runner.TaskOutcome;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Writes the final schema-1 JSON report atomically. */
public final class RunReportWriter {
    private static final int MAX_INVOCATIONS = 10_000;
    private static final int MAX_DIAGNOSTICS = 1_000;

    private final Redactor redactor;

    public RunReportWriter(Redactor redactor) {
        this.redactor = java.util.Objects.requireNonNull(redactor, "redactor");
    }

    public void write(RunReportInput input, Path reportFile) throws IOException {
        java.util.Objects.requireNonNull(input, "input");
        java.util.Objects.requireNonNull(reportFile, "reportFile");
        Path target = reportFile.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Report path has no parent: " + target);
        }
        PrivateDiagnosticFiles.createParents(parent);
        byte[] bytes = (Json.write(report(input)) + "\n").getBytes(StandardCharsets.UTF_8);
        Path temporary = PrivateDiagnosticFiles.newReportTemporary(parent, "." + target.getFileName() + ".");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    Map<String, Object> report(RunReportInput input) {
        JobOutcome outcome = input.outcome();
        JobDefinition definition = input.definition();
        ExecutionPlan plan = input.plan();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1);
        report.put("identity", identity(input));
        report.put("source", source(input));
        report.put("execution", execution(input));
        report.put("errors", errors(outcome));
        report.put("diagnostics", diagnostics(outcome));
        report.put("logging", logging(input.logging()));
        report.put("telemetry", Map.of("mode", "none"));
        return report;
    }

    private Map<String, Object> identity(RunReportInput input) {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("commandId", input.context().commandId());
        identity.put("runId", input.context().runId().toString());
        identity.put("jobName", input.context().jobName());
        input.context().correlationId().ifPresent(value ->
                identity.put("correlationId", redactor.redact(value)));
        identity.put("runtimeVersion", input.context().runtimeVersion());
        identity.put("locale", input.context().locale().toLanguageTag());
        identity.put("inventoryHash", input.inventoryHash());
        identity.put("definitionId", input.definition().definitionId().toString());
        return identity;
    }

    private Map<String, Object> source(RunReportInput input) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("file", input.scriptFile().getFileName().toString());
        source.put("sha256", input.scriptSha256());
        input.jobRevision().ifPresent(revision -> {
            source.put("revision", revision);
            source.put("revision_source", "provided");
        });
        return source;
    }

    private Map<String, Object> execution(RunReportInput input) {
        JobOutcome outcome = input.outcome();
        ExecutionPlan plan = input.plan();
        Map<String, Object> execution = new LinkedHashMap<>();
        execution.put("status", outcome.status().name());
        execution.put("cancelReason", outcome.cancelReason().map(Enum::name).orElse(null));
        execution.put("exitCode", outcome.exitCode());
        execution.put("startedAt", timestamp(outcome.startedAt()));
        execution.put("finishedAt", timestamp(outcome.finishedAt()));
        execution.put("durationMillis", outcome.durationMillis());
        execution.put("requestedTargets", outcome.requestedTargets());
        execution.put("finishRequested", outcome.finishRequested());
        outcome.finishReason().ifPresent(reason -> execution.put("finishReason", reason));
        execution.put("workRetained", outcome.workRetained());
        execution.put("selectedPlan", plan.selectedTasks().stream()
                .map(this::plannedTask).toList());
        execution.put("tasks", outcome.tasks().stream().map(this::task).toList());
        List<InvocationOutcome> allInvocations = outcome.invocations();
        execution.put("invocations", allInvocations.stream().limit(MAX_INVOCATIONS)
                .map(this::invocation).toList());
        execution.put("invocationsTruncated", allInvocations.size() > MAX_INVOCATIONS);
        return execution;
    }

    private Map<String, Object> plannedTask(TaskDefinition<?> task) {
        Map<String, Object> planned = new LinkedHashMap<>();
        planned.put("taskId", task.id());
        planned.put("registrationIndex", task.registrationIndex());
        planned.put("dependsOn", task.dependsOn().stream().map(ref -> ref.id()).toList());
        planned.put("after", task.after().stream().map(ref -> ref.id()).toList());
        planned.put("condition", task.condition() != null);
        if (task.timeout() != null) {
            planned.put("timeout", task.timeout().toString());
        }
        if (task.source() != null && !task.source().equals(
                ch.so.agi.gretl.runtime.runner.SourceLocation.unknown())) {
            planned.put("source", EventJson.sourceMap(task.source()));
        }
        return planned;
    }

    private Map<String, Object> task(TaskOutcome task) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("taskId", task.taskId());
        value.put("status", task.status().name());
        value.put("reason", task.reason());
        value.put("cancelReason", task.cancelReason().map(Enum::name).orElse(null));
        value.put("failurePhase", task.failurePhase());
        task.startedAt().ifPresent(time -> value.put("startedAt", timestamp(time)));
        task.finishedAt().ifPresent(time -> value.put("finishedAt", timestamp(time)));
        value.put("durationMillis", task.durationMillis());
        value.put("hasValue", task.slot() instanceof ResultSlot.Value);
        value.put("invocationCount", task.invocations().size());
        task.failure().ifPresent(failure -> value.put("errorRef", failure.id()));
        value.put("secondaryErrorRefs", task.secondaryFailures().stream()
                .map(FailureInfo::id).toList());
        return value;
    }

    private Map<String, Object> invocation(InvocationOutcome invocation) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("invocationId", invocation.invocationId());
        value.put("parentTaskId", invocation.parentTaskId());
        value.put("operationId", invocation.operationId());
        value.put("status", invocation.status().name());
        value.put("cancelReason", invocation.cancelReason().map(Enum::name).orElse(null));
        value.put("phase", invocation.phase());
        invocation.startedAt().ifPresent(time -> value.put("startedAt", timestamp(time)));
        invocation.finishedAt().ifPresent(time -> value.put("finishedAt", timestamp(time)));
        value.put("durationMillis", invocation.durationMillis());
        invocation.failure().ifPresent(failure -> value.put("errorRef", failure.id()));
        value.put("secondaryErrorRefs", invocation.secondaryFailures().stream()
                .map(FailureInfo::id).toList());
        return value;
    }

    private List<Map<String, Object>> errors(JobOutcome outcome) {
        Map<String, FailureInfo> unique = new LinkedHashMap<>();
        outcome.primaryFailure().ifPresent(failure ->
                collect(failure, unique, new LinkedHashSet<>()));
        outcome.secondaryFailures().forEach(failure ->
                collect(failure, unique, new LinkedHashSet<>()));
        for (TaskOutcome task : outcome.tasks()) {
            task.failure().ifPresent(failure ->
                    collect(failure, unique, new LinkedHashSet<>()));
            task.secondaryFailures().forEach(failure ->
                    collect(failure, unique, new LinkedHashSet<>()));
        }
        for (InvocationOutcome invocation : outcome.invocations()) {
            invocation.failure().ifPresent(failure ->
                    collect(failure, unique, new LinkedHashSet<>()));
            invocation.secondaryFailures().forEach(failure ->
                    collect(failure, unique, new LinkedHashSet<>()));
        }
        return unique.values().stream()
                .map(redactor::redact)
                .map(EventJson::failureMap)
                .toList();
    }

    private static void collect(FailureInfo failure, Map<String, FailureInfo> target,
                                Set<String> seen) {
        if (failure == null || !seen.add(failure.id())) {
            return;
        }
        target.putIfAbsent(failure.id(), failure);
        failure.causes().forEach(cause -> collect(cause, target, seen));
        failure.suppressed().forEach(suppressed -> collect(suppressed, target, seen));
    }

    private List<Map<String, Object>> diagnostics(JobOutcome outcome) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Diagnostic diagnostic : outcome.diagnostics().stream().limit(MAX_DIAGNOSTICS).toList()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("severity", diagnostic.severity().name());
            value.put("code", diagnostic.code());
            value.put("message", redactor.redact(diagnostic.message()));
            value.put("details", redactor.redactFields(diagnostic.details()));
            if (!diagnostic.artifacts().isEmpty()) {
                value.put("artifacts", diagnostic.artifacts().stream()
                        .map(this::artifact).toList());
            }
            result.add(value);
        }
        return result;
    }

    private Map<String, Object> artifact(DiagnosticArtifact artifact) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", artifact.name());
        value.put("path", artifact.path().toString());
        value.put("mediaType", artifact.mediaType());
        value.put("restricted", artifact.restricted());
        return value;
    }

    private Map<String, Object> logging(LoggingState logging) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("format", logging.format().name().toLowerCase());
        value.put("eventFile", logging.eventFileEnabled());
        logging.eventFile().ifPresent(path -> value.put("eventFilePath", path.toString()));
        value.put("degraded", logging.degraded());
        value.put("fileTruncated", logging.fileTruncated());
        value.put("fileLost", logging.fileLost());
        value.put("recordTruncations", logging.recordTruncations());
        return value;
    }

    private static String timestamp(Instant instant) {
        return instant.toString();
    }

}
