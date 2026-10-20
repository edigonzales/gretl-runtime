package ch.so.agi.gretl.tasks.formats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import org.junit.jupiter.api.Test;

class InterlisTaskProviderTest {
    @Test
    void serviceLoaderExposesAllFormatTasks() {
        OperationRegistry registry = OperationRegistry.load(getClass().getClassLoader());
        assertEquals(13, registry.all().size());
        assertEquals("Csv2Excel", registry.requireSymbol("Csv2Excel").symbol());
        assertEquals("CsvImport", registry.requireSymbol("CsvImport").symbol());
        assertEquals("CsvExport", registry.requireSymbol("CsvExport").symbol());
        assertEquals("JsonImport", registry.requireSymbol("JsonImport").symbol());
        assertEquals("CsvValidator", registry.requireSymbol("CsvValidator").symbol());
        assertEquals("JsonValidator", registry.requireSymbol("JsonValidator").symbol());
        assertEquals("Gpkg2Shp", registry.requireSymbol("Gpkg2Shp").symbol());
        assertEquals("GpkgImport", registry.requireSymbol("GpkgImport").symbol());
        assertEquals("GpkgExport", registry.requireSymbol("GpkgExport").symbol());
        assertEquals("GpkgValidator", registry.requireSymbol("GpkgValidator").symbol());
        assertEquals("ShpImport", registry.requireSymbol("ShpImport").symbol());
        assertEquals("ShpExport", registry.requireSymbol("ShpExport").symbol());
        assertEquals("ShpValidator", registry.requireSymbol("ShpValidator").symbol());
    }
}
