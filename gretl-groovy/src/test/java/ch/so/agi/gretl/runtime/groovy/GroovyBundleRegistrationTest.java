package ch.so.agi.gretl.runtime.groovy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.so.agi.gretl.runtime.groovy.fragment.CompiledScriptBundle;
import ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleSources;
import ch.so.agi.gretl.runtime.runner.DefinitionException;
import groovy.lang.GroovyClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GroovyBundleRegistrationTest {
    private static final String MISSING_DEFAULT = """
            action('run') { 'ok' }
            defaults 'missing'
            """;

    @TempDir Path temp;

    @ParameterizedTest
    @ValueSource(strings = {"en", "de"})
    void singleSourceUsesTheRequestedLocaleForBuilderErrors(String language) throws Exception {
        var environment = environment(language);
        Path script = Files.writeString(environment.jobRoot().resolve("job.groovy"), MISSING_DEFAULT);
        var snapshot = ScriptBundleSources.single(script, environment.locale());

        var failure = assertThrows(DefinitionException.class,
                () -> new GroovyJobLoader().loadBundle(snapshot, environment, List.of(), Map.of()));
        assertUnknownDefault(failure, language);
    }

    @Test
    void compiledBundleKeepsParameterFieldAndSourceForRegistrationErrors() throws Exception {
        var environment = environment("en");
        String text = "parameter('name', description: 'first')\nparameter('name', description: 'second')\n";
        Path source = Files.writeString(environment.jobRoot().resolve("job.groovy"), text);
        var bundle = ScriptBundleSources.single(source, environment.locale()).bundle();
        try (var classes = compiler()) {
            var entry = classes.parseClass(text, bundle.virtualName("job.groovy"));
            var metadata = CompiledScriptBundle.from(
                    bundle, Map.of("job.groovy", entry.getName()), List.of());
            Files.delete(source);
            var failure = assertThrows(GroovyDslException.class,
                    () -> new GroovyJobLoader().loadCompiledBundle(metadata, environment, classes));
            assertEquals("DUPLICATE_PARAMETER", failure.code());
            assertEquals("name", failure.fieldName());
            assertEquals(2, failure.source().line());
        }
    }

    @Test
    void compiledBundleRejectsInvalidScriptBaseBeforeStaticInitialization() throws Exception {
        var environment = environment("en");
        String sourceId = "invalid.groovy";
        Path source = Files.writeString(environment.jobRoot().resolve(sourceId), "action('run') {}");
        var bundle = ScriptBundleSources.single(source, environment.locale()).bundle();
        String virtualName = bundle.virtualName(sourceId);
        String className = virtualName.substring(0, virtualName.length() - ".groovy".length());
        Path marker = temp.resolve("invalid-initialized.txt");
        try (var classes = compiler()) {
            var invalid = classes.parseClass("""
                    class %s {
                        static { new File(new URI('%s')).text = 'initialized' }
                    }
                    """.formatted(className, marker.toUri()), virtualName);
            assertFalse(Files.exists(marker), "Compiling the fixture must not initialize it");
            var metadata = CompiledScriptBundle.from(bundle, Map.of(sourceId, invalid.getName()), List.of());
            Files.delete(source);

            var failure = assertThrows(GroovyDslException.class,
                    () -> new GroovyJobLoader().loadCompiledBundle(metadata, environment, classes));
            assertEquals("INVALID_SCRIPT_BASE", failure.code());
            assertFalse(Files.exists(marker), "Rejecting the class must not initialize it");

            Class.forName(invalid.getName(), true, classes);
            assertEquals("initialized", Files.readString(marker), "Explicit initialization must write the marker");
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
