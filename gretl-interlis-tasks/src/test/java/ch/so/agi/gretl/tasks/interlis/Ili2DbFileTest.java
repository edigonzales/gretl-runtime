package ch.so.agi.gretl.tasks.interlis;

import ch.so.agi.gretl.tasks.interlis.ili2db.Ili2DbOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Ili2DbFileTest {
    @TempDir Path work;

    @Test
    void importsIntoGeoPackage() throws Exception {
        Path db = work.resolve("test.gpkg");
        Path modelDir = fixture("Beispiel2.ili").getParent();
        Path input = fixture("Beispiel2a.xtf");
        Path datasetFile = Files.writeString(work.resolve("DatasetA.txt"), "A");
        run(Ili2DbOperations.gpkgImport(), b -> {
            b.setDatabaseFile(db);
            b.setModelNames(List.of("Beispiel2"));
            b.setModelDirectories(List.of(modelDir.toString()));
            b.setDefaultSrsCode("2056");
            b.setDataFiles(List.of(input));
        });
        assertTrue(Files.isRegularFile(db));
    }

    @Test
    void runsDuckDbSchemaImportAndExport() throws Exception {
        Path db = work.resolve("test.duckdb");
        Path modelDir = fixture("Beispiel2.ili").getParent();
        Path input = fixture("Beispiel2a.xtf");
        Path datasetFile = Files.writeString(work.resolve("DatasetA.txt"), "A");
        run(Ili2DbOperations.duckdbSchema(), b -> {
            b.setDatabaseFile(db);
            b.setModelNames(List.of("Beispiel2"));
            b.setModelDirectories(List.of(modelDir.toString()));
            b.setDefaultSrsCode("2056");
            b.setCreateBasketCol(true);
            b.setCreateTidCol(true);
        });
        run(Ili2DbOperations.duckdbImport(), b -> {
            b.setDatabaseFile(db);
            b.setModelNames(List.of("Beispiel2"));
            b.setModelDirectories(List.of(modelDir.toString()));
            b.setDataFiles(List.of(input));
            b.setDatasetFiles(List.of(datasetFile));
            b.setImportTid(true);
        });
        Path output = work.resolve("out.xtf");
        run(Ili2DbOperations.duckdbExport(), b -> {
            b.setDatabaseFile(db);
            b.setDataFiles(List.of(output));
            b.setDatasetNames(List.of("DatasetA"));
        });
        assertTrue(Files.isRegularFile(output));
        try (var connection = DriverManager.getConnection("jdbc:duckdb:" + db);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("select count(*) from t_ili2db_dataset")) {
            rows.next();
            assertEquals(1, rows.getInt(1));
        }
    }

    private void run(OperationType<Ili2DbOperations.Request, Ili2DbOperations.Result> type,
                     java.util.function.Consumer<Ili2DbOperations.Builder> configure) throws Exception {
        Ili2DbOperations.Builder builder = new Ili2DbOperations.Builder();
        configure.accept(builder);
        OperationHandler<Ili2DbOperations.Request, Ili2DbOperations.Result> handler =
                type.handlerFactory().get();
        handler.execute(builder.build(), context());
    }

    private Path fixture(String name) throws Exception {
        return Path.of(getClass().getResource("/fixtures/interlis/" + name).toURI());
    }

    private TestOperationContext context() {
        return new TestOperationContext(work, Map.of(), Map.of(), Map.of());
    }
}
