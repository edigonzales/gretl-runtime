package ch.so.agi.gretl.tasks.formats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import org.junit.jupiter.api.Test;

class InterlisTaskProviderTest {
    @Test
    void serviceLoaderExposesAllFormatTasks() {
        OperationRegistry registry = OperationRegistry.load(getClass().getClassLoader());
        assertEquals(27, registry.all().size());
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
        assertEquals("Ili2pgImportSchema", registry.requireSymbol("Ili2pgImportSchema").symbol());
        assertEquals("Ili2pgImport", registry.requireSymbol("Ili2pgImport").symbol());
        assertEquals("Ili2pgExport", registry.requireSymbol("Ili2pgExport").symbol());
        assertEquals("Ili2pgDelete", registry.requireSymbol("Ili2pgDelete").symbol());
        assertEquals("Ili2pgReplace", registry.requireSymbol("Ili2pgReplace").symbol());
        assertEquals("Ili2pgUpdate", registry.requireSymbol("Ili2pgUpdate").symbol());
        assertEquals("Ili2pgValidate", registry.requireSymbol("Ili2pgValidate").symbol());
        assertEquals("Ili2gpkgImport", registry.requireSymbol("Ili2gpkgImport").symbol());
        assertEquals("Ili2duckdbImportSchema", registry.requireSymbol("Ili2duckdbImportSchema").symbol());
        assertEquals("Ili2duckdbImport", registry.requireSymbol("Ili2duckdbImport").symbol());
        assertEquals("Ili2duckdbExport", registry.requireSymbol("Ili2duckdbExport").symbol());
        assertEquals("Av2ch", registry.requireSymbol("Av2ch").symbol());
        assertEquals("Av2geobau", registry.requireSymbol("Av2geobau").symbol());
        assertEquals("Gpkg2Dxf", registry.requireSymbol("Gpkg2Dxf").symbol());
    }
}
