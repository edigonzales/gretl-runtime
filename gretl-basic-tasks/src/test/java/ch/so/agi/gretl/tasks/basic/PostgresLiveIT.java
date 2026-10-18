package ch.so.agi.gretl.tasks.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Optional live PostgreSQL evidence; run explicitly with gretl.postgres.* properties. */
class PostgresLiveIT {
    @TempDir
    Path tempDir;
    private String url;
    private String user;
    private String password;

    @BeforeEach
    void setup() throws Exception {
        url = System.getProperty("gretl.postgres.url");
        assumeTrue(url != null, "gretl.postgres.url not set");
        user = System.getProperty("gretl.postgres.user", "gretl");
        password = System.getProperty("gretl.postgres.password", "gretl");
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS demo");
            statement.execute("CREATE TABLE demo (id int primary key, txt text, "
                    + "flag boolean, amount numeric(10,2))");
        }
    }

    @Test
    void fullScriptWithSemicolonAndDollarQuoteAndPreparedNull() throws Exception {
        Path script = tempDir.resolve("script.sql");
        Files.writeString(script, """
                INSERT INTO demo(id,txt) VALUES (1,'semi;colon');
                INSERT INTO demo(id,txt) VALUES (2,$$dollar;quote$$);
                """, StandardCharsets.UTF_8);
        SqlExecutorOperation.SqlBuilder scripts = new SqlExecutorOperation.SqlBuilder();
        scripts.setDatabase("db");
        scripts.setScripts(List.of(script));
        SqlResult result = new SqlExecutorOperation.SqlHandler()
                .execute(scripts.build(), context(Map.of()));
        assertEquals(1, result.executionCount());

        SqlExecutorOperation.SqlBuilder prepared = new SqlExecutorOperation.SqlBuilder();
        prepared.setDatabase("db");
        prepared.setStatements(List.of(new PreparedStatementSpec(
                "INSERT INTO demo(id,txt,flag,amount) VALUES (?,?,?,?)",
                Arrays.asList(3, "O'Brien", null, new BigDecimal("9.99")))));
        new SqlExecutorOperation.SqlHandler().execute(prepared.build(), context(Map.of()));

        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT txt, flag, amount FROM demo WHERE id=3")) {
            assertTrue(rows.next());
            assertEquals("O'Brien", rows.getString(1));
            assertEquals(null, rows.getObject(2));
            assertEquals(new BigDecimal("9.99"), rows.getBigDecimal(3));
        }
    }

    @Test
    void failedSecondScriptRollsBackWholeInvocation() throws Exception {
        Path first = tempDir.resolve("first.sql");
        Files.writeString(first, "INSERT INTO demo(id,txt) VALUES (10,'first')");
        Path second = tempDir.resolve("second.sql");
        Files.writeString(second, "INSERT INTO missing_table(id) VALUES (11)");
        SqlExecutorOperation.SqlBuilder builder = new SqlExecutorOperation.SqlBuilder();
        builder.setDatabase("db");
        builder.setScripts(List.of(first, second));
        SqlTaskException exception = assertThrows(SqlTaskException.class,
                () -> new SqlExecutorOperation.SqlHandler()
                        .execute(builder.build(), context(Map.of())));
        assertTrue(exception.sqlState() != null);
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COUNT(*) FROM demo WHERE id=10")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }

    private TestOperationContext context(Map<String, Object> extra) {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("jdbcUrl", url);
        config.put("user", user);
        config.put("passwordSecret", "dbPassword");
        config.putAll(extra);
        return new TestOperationContext(tempDir, Map.of(), Map.of("db", config),
                Map.of("dbPassword", password));
    }
}
