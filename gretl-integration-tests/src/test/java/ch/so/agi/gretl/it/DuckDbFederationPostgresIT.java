package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
class DuckDbFederationPostgresIT {
    @TempDir
    Path root;

    private PostgresFixture.Lease lease;

    @BeforeEach
    void setUp() throws Exception {
        lease = PostgresFixture.lease();
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("create table source_colors (id int primary key, name text)");
            statement.execute("insert into source_colors values (1,'red'), (2,'blue')");
        }
    }

    @AfterEach
    void tearDown() {
        if (lease != null) {
            lease.close();
        }
    }

    @Test
    void readsPostgresSourceAndExportsIntoPostgresTarget() throws Exception {
        Files.writeString(root.resolve("copy.sql"), """
                create schema if not exists result;
                create table result.records as
                    select * from src.colors order by id;
                """, StandardCharsets.UTF_8);
        JobRunner.Result result = JobRunner.run(root, """
                task('federation', DuckDbSqlExecutor) {
                    inMemory = true
                    installExtensions = true
                    sqlFiles = [file('copy.sql')]
                    sources = [[
                        type: 'postgres',
                        alias: 'src',
                        database: 'db',
                        tables: [[table: '%s.source_colors', alias: 'colors']]
                    ]]
                    targets = [[type: 'postgres', alias: 'tgt', database: 'db']]
                    exports = [[
                        type: 'postgres',
                        name: 'out',
                        target: 'tgt',
                        table: '%s.target_colors',
                        query: 'select * from result.records',
                        mode: 'replace',
                        create: true
                    ]]
                }
                defaults 'federation'
                """.formatted(lease.schema(), lease.schema()), config());

        assertEquals(0, result.code(), result.err());
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select id, name from target_colors order by id")) {
            rows.next();
            assertEquals(1, rows.getInt(1));
            assertEquals("red", rows.getString(2));
            rows.next();
            assertEquals(2, rows.getInt(1));
            assertEquals("blue", rows.getString(2));
        }
    }


    @Test
    void appendsAndTruncatesPostgresExport() throws Exception {
        Files.writeString(root.resolve("noop.sql"), "select 1;", StandardCharsets.UTF_8);
        JobRunner.Result append = JobRunner.run(root, """
                task('append', DuckDbSqlExecutor) {
                    inMemory = true
                    installExtensions = true
                    sqlFiles = [file('noop.sql')]
                    targets = [[type: 'postgres', alias: 'tgt', database: 'db']]
                    exports = [[
                        type: 'postgres',
                        name: 'out',
                        target: 'tgt',
                        table: '%s.target_colors',
                        query: "select 3 as id, 'green' as name",
                        mode: 'append',
                        create: true
                    ]]
                }
                defaults 'append'
                """.formatted(lease.schema()), config());
        assertEquals(0, append.code(), append.err());

        JobRunner.Result truncate = JobRunner.run(root, """
                task('truncate', DuckDbSqlExecutor) {
                    inMemory = true
                    installExtensions = true
                    sqlFiles = [file('noop.sql')]
                    targets = [[type: 'postgres', alias: 'tgt', database: 'db']]
                    exports = [[
                        type: 'postgres',
                        name: 'out',
                        target: 'tgt',
                        table: '%s.target_colors',
                        query: "select 4 as id, 'yellow' as name",
                        mode: 'truncate'
                    ]]
                }
                defaults 'truncate'
                """.formatted(lease.schema()), config());
        assertEquals(0, truncate.code(), truncate.err());

        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select id, name from target_colors order by id")) {
            rows.next();
            assertEquals(4, rows.getInt(1));
            assertEquals("yellow", rows.getString(2));
            assertEquals(false, rows.next());
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
