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
import java.nio.file.Files;
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
        assertEquals(harness.jobDir.resolve("config.groovy").toString(), source.file());
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
    void emptyAndMissingScriptsHaveDistinctOutcomes() throws Exception {
        var harness = new GroovyHarness(tempDir);
        var definition = harness.loadSource("empty.groovy", "// empty");
        assertTrue(definition.tasks().isEmpty());
        assertTrue(definition.defaultTargets().isEmpty());

        Path missing = harness.jobDir.resolve("missing.groovy");
        var failure = assertThrows(GroovyDslException.class, () -> harness.load(missing));
        assertEquals("JOB_NOT_FOUND", failure.code());
        assertEquals(missing.toString(), failure.source().file());
    }

    @Test
    void singleEntryWithoutIncludesUsesTheSameSymlinkBoundary() throws Exception {
        var harness = new GroovyHarness(tempDir);
        Files.writeString(harness.jobDir.resolve("job.groovy"), "action('run') {}");
        Path entryLink = Files.createSymbolicLink(harness.jobDir.resolve("entry.groovy"), Path.of("job.groovy"));
        Path rootLink = Files.createSymbolicLink(tempDir.resolve("alias"), harness.jobDir);
        for (Path entry : List.of(entryLink, rootLink.resolve("job.groovy"))) {
            var failure = assertThrows(GroovyDslException.class, () -> harness.load(entry));
            assertEquals("JOB_SOURCE_UNSAFE", failure.code());
            assertEquals(entry.toString(), failure.source().file());
        }
    }

    @Test
    void definitionFinalizationBelongsToTheLoader() throws Exception {
        var harness = new GroovyHarness(tempDir);
        var failure = assertThrows(GroovyDslException.class,
                () -> harness.loadSource("freeze.groovy", "action('run') {}\nfreeze()"));
        assertEquals("JOB_FREEZE_CONTROLLED", failure.code());
        assertEquals(2, failure.source().line());
    }

    @Test
    void ordinaryApplyAndImportedTypesWorkAndEachLoadOwnsItsHelperClass() throws Exception {
        var harness = new GroovyHarness(tempDir);
        String source = """
                import java.time.LocalDate as Day

                class Numbering {
                    static int count = 40
                    static int next() { ++count }
                }

                String apply(String name) { Day.of(2026, 1, 2).toString() + '-' + name }
                def label = apply('job')
                def first = Numbering.next()
                action('run') { label + ':' + first + ':' + Numbering.next() }
                defaults 'run'
                """;
        var first = harness.loadSource("numbering.groovy", source);
        var second = harness.load(harness.jobDir.resolve("numbering.groovy"));
        for (var definition : List.of(first, second)) {
            var outcome = harness.run(definition, Map.of());
            assertEquals(RunStatus.SUCCEEDED, outcome.status());
            assertEquals("2026-01-02-job:41:42",
                    ((ResultSlot.Value) outcome.task("run").orElseThrow().slot()).value());
        }
    }

    @Test
    void compilerDoesNotResolveGroovySourceOutsideTheSnapshot() throws Exception {
        var harness = new GroovyHarness(tempDir);
        Files.writeString(harness.jobDir.resolve("Uncaptured.groovy"), "class Uncaptured {}");
        var failure = assertThrows(GroovyDslException.class, () -> harness.loadSource("job.groovy",
                "action('run') { new Uncaptured() }"));
        assertEquals("JOB_LOAD_FAILED", failure.code());
        assertTrue(failure.getMessage().contains("Uncaptured"));
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
                () -> harness.loadSource("syntax.groovy", "// heading\ndef broken = )"));
        assertEquals("JOB_LOAD_FAILED", syntax.code());
        assertEquals(harness.jobDir.resolve("syntax.groovy").toString(), syntax.source().file());
        assertEquals(2, syntax.source().line());
        assertEquals(14, syntax.source().column());
    }

    @Test
    void duplicateTaskAndUnknownDefaultKeepTheirDiagnosticCodes() throws Exception {
        GroovyHarness harness = new GroovyHarness(tempDir);
        GroovyDslException duplicate = assertThrows(GroovyDslException.class,
                () -> harness.loadSource("duplicate.groovy", """
                        task('x', Echo) { text = 'a' }
                        task('x', Echo) { text = 'b' }
                        defaults 'x'
                        """));
        assertEquals("DUPLICATE_TASK_ID", duplicate.code());
        assertEquals(2, duplicate.source().line());

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
    void taskRefArgumentsAreEvaluatedBeforeRegistrationOrDefaults() throws Exception {
        var harness = new GroovyHarness(tempDir);
        var defaults = harness.loadSource("default-action.groovy", """
                def first = action('first') {}
                defaults first
                """);
        assertEquals(List.of("first"), defaults.defaultTargets());

        for (String source : List.of(
                "action('consumer', dependsOn: producer) {}\ndef producer = action('producer') {}\ndefaults 'consumer'",
                "def producer = action('producer', dependsOn: producer) {}",
                "action([dependsOn: [producer], dependsOn: 'producer'], 'consumer') {}\ndef producer = action('producer') {}",
                "action([description: [nested:[producer]], description: 'active'], 'consumer') {}\ndef producer = action('producer') {}",
                "defaults producer\ndef producer = action('producer') {}")) {
            var failure = assertThrows(GroovyDslException.class,
                    () -> harness.loadSource("early.groovy", source));
            assertEquals("JOB_LOAD_FAILED", failure.code());
            assertTrue(failure.getMessage().contains("producer"), failure.getMessage());
        }

        for (String source : List.of(
                "action('consumer', dependsOn: 'producer') {}\ndef producer = action('producer') {}",
                "def producer = action('producer') {}\naction('consumer', dependsOn: producer) {}")) {
            var definition = harness.loadSource("valid.groovy", source + "\ndefaults 'consumer'");
            assertEquals(List.of("consumer"), definition.defaultTargets());
            assertEquals(List.of("producer"), definition.task("consumer").orElseThrow()
                    .dependsOn().stream().map(ref -> ref.id()).toList());
        }
    }

    @Test
    void coercingATaskRefToAStringDoesNotPreserveItsDefaultTarget() throws Exception {
        var harness = new GroovyHarness(tempDir);
        var failure = assertThrows(DefinitionException.class, () -> harness.loadSource("coerced.groovy", """
                String first = action('first') {}
                defaults first
                """));
        assertEquals("UNKNOWN_DEFAULT_TARGET", failure.code());
        var typed = harness.loadSource("typed.groovy", """
                import ch.so.agi.gretl.runtime.api.TaskRef as Ref
                Ref first = action('first') {}
                defaults first
                """);
        assertEquals(List.of("first"), typed.defaultTargets());
    }

    @Test
    void projectEntryDefaultsStillEvaluateArgumentsAlthoughSettingsSelectTheTargets() throws Exception {
        var harness = new GroovyHarness(tempDir);
        Files.writeString(harness.jobDir.resolve("settings.gretl"), "include 'sub'\ndefaults ':sub:run'");
        Path job = Files.createDirectories(harness.jobDir.resolve("sub")).resolve("job.groovy");
        Files.writeString(job, "defaults ref\ndef ref = action('run') {}");
        var failure = assertThrows(GroovyDslException.class, harness::loadProject);
        assertEquals("JOB_LOAD_FAILED", failure.code());
        assertTrue(failure.getMessage().contains("ref"), failure.getMessage());

        Files.writeString(job, "def ref = action('run') {}\ndefaults ref");
        assertEquals(List.of(":sub:run"), harness.loadProject().defaultTargets());
    }

    @Test
    void unboundProjectDefaultArgumentFailsDuringLoad() throws Exception {
        var harness = new GroovyHarness(tempDir);
        Files.writeString(harness.jobDir.resolve("settings.gretl"), "defaults ':run'");
        Path job = harness.jobDir.resolve("job.groovy");
        Files.writeString(job, "action('run') {}\ndefaults missing");
        var failure = assertThrows(GroovyDslException.class, harness::loadProject);
        assertEquals("JOB_LOAD_FAILED", failure.code());
        assertTrue(failure.getMessage().contains("No such property: missing"), failure.getMessage());
        assertEquals(2, failure.source().line());

        Files.writeString(job, "action('run') {}\ndef target = 'run'\ndefaults target");
        assertEquals(List.of(":run"), harness.loadProject().defaultTargets());
    }

    @Test
    void fragmentTaskNamesCrossApplicationsWhileLocalTaskRefsDoNot() throws Exception {
        var harness = new GroovyHarness(tempDir);
        Path fragment = harness.jobDir.resolve("fragment.groovy");
        Files.writeString(harness.jobDir.resolve("nested.groovy"), "action('nested') {}");
        Files.writeString(fragment, """
                def ref = action('local', dependsOn: 'nested') {}
                apply from: 'nested.groovy'
                action('use', dependsOn: ref) {}
                """);
        String entry = "def ref = action('entry') {}\napply from: 'fragment.groovy'\ndefaults 'use'";
        var definition = harness.loadSource("job.groovy", entry);
        assertEquals(List.of("entry", "local", "nested", "use"), List.copyOf(definition.taskIds()));
        assertEquals(List.of("local"), definition.task("use").orElseThrow()
                .dependsOn().stream().map(ref -> ref.id()).toList());

        Files.writeString(fragment, "action('use', dependsOn: ref) {}");
        var failure = assertThrows(GroovyDslException.class, () -> harness.loadSource("job.groovy", entry));
        assertEquals("JOB_LOAD_FAILED", failure.code());
        assertTrue(failure.getMessage().contains("ref"), failure.getMessage());
    }

    @Test
    void resultCallsInDeferredClosuresCaptureOnlyAlreadyDeclaredLocals() throws Exception {
        var harness = new GroovyHarness(tempDir);
        var captured = harness.loadSource("captured.groovy", """
                def producer = action('producer') { 'value' }
                action('consumer', dependsOn: producer, when: { resultOptional(producer).present }) { result(producer) }
                defaults 'consumer'
                """);
        var succeeded = harness.run(captured, Map.of());
        assertEquals(RunStatus.SUCCEEDED, succeeded.status());
        assertEquals("value", ((ResultSlot.Value) succeeded.task("consumer").orElseThrow().slot()).value());

        for (String consumer : List.of(
                "action('consumer', dependsOn: 'producer') { result(producer) }",
                "action('consumer', dependsOn: 'producer', when: { resultOptional(producer).present }) {}")) {
            var definition = harness.loadSource("uncaptured.groovy", consumer
                    + "\ndef producer = action('producer') { 'value' }\ndefaults 'consumer'");
            var failed = harness.run(definition, Map.of());
            assertEquals(RunStatus.FAILED, failed.status(), consumer);
            assertEquals("UNKNOWN_PROPERTY", failed.task("consumer").orElseThrow()
                    .failure().orElseThrow().diagnostic().orElseThrow().code());
        }
    }

    @Test
    void deferredClosuresShareTheCurrentValueOfACapturedVariable() throws Exception {
        var harness = new GroovyHarness(tempDir);
        var definition = harness.loadSource("reassigned.groovy", """
                def producer = action('producer') {}
                action('change', dependsOn: producer) { producer = 'elsewhere' }
                action('consume', dependsOn: [producer, 'change']) { result(producer) }
                defaults 'consume'
                """);
        assertEquals(List.of("producer", "change"), definition.task("consume").orElseThrow()
                .dependsOn().stream().map(ref -> ref.id()).toList());
        var outcome = harness.run(definition, Map.of());
        assertEquals(RunStatus.FAILED, outcome.status());
        assertEquals("UNKNOWN_TASK_NAME", outcome.task("consume").orElseThrow()
                .failure().orElseThrow().diagnostic().orElseThrow().code());
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
                GroovyDslException.class,
                () -> harness.loadSource("duplicate-parameter.groovy", """
                        parameter('x', description: 'first')
                        parameter('x', description: 'second')
                        task('echo', Echo) { text = 'x' }
                        defaults 'echo'
                        """));
        assertEquals("DUPLICATE_PARAMETER", duplicate.code());
        assertEquals("x", duplicate.fieldName());
        assertEquals(2, duplicate.source().line());
        assertFalse(duplicate.getMessage().contains(duplicate.source().file()));
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
