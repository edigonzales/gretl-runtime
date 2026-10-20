package ch.so.agi.gretl.tasks.formats;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.internal.interlis.InterlisTaskException;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterlisValidatorTest {
    @TempDir Path work;

    @Test
    void csvValidatorAcceptsValidAndRejectsInvalidModelData() throws Exception {
        ValidatorOperations.Result csv = run(ValidatorOperations.csv(), "CsvModel",
                "CsvValidator/data1.csv", "CsvValidator/CsvModel.ili", false, false);
        assertTrue(csv.valid());
        assertFalse(run(ValidatorOperations.csv(), "CsvModel",
                "CsvValidatorFail/dataFail.csv", "CsvValidatorFail/CsvModel.ili", false, false).valid());
    }

    @Test
    void jsonValidatorAcceptsValidAndRejectsInvalidModelData() throws Exception {
        assertTrue(run(ValidatorOperations.json(), "Test2",
                "JsonValidatorOk/structAttrList.json", "JsonValidatorOk/Test2.ili", false).valid());
        assertFalse(run(ValidatorOperations.json(), "Test2",
                "JsonValidatorFail/structAttrList.json", "JsonValidatorFail/Test2.ili", false).valid());
    }

    @Test
    void shpValidatorAcceptsValidAndRejectsInvalidModelData() throws Exception {
        assertTrue(run(ValidatorOperations.shp(), "ShpModel",
                "ShpValidator/data.shp", "ShpValidator/ShpModel.ili", false).valid());
        assertFalse(run(ValidatorOperations.shp(), "ShpModel",
                "ShpValidatorFail/data.shp", "ShpValidatorFail/ShpModel.ili", false).valid());
    }

    @Test
    void gpkgValidatorAcceptsValidAndRejectsInvalidModelData() throws Exception {
        assertTrue(run(ValidatorOperations.gpkg(), "GpkgModel",
                "GpkgValidator/attributes.gpkg", "GpkgValidator/GpkgModelAttrs.ili", true).valid());
        assertFalse(run(ValidatorOperations.gpkg(), "GpkgModel",
                "GpkgValidatorFail/attributes.gpkg", "GpkgValidatorFail/GpkgModelAttrs.ili", true).valid());
    }

    @Test
    void failOnErrorTrueThrowsForInvalidData() throws Exception {
        Path fixture = fixture("CsvValidatorFail/dataFail.csv");
        ValidatorBuilder builder = new ValidatorBuilder();
        builder.setDataFiles(List.of(fixture));
        builder.setModelNames(List.of("CsvModel"));
        builder.setModelDirectories(List.of(fixture.getParent().getParent()
                .resolve("CsvValidatorFail").toString()));
        OperationHandler<ValidatorRequest, ValidatorOperations.Result> handler =
                ValidatorOperations.csv().handlerFactory().get();
        assertThrows(InterlisTaskException.class,
                () -> handler.execute(builder.build(), context()));
    }

    private ValidatorOperations.Result run(OperationType<ValidatorRequest, ValidatorOperations.Result> type,
                                           String model, String data, String ili,
                                           boolean gpkg) throws Exception {
        return run(type, model, data, ili, gpkg, true);
    }

    private ValidatorOperations.Result run(OperationType<ValidatorRequest, ValidatorOperations.Result> type,
                                           String model, String data, String ili,
                                           boolean gpkg, boolean header) throws Exception {
        Path file = fixture(data);
        Path modelFile = fixture(ili);
        ValidatorBuilder builder = new ValidatorBuilder();
        builder.setDataFiles(List.of(file));
        builder.setModelNames(List.of(model));
        builder.setModelDirectories(List.of(modelFile.getParent().toString()));
        builder.setFailOnError(false);
        builder.setLogFile(work.resolve(type.symbol() + ".log"));
        builder.setFirstLineIsHeader(header);
        if (gpkg) {
            builder.setTableName("Attributes");
        }
        return type.handlerFactory().get().execute(builder.build(), context());
    }

    private Path fixture(String relative) throws Exception {
        return Path.of(getClass().getResource("/fixtures/iox-wkf/" + relative).toURI());
    }

    private TestOperationContext context() {
        return new TestOperationContext(work, Map.of(), Map.of(), Map.of());
    }
}
