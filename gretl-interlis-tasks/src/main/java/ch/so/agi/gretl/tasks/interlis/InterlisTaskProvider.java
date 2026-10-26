package ch.so.agi.gretl.tasks.interlis;

import ch.so.agi.gretl.tasks.interlis.ili2db.Ili2DbOperations;

import ch.so.agi.gretl.tasks.interlis.formats.CsvImportOperation;

import ch.so.agi.gretl.tasks.interlis.av.AvOperations;

import ch.so.agi.gretl.tasks.interlis.formats.Gpkg2ShpOperation;

import ch.so.agi.gretl.tasks.interlis.formats.GpkgExportOperation;

import ch.so.agi.gretl.tasks.interlis.formats.CsvExportOperation;

import ch.so.agi.gretl.tasks.interlis.formats.GpkgImportOperation;

import ch.so.agi.gretl.tasks.interlis.formats.ShpImportOperation;

import ch.so.agi.gretl.tasks.interlis.validation.ValidatorOperations;

import ch.so.agi.gretl.tasks.interlis.formats.Csv2ExcelOperation;

import ch.so.agi.gretl.tasks.interlis.formats.JsonImportOperation;

import ch.so.agi.gretl.tasks.interlis.formats.ShpExportOperation;

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
