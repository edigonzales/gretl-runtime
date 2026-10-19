package ch.so.agi.gretl.tasks.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

class Db2DbOperationTest {
    @TempDir
    Path root;

    private String sourceUrl;
    private String targetUrl;

    @BeforeEach
    void databases() throws Exception {
        sourceUrl = h2("source");
        targetUrl = h2("target");
        createTable(sourceUrl, "create table colors (id int primary key, name varchar(100))");
        createTable(targetUrl, "create table colors (id int primary key, name varchar(100))");
    }

    @Test
    void copiesRowsAndDeletesTargetRowsInTransaction() throws Exception {
        execute(sourceUrl, "insert into colors values (1, 'red'), (2, 'blue')");
        Path sql = Files.writeString(root.resolve("select-colors.sql"),
                "select id, name from colors order by id", StandardCharsets.UTF_8);
        Db2DbOperation.Db2DbBuilder builder = request();
        builder.setTransfers(List.of(new Db2DbOperation.Db2DbTransfer(sql, "colors", true,
                List.of())));

        Db2DbOperation.Db2DbResult result = new Db2DbOperation.Db2DbHandler()
                .execute(builder.build(), context());

        assertEquals(1, result.executionCount());
        assertEquals(2, result.transferredRows());
        assertEquals(2, count("select count(*) from colors"));
    }

    @Test
    void rollsBackDeleteAndInsertWhenSecondTransferFails() throws Exception {
        execute(sourceUrl, "insert into colors values (1, 'red')");
        execute(targetUrl, "insert into colors values (9, 'old')");
        Path first = Files.writeString(root.resolve("first.sql"),
                "select id, name from colors", StandardCharsets.UTF_8);
        Path second = Files.writeString(root.resolve("second.sql"),
                "select id, name from missing_table", StandardCharsets.UTF_8);
        Db2DbOperation.Db2DbBuilder builder = request();
        builder.setTransfers(List.of(
                new Db2DbOperation.Db2DbTransfer(first, "colors", true, List.of()),
                new Db2DbOperation.Db2DbTransfer(second, "colors", false, List.of())));

        assertThrows(Db2DbTaskException.class, () -> new Db2DbOperation.Db2DbHandler()
                .execute(builder.build(), context()));

        assertEquals(1, count("select count(*) from colors"));
        assertEquals("old", string("select name from colors where id=9"));
    }

    @Test
    void substitutesParameterSets() throws Exception {
        execute(sourceUrl, "alter table colors add column import_year int");
        execute(sourceUrl, "insert into colors values (1, 'red', 2024), (2, 'blue', 2025)");
        Path sql = Files.writeString(root.resolve("select-year.sql"),
                "select id, name from colors where import_year = ${year}",
                StandardCharsets.UTF_8);
        Db2DbOperation.Db2DbBuilder builder = request();
        builder.setTransfers(List.of(new Db2DbOperation.Db2DbTransfer(sql, "colors", false,
                List.of())));
        builder.setSqlParameterSets(List.of(Map.of("year", "2024"), Map.of("year", "2025")));

        Db2DbOperation.Db2DbResult result = new Db2DbOperation.Db2DbHandler()
                .execute(builder.build(), context());

        assertEquals(2, result.executionCount());
        assertEquals(2, count("select count(*) from colors"));
    }

    @Test
    void rejectsBothParameterFormsAndEmptyTransfers() {
        Db2DbOperation.Db2DbBuilder builder = request();
        builder.setTransfers(List.of(new Db2DbOperation.Db2DbTransfer(
                root.resolve("x.sql"), "colors", false, List.of())));
        builder.setSqlParameters(Map.of("year", "2024"));
        builder.setSqlParameterSets(List.of(Map.of("year", "2025")));
        assertThrows(IllegalArgumentException.class, builder::build);

        Db2DbOperation.Db2DbBuilder empty = request();
        assertThrows(IllegalArgumentException.class, empty::build);
    }

    private Db2DbOperation.Db2DbBuilder request() {
        Db2DbOperation.Db2DbBuilder builder = new Db2DbOperation.Db2DbBuilder();
        builder.setSourceDatabase("source").setTargetDatabase("target");
        return builder;
    }

    private TestOperationContext context() {
        return new TestOperationContext(root, Map.of(), Map.of(
                "source", Map.of("jdbcUrl", sourceUrl, "user", "sa"),
                "target", Map.of("jdbcUrl", targetUrl, "user", "sa")), Map.of());
    }

    private String h2(String name) {
        return "jdbc:h2:mem:db2db_" + name + "_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
    }

    private void createTable(String url, String sql) throws Exception {
        execute(url, sql);
    }

    private void execute(String url, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int count(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(targetUrl, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private String string(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(targetUrl, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }
}
