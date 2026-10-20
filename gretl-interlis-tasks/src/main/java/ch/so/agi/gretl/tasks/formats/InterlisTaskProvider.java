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
                ValidatorOperations.shp());
    }
}
