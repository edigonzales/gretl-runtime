package ch.so.agi.gretl.runtime.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.runner.ExecutionPlan;
import ch.so.agi.gretl.runtime.runner.ExecutionPlanner;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.JobDefinitionBuilder;
import ch.so.agi.gretl.runtime.runner.JobOutcome;
import ch.so.agi.gretl.runtime.runner.RunEventSink;
import ch.so.agi.gretl.runtime.runner.RunInput;
import ch.so.agi.gretl.runtime.runner.SequentialJobRunner;
import ch.so.agi.gretl.runtime.runner.TaskRegistration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunReportWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesSchemaOneReportWithoutResultValuesOrSecrets() throws Exception {
        JobDefinitionBuilder builder = new JobDefinitionBuilder(tempDir);
        builder.register(TaskRegistration.builder("ok", Object.class)
                .body(context -> "do-not-serialize-this-value")
                .build());
        builder.defaults("ok");
        JobDefinition definition = builder.freeze();
        ExecutionPlan plan = new ExecutionPlanner().plan(definition, List.of());
        RunInput input = RunInput.builder()
                .parameters(new ch.so.agi.gretl.runtime.runner.ResolvedParameters(
                        Map.of(), Map.of(), List.of(), java.util.Set.of()))
                .jobRoot(tempDir).startCwd(tempDir).workRoot(tempDir.resolve("work"))
                .jobName("report-job").runId(UUID.randomUUID())
                .clock(Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneOffset.UTC))
                .monotonicClock(() -> 0L)
                .eventSink(RunEventSink.NO_OP)
                .build();
        JobOutcome outcome = new SequentialJobRunner().run(definition, plan, input);

        LogContext context = new LogContext("command-1", outcome.runId(), "report-job",
                "0.1.0-M6", Optional.of("test"));
        Path script = tempDir.resolve("job.groovy");
        Files.writeString(script, "// script", StandardCharsets.UTF_8);
        RunReportInput reportInput = new RunReportInput(
                context, outcome, definition, plan, script,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                Optional.of("revision-1"), "inventory-hash",
                new LoggingState(LoggingConfig.Format.TEXT, true, Optional.empty(),
                        false, false, false, 0),
                Instant.parse("2026-10-18T10:00:00Z"), Optional.of("test"));
        Path report = tempDir.resolve("reports/run.json");
        new RunReportWriter(new Redactor(new ch.so.agi.gretl.runtime.runner.SecretRegistry()))
                .write(reportInput, report);

        String json = Files.readString(report, StandardCharsets.UTF_8);
        Map<?, ?> parsed = (Map<?, ?>) Json.parse(json);
        assertEquals(1L, parsed.get("schemaVersion"));
        assertEquals("command-1", ((Map<?, ?>) parsed.get("identity")).get("commandId"));
        assertEquals("job.groovy", ((Map<?, ?>) parsed.get("source")).get("file"));
        assertEquals("provided", ((Map<?, ?>) parsed.get("source")).get("revision_source"));
        Map<?, ?> execution = (Map<?, ?>) parsed.get("execution");
        assertEquals("SUCCEEDED", execution.get("status"));
        assertTrue((Boolean) ((Map<?, ?>) ((List<?>) execution.get("tasks")).get(0))
                .get("hasValue"));
        assertEquals("none", ((Map<?, ?>) parsed.get("telemetry")).get("mode"));
        assertFalse(json.contains("do-not-serialize-this-value"));
        assertFalse(json.contains("password"));
    }
}
