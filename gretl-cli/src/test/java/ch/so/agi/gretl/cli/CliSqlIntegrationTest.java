package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliSqlIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void configTaskConfigScriptsAndReportWorkTogether() throws Exception {
        String db = "jdbc:h2:mem:cli_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(db, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE demo (id INT PRIMARY KEY, txt VARCHAR(50))");
        }
        Path sql = tempDir.resolve("insert.sql");
        Files.writeString(sql, "INSERT INTO demo(id,txt) VALUES (1,'O''Brien')");
        Path script = tempDir.resolve("job.groovy");
        Files.writeString(script, """
                task('insert', SqlExecutor) {
                    database = 'db'
                    scripts = [file('insert.sql')]
                }
                defaults 'insert'
                """);
        Path config = tempDir.resolve("runtime.json");
        Files.writeString(config, "{\"schemaVersion\":1,\"taskConfig\":{\"db\":{"
                + "\"jdbcUrl\":\"" + db + "\",\"user\":\"sa\"}},"
                + "\"runtime\":{\"workRoot\":\"" + tempDir.resolve("work")
                .toString().replace("\\", "\\\\") + "\",\"reportRoot\":\""
                + tempDir.resolve("reports").toString().replace("\\", "\\\\") + "\"}}");
        Path report = tempDir.resolve("report.json");

        Result result = run("run", script.toString(), "--config", config.toString(),
                "--report", report.toString());
        assertEquals(0, result.code(), result.err());
        Map<?, ?> json = (Map<?, ?>) Json.parse(Files.readString(report));
        assertEquals("SUCCEEDED", ((Map<?, ?>) json.get("execution")).get("status"));
        try (Connection connection = DriverManager.getConnection(db, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT txt FROM demo WHERE id=1")) {
            assertTrue(rows.next());
            assertEquals("O'Brien", rows.getString(1));
        }
        assertFalse(Files.readString(report).contains("O''Brien"));
    }

    private Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = new GretlCli(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8), Map.of(), tempDir,
                getClass().getClassLoader()).run(args);
        return new Result(code, out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int code, String out, String err) {
    }
}
