package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.logging.Json;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;

class OperationMetadataTest {
    @Test void everyPublicDescriptionExistsInEachLanguageWithoutFallback() throws Exception {
        var registry = OperationRegistry.load(getClass().getClassLoader());
        for (String suffix : List.of("", "_de")) {
            String resource = "/ch/so/agi/gretl/i18n/messages" + suffix + ".properties";
            try (var stream = getClass().getResourceAsStream(resource)) {
                assertNotNull(stream, resource);
                var catalog = new PropertyResourceBundle(new java.io.InputStreamReader(
                        stream, java.nio.charset.StandardCharsets.UTF_8));
                for (var operation : registry.all()) {
                    String prefix = "operation." + operation.id();
                    assertDescription(catalog, prefix + ".description", resource);
                    for (var input : operation.inputs()) {
                        assertDescription(catalog, prefix + ".field." + input.name()
                                + ".description", resource);
                    }
                    for (var output : operation.outputs()) {
                        assertDescription(catalog, prefix + ".output." + output.name()
                                + ".description", resource);
                    }
                }
            }
        }
    }

    private static void assertDescription(ResourceBundle catalog, String key, String resource) {
        assertTrue(catalog.containsKey(key), resource + ": " + key);
        String description = catalog.getString(key);
        assertFalse(description.isBlank(), key);
        assertFalse(description.contains("[."), "Descriptions must remain plain text: " + key);
    }

    @Test void entireCatalogMatchesVersionedIdentityAndResultContracts() throws Exception {
        var registry = OperationRegistry.load(getClass().getClassLoader());
        var expected = (List<?>) Json.parseFile(Path.of("../tests/contracts/operation-baseline.json"), 1_000_000);
        assertEquals(51, registry.all().size());
        var actual = new ArrayList<Object>();
        for (var registered : registry.registered()) {
            var op = registered.operation();
            actual.add(Map.of("symbol", op.symbol(), "id", op.id(), "provider", registered.origin(),
                    "resultType", op.resultType().getName(),
                    "outputs", op.outputs().stream().map(f -> Map.of("name", f.name(), "type", f.valueType(), "nullable", f.nullable())).toList(),
                    "inputs", op.inputs().stream().map(f -> Map.of("name", f.name(), "type", f.valueType(), "required", f.required())).toList()));
            assertFalse(op.example().isBlank());
        }
        Map<Object, Object> byId = new HashMap<>();
        for (Object row : actual) byId.put(((Map<?, ?>) row).get("id"), row);
        var moves = (Map<?, ?>) Json.parseFile(Path.of("../tests/contracts/java-type-moves.json"), 100_000);
        for (Object row : expected) {
            Object id = ((Map<?, ?>) row).get("id");
            // Keep every DSL/result-field contract from the accepted baseline.
            // Only the explicitly documented Java/provider moves are adapted.
            var migrated = new LinkedHashMap<Object, Object>((Map<?, ?>) row);
            String resultType = (String) migrated.get("resultType");
            int nested = resultType.indexOf('$');
            String topLevel = nested < 0 ? resultType : resultType.substring(0, nested);
            String movedType = moves.containsKey(topLevel) ? (String) moves.get(topLevel) : topLevel;
            migrated.put("resultType", movedType + (nested < 0 ? "" : resultType.substring(nested)));
            String provider = (String) migrated.get("provider");
            if (provider.endsWith("BasicTaskProvider")) {
                provider = Set.of("SqlExecutor", "Db2Db", "DuckDbSqlExecutor").contains(migrated.get("symbol"))
                        ? "ch.so.agi.gretl.tasks.database.DatabaseTaskProvider"
                        : "ch.so.agi.gretl.tasks.io.IoTaskProvider";
            } else if (provider.endsWith("CoreTaskProvider")) {
                provider = "ch.so.agi.gretl.tasks.io.IoTaskProvider";
            } else if (moves.containsKey(provider)) provider = (String) moves.get(provider);
            migrated.put("provider", provider);
            assertEquals(migrated, byId.get(id), "Operation metadata: " + id);
        }
        // Each versioned row above already verifies its provider, so a second provider-count table is unnecessary.
        assertEquals(expected.stream().map(row -> ((Map<?, ?>) row).get("id")).collect(java.util.stream.Collectors.toSet()),
                byId.keySet());
        assertTrue(registry.requireSymbol("CsvValidator").inputs().stream().noneMatch(f -> f.name().equals("tableName")));
        assertTrue(registry.requireSymbol("GpkgValidator").inputs().stream().anyMatch(f -> f.name().equals("tableName") && f.required()));
        assertTrue(registry.requireSymbol("Ili2gpkgImport").inputs().stream().anyMatch(f -> f.name().equals("defaultSrsCode")));
    }
}
