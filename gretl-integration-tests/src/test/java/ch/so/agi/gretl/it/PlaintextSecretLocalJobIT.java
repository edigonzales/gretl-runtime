package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks product output before ArtifactJobRunner applies its evidence redaction. */
class PlaintextSecretLocalJobIT {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void scriptAndJarResolvePlaintextThroughAliasesAndMaskRawOutputs(boolean script) throws Exception {
        Path root = ArtifactJobRunner.directory("plaintext-" + script);
        String sentinel = "plaintext-artifact-sentinel-" + script;
        var config = JobRunner.baseConfig(root);
        config.put("secrets", Map.of("dbPassword", Map.of("plaintext", sentinel)));
        JobRunner.taskConfig(config, "edit", Map.of(
                "jdbcUrl", "jdbc:sqlite:" + root.resolve("output/probe.sqlite"),
                "user", "test", "passwordSecret", "dbPassword"));
        var request = new ArtifactJobRunner.Request("plaintext-" + script,
                ArtifactJobRunner.fixture("plaintext-secret"), root, List.of(),
                Map.of("output", root.resolve("output")), Map.of(), config, Map.of(), Duration.ofSeconds(45));
        var prepared = ArtifactJobRunner.prepare(request, ArtifactJobRunner.Mode.JAR);
        var running = ArtifactJobRunner.start(prepared, script);
        try {
            assertTrue(running.process().waitFor(45, TimeUnit.SECONDS), "Product process timed out");
            assertEquals(10, running.process().exitValue());
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(sentinel.getBytes(StandardCharsets.UTF_8)));
            assertTrue(Files.isRegularFile(root.resolve("output/digest.txt")),
                    "Secret probe must execute; process evidence: " + root);
            assertEquals(digest, Files.readString(root.resolve("output/digest.txt")));
            try (var connection = java.sql.DriverManager.getConnection(
                    "jdbc:sqlite:" + root.resolve("output/probe.sqlite"));
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery(
                         "SELECT name FROM sqlite_master WHERE name='plaintext_probe'")) {
                assertTrue(rows.next(), "SqlExecutor must resolve passwordSecret and execute its statement");
            }

            String console = Files.readString(running.err());
            assertTrue(console.contains("plaintext-probe [REDACTED]"));
            assertFalse(console.contains(sentinel));
            assertFalse(Files.readString(running.out()).contains(sentinel));
            String report = Files.readString(prepared.report());
            assertTrue(report.contains("plaintext-failure [REDACTED]"));
            assertTrue(report.contains("plaintext-cause [REDACTED]"));
            assertFalse(report.contains(sentinel));
            try (var paths = Files.list(root.resolve("reports"))) {
                var events = paths.filter(path -> path.toString().endsWith(".events.jsonl")).toList();
                assertEquals(1, events.size());
                String contents = Files.readString(events.getFirst());
                assertTrue(contents.contains("plaintext-probe [REDACTED]"));
                assertFalse(contents.contains(sentinel));
            }
            assertFalse(Files.readString(root.resolve("command.json")).contains(sentinel));
            assertFalse(Files.readString(prepared.source().resolve("job-package.json")).contains(sentinel));
        } finally {
            if (running.process().isAlive()) ArtifactJobRunner.stop(running);
            ArtifactJobRunner.finish(running, false);
        }
    }
}
