package ch.so.agi.gretl.tasks.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TabularTaskTest {
    @TempDir
    Path root;

    private String url;

    @BeforeEach
    void database() throws Exception {
        url = "jdbc:h2:mem:w5_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1";
        execute("create table colors (id int primary key, name varchar(100))");
        execute("create table documents (document clob)");
    }

    @Test
    void convertsCsvToExcel() throws Exception {
        Path csv = Files.writeString(root.resolve("colors.csv"), """
                id;name
                1;red
                2;blue
                """, StandardCharsets.UTF_8);
        Path xlsx = root.resolve("colors.xlsx");
        Csv2ExcelOperation.Csv2ExcelBuilder builder =
                new Csv2ExcelOperation.Csv2ExcelBuilder();
        builder.setCsvFile(csv).setOutputFile(xlsx);

        Csv2ExcelOperation.Csv2ExcelResult result =
                new Csv2ExcelOperation.Csv2ExcelHandler()
                        .execute(builder.build(), context());

        assertEquals(xlsx, result.file());
        assertEquals(2, result.rows());
        try (InputStream input = Files.newInputStream(xlsx);
             Workbook workbook = WorkbookFactory.create(input)) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("id", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("red", sheet.getRow(1).getCell(1).getStringCellValue());
        }
    }

    @Test
    void importsAndExportsCsv() throws Exception {
        Path csv = Files.writeString(root.resolve("import.csv"), """
                id;name
                1;red
                2;blue
                """, StandardCharsets.UTF_8);
        CsvImportOperation.CsvImportBuilder importer =
                new CsvImportOperation.CsvImportBuilder();
        importer.setDatabase("db").setDataFile(csv).setTableName("colors");
        assertEquals(2, new CsvImportOperation.CsvImportHandler()
                .execute(importer.build(), context()).rows());
        assertEquals(2, scalar("select count(*) from colors"));

        Path output = root.resolve("export.csv");
        CsvExportOperation.CsvExportBuilder exporter =
                new CsvExportOperation.CsvExportBuilder();
        exporter.setDatabase("db").setDataFile(output).setTableName("colors");
        CsvExportOperation.CsvExportResult result =
                new CsvExportOperation.CsvExportHandler()
                        .execute(exporter.build(), context());
        assertEquals(2, result.rows());
        CsvSupport.ParsedCsv exported = CsvSupport.read(output, "UTF-8", ";", "\"", true);
        assertEquals(List.of("ID", "NAME"), exported.headers());
        assertEquals(2, exported.records().size());
    }

    @Test
    void importsJsonArrayAndDeletesExistingRows() throws Exception {
        Path json = Files.writeString(root.resolve("documents.json"), """
                [{"a":1},{"b":2}]
                """, StandardCharsets.UTF_8);
        JsonImportOperation.JsonImportBuilder builder =
                new JsonImportOperation.JsonImportBuilder();
        builder.setDatabase("db").setJsonFile(json)
                .setQualifiedTableName("documents").setColumnName("document")
                .setDeleteAllRows(true);
        assertEquals(2, new JsonImportOperation.JsonImportHandler()
                .execute(builder.build(), context()).rows());
        assertEquals(2, scalar("select count(*) from documents"));
    }

    @Test
    void validatesCsvStructureAndHeader() throws Exception {
        Path valid = Files.writeString(root.resolve("valid.csv"), """
                id;name
                1;red
                """, StandardCharsets.UTF_8);
        CsvValidatorOperation.CsvValidatorBuilder builder =
                new CsvValidatorOperation.CsvValidatorBuilder();
        builder.setDataFiles(List.of(valid));
        CsvValidatorOperation.CsvValidatorResult result =
                new CsvValidatorOperation.CsvValidatorHandler()
                        .execute(builder.build(), context());
        assertTrue(result.valid());

        Path invalid = Files.writeString(root.resolve("invalid.csv"), """
                id;id
                1;red;extra
                """, StandardCharsets.UTF_8);
        CsvValidatorOperation.CsvValidatorBuilder bad =
                new CsvValidatorOperation.CsvValidatorBuilder();
        bad.setDataFiles(List.of(invalid)).setFailOnError(false);
        CsvValidatorOperation.CsvValidatorResult invalidResult =
                new CsvValidatorOperation.CsvValidatorHandler()
                        .execute(bad.build(), context());
        assertFalse(invalidResult.valid());
        assertFalse(invalidResult.errors().isEmpty());
    }

    @Test
    void validatesJsonSyntax() throws Exception {
        Path valid = Files.writeString(root.resolve("valid.json"), "{\"a\":1}",
                StandardCharsets.UTF_8);
        JsonValidatorOperation.JsonValidatorBuilder builder =
                new JsonValidatorOperation.JsonValidatorBuilder();
        builder.setDataFiles(List.of(valid));
        assertTrue(new JsonValidatorOperation.JsonValidatorHandler()
                .execute(builder.build(), context()).valid());

        Path invalid = Files.writeString(root.resolve("invalid.json"), "{not-json",
                StandardCharsets.UTF_8);
        JsonValidatorOperation.JsonValidatorBuilder bad =
                new JsonValidatorOperation.JsonValidatorBuilder();
        bad.setDataFiles(List.of(invalid)).setFailOnError(false);
        assertFalse(new JsonValidatorOperation.JsonValidatorHandler()
                .execute(bad.build(), context()).valid());
        assertThrows(TabularTaskException.class,
                () -> new JsonValidatorOperation.JsonValidatorHandler()
                        .execute(new JsonValidatorOperation.JsonValidatorBuilder()
                                .setDataFiles(List.of(invalid)).build(), context()));
    }

    private TestOperationContext context() {
        return new TestOperationContext(root, Map.of(),
                Map.of("db", Map.of("jdbcUrl", url, "user", "sa")), Map.of());
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int scalar(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
