package ch.so.agi.gretl.tasks.database;

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

        assertEquals("SQL_STATEMENT", exception.code());
        assertEquals("db", exception.databaseAlias());
        assertFalse(exception.getMessage().contains("no_such_table"));
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM demo")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }

    @Test
    void scriptKeepsQuotedSemicolonAndCountsUpdates() throws Exception {
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

    @Test
    void distinguishesOmittedEmptyAndRepeatedParameterSets() throws Exception {
        Path sql = tempDir.resolve("query.sql");
        Files.writeString(sql, "\uFEFFSELECT '${value}';");
        assertEquals(1, executeFile(sql, Map.of()).executionCount());
        for (Object emptyMap : List.of(Map.of(), List.of(Map.of()))) {
            var failure = assertThrows(SqlTaskException.class,
                    () -> executeFile(sql, Map.of("sqlParameters", emptyMap)));
            assertEquals("SQL_PARAMETER_MISSING", failure.code());
            assertTrue(failure.getMessage().contains("value"));
        }
        for (String field : List.of("sqlParameters", "sqlParameterSets")) {
            var empty = new SqlExecutorOperation.SqlHandler().execute(
                    fileRequest(sql, Map.of(field, List.of())),
                    new TestOperationContext(tempDir, Map.of(), Map.of(), Map.of()));
            assertEquals(0, empty.executionCount());
        }
        var map = new java.util.LinkedHashMap<>(Map.of("value", "Grüezi"));
        var request = fileRequest(sql, Map.of("sqlParameters", map));
        map.put("value", "later");
        assertEquals("Grüezi", request.parameterSets().orElseThrow().getFirst().get("value"));
        Files.writeString(sql, "/* no statement */;");
        assertEquals(1, executeFile(sql, Map.of("sqlParameterSets", List.of(Map.of()))).executionCount());
        assertEquals(2, executeFile(sql, Map.of("sqlParameters", List.of(Map.of(), Map.of()))).executionCount());
    }

    @Test
    void rejectsConflictingAndMalformedTemplateFields() {
        Path sql = tempDir.resolve("query.sql");
        assertThrows(IllegalArgumentException.class,
                () -> fileRequest(sql, Map.of("scripts", List.of())));
        assertThrows(IllegalArgumentException.class, () -> fileRequest(sql,
                Map.of("sqlParameters", Map.of(), "sqlParameterSets", List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> fileRequest(sql, Map.of("sqlParameters", java.util.Arrays.asList((Object) null))));
        assertThrows(IllegalArgumentException.class,
                () -> fileRequest(sql, Map.of("sqlParameters", Map.of("value", 1))));
        assertThrows(IllegalArgumentException.class,
                () -> fileRequest(sql, Map.of("sqlParameterSets", Map.of())));
        assertThrows(IllegalArgumentException.class,
                () -> fileRequest(sql, Map.of("maxLoggedRows", -1)));
    }

    @Test
    void logsRowsAndVisibleTruncationOrDisablesOutput() throws Exception {
        Path sql = tempDir.resolve("query.sql");
        Files.writeString(sql, "SELECT x FROM (VALUES ('first'), ('second')) t(x) ORDER BY x;");
        for (Map<String, Object> options : List.<Map<String, Object>>of(
                Map.of("maxLoggedRows", 1), Map.of("logResults", false), Map.of("maxLoggedRows", 0))) {
            var context = context(Map.of(), Map.of());
            var result = new SqlExecutorOperation.SqlHandler().execute(fileRequest(sql, options), context);
            assertEquals(1, result.executions().getFirst().resultSetCount());
            List<String> rows = context.logs().stream().filter(log -> log.startsWith("gretl.sql.row:")).toList();
            if (options.containsKey("logResults")) {
                assertEquals(List.of(), rows);
            } else if (options.get("maxLoggedRows").equals(0)) {
                assertEquals(List.of("gretl.sql.row: first", "gretl.sql.row: second"), rows);
            } else {
                assertEquals(List.of("gretl.sql.row: first"), rows);
                assertTrue(context.logs().stream().anyMatch(log -> log.startsWith("gretl.sql.rowsTruncated:")));
            }
        }
    }

    @Test
    void executesDuckDbFilesInOrderAndKeepsRepeatedPaths() throws Exception {
        String duckDb = "jdbc:duckdb:" + tempDir.resolve("data.duckdb");
        Path setup = tempDir.resolve("setup.sql");
        Path load = tempDir.resolve("load.sql");
        Files.writeString(setup, "CREATE TABLE sightings AS SELECT 1 AS id, 'POINT (0 0)' AS wkt WHERE FALSE;");
        Files.writeString(load, """
                INSERT INTO sightings SELECT id, '${wkt}'
                FROM (SELECT unnest(generate_series(1, 2)) AS id);
                SELECT count(*) FROM sightings;
                """);
        var builder = new SqlExecutorOperation.SqlBuilder();
        builder.setDatabase("db");
        builder.setScripts(List.of(setup, load, load));
        for (var field : SqlExecutorOperation.type().inputs()) {
            if (field.name().equals("sqlParameters")) {
                assign(builder, field, Map.of("wkt", "POINT (7 47)"));
            }
        }
        SqlResult result = new SqlExecutorOperation.SqlHandler().execute(builder.build(),
                context(Map.of("jdbcUrl", duckDb), Map.of()));
        assertEquals(3, result.executionCount());
        assertEquals(1, result.executions().getLast().resultSetCount());
        try (var connection = DriverManager.getConnection(duckDb);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*), min(wkt), max(wkt) FROM sightings")) {
            assertTrue(rows.next());
            assertEquals(4, rows.getInt(1));
            assertEquals("POINT (7 47)", rows.getString(2));
            assertEquals("POINT (7 47)", rows.getString(3));
        }
    }

    private SqlResult executeFile(Path sql, Map<String, Object> options) {
        return new SqlExecutorOperation.SqlHandler().execute(fileRequest(sql, options), context(Map.of(), Map.of()));
    }

    private static SqlExecutorOperation.SqlRequest fileRequest(Path sql, Map<String, Object> options) {
        var builder = new SqlExecutorOperation.SqlBuilder();
        builder.setDatabase("db");
        builder.setSqlFiles(List.of(sql));
        for (var field : SqlExecutorOperation.type().inputs()) {
            if (options.containsKey(field.name())) {
                assign(builder, field, options.get(field.name()));
            }
        }
        return builder.build();
    }

    private static <T> void assign(SqlExecutorOperation.SqlBuilder builder,
                                   ch.so.agi.gretl.runtime.api.InputField<SqlExecutorOperation.SqlRequest, T> field,
                                   Object value) {
        field.assign().accept(builder, field.convert().apply(value));
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
