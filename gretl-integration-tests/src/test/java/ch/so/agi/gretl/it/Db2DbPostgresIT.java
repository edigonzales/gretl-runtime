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
class Db2DbPostgresIT {
    @TempDir
    Path root;

    private PostgresFixture.Lease lease;

    @BeforeEach
    void setUp() throws Exception {
        lease = PostgresFixture.lease();
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("create table colors (id int primary key, name text)");
            statement.execute("create table colors_copy (id int primary key, name text)");
            statement.execute("insert into colors values (1,'red'), (2,'blue')");
            statement.execute("insert into colors_copy values (9,'old')");
        }
    }

    @AfterEach
    void tearDown() {
        if (lease != null) {
            lease.close();
        }
    }

    @Test
    void transfersFromPostgresSearchPathIntoAnotherTable() throws Exception {
        Files.writeString(root.resolve("select-colors.sql"), """
                SET search_path TO %s;
                SELECT id, name FROM colors ORDER BY id;
                """.formatted(lease.schema()), StandardCharsets.UTF_8);
        JobRunner.Result result = JobRunner.run(root, """
                task('copy', Db2Db) {
                    sourceDatabase = 'db'
                    targetDatabase = 'db'
                    transfers = [[
                        sqlFile: file('select-colors.sql'),
                        targetTable: 'colors_copy',
                        deleteAllRows: true
                    ]]
                }
                defaults 'copy'
                """, config());

        assertEquals(0, result.code(), result.err());
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, name FROM colors_copy ORDER BY id")) {
            rows.next();
            assertEquals(1, rows.getInt(1));
            assertEquals("red", rows.getString(2));
            rows.next();
            assertEquals(2, rows.getInt(1));
            assertEquals("blue", rows.getString(2));
        }
    }

    @Test
    void convertsWktIntoPostgisGeometry() throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("create table geo_source (id int primary key, wkt text)");
            statement.execute("create table geo_target (id int primary key, "
                    + "geom geometry(Point,4326))");
            statement.execute("insert into geo_source values (1, 'POINT (8 47)')");
        }
        Files.writeString(root.resolve("select-geo.sql"),
                "SELECT id, wkt AS geom FROM geo_source", StandardCharsets.UTF_8);
        JobRunner.Result result = JobRunner.run(root, """
                task('copy', Db2Db) {
                    sourceDatabase = 'db'
                    targetDatabase = 'db'
                    transfers = [[
                        sqlFile: file('select-geo.sql'),
                        targetTable: 'geo_target',
                        deleteAllRows: true,
                        geometryColumns: ['geom:WKT:4326']
                    ]]
                }
                defaults 'copy'
                """, config());

        assertEquals(0, result.code(), result.err());
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT ST_AsText(geom) FROM geo_target WHERE id=1")) {
            rows.next();
            assertEquals("POINT(8 47)", rows.getString(1));
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
