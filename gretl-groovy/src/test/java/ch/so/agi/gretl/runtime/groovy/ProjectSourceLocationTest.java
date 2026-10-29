package ch.so.agi.gretl.runtime.groovy;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.groovy.project.GretlProjectParser;
import ch.so.agi.gretl.runtime.runner.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectSourceLocationTest {
    @TempDir Path temp;

    @Test void prj112CompilerAndToplevelFailuresKeepOriginalPathsAndActualLines() throws Exception {
        for (String body : List.of("throw new IllegalStateException('load-failure')", "task('run', MissingType) { }", "action('run') {")) {
            var h = new GroovyHarness(Files.createTempDirectory(temp, "load-"));
            Path source = tree(h, "// heading\n" + body + "\n");
            var error = assertThrows(GroovyDslException.class, () -> load(h));
            assertEquals("JOB_LOAD_FAILED", error.code()); assertEquals(source.toString(), error.source().file());
            assertEquals(2, error.source().line()); assertNotNull(error.getCause());
            assertTrue(error.getMessage().contains(source.toString())); assertFalse(error.getMessage().contains("GretlProject_"));
            assertEquals(0, h.events.count("gretl.run.started"));
        }
    }

    @Test void prj112WhenConfigurationActionAndCleanupFailuresHaveStructuredOriginalSources() throws Exception {
        record Sample(String source, String code, int line, boolean cleanup) {}
        var cases = List.of(
                new Sample("// heading\naction('run', when: { 'not boolean' }) { 'never' }", "CONDITION_NOT_BOOLEAN", 2, false),
                new Sample("// heading\ntask('run', Echo) {\n unknown = 'wrong'\n}", "UNKNOWN_CONFIGURATION_FIELD", 3, false),
                new Sample("// heading\ntask('run', RequiredText) { }", "MISSING_REQUIRED_FIELD", 2, false),
                new Sample("// heading\naction('run') {\n missingProperty\n}", "UNKNOWN_PROPERTY", 3, false),
                new Sample("// heading\naction('run') {\n missingHelper()\n}", "UNKNOWN_HELPER", 3, false),
                new Sample("action('run') { 'ok' }\ncleanup('release') {\n missingProperty\n}", "UNKNOWN_PROPERTY", 3, true));
        for (var sample : cases) {
            var h = new GroovyHarness(Files.createTempDirectory(temp, "execution-"));
            Path source = tree(h, sample.source()); var outcome = h.run(load(h), Map.of());
            assertEquals(RunStatus.FAILED, outcome.status(), sample.toString()); assertEquals(10, outcome.exitCode());
            FailureInfo failure = sample.cleanup() ? outcome.primaryFailure().orElseThrow() : outcome.tasks().getFirst().failure().orElseThrow();
            assertEquals(sample.code(), failure.diagnostic().orElseThrow().code(), failure.toString());
            assertEquals(source.toString(), failure.source().orElseThrow().file());
            assertEquals(sample.line(), failure.source().orElseThrow().line());
            assertFalse(failure.frames().stream().anyMatch(frame -> frame.fileName().startsWith("GretlProject_")));
            if (sample.cleanup()) assertEquals(TaskStatus.SUCCEEDED, outcome.tasks().getFirst().status());
        }
    }

    @Test void sharedCompilerLoaderFindsExplicitRootHelpersWithoutLeakingBindingsOrClosingEarly() throws Exception {
        var h = new GroovyHarness(temp);
        Files.createDirectories(h.jobDir.resolve("helpers"));
        Files.writeString(h.jobDir.resolve("helpers/Label.groovy"), "package helpers\nclass Label { static String value() { 'helper-value' } }\n");
        tree(h, "import helpers.Label\n@groovy.transform.Field def captured = binding.getVariable('Echo')\ndef constructorValue = captured\naction('run') { assert constructorValue.is(Echo); Label.value() }\n");
        var definition = load(h); var outcome = h.run(definition, Map.of());
        assertEquals(RunStatus.SUCCEEDED, outcome.status(), outcome.toString());
        assertEquals("helper-value", GroovyProjectLoaderTest.value(outcome, ":a:b:run"));
    }

    private Path tree(GroovyHarness h, String source) throws Exception {
        Files.writeString(h.jobDir.resolve("settings.gretl"), "include 'a:b'\ndefaults ':a:b:run'\n");
        Path file = h.jobDir.resolve("a/b/job.groovy"); Files.createDirectories(file.getParent()); Files.writeString(file, source); return file.toRealPath();
    }
    private JobDefinition load(GroovyHarness h) { return new GroovyJobLoader().loadProject(new GretlProjectParser().parse(h.jobDir), h.environment); }
}
