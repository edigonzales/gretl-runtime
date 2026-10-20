package ch.so.agi.gretl.tasks.formats;

import ch.so.agi.gretl.runtime.api.OperationProvider;
import ch.so.agi.gretl.runtime.api.OperationType;
import java.util.Collection;
import java.util.List;

/** Standard format and INTERLIS validation task catalog. */
public final class InterlisTaskProvider implements OperationProvider {
    @Override public Collection<OperationType<?, ?>> operations() {
        return List.of(
                Csv2ExcelOperation.type(),
                CsvImportOperation.type(),
                CsvExportOperation.type(),
                JsonImportOperation.type(),
                Gpkg2ShpOperation.type(),
                GpkgImportOperation.type(),
                GpkgExportOperation.type(),
                ShpImportOperation.type(),
                ShpExportOperation.type(),
                ValidatorOperations.csv(),
                ValidatorOperations.json(),
                ValidatorOperations.gpkg(),
                ValidatorOperations.shp(),
                Ili2DbOperations.pgSchema(),
                Ili2DbOperations.pgImport(),
                Ili2DbOperations.pgReplace(),
                Ili2DbOperations.pgUpdate(),
                Ili2DbOperations.pgExport(),
                Ili2DbOperations.pgDelete(),
                Ili2DbOperations.pgValidate(),
                Ili2DbOperations.gpkgImport(),
                Ili2DbOperations.duckdbSchema(),
                Ili2DbOperations.duckdbImport(),
                Ili2DbOperations.duckdbExport(),
                AvOperations.av2ch(),
                AvOperations.av2geobau(),
                AvOperations.gpkg2dxf());
    }
}
