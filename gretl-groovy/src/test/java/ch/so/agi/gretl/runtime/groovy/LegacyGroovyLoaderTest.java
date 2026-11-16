package ch.so.agi.gretl.runtime.groovy;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void compiledBundleKeepsParameterFieldAndSourceForRegistrationErrors() throws Exception {
        var environment = environment("en");
        String text = "parameter('name', description: 'first')\nparameter('name', description: 'second')\n";
        Path source = Files.writeString(environment.jobRoot().resolve("job.groovy"), text);
        var bundle = ScriptBundleSources.single(source, environment.locale(), false).bundle();
        try (var classes = new GroovyClassLoader(getClass().getClassLoader(), CompilerSettings.create())) {
            var entry = classes.parseClass(text, bundle.virtualName("job.groovy"));
            var metadata = ch.so.agi.gretl.runtime.groovy.fragment.CompiledScriptBundle.from(
                    bundle, java.util.Map.of("job.groovy", entry.getName()), List.of());
            Files.delete(source);
            var failure = assertThrows(GroovyDslException.class,
                    () -> new GroovyJobLoader().loadCompiledBundle(metadata, environment, classes));
            assertEquals("DUPLICATE_PARAMETER", failure.code());
            assertEquals("name", failure.fieldName());
            assertEquals(2, failure.source().line());
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
