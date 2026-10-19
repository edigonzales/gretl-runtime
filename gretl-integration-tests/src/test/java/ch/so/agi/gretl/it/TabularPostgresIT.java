package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(300)
class TabularPostgresIT {
    @TempDir
    Path root;

    private PostgresFixture.Lease lease;

    @BeforeEach
    void setUp() throws Exception {
        lease = PostgresFixture.lease();
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("create table csv_colors (id int primary key, name text)");
            statement.execute("create table documents (document text)");
        }
    }

    @AfterEach
    void tearDown() {
        if (lease != null) {
            lease.close();
        }
    }

    @Test
    void importsAndExportsCsvAndImportsJsonAgainstPostgres() throws Exception {
        Files.writeString(root.resolve("colors.csv"), """
                id;name
                1;red
                2;blue
                """, StandardCharsets.UTF_8);
        JobRunner.Result imported = JobRunner.run(root, """
                task('import', CsvImport) {
                    database = 'db'
                    dataFile = file('colors.csv')
                    tableName = 'csv_colors'
                }
                defaults 'import'
                """, config());
        assertEquals(0, imported.code(), imported.err());
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select count(*) from csv_colors")) {
            rows.next();
            assertEquals(2, rows.getInt(1));
        }

        JobRunner.Result exported = JobRunner.run(root, """
                task('export', CsvExport) {
                    database = 'db'
                    dataFile = file('export.csv')
                    tableName = 'csv_colors'
                }
                defaults 'export'
                """, config());
        assertEquals(0, exported.code(), exported.err());
        String csv = Files.readString(root.resolve("export.csv"));
        assertTrue(csv.contains("red"));
        assertTrue(csv.contains("blue"));

        Files.writeString(root.resolve("documents.json"),
                "[{\"a\":1},{\"b\":2}]", StandardCharsets.UTF_8);
        JobRunner.Result json = JobRunner.run(root, """
                task('json', JsonImport) {
                    database = 'db'
                    jsonFile = file('documents.json')
                    qualifiedTableName = 'documents'
                    columnName = 'document'
                    deleteAllRows = true
                }
                defaults 'json'
                """, config());
        assertEquals(0, json.code(), json.err());
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select count(*) from documents")) {
            rows.next();
            assertEquals(2, rows.getInt(1));
        }
    }

    private Map<String, Object> config() throws Exception {
        Map<String, Object> config = JobRunner.baseConfig(root);
        JobRunner.secret(root, config, "dbPassword", lease.password());
        Map<String, Object> db = new LinkedHashMap<>();
        db.put("jdbcUrl", lease.jdbcUrl());
        db.put("user", lease.user());
        db.put("passwordSecret", "dbPassword");
        JobRunner.taskConfig(config, "db", db);
        return config;
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(lease.jdbcUrl(), lease.user(), lease.password());
    }
}
