package ch.so.agi.gretl.runtime.groovy;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.api.CancelReason;
import ch.so.agi.gretl.runtime.groovy.project.GretlProjectParser;
import ch.so.agi.gretl.runtime.runner.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectScopeTest {
    @TempDir Path temp;

    @Test
    void prj103SelectionUsesOnlyDependsOnClosureAndAfterOnlyOrdersSelectedNodes() throws Exception {
        var h = new GroovyHarness(temp);
        settings(h, "include 'downstream', 'upstream', 'unrelated'\ndefaults ':downstream:run'");
        script(h, "downstream", "action('run', dependsOn: ':upstream:run', after: ':unrelated:run') { 'downstream' }");
        script(h, "upstream", "action('run') { 'upstream' }");
        script(h, "unrelated", "action('run') { throw new AssertionError('not selected') }");
        var definition = load(h);
        var plan = new ExecutionPlanner().plan(definition, List.of(":downstream:run"));
        assertEquals(List.of(":upstream:run", ":downstream:run"), plan.selectedTasks().stream().map(TaskDefinition::id).toList());
        var outcome = h.run(definition, Map.of()); assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals(List.of(":upstream:run", ":downstream:run"), outcome.tasks().stream().map(TaskOutcome::taskId).toList());
        assertEquals(List.of(":upstream:run"), new ExecutionPlanner().plan(definition, List.of(":upstream:run")).selectedTasks().stream().map(TaskDefinition::id).toList());
    }

    @Test
    void prj104And105AfterNeverPermitsResultOrOptionalResultAccess() throws Exception {
        for (String access : List.of("result", "resultOptional")) {
            var h = new GroovyHarness(temp.resolve(access));
            settings(h, "include 'a', 'b'\ndefaults ':a:run', ':b:run'");
            script(h, "a", "action('run') { 'a' }");
            script(h, "b", "action('run', after: ':a:run') { " + access + "(':a:run') }");
            var outcome = h.run(load(h), Map.of()); assertEquals(RunStatus.FAILED, outcome.status());
            var failure = outcome.tasks().getLast().failure().orElseThrow();
            assertTrue(hasCode(failure, "UNDECLARED_RESULT_DEPENDENCY"), failure.toString());
            assertEquals(":b:run", outcome.tasks().getLast().taskId());
        }
    }

    @Test
    void prj106UnselectedDataCyclesUnknownLinksAndDuplicateIdsAreDefinitionErrors() throws Exception {
        var h = new GroovyHarness(temp);
        settings(h, "include 'a', 'b'\ndefaults ':a:ok'");
        script(h, "a", "action('ok') { 'ok' }\naction('cycle', dependsOn: ':b:cycle') { 'a' }");
        script(h, "b", "action('cycle', dependsOn: ':a:cycle') { 'b' }");
        var definition = load(h);
        assertEquals("DEPENDENCY_CYCLE", assertThrows(DefinitionException.class,
                () -> new ExecutionPlanner().plan(definition, List.of(":a:ok"))).code());
        script(h, "b", "action('cycle', dependsOn: ':missing:run') { 'b' }");
        var unknown = assertThrows(GroovyDslException.class, () -> load(h)); assertEquals("UNKNOWN_TASK_REFERENCE", unknown.code());
        assertEquals(h.jobDir.resolve("b/job.groovy").toRealPath().toString(), unknown.source().file());
        script(h, "b", "action('cycle') { 'b' }\naction('cycle') { 'duplicate' }");
        var duplicate = assertThrows(GroovyDslException.class, () -> load(h)); assertEquals("DUPLICATE_TASK_ID", duplicate.code());
        assertTrue(duplicate.getMessage().contains("job.groovy:1")); assertTrue(duplicate.getMessage().contains("job.groovy:2"));
    }

    @Test
    void prj107FileScopeSurvivesWhenConfigurationNestedRunActionAndCleanupContext() throws Exception {
        var h = new GroovyHarness(temp);
        settings(h, "include 'a', 'b:c'\ndefaults ':a:run', ':b:c:run'");
        for (String directory : List.of("a", "b/c")) {
            String expected = directory.equals("a") ? "AAA" : "BBBBB";
            Path input = h.jobDir.resolve(directory).resolve("sql/update.sql"); Files.createDirectories(input.getParent()); Files.writeString(input, expected);
            script(h, directory, """
                    parameter('local', description: 'Local', defaultValue: '%s')
                    task('size', FileSize, when: { assert java.nio.file.Files.readString(file('sql/update.sql')) == '%s'; true }) {
                        input = ctx.file('sql/update.sql')
                    }
                    action('run', dependsOn: 'size') { ctx ->
                        assert java.nio.file.Files.readString(ctx.file('sql/update.sql')) == parameter('local')
                        assert ctx.path('input.txt') == path('input.txt')
                        assert ctx.run(FileSize) { input = file('sql/update.sql') }.bytes == %d
                        result('size').bytes
                    }
                    cleanup('release') { ch.so.agi.gretl.runtime.api.CleanupContext c ->
                        assert java.nio.file.Files.readString(c.file('sql/update.sql')) == java.nio.file.Files.readString(file('sql/update.sql'))
                        assert ctx.parameter('local') == '%s'
                        java.nio.file.Files.writeString(path('cleanup-%s.txt'), java.nio.file.Files.readString(c.file('sql/update.sql')))
                    }
                    """.formatted(expected, expected, expected.length(), expected, expected));
        }
        var definition = load(h); var outcome = h.run(definition, Map.of());
        assertEquals(RunStatus.SUCCEEDED, outcome.status(), outcome.toString());
        assertEquals(3L, GroovyProjectLoaderTest.value(outcome, ":a:run")); assertEquals(5L, GroovyProjectLoaderTest.value(outcome, ":b:c:run"));
        assertEquals("AAA", Files.readString(temp.resolve("cleanup-AAA.txt"))); assertEquals("BBBBB", Files.readString(temp.resolve("cleanup-BBBBB.txt")));
        assertEquals(List.of(":a:release", ":b:c:release"), definition.cleanups().stream().map(CleanupDefinition::name).toList());
        assertEquals(4, outcome.tasks().stream().flatMap(t -> t.invocations().stream()).count());
    }

    @Test
    void prj109And113OneCleanupLifecycleRetainsFailureNoDataAndCancellationSemantics() throws Exception {
        for (String mode : List.of("success", "failure", "no-data", "cancel")) {
            var h = new GroovyHarness(temp.resolve(mode));
            settings(h, "include 'a', 'b'\ndefaults ':a:run', ':b:run'");
            String body = switch (mode) { case "failure" -> "throw new IllegalStateException('business-failure')"; case "no-data" -> "ctx.finishJob('empty')"; default -> "'a'"; };
            script(h, "a", "action('run') { " + body + " }\ncleanup('release') { java.nio.file.Files.writeString(path('cleanup.txt'), 'a', java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND) }");
            script(h, "b", "action('run') { 'b' }\ncleanup('release') { java.nio.file.Files.writeString(path('cleanup.txt'), 'b', java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND) }");
            var definition = load(h); JobOutcome outcome;
            if (mode.equals("cancel")) { var control = new RunControl(SystemMonotonicClock.INSTANCE, Optional.empty()); control.requestCancel(CancelReason.REQUESTED); outcome = h.run(definition, Map.of(), control); }
            else outcome = h.run(definition, Map.of());
            assertEquals(switch(mode) {case "failure" -> RunStatus.FAILED; case "no-data" -> RunStatus.NO_DATA; case "cancel" -> RunStatus.CANCELLED; default -> RunStatus.SUCCEEDED;}, outcome.status(), outcome.toString());
            assertEquals("ba", Files.readString(h.tempDir.resolve("cleanup.txt")));
            assertEquals(1, h.events.count("gretl.run.started")); assertEquals(1, h.events.count("gretl.run.finished"));
            if (!mode.equals("success")) assertEquals(TaskStatus.NOT_RUN, outcome.tasks().getLast().status());
        }
    }

    @Test
    void prj111SingleFileRetainsUnqualifiedNamesInsideProjectDirectory() throws Exception {
        var h = new GroovyHarness(temp);
        settings(h, "System.exit(99)");
        Path file = script(h, "", "parameter('legacy.name', description: 'Legacy', defaultValue: 'old')\naction('legacy:run') { parameter('legacy.name') }\ndefaults 'legacy:run'");
        var definition = new GroovyJobLoader().load(file, h.environment);
        assertEquals(List.of("legacy:run"), definition.defaultTargets());
        assertEquals("old", GroovyProjectLoaderTest.value(h.run(definition, Map.of()), "legacy:run"));
    }

    @Test
    void projectDeclarationsRejectQualifiedLocalNamesAndFreezeIsLoaderControlled() throws Exception {
        var h = new GroovyHarness(temp); settings(h, "include 'a'");
        for (String source : List.of("action(':wrong') { }", "parameter('bad.name', description: 'Bad')", "cleanup('a:b') { }")) {
            Path file = script(h, "a", source);
            var failure = assertThrows(GroovyDslException.class, () -> load(h));
            assertEquals("PROJECT_NAME_INVALID", failure.code());
            assertEquals(file.toString(), failure.source().file()); assertEquals(1, failure.source().line());
        }
        script(h, "a", "freeze()\naction('run') { }");
        assertEquals("PROJECT_FREEZE_CONTROLLED", assertThrows(GroovyDslException.class, () -> load(h)).code());
        script(h, "a", "parameter('p', description: 'P')\nparameter('p', description: 'Duplicate')");
        var duplicate = assertThrows(GroovyDslException.class, () -> load(h));
        assertEquals("DUPLICATE_PARAMETER", duplicate.code()); assertEquals(2, duplicate.source().line());
    }

    @Test
    void sharedBuilderRejectsForeignTaskRefsAndOptionalSkippedResultUsesSameStore() throws Exception {
        var h = new GroovyHarness(temp); settings(h, "include 'a', 'b'\ndefaults ':b:run'");
        script(h, "a", "action('run', when: { false }) { throw new AssertionError('skipped') }");
        script(h, "b", "action('run', dependsOn: ':a:run') { assert resultOptional(':a:run').empty; 'ok' }");
        var outcome = h.run(load(h), Map.of()); assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals(TaskStatus.SKIPPED, outcome.tasks().getFirst().status());
        assertEquals("ok", GroovyProjectLoaderTest.value(outcome, ":b:run"));
        script(h, "b", """
                def foreign = [id: { ':a:run' }, resultType: { Object }] as ch.so.agi.gretl.runtime.api.TaskRef
                action('run', dependsOn: foreign) { 'bad' }
                """);
        assertEquals("FOREIGN_TASK_REFERENCE", assertThrows(DefinitionException.class, () -> load(h)).code());
    }

    private boolean hasCode(FailureInfo failure, String code) {
        return failure.diagnostic().map(d -> d.code().equals(code)).orElse(false) || failure.causes().stream().anyMatch(c -> hasCode(c, code));
    }
    private JobDefinition load(GroovyHarness h) { return new GroovyJobLoader().loadProject(new GretlProjectParser().parse(h.jobDir), h.environment); }
    private void settings(GroovyHarness h, String source) throws Exception { Files.writeString(h.jobDir.resolve("settings.gretl"), source); }
    private Path script(GroovyHarness h, String dir, String source) throws Exception { Path p = h.jobDir.resolve(dir).resolve("job.groovy"); Files.createDirectories(p.getParent()); Files.writeString(p, source); return p.toRealPath(); }
}
