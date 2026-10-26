package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.job.runtime.RuntimeConfig;
import ch.so.agi.gretl.job.runtime.StartException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.runner.SecretRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
