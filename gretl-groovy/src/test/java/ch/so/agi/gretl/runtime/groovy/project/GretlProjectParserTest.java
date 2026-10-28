package ch.so.agi.gretl.runtime.groovy.project;

import static org.junit.jupiter.api.Assertions.*;

import ch.so.agi.gretl.runtime.api.DiagnosticCarrier;
import ch.so.agi.gretl.runtime.groovy.SourceLocation;
import ch.so.agi.gretl.runtime.groovy.DefinitionEnvironment;
import ch.so.agi.gretl.runtime.groovy.GroovyJobLoader;
import ch.so.agi.gretl.runtime.runner.ExecutionPlanner;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import ch.so.agi.gretl.runtime.runner.ResultSlot;
import ch.so.agi.gretl.runtime.runner.RunInput;
import ch.so.agi.gretl.runtime.runner.RunStatus;
import ch.so.agi.gretl.runtime.runner.SequentialJobRunner;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GretlProjectParserTest {
    @TempDir Path temp;

    @Test
    void prj001RootThenExplicitIncludeOrderAndSettingsPositionsAreStable() throws Exception {
        Path root = project("// ordered\ninclude 'a', ':b:c'\n\ndefaults ':b:c:run', ':a:run'\n");
        script(root, ""); script(root, "a"); script(root, "b/c");
        script(root, "b"); script(root, "notIncluded");
        for (int repeat = 0; repeat < 3; repeat++) {
            GretlProject parsed = new GretlProjectParser().parse(root.resolve("."));
            assertEquals(root.toRealPath(), parsed.root());
            assertEquals(root.resolve("settings.gretl").toRealPath(), parsed.settingsFile());
            assertEquals(List.of(":", ":a", ":b:c"),
                    parsed.entries().stream().map(e -> e.projectPath().canonical()).toList());
            assertEquals(List.of(root.resolve("job.groovy"), root.resolve("a/job.groovy"),
                    root.resolve("b/c/job.groovy")), parsed.entries().stream().map(ProjectEntry::scriptFile).toList());
            assertEquals(List.of(":b:c:run", ":a:run"), parsed.defaultTargets());
            SourceLocation source = parsed.defaultTargetSources().get(":b:c:run");
            assertEquals(root.resolve("settings.gretl").toString(), source.file());
            assertEquals(4, source.line());
            assertEquals(10, source.column());
            assertThrows(UnsupportedOperationException.class, () -> parsed.entries().clear());
            assertThrows(UnsupportedOperationException.class, () -> parsed.defaultTargets().clear());
            assertThrows(UnsupportedOperationException.class, () -> parsed.defaultTargetSources().clear());
        }
    }

    @Test
    void prj002NestedIncludesNeedNoRootOrImplicitParentJob() throws Exception {
        Path root = project("include 'b:c', 'a'\n");
        script(root, "a"); script(root, "b/c");
        Files.writeString(root.resolve("b/c/settings.gretl"), "System.exit(99)");
        GretlProject parsed = new GretlProjectParser().parse(root);
        assertEquals(List.of(":b:c", ":a"), parsed.entries().stream()
                .map(e -> e.projectPath().canonical()).toList());
        assertTrue(parsed.defaultTargets().isEmpty());
        assertFalse(Files.exists(root.resolve("b/job.groovy")));
    }

    @Test
    void immutableRecordsNormalizePathsAndDefensivelyCopyAllCollections() {
        Path root = temp.toAbsolutePath().normalize();
        var entries = new ArrayList<>(List.of(new ProjectEntry(ProjectPath.root(), root.resolve("a/../job.groovy"))));
        var defaults = new ArrayList<>(List.of(":run"));
        var sources = new LinkedHashMap<String, SourceLocation>();
        sources.put(":run", new SourceLocation("settings.gretl", 2, 1));
        var value = new GretlProject(root.resolve("."), root.resolve("a/../settings.gretl"), entries, defaults, sources);
        entries.clear(); defaults.clear(); sources.clear();
        assertEquals(root, value.root());
        assertEquals(root.resolve("settings.gretl"), value.settingsFile());
        assertEquals(root.resolve("job.groovy"), value.entries().getFirst().scriptFile());
        assertEquals(List.of(":run"), value.defaultTargets());
        assertEquals(2, value.defaultTargetSources().get(":run").line());
        assertTrue(new GretlProject(root, root.resolve("settings.gretl"), List.of(), List.of())
                .defaultTargetSources().isEmpty());
    }

    @Test
    void prj003MalformedAndDuplicateDirectivesHaveCodesAndOriginalPositions() throws Exception {
        for (String include : List.of("include()", "include ':'", "include '..'", "include 'a/b'",
                "include 'a\\\\b'", "include 'a::b'", "include 'a:'", "include '1a'")) {
            rejected("// heading\n" + include, "PROJECT_INCLUDE_INVALID", 2);
        }
        ProjectException duplicate = rejected("include 'a',\n ':a'", "PROJECT_INCLUDE_DUPLICATE", 2);
        assertTrue(duplicate.getMessage().contains("a"));
        assertTrue(duplicate.getMessage().contains("settings.gretl:1"));
        assertTrue(duplicate.getMessage().contains("settings.gretl:2"));
        rejected("include 'a'\ninclude 'b'", "PROJECT_SETTINGS_INVALID", 2);
        rejected("defaults ':a:run'\ndefaults ':b:run'", "PROJECT_SETTINGS_INVALID", 2);
        rejected("defaults ':a:run', ':a:run'", "DUPLICATE_DEFAULT_TARGET", 1);

        Path root = project("// heading\ninclude 'missing'\n");
        ProjectException missing = assertThrows(ProjectException.class, () -> new GretlProjectParser().parse(root));
        assertEquals("PROJECT_ENTRY_MISSING", missing.code());
        assertEquals(2, missing.source().line());
        assertTrue(missing.getMessage().contains(root.resolve("missing/job.groovy").toString()));
    }

    @Test
    void prj004UnsupportedSettingsNeverExecuteOrCreateTheirMarker() throws Exception {
        Path marker = temp.resolve("must-not-exist");
        String markerText = marker.toString().replace("\\", "\\\\").replace("'", "\\'");
        List<String> attempts = List.of(
                "new File('" + markerText + "').text = 'executed'",
                "println 'executed'", "System.exit(99)", "def value = 'a'\ninclude value",
                "include \"a$" + "{1}\"", "include('a' + 'b')", "include(['a'])", "include(*['a'])",
                "this.include('a')", "this?.include('a')", "include(name: 'a')", "include { 'a' }",
                "import java.io.File\ninclude 'a'", "import java.io.*\ninclude 'a'",
                "import static java.lang.System.exit\ninclude 'a'",
                "import static java.lang.System.*\ninclude 'a'", "package unsafe\ninclude 'a'",
                "class Hidden { static { new File('" + markerText + "').text = 'bad' } }\ninclude 'a'",
                "def helper() { 1 }; include 'a'", "for (x in [1]) { include 'a' }",
                "if (true) include 'a'", "return null", "true", "label: include 'a'",
                "@groovy.transform.Field String value='a'\ninclude 'a'",
                "@groovy.transform.ASTTest(value={ new File('" + markerText + "').text='bad' }) def value='a'",
                "@groovy.transform.ASTTest(phase=org.codehaus.groovy.control.CompilePhase.CONVERSION, "
                        + "value={ new File('" + markerText + "').text='bad' }) def value='a'",
                "@Grab('invalid:no-network:1') def value='a'",
                "include(/a/)", "include '''a'''", "include \"\"\"a\"\"\"", "include 1");
        for (String attempt : attempts) {
            rejected(attempt, "PROJECT_SETTINGS_UNSUPPORTED", -1);
            assertFalse(Files.exists(marker), attempt);
        }
    }

    @Test
    void invalidGroovyHasItsOwnCauseAndActualSyntaxLine() throws Exception {
        ProjectException failure = rejected("// heading\ninclude (", "PROJECT_SETTINGS_INVALID", 2);
        assertNotNull(failure.getCause());
        assertTrue(failure.source().column() > 0);
        assertTrue(failure instanceof DiagnosticCarrier);
        assertEquals(failure.code(), failure.diagnostic().code());
    }

    @Test
    void prj006RequiresSettingsAndAtLeastOneRegularScript() throws Exception {
        Path root = Files.createDirectory(temp.resolve("empty"));
        ProjectException absent = assertThrows(ProjectException.class, () -> new GretlProjectParser().parse(root));
        assertEquals("PROJECT_SETTINGS_MISSING", absent.code());
        Files.writeString(root.resolve("settings.gretl"), "// root-only\n");
        assertEquals("PROJECT_EMPTY", assertThrows(ProjectException.class,
                () -> new GretlProjectParser().parse(root)).code());
        script(root, "");
        assertEquals(List.of(ProjectPath.root()), new GretlProjectParser().parse(root).entries().stream()
                .map(ProjectEntry::projectPath).toList());
        Files.delete(root.resolve("job.groovy"));
        Files.createDirectory(root.resolve("job.groovy"));
        assertEquals("PROJECT_ENTRY_MISSING", assertThrows(ProjectException.class,
                () -> new GretlProjectParser().parse(root)).code());
    }

    @Test
    void prj006RejectsSymlinksAtRootSettingsDirectoriesAndScriptEvenIfContained() throws Exception {
        Path outside = Files.createDirectory(temp.resolve("outside"));
        script(outside, "");
        Files.writeString(outside.resolve("settings.gretl"), "");
        Path alias = Files.createSymbolicLink(temp.resolve("alias"), outside);
        assertEquals("PROJECT_PATH_UNSAFE", assertThrows(ProjectException.class,
                () -> new GretlProjectParser().parse(alias)).code());

        Path root = project("");
        Files.delete(root.resolve("settings.gretl"));
        Files.createSymbolicLink(root.resolve("settings.gretl"), outside.resolve("settings.gretl"));
        unsafe(root);
        Files.delete(root.resolve("settings.gretl"));
        Files.writeString(root.resolve("settings.gretl"), "include 'a'\n");
        Files.createSymbolicLink(root.resolve("a"), outside);
        unsafe(root);
        Files.delete(root.resolve("a"));
        Files.createDirectories(root.resolve("b/c"));
        Files.createSymbolicLink(root.resolve("a"), root.resolve("b"));
        Files.writeString(root.resolve("settings.gretl"), "include 'a:c'");
        unsafe(root);
        Files.writeString(root.resolve("settings.gretl"), "include 'b:c'");
        Files.createSymbolicLink(root.resolve("b/c/job.groovy"), outside.resolve("job.groovy"));
        unsafe(root);
        Files.delete(root.resolve("b/c/job.groovy"));
        Files.createSymbolicLink(root.resolve("job.groovy"), temp.resolve("missing-target"));
        Files.writeString(root.resolve("settings.gretl"), "");
        unsafe(root);
        assertEquals("PROJECT_PATH_UNSAFE", assertThrows(ProjectException.class,
                () -> new GretlProjectParser().validateContainedRegularFile(root,
                        outside.resolve("job.groovy"), "job.groovy")).code());
    }

    @Test
    void prj006SizeLimitCountsUtf8BytesAndMalformedUtf8IsRejected() throws Exception {
        Path root = project("//" + " ".repeat(262142));
        script(root, "");
        assertEquals(1, new GretlProjectParser().parse(root).entries().size());
        Files.writeString(root.resolve("settings.gretl"), "//" + "ä".repeat(131072));
        assertEquals("PROJECT_SETTINGS_INVALID", assertThrows(ProjectException.class,
                () -> new GretlProjectParser().parse(root)).code());
        Files.write(root.resolve("settings.gretl"), new byte[] {(byte) 0xc3, (byte) 0x28});
        assertEquals("PROJECT_SETTINGS_INVALID", assertThrows(ProjectException.class,
                () -> new GretlProjectParser().parse(root)).code());
    }

    @Test
    void prj007QualifiedDefaultsAreSyntaxCheckedWithoutLoadingJobs() throws Exception {
        for (String directive : List.of("defaults()", "defaults 'run'", "defaults ':'", "defaults ':a::run'",
                "defaults ':a:run:'", "defaults ':a/run'")) {
            rejected(directive, "PROJECT_TARGET_INVALID", 1);
        }
        Path root = project("\ninclude \"a\"\ndefaults ':a:unknown', ':rootTask'\n");
        Path marker = temp.resolve("job-body-must-not-run");
        script(root, "a");
        Files.writeString(root.resolve("a/job.groovy"), "new File('"
                + marker.toString().replace("\\", "\\\\") + "').text='bad'\n"
                + "task('run', MissingType) { throw new AssertionError('task') }\n");
        var parsed = new GretlProjectParser().parse(root);
        assertEquals(List.of(":a:unknown", ":rootTask"), parsed.defaultTargets());
        assertEquals(3, parsed.defaultTargetSources().get(":a:unknown").line());
        assertFalse(Files.exists(marker));
    }

    @Test
    void relativeProjectArgumentSpacesAndCrLfKeepTheSameLogicalPathsAndPositions() throws Exception {
        Path root = Files.createDirectory(temp.resolve("project with spaces")).toRealPath();
        Files.writeString(root.resolve("settings.gretl"), "// comment\r\ninclude 'a:b'\r\ndefaults ':a:b:run'\r\n");
        script(root, "a/b");
        Path relative = Path.of("").toAbsolutePath().relativize(root);
        var parsed = new GretlProjectParser().parse(relative);
        assertEquals(root, parsed.root());
        assertEquals(root.resolve("a/b/job.groovy"), parsed.entries().getFirst().scriptFile());
        assertEquals(":a:b", parsed.entries().getFirst().projectPath().canonical());
        assertEquals(3, parsed.defaultTargetSources().get(":a:b:run").line());
        assertEquals(10, parsed.defaultTargetSources().get(":a:b:run").column());
    }

    @Test
    void documentedFormatExampleParsesAndBothScriptsStillRunAsIndependentSingleJobs() {
        Path example = Path.of("..", "examples", "project-subprojects").toAbsolutePath().normalize();
        var parsed = new GretlProjectParser().parse(example);
        assertEquals(List.of(":source", ":checks:local"), parsed.entries().stream()
                .map(e -> e.projectPath().canonical()).toList());
        assertEquals(List.of(":checks:local:run"), parsed.defaultTargets());
        List<String> expected = List.of("Quelle: lokale synthetische Daten", "Prüfung: eigener lokaler Datenbestand");
        for (int index = 0; index < parsed.entries().size(); index++) {
            var entry = parsed.entries().get(index);
            var definition = new GroovyJobLoader().load(entry.scriptFile(),
                    DefinitionEnvironment.of(OperationRegistry.fromProviders(List.of()), entry.scriptFile().getParent()));
            assertEquals(List.of("run"), definition.tasks().stream().map(t -> t.id()).toList());
            var plan = new ExecutionPlanner().plan(definition, List.of());
            var outcome = new SequentialJobRunner().run(definition, plan, RunInput.builder()
                    .jobRoot(entry.scriptFile().getParent()).startCwd(temp).workRoot(temp.resolve("work")).build());
            assertEquals(RunStatus.SUCCEEDED, outcome.status());
            assertEquals(expected.get(index), ((ResultSlot.Value) outcome.task("run").orElseThrow().slot()).value());
        }
    }

    @Test
    void diagnosticsUseEnglishAndGermanWithoutLosingCodeOrSource() throws Exception {
        Path root = project("println 'forbidden'");
        var english = assertThrows(ProjectException.class, () -> new GretlProjectParser(Locale.ENGLISH).parse(root));
        var german = assertThrows(ProjectException.class, () -> new GretlProjectParser(Locale.GERMAN).parse(root));
        assertEquals(english.code(), german.code());
        assertEquals(english.source(), german.source());
        assertNotEquals(english.getMessage(), german.getMessage());
        assertTrue(english.getMessage().contains("settings.gretl:1"));
        assertTrue(german.getMessage().contains("settings.gretl:1"));
    }

    private Path project(String settings) throws Exception {
        Path root = Files.createTempDirectory(temp, "project-").toRealPath();
        Files.writeString(root.resolve("settings.gretl"), settings, StandardCharsets.UTF_8);
        return root;
    }

    private void script(Path root, String directory) throws Exception {
        Path folder = root.resolve(directory);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("job.groovy"), "throw new AssertionError('job must not load')\n");
    }

    private ProjectException rejected(String text, String code, int line) throws Exception {
        Path root = project(text);
        ProjectException failure = assertThrows(ProjectException.class, () -> new GretlProjectParser().parse(root), text);
        assertEquals(code, failure.code(), text + " -> " + failure.getMessage());
        assertEquals(root.resolve("settings.gretl").toString(), failure.source().file(), text);
        if (line >= 0) assertEquals(line, failure.source().line(), text);
        return failure;
    }

    private void unsafe(Path root) {
        assertEquals("PROJECT_PATH_UNSAFE", assertThrows(ProjectException.class,
                () -> new GretlProjectParser().parse(root)).code());
    }
}
