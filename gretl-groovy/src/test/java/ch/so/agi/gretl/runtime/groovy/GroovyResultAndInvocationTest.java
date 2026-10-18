package ch.so.agi.gretl.runtime.groovy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.JobOutcome;
import ch.so.agi.gretl.runtime.runner.ResultSlot;
import ch.so.agi.gretl.runtime.runner.RunStatus;
import ch.so.agi.gretl.runtime.runner.TaskOutcome;
import ch.so.agi.gretl.runtime.runner.TaskStatus;
import ch.so.agi.gretl.runtime.runner.InvocationStatus;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroovyResultAndInvocationTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void resetCounters() {
        TestGroovyOperations.reset();
    }

    @Test
    void typedResultViewMapsOnlyDeclaredOutputFields() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("result.groovy"),
                Map.of("text", "abc"));

        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals("abc", echoOutput(outcome, "producer"));
        assertEquals("abc", echoOutput(outcome, "consumer"));
        assertEquals(2, TestGroovyOperations.HANDLER_CALLS.get());
    }

    @Test
    void unknownOutputFieldNamesTheAvailableFields() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("unknown-output.groovy"), Map.of());

        assertEquals(RunStatus.FAILED, outcome.status());
        var failure = outcome.task("read").orElseThrow().failure().orElseThrow();
        assertEquals(GroovyDslException.class.getName(), failure.type());
        assertTrue(failure.message().contains("missing"));
        assertTrue(failure.message().contains("result"));
    }

    @Test
    void nullableOutputFieldMayBeNullWithoutBecomingAMissingProducer() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("nullable.groovy"), Map.of());

        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals(TaskStatus.SUCCEEDED,
                outcome.task("nullable").orElseThrow().status());
        assertEquals(TaskStatus.SUCCEEDED, outcome.task("read").orElseThrow().status());
        assertEquals(ResultSlot.NoValue.INSTANCE, outcome.task("read").orElseThrow().slot());
    }

    @Test
    void resultOptionalOfSkippedTaskIsEmptyAndDirectResultFails() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome optional = harness.run(
                harness.loadResource("optional-result.groovy", "optional-result-1.groovy"), Map.of());
        assertEquals(RunStatus.SUCCEEDED, optional.status());
        assertEquals(new ResultSlot.Value("absent"),
                optional.task("read").orElseThrow().slot());

        GroovyHarness second = new GroovyHarness(tempDir.resolve("second"));
        JobOutcome direct = second.run(
                second.loadResource("optional-direct.groovy"), Map.of());
        assertEquals(RunStatus.FAILED, direct.status());
        assertEquals(TaskStatus.FAILED, direct.task("read").orElseThrow().status());
    }

    @Test
    void pathParameterAndFileSizeResultWorkAgainstARealFile() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        Path file = tempDir.resolve("data.txt");
        byte[] content = "Äpfel & Öl".getBytes(StandardCharsets.UTF_8);
        Files.write(file, content);
        JobOutcome outcome = harness.run(harness.loadResource("file.groovy"),
                Map.of("file", file.toString()));

        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals(new ResultSlot.Value((long) content.length),
                outcome.task("report").orElseThrow().slot());
        assertEquals(1, TestGroovyOperations.HANDLER_CALLS.get());
    }

    @Test
    void ctxRunCreatesANestedInvocationAndReturnsTheJavaResult() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("ctxrun.groovy"),
                Map.of("text", "hello"));

        assertEquals(RunStatus.SUCCEEDED, outcome.status(),
                () -> "failure=" + outcome.task("run").orElseThrow().failure()
                        + " events=" + harness.events.events());
        assertEquals(new ResultSlot.Value("hello"),
                outcome.task("run").orElseThrow().slot());
        assertEquals(1, outcome.invocations().size());
        var invocation = outcome.invocations().get(0);
        assertEquals("run", invocation.parentTaskId());
        assertEquals("test.echo", invocation.operationId());
        assertEquals(InvocationStatus.SUCCEEDED, invocation.status());
        assertEquals(1, TestGroovyOperations.BUILDER_CREATIONS.get());
        assertEquals(1, TestGroovyOperations.HANDLER_CALLS.get());
    }

    @Test
    void repeatedCtxRunCallsCreateDistinctInvocationsInOrder() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("ctxrun-loop.groovy"), Map.of());

        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals(new ResultSlot.Value("v0,v1"), outcome.task("run").orElseThrow().slot());
        assertEquals(2, outcome.invocations().size());
        assertEquals(2, outcome.invocations().stream().map(var -> var.invocationId())
                .distinct().count());
        assertEquals(2, TestGroovyOperations.HANDLER_CALLS.get());
    }

    @Test
    void cleanupCannotStartANewOperation() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("cleanup-no-run.groovy"), Map.of());

        assertEquals(RunStatus.FAILED, outcome.status());
        assertEquals(TaskStatus.SUCCEEDED, outcome.task("echo").orElseThrow().status());
        assertEquals(1, outcome.invocations().size());
        assertEquals("echo", outcome.invocations().get(0).parentTaskId());
        assertEquals(1, harness.events.count("gretl.cleanup.failed"));
    }

    @Test
    void configurationCanCreateAndUseAWorkPathFile() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("workpath.groovy"), Map.of());

        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        ResultSlot.Value value = (ResultSlot.Value) outcome.task("write").orElseThrow().slot();
        assertEquals(3L, ((TestGroovyOperations.FileSizeResult) value.value()).bytes());
    }

    @Test
    void caughtCtxRunFailureStillAllowsASuccessfulAction() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("ctxrun-fail.groovy"), Map.of());

        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals(new ResultSlot.Value("caught"),
                outcome.task("run").orElseThrow().slot());
        assertEquals(1, outcome.invocations().size());
        assertEquals(InvocationStatus.FAILED, outcome.invocations().get(0).status());
        assertEquals(IllegalStateException.class.getName(),
                outcome.invocations().get(0).failure().orElseThrow().type());
    }

    @Test
    void ctxRunConfigurationFailureHasInvocationIdentityAndConfigurationPhase() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(
                harness.loadResource("ctxrun-config-error.groovy"), Map.of());

        assertEquals(RunStatus.FAILED, outcome.status());
        assertEquals(TaskStatus.FAILED, outcome.task("run").orElseThrow().status());
        assertEquals(0, TestGroovyOperations.BUILDER_CREATIONS.get());
        var invocation = outcome.invocations().get(0);
        assertEquals(InvocationStatus.FAILED, invocation.status());
        assertEquals("configuration", invocation.phase());
        assertTrue(invocation.invocationId().startsWith("i"));
        assertTrue(invocation.failure().orElseThrow().message().contains("unknown"));
    }

    @Test
    void resultWithoutDependsOnFailsEvenWhenTheProducerRanEarlier() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(
                harness.loadResource("result-undeclared.groovy"), Map.of());

        assertEquals(RunStatus.FAILED, outcome.status());
        assertEquals(TaskStatus.SUCCEEDED,
                outcome.task("producer").orElseThrow().status());
        var failure = outcome.task("consumer").orElseThrow().failure().orElseThrow();
        assertTrue(failure.message().contains("dependsOn")
                || failure.message().contains("UNDECLARED"), failure.message());
    }

    @Test
    void unknownResultTaskNameIsADslError() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(
                harness.loadResource("unknown-task-result.groovy"), Map.of());

        assertEquals(RunStatus.FAILED, outcome.status());
        var failure = outcome.task("read").orElseThrow().failure().orElseThrow();
        assertTrue(failure.message().contains("missing-task"));
        assertTrue(failure.message().contains("Unbekannter Taskname"));
    }

    @Test
    void voidOperationSucceedsWithoutAValue() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("void-task.groovy"), Map.of());

        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals(TaskStatus.SUCCEEDED, outcome.task("void").orElseThrow().status());
        assertEquals(ResultSlot.NoValue.INSTANCE, outcome.task("void").orElseThrow().slot());
    }

    @Test
    void actionResultIsRawAndActionUnknownFieldHasSource() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome raw = harness.run(harness.loadResource("action-result.groovy"), Map.of());
        assertEquals(RunStatus.SUCCEEDED, raw.status());
        assertEquals(new ResultSlot.Value("raw-action-value"),
                raw.task("read").orElseThrow().slot());

        GroovyHarness second = new GroovyHarness(tempDir.resolve("unknown-action"));
        JobOutcome unknown = second.run(
                second.loadResource("action-unknown-field.groovy"), Map.of());
        assertEquals(RunStatus.FAILED, unknown.status());
        var failure = unknown.task("read").orElseThrow().failure().orElseThrow();
        assertTrue(failure.message().contains("unknownActionField"));
        assertTrue(failure.message().contains("action"));
        assertTrue(failure.message().matches(".*action-unknown-field\\.groovy:\\d+.*"),
                failure.message());
    }

    @Test
    void directForeignSystemOutIsNotCapturedAsProductMessage() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobDefinition definition = harness.loadResource("foreign-output.groovy");
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        JobOutcome outcome;
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            outcome = harness.run(definition, Map.of());
        } finally {
            System.setOut(original);
        }

        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertTrue(captured.toString(StandardCharsets.UTF_8).contains("foreign-out"));
        assertTrue(harness.messages.messages().contains("product-out"));
        assertFalse(harness.events.events().stream()
                .anyMatch(event -> event.message().contains("foreign-out")));
    }

    @Test
    void resultViewRejectsUnknownFieldsAndWrites() {
        var operation = TestGroovyOperations.echo();
        ResultView<TestGroovyOperations.EchoResult> view = new ResultView<>(
                operation, new TestGroovyOperations.EchoResult("value"));
        assertEquals("value", view.getProperty("result"));

        GroovyDslException unknown = assertThrows(GroovyDslException.class,
                () -> view.getProperty("missing"));
        assertEquals("UNKNOWN_OUTPUT_FIELD", unknown.code());
        assertTrue(unknown.available().contains("result"));

        GroovyDslException write = assertThrows(GroovyDslException.class,
                () -> view.setProperty("result", "new"));
        assertEquals("READ_ONLY_RESULT", write.code());
    }

    @Test
    void nonNullableNullOutputFieldIsAnErrorButNullableIsNot() {
        var echo = TestGroovyOperations.echo();
        ResultView<TestGroovyOperations.EchoResult> nonNullable = new ResultView<>(
                echo, new TestGroovyOperations.EchoResult(null));
        GroovyDslException nullError = assertThrows(GroovyDslException.class,
                () -> nonNullable.getProperty("result"));
        assertEquals("NULL_OUTPUT_FIELD", nullError.code());

        ResultView<TestGroovyOperations.NullableResult> nullable = new ResultView<>(
                TestGroovyOperations.nullable(), new TestGroovyOperations.NullableResult(null));
        assertNull(nullable.getProperty("maybe"));
    }

    private static Object echoOutput(JobOutcome outcome, String taskId) {
        TaskOutcome task = outcome.task(taskId).orElseThrow();
        ResultSlot.Value value = (ResultSlot.Value) task.slot();
        return new ResultView<>(TestGroovyOperations.echo(),
                (TestGroovyOperations.EchoResult) value.value()).getProperty("result");
    }
}
