package ch.so.agi.gretl.tasks.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BasicTaskProviderTest {
    @Test
    void serviceLoaderExposesTheTwentyStandardTasks() {
        OperationRegistry registry = OperationRegistry.load(getClass().getClassLoader());
        assertEquals(20, registry.all().size());
        assertEquals("HttpDownload", registry.requireSymbol("HttpDownload").symbol());
        assertEquals("Curl", registry.requireSymbol("Curl").symbol());
        assertEquals("Gzip", registry.requireSymbol("Gzip").symbol());
        assertEquals("Extract", registry.requireSymbol("Extract").symbol());
        assertEquals("Db2Db", registry.requireSymbol("Db2Db").symbol());
        assertEquals("DuckDbSqlExecutor",
                registry.requireSymbol("DuckDbSqlExecutor").symbol());
        assertEquals("Csv2Excel", registry.requireSymbol("Csv2Excel").symbol());
        assertEquals("CsvImport", registry.requireSymbol("CsvImport").symbol());
        assertEquals("CsvExport", registry.requireSymbol("CsvExport").symbol());
        assertEquals("JsonImport", registry.requireSymbol("JsonImport").symbol());
        assertEquals("CsvValidator", registry.requireSymbol("CsvValidator").symbol());
        assertEquals("JsonValidator", registry.requireSymbol("JsonValidator").symbol());
        assertEquals("Gpkg2Shp", registry.requireSymbol("Gpkg2Shp").symbol());
        assertEquals("GpkgImport", registry.requireSymbol("GpkgImport").symbol());
        assertEquals("GpkgExport", registry.requireSymbol("GpkgExport").symbol());
        assertEquals("ShpImport", registry.requireSymbol("ShpImport").symbol());
        assertEquals("ShpExport", registry.requireSymbol("ShpExport").symbol());
        assertEquals("GpkgValidator", registry.requireSymbol("GpkgValidator").symbol());
        assertEquals("ShpValidator", registry.requireSymbol("ShpValidator").symbol());
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

        OperationType<?, ?> curl = registry.requireSymbol("Curl");
        assertTrue(curl.inputs().stream().anyMatch(field -> field.name().equals("serverUrl")
                && field.required()));
        assertTrue(curl.inputs().stream().anyMatch(field -> field.name().equals("expectedStatusCode")
                && field.required()));
        assertEquals(16L * 1024 * 1024, curl.inputs().stream()
                .filter(field -> field.name().equals("maxBytes"))
                .findFirst().orElseThrow().defaultValue().orElseThrow());
        assertTrue(curl.outputs().stream().anyMatch(field -> field.name().equals("outputFile")
                && field.nullable()));
        assertTrue(curl.example().contains("Curl"));

        OperationType<?, ?> gzip = registry.requireSymbol("Gzip");
        assertTrue(gzip.inputs().stream().anyMatch(field -> field.name().equals("dataFile")
                && field.required()));
        assertTrue(gzip.inputs().stream().anyMatch(field -> field.name().equals("gzipFile")
                && field.required()));
        assertEquals(true, gzip.inputs().stream()
                .filter(field -> field.name().equals("overwrite"))
                .findFirst().orElseThrow().defaultValue().orElseThrow());
        assertTrue(gzip.outputs().stream().anyMatch(field -> field.name().equals("bytes")));

        var formData = curl.inputs().stream()
                .filter(field -> field.name().equals("formData"))
                .findFirst().orElseThrow();
        Object convertedForm = formData.convert().apply(Map.of(
                "topic", "npl", "file", java.nio.file.Path.of("data.xtf")));
        assertTrue(convertedForm.toString().contains("topic"));
        var password = curl.inputs().stream()
                .filter(field -> field.name().equals("password"))
                .findFirst().orElseThrow();
        Object convertedSecret = password.convert().apply(
                ch.so.agi.gretl.runtime.api.SecretValue.deferred(() -> "secret"));
        assertTrue(convertedSecret instanceof ch.so.agi.gretl.runtime.api.SecretValue);
        assertThrows(IllegalArgumentException.class,
                () -> password.convert().apply("plain-text-password"));
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
