package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
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
class PackagedSqlJobPostgresIT {
    @TempDir
    Path root;

    private PostgresFixture.Lease lease;

    @BeforeEach
    void setUp() throws Exception {
        lease = PostgresFixture.lease();
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("create table packaged_demo (id int primary key, name text)");
        }
    }

    @AfterEach
    void tearDown() {
        if (lease != null) {
            lease.close();
        }
    }

    @Test
    void packagedSqlJobRunsAgainstPostgres() throws Exception {
        Path distribution = Path.of("..", "gretl-dist", "target", "dist").toAbsolutePath()
                .normalize();
        Path launcher = distribution.resolve("bin/gretl");
        assertTrue(Files.isRegularFile(launcher),
                "Distribution launcher is missing; run the reactor build first: " + launcher);

        Files.writeString(root.resolve("insert.sql"),
                "INSERT INTO packaged_demo(id, name) VALUES (1, 'packaged')",
                StandardCharsets.UTF_8);
        Files.writeString(root.resolve("job.groovy"), """
                task('insert', SqlExecutor) {
                    database = 'db'
                    scripts = [file('insert.sql')]
                }
                defaults 'insert'
                """);
        Files.writeString(root.resolve("job-package.json"), """
                {
                  "schemaVersion": 1,
                  "name": "postgres-job",
                  "version": "2026.10.19",
                  "runtimeVersion": "0.1.0-SNAPSHOT",
                  "entryScript": "job.groovy",
                  "sources": [],
                  "resources": ["insert.sql"],
                  "dependencies": []
                }
                """);
        Files.writeString(root.resolve("password.txt"), lease.password(),
                StandardCharsets.UTF_8);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("schemaVersion", 1L);
        config.put("secrets", Map.of("dbPassword", Map.of("file", "password.txt")));
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("jdbcUrl", lease.jdbcUrl());
        database.put("user", lease.user());
        database.put("passwordSecret", "dbPassword");
        config.put("taskConfig", Map.of("db", database));
        config.put("runtime", Map.of(
                "workRoot", root.resolve("work").toString(),
                "reportRoot", root.resolve("reports").toString()));
        Path configFile = root.resolve("runtime.json");
        Files.writeString(configFile, Json.write(config), StandardCharsets.UTF_8);

        Result packaged = run(root, launcher.toString(), "package",
                root.resolve("job-package.json").toString(),
                "--output", root.resolve("job.jar").toString());
        assertEquals(0, packaged.code(), packaged.output());
        assertTrue(Files.isRegularFile(root.resolve("job.jar")));

        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Result run = run(root, java.toString(), "-jar",
                root.resolve("job.jar").toString(),
                "--config", configFile.toString(),
                "--report", root.resolve("report.json").toString());
        assertEquals(0, run.code(), run.output());

        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select name from packaged_demo where id=1")) {
            rows.next();
            assertEquals("packaged", rows.getString(1));
        }
    }

    private Result run(Path directory, String... command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true);
        String javaHome = System.getProperty("java.home");
        builder.environment().put("JAVA_HOME", javaHome);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        int code = process.waitFor();
        return new Result(code, output);
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(lease.jdbcUrl(), lease.user(), lease.password());
    }

    private record Result(int code, String output) {
    }
}
