package ch.so.agi.gretl.tasks.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DuckDbSqlExecutorOperationTest {
    @TempDir
    Path root;

    @Test
    void executesMultipleStatementsInMemory() throws Exception {
        Path sql = Files.writeString(root.resolve("create.sql"), """
                create table colors (id integer, name varchar);
                insert into colors values (1, 'red');
                select * from colors;
                """, StandardCharsets.UTF_8);
        DuckDbSqlExecutorOperation.DuckDbBuilder builder =
                new DuckDbSqlExecutorOperation.DuckDbBuilder();
        builder.setInMemory(true).setSqlFiles(List.of(sql));

        DuckDbSqlExecutorOperation.DuckDbResult result =
                new DuckDbSqlExecutorOperation.DuckDbHandler()
                        .execute(builder.build(), context());

        assertEquals(3, result.executionCount());
        assertEquals(1, result.executions().stream()
                .mapToInt(DuckDbSqlExecutorOperation.DuckDbExecution::resultSets).sum());
    }

    @Test
    void persistsFileDatabaseAndUsesParameterSets() throws Exception {
        Path database = root.resolve("work.duckdb");
        Path create = Files.writeString(root.resolve("create.sql"),
                "create table if not exists colors (id integer);", StandardCharsets.UTF_8);
        Path insert = Files.writeString(root.resolve("insert.sql"),
                "insert into colors values (${id});", StandardCharsets.UTF_8);
        DuckDbSqlExecutorOperation.DuckDbBuilder builder =
                new DuckDbSqlExecutorOperation.DuckDbBuilder();
        builder.setDatabase(database).setSqlFiles(List.of(create, insert));
        builder.setSqlParameterSets(List.of(Map.of("id", "1"), Map.of("id", "2")));

        DuckDbSqlExecutorOperation.DuckDbResult result =
                new DuckDbSqlExecutorOperation.DuckDbHandler()
                        .execute(builder.build(), context());

        assertEquals(4, result.executionCount());
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + database);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("select count(*) from colors")) {
            rows.next();
            assertEquals(2, rows.getInt(1));
        }
    }

    @Test
    void rollsBackAllStatementsOnFailure() throws Exception {
        Path database = root.resolve("rollback.duckdb");
        Path sql = Files.writeString(root.resolve("fail.sql"), """
                create table colors (id integer);
                insert into colors values (1);
                select * from missing_table;
                """, StandardCharsets.UTF_8);
        DuckDbSqlExecutorOperation.DuckDbBuilder builder =
                new DuckDbSqlExecutorOperation.DuckDbBuilder();
        builder.setDatabase(database).setSqlFiles(List.of(sql));

        assertThrows(DuckDbTaskException.class,
                () -> new DuckDbSqlExecutorOperation.DuckDbHandler()
                        .execute(builder.build(), context()));

        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + database);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select count(*) from information_schema.tables where table_name='colors'")) {
            rows.next();
            assertEquals(0, rows.getInt(1));
        }
    }


    @Test
    void readsCsvAndExportsParquet() throws Exception {
        Path csv = Files.writeString(root.resolve("input.csv"), """
                id;name
                1;red
                2;blue
                """, StandardCharsets.UTF_8);
        Path sql = Files.writeString(root.resolve("transform.sql"), """
                create schema if not exists result;
                create table result.records as select * from input.data order by id;
                """, StandardCharsets.UTF_8);
        Path parquet = root.resolve("records.parquet");
        DuckDbSqlExecutorOperation.DuckDbBuilder builder =
                new DuckDbSqlExecutorOperation.DuckDbBuilder();
        builder.setInMemory(true).setSqlFiles(List.of(sql))
                .setSources(List.of(Map.of("type", "csv", "alias", "input",
                        "file", csv, "table", "data", "header", true, "delimiter", ";")))
                .setExports(List.of(Map.of("type", "parquet", "name", "records",
                        "query", "select * from result.records", "file", parquet,
                        "overwrite", true)));

        DuckDbSqlExecutorOperation.DuckDbResult result =
                new DuckDbSqlExecutorOperation.DuckDbHandler()
                        .execute(builder.build(), context());

        assertEquals(List.of(parquet), result.exportFiles());
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select count(*) from read_parquet('" + parquet + "')")) {
            rows.next();
            assertEquals(2, rows.getInt(1));
        }
    }

    @Test
    void readsGpkgAndExportsGpkgAndXlsx() throws Exception {
        Path input = root.resolve("input.gpkg");
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
             Statement statement = connection.createStatement()) {
            statement.execute("install spatial");
            statement.execute("load spatial");
            statement.execute("copy (select 1 as id, 'red' as name, "
                    + "ST_Point(8, 47) as geom) to '" + input
                    + "' with (format gdal, driver 'GPKG', srs '4326')");
        }
        Path sql = Files.writeString(root.resolve("gpkg.sql"), """
                create schema if not exists result;
                create table result.records as select * from input.input;
                """, StandardCharsets.UTF_8);
        Path gpkg = root.resolve("result.gpkg");
        Path xlsx = root.resolve("result.xlsx");
        DuckDbSqlExecutorOperation.DuckDbBuilder builder =
                new DuckDbSqlExecutorOperation.DuckDbBuilder();
        builder.setInMemory(true).setInstallExtensions(true).setSqlFiles(List.of(sql))
                .setSources(List.of(Map.of("type", "gpkg", "alias", "input",
                        "file", input, "layer", "input")))
                .setExports(List.of(
                        Map.of("type", "gpkg", "name", "records", "layer", "records",
                                "query", "select * from result.records",
                                "file", gpkg, "overwrite", true),
                        Map.of("type", "xlsx", "name", "sheet", "sheet", "Records",
                                "query", "select * from result.records",
                                "file", xlsx, "overwrite", true)));

        DuckDbSqlExecutorOperation.DuckDbResult result =
                new DuckDbSqlExecutorOperation.DuckDbHandler()
                        .execute(builder.build(), context());

        assertEquals(List.of(gpkg, xlsx), result.exportFiles());
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
             Statement statement = connection.createStatement()) {
            statement.execute("load spatial");
            try (ResultSet rows = statement.executeQuery(
                    "select count(*) from ST_Read('" + gpkg + "')")) {
                rows.next();
                assertEquals(1, rows.getInt(1));
            }
        }
    }

    @Test
    void validatesDatabaseSelectionAndParameters() throws Exception {
        Path sql = Files.writeString(root.resolve("valid.sql"),
                "select 1;", StandardCharsets.UTF_8);
        DuckDbSqlExecutorOperation.DuckDbBuilder missing =
                new DuckDbSqlExecutorOperation.DuckDbBuilder();
        missing.setSqlFiles(List.of(sql));
        assertThrows(IllegalArgumentException.class, missing::build);

        DuckDbSqlExecutorOperation.DuckDbBuilder both =
                new DuckDbSqlExecutorOperation.DuckDbBuilder();
        both.setInMemory(true).setDatabase(root.resolve("x.duckdb"))
                .setSqlFiles(List.of(sql));
        assertThrows(IllegalArgumentException.class, both::build);

        DuckDbSqlExecutorOperation.DuckDbBuilder params =
                new DuckDbSqlExecutorOperation.DuckDbBuilder();
        params.setInMemory(true).setSqlFiles(List.of(sql))
                .setSqlParameters(Map.of("id", "1"))
                .setSqlParameterSets(List.of(Map.of("id", "2")));
        assertThrows(IllegalArgumentException.class, params::build);
    }


    @Test
    void exportFailureLeavesNoFinalOrTempFile() throws Exception {
        Path sql = Files.writeString(root.resolve("export.sql"), """
                create schema if not exists result;
                create table result.records as select 1 as id;
                """, StandardCharsets.UTF_8);
        Path good = root.resolve("good.parquet");
        Path broken = root.resolve("broken.parquet");
        DuckDbSqlExecutorOperation.DuckDbBuilder builder =
                new DuckDbSqlExecutorOperation.DuckDbBuilder();
        builder.setInMemory(true).setSqlFiles(List.of(sql))
                .setExports(List.of(
                        Map.of("type", "parquet", "name", "good",
                                "query", "select * from result.records",
                                "file", good, "overwrite", true),
                        Map.of("type", "parquet", "name", "broken",
                                "query", "select * from result.missing",
                                "file", broken, "overwrite", true)));

        assertThrows(DuckDbTaskException.class,
                () -> new DuckDbSqlExecutorOperation.DuckDbHandler()
                        .execute(builder.build(), context()));

        assertTrue(Files.notExists(good));
        assertTrue(Files.notExists(broken));
        try (var files = Files.list(root)) {
            assertEquals(0, files.filter(path -> path.getFileName().toString()
                    .contains(".tmp.parquet")).count());
        }
    }

    @Test
    void rejectsUnsupportedSourceConfiguration() throws Exception {
        Path sql = Files.writeString(root.resolve("valid.sql"), "select 1;",
                StandardCharsets.UTF_8);
        DuckDbSqlExecutorOperation.DuckDbBuilder builder =
                new DuckDbSqlExecutorOperation.DuckDbBuilder();
        builder.setInMemory(true).setSqlFiles(List.of(sql))
                .setSources(List.of(Map.of("type", "xml", "alias", "input",
                        "file", sql)));

        assertThrows(DuckDbTaskException.class,
                () -> new DuckDbSqlExecutorOperation.DuckDbHandler()
                        .execute(builder.build(), context()));
    }

    @Test
    void rejectsUnreadableAndNonSqlFiles() {
        DuckDbSqlExecutorOperation.DuckDbBuilder builder =
                new DuckDbSqlExecutorOperation.DuckDbBuilder();
        builder.setInMemory(true).setSqlFiles(List.of(root.resolve("missing.sql")));
        assertThrows(DuckDbTaskException.class,
                () -> new DuckDbSqlExecutorOperation.DuckDbHandler()
                        .execute(builder.build(), context()));
        assertTrue(Files.notExists(root.resolve("missing.sql")));
    }

    private TestOperationContext context() {
        return new TestOperationContext(root, Map.of(), Map.of(), Map.of());
    }
}
