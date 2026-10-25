package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.logging.Json;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;

class OperationMetadataTest {
    @Test void entireCatalogMatchesVersionedIdentityAndResultContracts() throws Exception {
        var registry = OperationRegistry.load(getClass().getClassLoader());
        var expected = (List<?>) Json.parseFile(Path.of("../docs/implementation/improvement-plan/operation-inventory.json"), 1_000_000);
        assertEquals(44, registry.all().size());
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
        for (Object row : expected) {
            Object id = ((Map<?, ?>) row).get("id");
            assertEquals(row, byId.get(id), "Operation metadata: " + id);
        }
        assertTrue(registry.requireSymbol("CsvValidator").inputs().stream().noneMatch(f -> f.name().equals("tableName")));
        assertTrue(registry.requireSymbol("GpkgValidator").inputs().stream().anyMatch(f -> f.name().equals("tableName") && f.required()));
        assertTrue(registry.requireSymbol("Ili2gpkgImport").inputs().stream().anyMatch(f -> f.name().equals("defaultSrsCode")));
    }
}
