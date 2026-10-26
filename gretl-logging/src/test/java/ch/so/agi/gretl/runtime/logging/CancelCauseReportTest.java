package ch.so.agi.gretl.runtime.logging;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.api.*;
import ch.so.agi.gretl.runtime.runner.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.io.TempDir;

class CancelCauseReportTest {
    @TempDir Path root;
    @ParameterizedTest @EnumSource(CancelReason.class)
    void reasonIsCarriedFromInvocationToTaskRunReportAndTerminalEvents(CancelReason reason) throws Exception {
        UUID runId = UUID.randomUUID();
        var logContext = new LogContext("cause-test", runId, "cause-job", "test", Optional.empty());
        var redactor = new Redactor(new SecretRegistry());
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        var config = LoggingConfig.defaults().withOverrides(LoggingConfig.Format.JSON, null);
        Path events = root.resolve("events.jsonl");
        try (var logging = new LoggingPipeline(config, logContext, redactor, new PrintStream(console), events, Clock.systemUTC())) {
            var op = new OperationType<>("test.cancel", "CancelProbe", "cancel test", String.class, String.class,
                    () -> new ConfigBuilder<String>() { public String build() { return "ok"; } },
                    () -> (request, context) -> { throw new TaskCancelledException(reason); }, List.of(), List.of(), "probe");
            JobDefinitionBuilder b = new JobDefinitionBuilder(root);
            b.register(TaskRegistration.builder("first", Object.class).body(c -> "success").build());
            b.register(TaskRegistration.builder("cancel", Object.class).dependsOn("first").body(c -> c.call(op, "request")).build());
            b.defaults("cancel"); var definition = b.freeze(); var plan = new ExecutionPlanner().plan(definition, List.of());
            var outcome = new SequentialJobRunner().run(definition, plan, RunInput.builder().startCwd(root).jobRoot(root)
                    .workRoot(root.resolve("work")).runId(runId).jobName("cause-job").eventSink(logging).build());
            assertEquals(Optional.of(reason), outcome.cancelReason());
            assertEquals(Optional.empty(), outcome.tasks().getFirst().cancelReason());
            Path report = root.resolve("report.json");
            new RunReportWriter(redactor).write(new RunReportInput(logContext, outcome, definition, plan, root.resolve("job.groovy"),
                    "a".repeat(64), Optional.empty(), "inventory", logging.state(), Instant.now(), Optional.empty()), report);
            Map<?,?> parsed = (Map<?,?>) Json.parseFile(report, 1L << 20);
            Map<?,?> execution = (Map<?,?>) parsed.get("execution");
            assertEquals(reason.name(), execution.get("cancelReason"));
            var tasks = (List<?>) execution.get("tasks");
            assertNull(((Map<?,?>) tasks.getFirst()).get("cancelReason"));
            assertEquals(reason.name(), ((Map<?,?>) tasks.get(1)).get("cancelReason"));
            assertEquals(reason.name(), ((Map<?,?>) ((List<?>) execution.get("invocations")).getFirst()).get("cancelReason"));
            var records = Files.readAllLines(events).stream().map(line -> (Map<?,?>) Json.parse(line)).toList();
            for (String terminal : List.of("gretl.run.finished", "gretl.task.cancelled")) {
                Map<?,?> event = records.stream().filter(r -> terminal.equals(r.get("event_name"))).findFirst().orElseThrow();
                assertEquals(reason.name(), ((Map<?,?>) event.get("attributes")).get("cancel_reason"));
            }
        }
    }
}
