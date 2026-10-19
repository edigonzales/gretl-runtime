package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliDuckDbIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void duckDbTaskRunsThroughCli() throws Exception {
        Path database = tempDir.resolve("work.duckdb");
        Path sql = tempDir.resolve("create.sql");
        Files.writeString(sql, """
                create table colors (id integer, name varchar);
                insert into colors values (1, 'red');
                """, StandardCharsets.UTF_8);
        Path script = tempDir.resolve("job.groovy");
        Files.writeString(script, """
                task('analyse', DuckDbSqlExecutor) {
                    database = file('work.duckdb')
                    sqlFiles = [file('create.sql')]
                }
                defaults 'analyse'
                """);
        Path config = tempDir.resolve("runtime.json");
        Files.writeString(config, "{\"schemaVersion\":1,"
                + "\"runtime\":{\"workRoot\":\"" + tempDir.resolve("work")
                .toString().replace("\\", "\\\\") + "\",\"reportRoot\":\""
                + tempDir.resolve("reports").toString().replace("\\", "\\\\") + "\"}}");

        Result result = run("run", script.toString(), "--config", config.toString(),
                "--report", tempDir.resolve("report.json").toString());

        assertEquals(0, result.code(), result.err());
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + database);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("select count(*) from colors")) {
            rows.next();
            assertEquals(1, rows.getInt(1));
        }
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
