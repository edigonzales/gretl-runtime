package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class SftpExchangeIT {
    private static final String IMAGE = "atmoz/sftp:alpine@sha256:c5fb9e4beb8b7054a4c0b65c5e11df47cc20da95ad6002e88aecff62bab06372";

    @Test
    void scriptAndDetachedIoJarExchangeFilesWithOpenSsh() throws Exception {
        try (var server = new GenericContainer<>(DockerImageName.parse(IMAGE))
                .withLabel("gretl.verification.run", System.getProperty("gretl.test.runId", "development"))
                .withExposedPorts(22)
                .withCommand("fixture:test-sftp-password:1001:1001:exchange")
                .waitingFor(Wait.forListeningPort())) {
            server.start();
            Path root = ArtifactJobRunner.directory("sftp-exchange");
            Path knownHosts = root.resolve("known_hosts");
            String host = server.getHost();
            int port = server.getMappedPort(22);
            String publicKeys = command(server, "sh", "-ec", "cat /etc/ssh/ssh_host_*_key.pub");
            Files.write(knownHosts, publicKeys.lines().filter(line -> !line.isBlank())
                    .map(key -> "[" + host + "]:" + port + " " + key).toList());

            seed(server);
            var failed = ArtifactJobRunner.run(request(root, ArtifactJobRunner.fixture("sftp-exchange"),
                    host, port, knownHosts, "yes"));
            assertEquals(10, failed.code(), failed.err());
            assertTrue(failed.err().contains("Synthetic processing failure"), failed.err());
            assertEquals("first\n", command(server, "cat", "/home/fixture/exchange/first.csv"));
            assertEquals("second\n", command(server, "cat", "/home/fixture/exchange/nested/second.CSV"));

            Path script = ArtifactJobRunner.directory("sftp-script");
            var result = ArtifactJobRunner.run(request(script, ArtifactJobRunner.fixture("sftp-exchange"),
                    host, port, knownHosts, "no"));
            assertExchange(server, result);

            seed(server);
            Path jar = ArtifactJobRunner.directory("sftp-jar");
            Path source = SelectedPackageSupport.fixture(jar, "sftp-exchange", Map.of(), List.of("io"), false);
            assertExchange(server, SelectedPackageSupport.detached(request(jar, source, host, port, knownHosts, "no")));
        }
    }

    private static ArtifactJobRunner.Request request(Path root, Path source, String host, int port,
                                                     Path knownHosts, String fail) throws Exception {
        var config = JobRunner.baseConfig(root);
        JobRunner.secret(root, config, "sftpPassword", "test-sftp-password");
        JobRunner.taskConfig(config, "exchange", Map.of("host", host, "port", port, "user", "fixture",
                "knownHostsFile", knownHosts.toString(), "passwordSecret", "sftpPassword"));
        return new ArtifactJobRunner.Request("sftp-exchange", source, root, List.of(),
                Map.of("output", root.resolve("output"), "fail", fail), Map.of(), config, Map.of(),
                Duration.ofSeconds(90));
    }

    private static void seed(GenericContainer<?> server) throws Exception {
        command(server, "sh", "-ec", """
                cd /home/fixture/exchange
                mkdir -p nested
                printf 'first\n' > first.csv
                printf 'second\n' > nested/second.CSV
                printf 'keep\n' > nested/keep.csv
                chown -R 1001:1001 .
                """);
    }

    private static void assertExchange(GenericContainer<?> server, ArtifactJobRunner.Result result) throws Exception {
        assertEquals(0, result.code(), result.err());
        assertEquals("first\nsecond\n", Files.readString(result.directory().resolve("output/processed.csv")));
        assertEquals(List.of(".:csv=1", "nested:csv=2"),
                Files.readAllLines(result.directory().resolve("output/formats.txt")));
        try (var zip = new java.util.zip.ZipFile(result.directory().resolve("output/processed.zip").toFile())) {
            assertEquals(List.of("processed.csv"), zip.stream().map(java.util.zip.ZipEntry::getName).toList());
            try (var file = zip.getInputStream(zip.getEntry("processed.csv"))) {
                assertArrayEquals("first\nsecond\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), file.readAllBytes());
            }
        }
        assertEquals("first\nsecond\n", command(server, "cat", "/home/fixture/exchange/upload.csv"));
        assertEquals("keep\n", command(server, "cat", "/home/fixture/exchange/nested/keep.csv"));
        command(server, "sh", "-ec", """
                cd /home/fixture/exchange
                test ! -e first.csv && test ! -e nested/second.CSV
                """);
        assertFalse(result.err().contains("test-sftp-password"));
    }

    private static String command(GenericContainer<?> server, String... command) throws Exception {
        var result = server.execInContainer(command);
        assertEquals(0, result.getExitCode(), result.getStderr());
        return result.getStdout();
    }
}
