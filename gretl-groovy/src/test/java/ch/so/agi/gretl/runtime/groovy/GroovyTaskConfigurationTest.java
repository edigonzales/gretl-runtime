package ch.so.agi.gretl.runtime.groovy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.JobOutcome;
import ch.so.agi.gretl.runtime.runner.ResultSlot;
import ch.so.agi.gretl.runtime.runner.RunStatus;
import ch.so.agi.gretl.runtime.runner.TaskStatus;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroovyTaskConfigurationTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void resetCounters() {
        TestGroovyOperations.reset();
    }

    @Test
    void defaultsAssignmentConversionAndLastAssignmentWin() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobDefinition definition = harness.loadResource("config.groovy");
        JobOutcome outcome = harness.run(definition, Map.of("text", "x"));

        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals("prefix-x", echoResult(outcome, "echo"));
        assertEquals(1, TestGroovyOperations.BUILDER_CREATIONS.get());
        assertEquals(1, TestGroovyOperations.HANDLER_CALLS.get());
    }

    @Test
    void configurationSupportsExplicitZeroAndOneParameterForms() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome zero = harness.run(harness.loadResource("explicit-zero.groovy"), Map.of());
        assertEquals("zero-args", echoResult(zero, "echo"));

        JobOutcome one = harness.run(harness.loadResource("config-ctx.groovy"),
                Map.of("text", "from-ctx"));
        assertEquals("from-ctx", echoResult(one, "echo"));
    }

    @Test
    void repeatedRunsUseTheStoredOriginalClosureAndFreshScopes() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobDefinition definition = harness.loadResource("config.groovy");

        JobOutcome first = harness.run(definition, Map.of("text", "one"));
        JobOutcome second = harness.run(definition, Map.of("text", "two"));

        assertEquals(RunStatus.SUCCEEDED, first.status());
        assertEquals(RunStatus.SUCCEEDED, second.status());
        assertEquals("prefix-one", echoResult(first, "echo"));
        assertEquals("prefix-two", echoResult(second, "echo"));
        assertEquals(2, TestGroovyOperations.HANDLER_CALLS.get());
    }

    @Test
    void returnEndsOnlyTheConfigurationClosureAndDoesNotSkip() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("return.groovy"), Map.of());

        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals("before", echoResult(outcome, "echo"));
        assertEquals(1, TestGroovyOperations.HANDLER_CALLS.get());
    }

    @Test
    void lexicalDefinitionShadowsAFieldWithoutSetterFallback() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("shadow.groovy"), Map.of());

        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals("default", echoResult(outcome, "echo"));
    }

    private static Object echoResult(JobOutcome outcome, String taskId) {
        ResultSlot.Value value = (ResultSlot.Value) outcome.task(taskId).orElseThrow().slot();
        return new ResultView<>(TestGroovyOperations.echo(),
                (TestGroovyOperations.EchoResult) value.value())
                .getProperty("result");
    }

    @Test
    void unknownFieldFailsWithTaskFieldSourceAndAvailableNames() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("unknownfield.groovy"), Map.of());

        assertEquals(RunStatus.FAILED, outcome.status());
        var failure = outcome.task("echo").orElseThrow().failure().orElseThrow();
        assertEquals(GroovyDslException.class.getName(), failure.type());
        assertTrue(failure.message().contains("unknownField"));
        assertEquals("UNKNOWN_CONFIGURATION_FIELD",
                failure.diagnostic().orElseThrow().code());
        assertEquals(java.util.Optional.of("echo"),
                failure.diagnostic().orElseThrow().taskId());
        assertEquals(java.util.Optional.of("unknownField"),
                failure.diagnostic().orElseThrow().field());
        assertTrue(failure.diagnostic().orElseThrow().available().contains("text"));
        assertTrue(failure.source().isPresent());
    }

    @Test
    void missingRequiredFieldIsRejectedBeforeBuilderCall() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("missing-required.groovy"), Map.of());

        assertEquals(RunStatus.FAILED, outcome.status());
        var failure = outcome.task("required").orElseThrow().failure().orElseThrow();
        assertTrue(failure.message().contains("Pflichtfeld text"));
        assertEquals(0, TestGroovyOperations.BUILDER_CREATIONS.get());
    }

    @Test
    void twoParameterClosureIsRejectedBeforeHandlerCall() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("arity.groovy"), Map.of());

        assertEquals(RunStatus.FAILED, outcome.status());
        var failure = outcome.task("echo").orElseThrow().failure().orElseThrow();
        assertEquals(GroovyDslException.class.getName(), failure.type());
        assertTrue(failure.message().contains("CLOSURE_ARITY")
                || failure.message().contains("erlaubt null oder einen"));
        assertEquals(0, TestGroovyOperations.BUILDER_CREATIONS.get());
    }

    @Test
    void fieldUsedAsMethodGivesAssignmentHint() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("unknownmethod.groovy"), Map.of());

        assertEquals(RunStatus.FAILED, outcome.status());
        var failure = outcome.task("echo").orElseThrow().failure().orElseThrow();
        assertTrue(failure.message().contains("name = wert"));
    }

    @Test
    void reservedNameCannotBeWrittenAsConfigurationField() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("reserved.groovy"), Map.of());

        assertEquals(RunStatus.FAILED, outcome.status());
        var failure = outcome.task("echo").orElseThrow().failure().orElseThrow();
        assertTrue(failure.message().contains("ctx"));
        assertTrue(failure.message().contains("RESERVED")
                || failure.message().contains("Reservierter"));
    }

    @Test
    void whenFalseSkipsWithoutBuilderOrHandler() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("when.groovy"),
                Map.of("text", "hello", "aktiv", "false"));

        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals(TaskStatus.SKIPPED, outcome.task("echo").orElseThrow().status());
        assertEquals("PREDICATE_FALSE", outcome.task("echo").orElseThrow().reason());
        assertEquals(0, TestGroovyOperations.BUILDER_CREATIONS.get());
        assertEquals(0, TestGroovyOperations.HANDLER_CREATIONS.get());
    }

    @Test
    void whenTrueAndOneParameterFacadeEvaluateNormally() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobOutcome outcome = harness.run(harness.loadResource("when-ctx.groovy"),
                Map.of("aktiv", "true"));

        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals("when-ctx", echoResult(outcome, "echo"));
        assertEquals(1, TestGroovyOperations.HANDLER_CALLS.get());
    }

    @Test
    void whenMustReturnABoolean() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobDefinition definition = harness.loadSource("when-bad.groovy", """
                task('echo', Echo, when: { 'yes' }) { text = 'x' }
                defaults 'echo'
                """);
        JobOutcome outcome = harness.run(definition, Map.of());

        assertEquals(RunStatus.FAILED, outcome.status());
        assertTrue(outcome.task("echo").orElseThrow().failure().orElseThrow()
                .message().contains("Boolean"));
    }

    @Test
    void topLevelParameterReadIsRejectedAtLoadTime() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        GroovyDslException exception = assertThrows(GroovyDslException.class,
                () -> harness.loadSource("toplevel.groovy", """
                        parameter('x')
                        task('echo', Echo) { text = 'x' }
                        defaults 'echo'
                        """));
        assertEquals("TOPLEVEL_PARAMETER_READ", exception.code());
        assertFalse(exception.source().line() == 0);
    }

    @Test
    void unknownOptionsAndMissingDescriptionAreLoadErrors() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        GroovyDslException unknownOption = assertThrows(GroovyDslException.class,
                () -> harness.loadSource("unknown-option.groovy", """
                        parameter('x', description: 'X', unknown: true)
                        task('echo', Echo) { text = 'x' }
                        defaults 'echo'
                        """));
        assertEquals("UNKNOWN_OPTION", unknownOption.code());

        GroovyDslException missingDescription = assertThrows(GroovyDslException.class,
                () -> harness.loadSource("missing-description.groovy", """
                        parameter('x', required: true)
                        task('echo', Echo) { text = 'x' }
                        defaults 'echo'
                        """));
        assertEquals("MISSING_OPTION", missingDescription.code());

        GroovyDslException blankDescription = assertThrows(GroovyDslException.class,
                () -> harness.loadSource("blank-description.groovy", """
                        task('echo', Echo, description: ' ') { text = 'x' }
                        defaults 'echo'
                        """));
        assertEquals("INVALID_OPTION", blankDescription.code());
    }
}
