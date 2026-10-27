package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.job.runtime.RuntimeConfig;
import ch.so.agi.gretl.job.runtime.StartException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.api.ContextAccessException;
import ch.so.agi.gretl.runtime.logging.Json;
import ch.so.agi.gretl.runtime.runner.SecretRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void minimalConfigUsesDefaultsAndStrictUnknownFieldsFail() throws Exception {
        Path config = tempDir.resolve("runtime.json");
        Files.writeString(config, "{\"schemaVersion\":1}");
        RuntimeConfig runtime = RuntimeConfig.load(config, tempDir);
        assertTrue(runtime.taskConfig().isEmpty());
        assertEquals(10_000, runtime.logging().progressInterval().toMillis());

        Files.writeString(config, "{\"schemaVersion\":1,\"unknown\":true}");
        assertThrows(StartException.class, () -> RuntimeConfig.load(config, tempDir));
        Files.writeString(config, "{\"schemaVersion\":2}");
        assertThrows(StartException.class, () -> RuntimeConfig.load(config, tempDir));
        Files.writeString(config, "{\"schemaVersion\":1,\"params\":{\"x\":1}}");
        assertThrows(StartException.class, () -> RuntimeConfig.load(config, tempDir));
        Files.writeString(config, "{\"schemaVersion\":1,\"secrets\":{\"s\":{\"environment\":\"A\",\"file\":\"b\"}}}");
        assertThrows(StartException.class, () -> RuntimeConfig.load(config, tempDir));
        Files.writeString(config, "{\"schemaVersion\":1,\"language\":\"fr\"}");
        assertThrows(StartException.class, () -> RuntimeConfig.load(config, tempDir));
        Files.writeString(config, "{\"schemaVersion\":1,\"language\":\"en\"}");
        assertEquals("en", RuntimeConfig.load(config, tempDir).language().orElseThrow());
    }

    @Test
    void topLevelConfigErrorsFollowTheRequestedLocale() throws Exception {
        Path config = tempDir.resolve("runtime-broken.json");
        Files.writeString(config, "{broken");
        StartException english = assertThrows(StartException.class,
                () -> RuntimeConfig.load(config, tempDir, Locale.ENGLISH));
        assertTrue(english.getMessage().contains("Runtime configuration is invalid"),
                english.getMessage());
        StartException german = assertThrows(StartException.class,
                () -> RuntimeConfig.load(config, tempDir, Locale.GERMAN));
        assertTrue(german.getMessage().contains("Runtimekonfiguration ist ungueltig"),
                german.getMessage());
    }

    @Test
    void nestedConfigErrorsFollowTheRequestedLocale() throws Exception {
        Path config = tempDir.resolve("runtime-nested.json");
        Files.writeString(config, "{\"schemaVersion\":1,\"runtime\":{\"unknown\":true}}");
        StartException english = assertThrows(StartException.class,
                () -> RuntimeConfig.load(config, tempDir, Locale.ENGLISH));
        assertTrue(english.getMessage().contains("Unknown field runtime.unknown"),
                english.getMessage());
        StartException german = assertThrows(StartException.class,
                () -> RuntimeConfig.load(config, tempDir, Locale.GERMAN));
        assertTrue(german.getMessage().contains("Unbekanntes Feld runtime.unknown"),
                german.getMessage());
    }

    @Test
    void configPathsAndSecretFileResolveAgainstConfigDirectory() throws Exception {
        Path configDir = Files.createDirectories(tempDir.resolve("conf"));
        Files.writeString(configDir.resolve("auth.txt"), "secret-value\n");
        Path config = configDir.resolve("runtime.json");
        Files.writeString(config, """
                {
                  "schemaVersion": 1,
                  "runtime": {"workRoot": "work", "reportRoot": "reports"},
                  "secrets": {"auth": {"file": "auth.txt"}},
                  "taskConfig": {"db": {"jdbcUrl": "jdbc:h2:mem:x", "nested": {"a": 1}}}
                }
                """);
        RuntimeConfig runtime = RuntimeConfig.load(config, tempDir);
        assertEquals(configDir.resolve("work").toAbsolutePath().normalize(),
                runtime.workRoot());
        assertEquals(configDir.resolve("reports").toAbsolutePath().normalize(),
                runtime.reportRoot());
        SecretRegistry registry = new SecretRegistry();
        String value = runtime.secretResolver(Map.of(), registry).resolve("auth");
        assertEquals("secret-value", value);
        assertTrue(registry.values().contains("secret-value"));
        assertEquals(1L, runtime.taskConfig().get("db").get("nested") instanceof Map<?, ?> map
                ? map.get("a") : null);
    }

    @Test
    void environmentSecretAndMissingSourceAreReportedWithoutValue() throws Exception {
        Path config = tempDir.resolve("runtime.json");
        Files.writeString(config, "{\"schemaVersion\":1,\"secrets\":{\"s\":{\"environment\":\"SECRET_ENV\"}}}");
        RuntimeConfig runtime = RuntimeConfig.load(config, tempDir);
        assertEquals("from-env", runtime.secretResolver(Map.of("SECRET_ENV", "from-env"),
                new SecretRegistry()).resolve("s"));
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> runtime.secretResolver(Map.of(), new SecretRegistry()).resolve("s"));
        assertTrue(missing.getMessage().contains("SECRET_ENV"));
        assertFalse(missing.getMessage().contains("from-env"));
    }

    @Test
    void plaintextIsPreservedAndRegisteredOnlyOnAccess() throws Exception {
        Path config = tempDir.resolve("plaintext.json");
        for (String value : List.of("", "  ", "\t ä🚲 ${UNCHANGED} \\\"\r\n\0")) {
            Files.writeString(config, Json.write(Map.of("schemaVersion", 1,
                    "secrets", Map.of("s", Map.of("plaintext", value)))));
            RuntimeConfig runtime = RuntimeConfig.load(config, tempDir);
            SecretRegistry registry = new SecretRegistry();
            var resolver = runtime.secretResolver(Map.of(), registry);
            assertTrue(registry.values().isEmpty());
            assertEquals(value, resolver.resolve("s"));
            assertEquals(List.of(value), registry.values());
            assertEquals(value, resolver.resolve("s"));
            assertEquals(1, registry.values().size());
        }
    }

    @Test
    void allSecretSourcesRequireExactlyOneStringSource() throws Exception {
        Path config = tempDir.resolve("invalid-secret.json");
        for (String source : List.of("{}", "null", "\"direct-string\"",
                "{\"plaintext\":null}", "{\"plaintext\":42}", "{\"plaintext\":true}",
                "{\"plaintext\":[]}", "{\"plaintext\":{}}", "{\"value\":\"sentinel\"}",
                "{\"environment\":null}", "{\"file\":null}",
                "{\"environment\":\"ENV\",\"file\":\"auth.txt\"}",
                "{\"plaintext\":\"sentinel\",\"environment\":\"ENV\"}",
                "{\"plaintext\":\"sentinel\",\"file\":\"auth.txt\"}",
                "{\"plaintext\":\"sentinel\",\"environment\":\"ENV\",\"file\":\"auth.txt\"}")) {
            Files.writeString(config, "{\"schemaVersion\":1,\"secrets\":{\"s\":" + source + "}}");
            for (Locale locale : List.of(Locale.GERMAN, Locale.ENGLISH)) {
                StartException failure = assertThrows(StartException.class,
                        () -> RuntimeConfig.load(config, tempDir, locale));
                assertEquals(2, failure.code());
                assertFalse(failure.getMessage().contains("sentinel"));
            }
        }
    }

    @Test
    void secretSourceDiagnosticsIncludeAllChoicesInBothLanguages() throws Exception {
        Path config = tempDir.resolve("choices.json");
        Files.writeString(config, "{\"schemaVersion\":1,\"secrets\":{\"s\":{}}}");
        for (Locale locale : List.of(Locale.GERMAN, Locale.ENGLISH)) {
            var failure = assertThrows(StartException.class,
                    () -> RuntimeConfig.load(config, tempDir, locale));
            for (String choice : List.of("environment", "file", "plaintext")) {
                assertTrue(failure.getMessage().contains(choice));
            }
        }
    }

    @Test
    void secretSourceMasksPlaintextAndPreservesExistingConstructor() {
        String sentinel = "plaintext-source-sentinel";
        var plaintext = new RuntimeConfig.SecretSource(null, null, sentinel);
        assertTrue(plaintext.toString().contains("[REDACTED]"));
        assertFalse(plaintext.toString().contains(sentinel));
        assertFalse(Map.of("s", plaintext).toString().contains(sentinel));
        assertEquals(sentinel, plaintext.plaintext());
        assertEquals("ENV", new RuntimeConfig.SecretSource("ENV", null).environment());
        assertEquals(Path.of("auth"), new RuntimeConfig.SecretSource(null, Path.of("auth")).file());
        assertThrows(IllegalArgumentException.class, () -> new RuntimeConfig.SecretSource(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new RuntimeConfig.SecretSource("ENV", null, sentinel));
    }

    @Test
    void unusedPlaintextIsNotRegisteredAndOversizedValueFailsBeforeRelease() throws Exception {
        Path config = tempDir.resolve("large-secret.json");
        Files.writeString(config, Json.write(Map.of("schemaVersion", 1,
                "secrets", Map.of("s", Map.of("plaintext", "x".repeat(64 * 1024 + 1))))));
        RuntimeConfig runtime = RuntimeConfig.load(config, tempDir);
        SecretRegistry registry = new SecretRegistry();
        var resolver = runtime.secretResolver(Map.of(), registry);
        assertTrue(registry.values().isEmpty());
        assertThrows(ContextAccessException.class, () -> resolver.resolve("s"));
        assertTrue(registry.values().isEmpty());
    }
}
