package ch.so.agi.gretl.tasks.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BasicTaskProviderTest {
    @Test
    void serviceLoaderExposesExactlyTheThreeStandardTasks() {
        OperationRegistry registry = OperationRegistry.load(getClass().getClassLoader());
        assertEquals(3, registry.all().size());
        assertEquals("HttpDownload", registry.requireSymbol("HttpDownload").symbol());
        assertEquals("Extract", registry.requireSymbol("Extract").symbol());
        assertEquals("SqlExecutor", registry.requireSymbol("SqlExecutor").symbol());
        assertFalse(registry.all().stream().anyMatch(operation ->
                operation.symbol().equals("Publisher")));
    }

    @Test
    void metadataCarriesFieldsDefaultsOutputsAndExamples() {
        OperationRegistry registry = OperationRegistry.load(getClass().getClassLoader());
        OperationType<?, ?> http = registry.requireSymbol("HttpDownload");
        assertTrue(http.inputs().stream().anyMatch(field -> field.name().equals("url")
                && field.required()));
        assertEquals(1L << 30, http.inputs().stream()
                .filter(field -> field.name().equals("maxBytes"))
                .findFirst().orElseThrow().defaultValue().orElseThrow());
        assertEquals(false, http.inputs().stream()
                .filter(field -> field.name().equals("overwrite"))
                .findFirst().orElseThrow().defaultValue().orElseThrow());
        assertTrue(http.outputs().stream().anyMatch(field -> field.name().equals("sha256")));
        assertTrue(http.example().contains("HttpDownload"));

        OperationType<?, ?> extract = registry.requireSymbol("Extract");
        assertTrue(extract.inputs().stream().anyMatch(field -> field.name().equals("compression")
                && field.allowedValues().equals(java.util.List.of("ZIP", "GZIP"))));
        assertTrue(extract.outputs().stream().anyMatch(field -> field.name().equals("files")));

        OperationType<?, ?> sql = registry.requireSymbol("SqlExecutor");
        assertTrue(sql.inputs().stream().anyMatch(field -> field.name().equals("scripts")));
        assertTrue(sql.outputs().stream().anyMatch(field -> field.name().equals("executions")));
    }

    @Test
    void httpHeaderConversionRejectsCaseInsensitiveDuplicatesAndSecretsAreMasked() {
        var type = HttpDownloadOperation.type();
        var headers = type.inputs().stream().filter(field -> field.name().equals("headers"))
                .findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> headers.convert().apply(Map.of("X-Test", "a", "x-test", "b")));
        Object converted = headers.convert().apply(Map.of("Authorization",
                ch.so.agi.gretl.runtime.api.SecretValue.deferred(() -> "secret")));
        assertTrue(converted.toString().contains("[REDACTED]"));
    }
}
