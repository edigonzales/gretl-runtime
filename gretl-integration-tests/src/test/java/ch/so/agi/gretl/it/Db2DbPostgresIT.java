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


    @Test
    void copiesBetweenIndependentPostgresConnections() throws Exception {
        try (PostgresFixture.Lease target = PostgresFixture.lease()) {
            try (Connection connection = connection(target);
                 Statement statement = connection.createStatement()) {
                statement.execute("create table colors_copy (id int primary key, name text)");
            }
            Files.writeString(root.resolve("independent.sql"),
                    "SELECT id, name FROM colors ORDER BY id", StandardCharsets.UTF_8);
            JobRunner.Result result = JobRunner.run(root, """
                    task('copy', Db2Db) {
                        sourceDatabase = 'source'
                        targetDatabase = 'target'
                        transfers = [[
                            sqlFile: file('independent.sql'),
                            targetTable: 'colors_copy'
                        ]]
                    }
                    defaults 'copy'
                    """, config(lease, target));

            assertEquals(0, result.code(), result.err());
            try (Connection connection = connection(target);
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(
                         "SELECT COUNT(*) FROM colors_copy")) {
                rows.next();
                assertEquals(2, rows.getInt(1));
            }
        }
    }

    @Test
    void parameterSetFailureRollsBackAllSets() throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM colors_copy");
        }
        Files.writeString(root.resolve("parameterized.sql"),
                "SELECT id, name FROM colors WHERE id = ${id}",
                StandardCharsets.UTF_8);
        JobRunner.Result result = JobRunner.run(root, """
                task('copy', Db2Db) {
                    sourceDatabase = 'db'
                    targetDatabase = 'db'
                    transfers = [[
                        sqlFile: file('parameterized.sql'),
                        targetTable: 'colors_copy'
                    ]]
                    sqlParameterSets = [[id: '1'], [id: '1']]
                }
                defaults 'copy'
                """, config());

        assertEquals(10, result.code(), result.out());
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM colors_copy")) {
            rows.next();
            assertEquals(0, rows.getInt(1));
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

    private Map<String, Object> config(PostgresFixture.Lease source,
                                       PostgresFixture.Lease target) throws Exception {
        Map<String, Object> config = JobRunner.baseConfig(root);
        JobRunner.secret(root, config, "dbPassword", source.password());
        Map<String, Object> sourceDb = new LinkedHashMap<>();
        sourceDb.put("jdbcUrl", source.jdbcUrl());
        sourceDb.put("user", source.user());
        sourceDb.put("passwordSecret", "dbPassword");
        JobRunner.taskConfig(config, "source", sourceDb);
        Map<String, Object> targetDb = new LinkedHashMap<>();
        targetDb.put("jdbcUrl", target.jdbcUrl());
        targetDb.put("user", target.user());
        targetDb.put("passwordSecret", "dbPassword");
        JobRunner.taskConfig(config, "target", targetDb);
        return config;
    }

    private Connection connection() throws Exception {
        return connection(lease);
    }

    private Connection connection(PostgresFixture.Lease value) throws Exception {
        return DriverManager.getConnection(value.jdbcUrl(), value.user(), value.password());
    }
}
