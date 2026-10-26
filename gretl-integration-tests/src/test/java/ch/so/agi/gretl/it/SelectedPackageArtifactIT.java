package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class SelectedPackageArtifactIT {
    @Test void databaseOnlyPackageRunsSqlDb2dbAndNativeDuckdbOutsideCheckoutWithoutMaven() throws Exception {
        try (var lease = PostgresFixture.lease()) {
            Path root = ArtifactJobRunner.directory("selected-database");
            Path source = SelectedPackageSupport.fixture(root, "database", Map.of(), List.of("database"), false);
            Files.writeString(source.resolve("simple.sql"), "create table packet(value integer,label varchar); insert into packet values(7,'sieben'); COPY packet TO ${output} (HEADER, DELIMITER ',');\n");
            Files.writeString(source.resolve("job.groovy"), """
                    parameter('output', type: 'path', required: true, description: 'Result directory')
                    task('selected-duck', DuckDbSqlExecutor) {
                        inMemory = true; sqlFiles = [file('simple.sql')]
                        sqlParameters = [output: \"'\" + parameter('output').resolve('duck.csv').toString().replace(\"'\", \"''\") + \"'\"]
                    }
                    """, StandardOpenOption.APPEND);
            SelectedPackageSupport.descriptor(source, List.of("database"), false, List.of());
            var config = JobRunner.baseConfig(root); JobRunner.secret(root, config, "dbPassword", lease.password());
            JobRunner.taskConfig(config, "db", Map.of("jdbcUrl", lease.jdbcUrl(), "user", lease.user(), "passwordSecret", "dbPassword"));
            var result = SelectedPackageSupport.detached(new ArtifactJobRunner.Request("selected-database", source, root, List.of("update", "selected-duck"),
                    Map.of("schema", lease.schema(), "output", root.resolve("output")), Map.of(), config,
                    Map.of("postgres", new ArtifactJobRunner.Endpoint(lease.jdbcUrl(), lease.imageJdbcUrl())), Duration.ofSeconds(90)));
            assertEquals(0, result.code(), result.err());
            assertEquals(List.of("value,label", "7,sieben"), Files.readAllLines(root.resolve("output/duck.csv")));
            try (var connection = DriverManager.getConnection(lease.jdbcUrl(), lease.user(), lease.password()); var statement = connection.createStatement();
                 var rows = statement.executeQuery("select id,name from colors_copy order by id")) {
                assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); assertEquals("red", rows.getString(2));
                assertTrue(rows.next()); assertEquals(2, rows.getInt(1)); assertEquals("Grün", rows.getString(2)); assertFalse(rows.next());
            }
        }
    }
}
