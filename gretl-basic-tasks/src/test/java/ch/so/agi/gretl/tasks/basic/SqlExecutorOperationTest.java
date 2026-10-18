package ch.so.agi.gretl.tasks.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlExecutorOperationTest {
    @TempDir
    Path tempDir;
    private String jdbcUrl;

    @BeforeEach
    void database() throws Exception {
        jdbcUrl = "jdbc:h2:mem:gretl_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE demo (id INT PRIMARY KEY, txt VARCHAR(100), "
                    + "flag BOOLEAN, amount DECIMAL(10,2))");
        }
    }

    @Test
    void preparedStatementsBindApostropheNullAndNumbersAndCommit() throws Exception {
        SqlExecutorOperation.SqlBuilder builder = new SqlExecutorOperation.SqlBuilder();
        builder.setDatabase("db");
        builder.setStatements(List.of(
                new PreparedStatementSpec("INSERT INTO demo(id,txt,flag,amount) VALUES (?,?,?,?)",
                        java.util.Arrays.asList(1, "O'Brien", null, new BigDecimal("12.50"))),
                new PreparedStatementSpec("INSERT INTO demo(id,txt,flag,amount) VALUES (?,?,?,?)",
                        java.util.Arrays.asList(2, null, true, null))));
        SqlResult result = new SqlExecutorOperation.SqlHandler()
                .execute(builder.build(), context(Map.of(), Map.of()));

        assertEquals(2, result.executionCount());
        assertEquals(List.of(1L), result.executions().get(0).updateCounts());
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT txt, flag, amount FROM demo ORDER BY id")) {
            assertTrue(rows.next());
            assertEquals("O'Brien", rows.getString(1));
            assertEquals(null, rows.getObject(2));
            assertEquals(new BigDecimal("12.50"), rows.getBigDecimal(3));
            assertTrue(rows.next());
            assertEquals(null, rows.getString(1));
            assertEquals(true, rows.getBoolean(2));
        }
    }

    @Test
    void secondStatementFailureRollsBackFirstAndKeepsSafeDetail() throws Exception {
        SqlExecutorOperation.SqlBuilder builder = new SqlExecutorOperation.SqlBuilder();
        builder.setDatabase("db");
        builder.setStatements(List.of(
                new PreparedStatementSpec("INSERT INTO demo(id,txt) VALUES (1,'first')", List.of()),
                new PreparedStatementSpec("INSERT INTO no_such_table(id) VALUES (2)", List.of())));
        SqlTaskException exception = assertThrows(SqlTaskException.class,
                () -> new SqlExecutorOperation.SqlHandler()
                        .execute(builder.build(), context(Map.of(), Map.of())));

        assertTrue(exception.getMessage().contains("SQL"));
        assertTrue(exception.getMessage().contains("db"));
        assertFalse(exception.getMessage().contains("no_such_table"));
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM demo")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }

    @Test
    void scriptExecutesAsOneTrustedScriptAndCountsUpdates() throws Exception {
        Path script = tempDir.resolve("insert.sql");
        Files.writeString(script, "INSERT INTO demo(id,txt) VALUES (3,'semi;colon')",
                StandardCharsets.UTF_8);
        SqlExecutorOperation.SqlBuilder builder = new SqlExecutorOperation.SqlBuilder();
        builder.setDatabase("db");
        builder.setScripts(List.of(script));
        SqlResult result = new SqlExecutorOperation.SqlHandler()
                .execute(builder.build(), context(Map.of(), Map.of()));

        assertEquals(1, result.executionCount());
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT txt FROM demo WHERE id=3")) {
            assertTrue(rows.next());
            assertEquals("semi;colon", rows.getString(1));
        }
    }

    @Test
    void resultSetsAreConsumedAndClosed() throws Exception {
        SqlExecutorOperation.SqlBuilder builder = new SqlExecutorOperation.SqlBuilder();
        builder.setDatabase("db");
        builder.setStatements(List.of(
                new PreparedStatementSpec("SELECT * FROM demo", List.of())));
        SqlResult result = new SqlExecutorOperation.SqlHandler()
                .execute(builder.build(), context(Map.of(), Map.of()));
        assertEquals(1, result.executions().get(0).resultSetCount());
    }

    @Test
    void passwordSecretAndConfigErrorsAreHandledWithoutLeakingValues() throws Exception {
        SqlExecutorOperation.SqlBuilder builder = new SqlExecutorOperation.SqlBuilder();
        builder.setDatabase("db");
        builder.setStatements(List.of(new PreparedStatementSpec(
                "SELECT COUNT(*) FROM demo", List.of())));
        SqlResult result = new SqlExecutorOperation.SqlHandler().execute(builder.build(),
                context(Map.of("user", "sa"), Map.of("passwordSecret", "dbSecret")));
        assertEquals(1, result.executionCount());

        SqlExecutorOperation.SqlBuilder unknown = new SqlExecutorOperation.SqlBuilder();
        unknown.setDatabase("db");
        unknown.setStatements(List.of(new PreparedStatementSpec(
                "SELECT COUNT(*) FROM demo", List.of())));
        assertThrows(SqlTaskException.class, () -> new SqlExecutorOperation.SqlHandler()
                .execute(unknown.build(), context(Map.of("user", "sa",
                        "password", "leak"), Map.of())));
    }

    @Test
    void builderRequiresExactlyOneNonEmptyList() {
        SqlExecutorOperation.SqlBuilder empty = new SqlExecutorOperation.SqlBuilder();
        empty.setDatabase("db");
        assertThrows(IllegalArgumentException.class, empty::build);

        SqlExecutorOperation.SqlBuilder both = new SqlExecutorOperation.SqlBuilder();
        both.setDatabase("db");
        both.setScripts(List.of(tempDir.resolve("a.sql")));
        both.setStatements(List.of(new PreparedStatementSpec("SELECT 1", List.of())));
        assertThrows(IllegalArgumentException.class, both::build);
    }

    @Test
    void missingScriptAndConnectionFailureAreSafeErrors() throws Exception {
        SqlExecutorOperation.SqlBuilder missing = new SqlExecutorOperation.SqlBuilder();
        missing.setDatabase("db");
        missing.setScripts(List.of(tempDir.resolve("missing.sql")));
        assertThrows(SqlTaskException.class, () -> new SqlExecutorOperation.SqlHandler()
                .execute(missing.build(), context(Map.of(), Map.of())));

        SqlExecutorOperation.SqlBuilder bad = new SqlExecutorOperation.SqlBuilder();
        bad.setDatabase("db");
        bad.setStatements(List.of(new PreparedStatementSpec("SELECT 1", List.of())));
        assertThrows(SqlTaskException.class, () -> new SqlExecutorOperation.SqlHandler()
                .execute(bad.build(), context(Map.of("jdbcUrl",
                        "jdbc:h2:tcp://127.0.0.1:1/nope"), Map.of())));
    }

    private TestOperationContext context(Map<String, Object> configExtra,
                                         Map<String, String> secrets) {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("jdbcUrl", jdbcUrl);
        config.put("user", "sa");
        config.putAll(configExtra);
        return new TestOperationContext(tempDir, Map.of(),
                Map.of("db", config), secrets);
    }
}
