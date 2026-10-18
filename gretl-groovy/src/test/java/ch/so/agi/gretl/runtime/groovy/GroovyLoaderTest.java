package ch.so.agi.gretl.runtime.groovy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.runner.DefinitionException;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.JobOutcome;
import ch.so.agi.gretl.runtime.runner.ParameterValueType;
import ch.so.agi.gretl.runtime.runner.ResultSlot;
import ch.so.agi.gretl.runtime.runner.RunStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroovyLoaderTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void resetCounters() {
        TestGroovyOperations.reset();
    }

    @Test
    void compilerSettingsPinJava25AndDisableGrape() {
        var configuration = CompilerSettings.create();
        assertEquals("25", configuration.getTargetBytecode());
        assertEquals(GretlScript.class.getName(), configuration.getScriptBaseClass());
        assertTrue(configuration.getDisabledGlobalASTTransformations()
                .contains(CompilerSettings.GRAPE_TRANSFORMATION));
    }

    @Test
    void loadingDoesNotEvaluateConfigurationDefaultsOrHandlers() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobDefinition definition = harness.loadResource("config.groovy");

        assertEquals(0, TestGroovyOperations.BUILDER_CREATIONS.get());
        assertEquals(0, TestGroovyOperations.HANDLER_CREATIONS.get());
        assertEquals(0, TestGroovyOperations.HANDLER_CALLS.get());
        var source = definition.task("echo").orElseThrow().source();
        assertEquals("config.groovy", source.file());
        assertTrue(source.line() > 0);
    }

    @Test
    void twoLoadsHaveFreshDefinitionsBindingsAndResultScopes() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobDefinition first = harness.loadResource("config.groovy", "config-1.groovy");
        JobDefinition second = harness.loadResource("config.groovy", "config-2.groovy");

        assertNotEquals(first.definitionId(), second.definitionId());
        assertNotEquals(first.task("echo").orElseThrow().ref(),
                second.task("echo").orElseThrow().ref());

        JobOutcome firstOutcome = harness.run(first, Map.of("text", "one"));
        JobOutcome secondOutcome = harness.run(second, Map.of("text", "two"));

        assertEquals(RunStatus.SUCCEEDED, firstOutcome.status());
        assertEquals(RunStatus.SUCCEEDED, secondOutcome.status());
        assertEquals("prefix-one", echoOutput(firstOutcome, "echo"));
        assertEquals("prefix-two", echoOutput(secondOutcome, "echo"));
        assertEquals(2, TestGroovyOperations.HANDLER_CALLS.get());
    }

    @Test
    void topLevelAndActionPrintlnAreUserMessagesNotRunStatus() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobDefinition definition = harness.loadResource("println.groovy");
        assertTrue(harness.messages.messages().contains("load-message"));

        JobOutcome outcome = harness.run(definition, Map.of());
        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertTrue(harness.messages.messages().contains("action-message"));
        assertFalse(harness.events.events().stream()
                .anyMatch(event -> "load-message".equals(event.message())
                        || "action-message".equals(event.message())));
    }

    @Test
    void taskRefsAndStringListsCanBeUsedForDependencies() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobDefinition definition = harness.loadSource("refs.groovy", """
                def first = task('first', Echo) { text = 'first' }
                task('second', Echo, dependsOn: first) { text = 'second' }
                task('third', Echo, dependsOn: ['first', 'second']) { text = 'third' }
                defaults 'third'
                """);

        assertEquals(List.of("first"), definition.task("second").orElseThrow()
                .dependsOn().stream().map(ref -> ref.id()).toList());
        JobOutcome outcome = harness.run(definition, Map.of());
        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals(3, TestGroovyOperations.HANDLER_CALLS.get());
    }

    @Test
    void parameterDeclarationsCarryTypesDefaultsAndRequiredFlags() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobDefinition definition = harness.loadSource("parameters.groovy", """
                parameter('text', description: 'Text', type: 'text', defaultValue: 'a')
                parameter('flag', description: 'Flag', type: 'boolean', defaultValue: false)
                parameter('count', description: 'Count', type: 'integer', defaultValue: 10)
                parameter('file', description: 'File', type: 'path', required: true)
                task('echo', Echo) { text = parameter('text') }
                defaults 'echo'
                """);

        assertEquals(4, definition.parameters().size());
        assertEquals(ParameterValueType.TEXT, definition.parameters().get(0).type());
        assertEquals(ParameterValueType.BOOLEAN, definition.parameters().get(1).type());
        assertEquals(ParameterValueType.INTEGER, definition.parameters().get(2).type());
        assertEquals(ParameterValueType.PATH, definition.parameters().get(3).type());
        assertEquals("a", definition.parameters().get(0).defaultValue().orElseThrow());
        assertEquals(false, definition.parameters().get(1).defaultValue().orElseThrow());
        assertEquals(10, definition.parameters().get(2).defaultValue().orElseThrow());
        assertTrue(definition.parameters().get(3).required());
    }

    @Test
    void unknownSymbolAndSyntaxErrorsAreLoadErrorsWithSource() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        GroovyDslException unknown = assertThrows(GroovyDslException.class,
                () -> harness.loadSource("unknown-symbol.groovy", """
                        task('x', Missing) { }
                        defaults 'x'
                        """));
        assertEquals("JOB_LOAD_FAILED", unknown.code());
        assertTrue(unknown.source().file().contains("unknown-symbol.groovy"));

        GroovyDslException syntax = assertThrows(GroovyDslException.class,
                () -> harness.loadSource("syntax.groovy", "task('x', Echo) {"));
        assertEquals("JOB_LOAD_FAILED", syntax.code());
    }

    @Test
    void duplicateTaskAndUnknownDefaultArePreservedDefinitionErrors() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        DefinitionException duplicate = assertThrows(DefinitionException.class,
                () -> harness.loadSource("duplicate.groovy", """
                        task('x', Echo) { text = 'a' }
                        task('x', Echo) { text = 'b' }
                        defaults 'x'
                        """));
        assertEquals("DUPLICATE_TASK_ID", duplicate.code());

        DefinitionException unknownDefault = assertThrows(DefinitionException.class,
                () -> harness.loadSource("unknown-default.groovy", """
                        task('x', Echo) { text = 'a' }
                        defaults 'missing'
                        """));
        assertEquals("UNKNOWN_DEFAULT_TARGET", unknownDefault.code());
    }

    @Test
    void defaultsAcceptTaskRefsAndAfterOnlyOrdersSelectedTasks() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobDefinition definition = harness.loadSource("defaults-ref.groovy", """
                def first = task('first', Echo) { text = 'first' }
                task('second', Echo, after: 'first') { text = 'second' }
                defaults first, 'second'
                """);
        JobOutcome outcome = harness.run(definition, Map.of());
        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals(2, TestGroovyOperations.HANDLER_CALLS.get());
        assertEquals(List.of("first"), definition.task("second").orElseThrow()
                .after().stream().map(ref -> ref.id()).toList());
    }

    @Test
    void timeoutOptionIsAPositiveDurationAndInvalidValuesAreRejected() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobDefinition definition = harness.loadSource("timeout.groovy", """
                task('x', Echo, timeout: 'PT2S') { text = 'x' }
                defaults 'x'
                """);
        assertEquals(java.time.Duration.ofSeconds(2),
                definition.task("x").orElseThrow().timeout());

        GroovyDslException invalid = assertThrows(GroovyDslException.class,
                () -> harness.loadSource("bad-timeout.groovy", """
                        task('x', Echo, timeout: 'soon') { text = 'x' }
                        defaults 'x'
                        """));
        assertEquals("INVALID_TIMEOUT", invalid.code());
    }

    @Test
    void parameterDefaultsAreResolvedWhenNoCliValueIsGiven() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobDefinition definition = harness.loadSource("param-default.groovy", """
                parameter('text', description: 'Text', defaultValue: 'from-default')
                task('x', Echo) { text = parameter('text') }
                defaults 'x'
                """);
        JobOutcome outcome = harness.run(definition, Map.of());
        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals("from-default", echoOutput(outcome, "x"));
    }

    @Test
    void duplicateParameterNamesAreDefinitionErrors() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        var duplicate = assertThrows(
                ch.so.agi.gretl.runtime.runner.ParameterException.class,
                () -> harness.loadSource("duplicate-parameter.groovy", """
                        parameter('x', description: 'first')
                        parameter('x', description: 'second')
                        task('echo', Echo) { text = 'x' }
                        defaults 'echo'
                        """));
        assertEquals("DUPLICATE_PARAMETER", duplicate.code());
    }

    @Test
    void cleanupClosureIsRegisteredAndRunsAfterTheTask() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        JobDefinition definition = harness.loadSource("cleanup.groovy", """
                task('x', Echo) { text = 'value' }
                cleanup('one') { println 'cleanup-one' }
                cleanup('two') { println 'cleanup-two' }
                defaults 'x'
                """);
        assertEquals(2, definition.cleanups().size());
        JobOutcome outcome = harness.run(definition, Map.of());
        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals(List.of("cleanup-two", "cleanup-one").stream()
                .filter(message -> harness.messages.messages().contains(message)).count(), 2);
    }

    private static Object echoOutput(JobOutcome outcome, String taskId) {
        ResultSlot.Value value = (ResultSlot.Value) outcome.task(taskId).orElseThrow().slot();
        return new ResultView<>(TestGroovyOperations.echo(),
                (TestGroovyOperations.EchoResult) value.value()).getProperty("result");
    }
}
