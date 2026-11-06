package ch.so.agi.gretl.runtime.groovy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleSources;
import ch.so.agi.gretl.runtime.runner.DefinitionException;
import groovy.lang.GroovyClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LegacyGroovyLoaderTest {
    private static final String MISSING_DEFAULT = """
            action('run') { 'ok' }
            defaults 'missing'
            """;

    @TempDir Path temp;

    @ParameterizedTest
    @ValueSource(strings = {"en", "de"})
    void singleSourceFallbackUsesTheRequestedLocaleForBuilderErrors(String language) throws Exception {
        var environment = environment(language);
        Path script = Files.writeString(environment.jobRoot().resolve("legacy.groovy"), MISSING_DEFAULT);
        assertNull(ScriptBundleSources.single(script, environment.locale()),
                "This fixture must reach the single-source fallback, not the bundle loader");

        var failure = assertThrows(DefinitionException.class,
                () -> new GroovyJobLoader().load(script, environment));
        assertUnknownDefault(failure, language);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "de"})
    void legacyCompiledEntryUsesTheRequestedLocaleForBuilderErrors(String language) throws Exception {
        var environment = environment(language);
        try (var classes = compiler()) {
            Class<?> entry = classes.parseClass(MISSING_DEFAULT, "legacy.groovy");
            Path label = environment.jobRoot().resolve("legacy.groovy");
            assertFalse(Files.exists(label), "This fixture must load from bytecode alone");

            var failure = assertThrows(DefinitionException.class,
                    () -> new GroovyJobLoader().loadCompiled(entry.getName(), label, environment, classes));
            assertUnknownDefault(failure, language);
        }
    }

    @Test
    void invalidCompiledEntryIsRejectedBeforeItsStaticInitializerRuns() throws Exception {
        var environment = environment("en");
        Path marker = temp.resolve("invalid-initialized.txt");
        try (var classes = compiler()) {
            Class<?> entry = classes.parseClass("""
                    class InvalidEntry {
                        static { new File(new URI('%s')).text = 'initialized' }
                    }
                    """.formatted(marker.toUri()), "InvalidEntry.groovy");
            assertFalse(Files.exists(marker), "Compiling the fixture must not initialize it");

            Path label = environment.jobRoot().resolve("invalid.groovy");
            var failure = assertThrows(GroovyDslException.class,
                    () -> new GroovyJobLoader().loadCompiled(entry.getName(), label, environment, classes));
            assertEquals("INVALID_SCRIPT_BASE", failure.code());
            assertEquals(new SourceLocation("invalid.groovy", 0, 0), failure.source());
            assertFalse(Files.exists(marker), "Rejecting the invalid entry must not initialize it");

            Class.forName(entry.getName(), true, classes);
            assertEquals("initialized", Files.readString(marker), "The initializer is observable");
        }
    }

    @Test
    void validCompiledEntryInitializesAndEachLoadReceivesAFreshBinding() throws Exception {
        var environment = environment("en");
        Path marker = temp.resolve("valid-initialized.txt");
        try (var classes = compiler()) {
            Class<?> entry = classes.parseClass("""
                    @groovy.transform.Field static final String initialized = initialize()
                    static String initialize() {
                        new File(new URI('%s')).text = 'initialized'
                        'ready'
                    }
                    assert initialized == 'ready'
                    assert !binding.hasVariable('loadMarker')
                    loadMarker = 'assigned by this script instance'
                    task('echo', Echo) { text = 'ok' }
                    defaults 'echo'
                    """.formatted(marker.toUri()), "legacy.groovy");
            assertFalse(Files.exists(marker));

            Path label = environment.jobRoot().resolve("legacy.groovy");
            var loader = new GroovyJobLoader();
            var first = loader.loadCompiled(entry.getName(), label, environment, classes);
            assertEquals("initialized", Files.readString(marker));
            var second = loader.loadCompiled(entry.getName(), label, environment, classes);
            assertNotEquals(first.definitionId(), second.definitionId());
            assertEquals(List.of("echo"), first.defaultTargets());
            assertEquals(first.defaultTargets(), second.defaultTargets());
        }
    }

    private DefinitionEnvironment environment(String language) throws Exception {
        var harness = new GroovyHarness(temp);
        return new DefinitionEnvironment(harness.registry, harness.jobDir, temp, harness.messages,
                Locale.forLanguageTag(language));
    }

    private static GroovyClassLoader compiler() {
        return new GroovyClassLoader(GroovyJobLoader.class.getClassLoader(), CompilerSettings.create());
    }

    private static void assertUnknownDefault(DefinitionException failure, String language) {
        assertEquals("UNKNOWN_DEFAULT_TARGET", failure.code());
        assertEquals(language.equals("en") ? "Default target is not a registered task: missing"
                : "Default-Ziel ist kein registrierter Task: missing", failure.getMessage());
    }
}
