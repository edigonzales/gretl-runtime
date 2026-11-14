package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
@org.junit.jupiter.api.Tag("docker")
class PostgresSqlExecutorIT {
    @TempDir
    Path root;

    private PostgresFixture.Lease lease;

    @BeforeEach
    void setUp() throws Exception {
        lease = PostgresFixture.lease();
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("create table demo (id int primary key, txt text, "
                    + "flag boolean, amount numeric(10,2))");
        }
    }

    @AfterEach
    void tearDown() {
        if (lease != null) {
            lease.close();
        }
    }

    @Test
    void executesScriptWithSemicolonDollarQuoteAndPreparedNull() throws Exception {
        Path script = root.resolve("insert.sql");
        Files.writeString(script, """
                INSERT INTO demo(id,txt) VALUES (1,'semi;colon');
                INSERT INTO demo(id,txt) VALUES (2,$$dollar;quote$$);
                """, StandardCharsets.UTF_8);
        Map<String, Object> config = config();
        JobRunner.Result scriptRun = JobRunner.run(root, """
                task('execute', SqlExecutor) {
                    database = 'db'
                    scripts = [file('insert.sql')]
                }
                defaults 'execute'
                """, config);
        assertEquals(0, scriptRun.code(), scriptRun.err());

        JobRunner.Result preparedRun = JobRunner.run(root, """
                task('prepared', SqlExecutor) {
                    database = 'db'
                    statements = [[
                        sql: 'INSERT INTO demo(id,txt,flag,amount) VALUES (?,?,?,?)',
                        parameters: [3, "O'Brien", null, 9.99]
                    ]]
                }
                defaults 'prepared'
                """, config);
        assertEquals(0, preparedRun.code(), preparedRun.err());

        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT txt, flag, amount FROM demo WHERE id=3")) {
            assertTrue(rows.next());
            assertEquals("O'Brien", rows.getString(1));
            assertNull(rows.getObject(2));
            assertEquals(new java.math.BigDecimal("9.99"), rows.getBigDecimal(3));
        }
        assertFalse(Files.readString(scriptRun.report()).contains("gretl_password"));
    }

    @Test
    void failedSecondScriptRollsBackTheWholeInvocation() throws Exception {
        Files.writeString(root.resolve("first.sql"),
                "INSERT INTO demo(id,txt) VALUES (10,'first')", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("second.sql"),
                "INSERT INTO missing_table(id) VALUES (11)", StandardCharsets.UTF_8);
        JobRunner.Result result = JobRunner.run(root, """
                task('execute', SqlExecutor) {
                    database = 'db'
                    scripts = [file('first.sql'), file('second.sql')]
                }
                defaults 'execute'
                """, config());

        assertEquals(10, result.code(), result.out() + result.err());
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COUNT(*) FROM demo WHERE id=10")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }

    @Test
    void commitsEachParameterSetAndStopsAfterFailure() throws Exception {
        Files.writeString(root.resolve("first.SQL"), """
                /* Outer ; /* nested ; */ end */
                INSERT INTO ${schema}.demo(id, txt) VALUES (${first}, '${label};first');
                DO $body$ BEGIN
                    UPDATE ${schema}.demo SET txt = txt || ';do' WHERE id = ${first};
                END $body$;
                """);
        Files.writeString(root.resolve("second.sql"),
                "INSERT INTO ${schema}.demo(id, txt) VALUES (${second}, '${label};second');");
        JobRunner.Result run = JobRunner.run(root, """
                task('execute', SqlExecutor) {
                    database = 'db'
                    sqlFiles = [file('first.SQL'), file('second.sql')]
                    sqlParameters = [
                        [schema: '%1$s', first: '1', second: '2', label: 'one$1'],
                        [schema: '%1$s', first: '3', second: '2', label: 'two'],
                        [schema: '%1$s', first: '4', second: '5', label: 'three']
                    ]
                }
                defaults 'execute'
                """.formatted(lease.schema()), config());
        assertEquals(10, run.code(), run.out() + run.err());
        String diagnostics = run.out() + run.err() + Files.readString(run.report());
        assertTrue(diagnostics.contains("second.sql"), diagnostics);
        assertTrue(diagnostics.contains("23505"), diagnostics);
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, txt FROM demo ORDER BY id")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
            assertEquals("one$1;first;do", rows.getString(2));
            assertTrue(rows.next());
            assertEquals(2, rows.getInt(1));
            assertEquals("one$1;second", rows.getString(2));
            assertFalse(rows.next());
        }
    }

    @Test
    void masksKnownDatabaseSecretInResultRows() throws Exception {
        Files.writeString(root.resolve("diagnose.sql"),
                "SELECT '" + lease.password().replace("'", "''") + "' || '-diagnose';");
        JobRunner.Result run = JobRunner.run(root, """
                task('execute', SqlExecutor) {
                    database = 'db'
                    scripts = [file('diagnose.sql')]
                }
                defaults 'execute'
                """, config());
        assertEquals(0, run.code());
        String logs = run.out() + run.err();
        assertTrue(logs.contains("-diagnose"));
        assertFalse(logs.contains(lease.password()));
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
