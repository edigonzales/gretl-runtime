package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The same fixtures and independent FTP/S3 clients are used in either transport. */
class ArtifactServicesIT {
    @Test void ftpRoundTripListOverwriteDeleteAndRejectedLogin() throws Exception {
        try (var fixture = new FtpFixture(); var lease = fixture.lease()) {
            var params = new LinkedHashMap<>(Map.of("server", fixture.server(), "user", fixture.user(), "remoteDir", lease.directory()));
            var endpoints = Map.of("ftp", new ArtifactJobRunner.Endpoint(fixture.server(), "ftp:21"));
            var upload = run("ftp-upload", params, "ftpPassword", fixture.password(), endpoints);
            assertEquals(0, upload.code(), upload.err());
            assertEquals("one", new String(lease.read("one.txt"), java.nio.charset.StandardCharsets.UTF_8));
            var listed = run("ftp-list-result", params, "ftpPassword", fixture.password(), endpoints);
            assertEquals(0, listed.code(), listed.err());
            assertTrue(Files.readString(listed.directory().resolve("output/listing.txt")).contains("one.txt"));
            var download = run("ftp-download", params, "ftpPassword", fixture.password(), endpoints);
            assertEquals(0, download.code(), download.err());
            assertArrayEquals(Files.readAllBytes(ArtifactJobRunner.fixture("services").resolve("one.txt")), Files.readAllBytes(download.directory().resolve("output/one.txt")));
            var wrong = run("ftp-list", params, "ftpPassword", "definitely-wrong", endpoints);
            assertEquals(10, wrong.code(), wrong.err());
            String report = Files.readString(wrong.report());
            assertTrue(report.contains("FtpTaskException"), report); assertTrue(report.contains("FTP-Anmeldung fehlgeschlagen"), report);
            assertFalse(report.contains("definitely-wrong")); assertFalse(wrong.err().contains("definitely-wrong"));
            assertEquals(List.of("one.txt"), lease.list());
            var deleted = run("ftp-delete", params, "ftpPassword", fixture.password(), endpoints);
            assertEquals(0, deleted.code(), deleted.err()); assertTrue(lease.list().isEmpty());
            params.put("all", "true");
            assertEquals(0, run("ftp-delete", params, "ftpPassword", fixture.password(), endpoints).code());
        }
    }
    @Test void s3FilesDirectoryTreeCopyDownloadDeleteAndMissingBucket() throws Exception {
        try (var fixture = new S3Fixture(); var lease = fixture.lease()) {
            var params = new LinkedHashMap<>(Map.of("accessKey", lease.accessKey(), "endpoint", lease.endpoint(), "region", lease.region(),
                    "sourceBucket", lease.sourceBucket(), "targetBucket", lease.targetBucket()));
            var endpoints = Map.of("s3", new ArtifactJobRunner.Endpoint(lease.endpoint(), "http://s3:4566"));
            var upload = run("s3-upload", params, "s3SecretKey", lease.secretKey(), endpoints);
            assertEquals(0, upload.code(), upload.err());
            assertEquals(List.of("one.txt", "three.csv", "two.txt"), lease.keys(lease.sourceBucket()));
            assertEquals("one", lease.read(lease.sourceBucket(), "one.txt"));
            assertEquals("test", lease.head(lease.sourceBucket(), "one.txt").metadata().get("source"));
            assertEquals("text/plain", lease.head(lease.sourceBucket(), "one.txt").contentType());
            var copy = run("s3-copy", params, "s3SecretKey", lease.secretKey(), endpoints);
            assertEquals(0, copy.code(), copy.err());
            assertEquals(lease.keys(lease.sourceBucket()), lease.keys(lease.targetBucket()));
            assertEquals("true", lease.head(lease.targetBucket(), "one.txt").metadata().get("copied"));
            var download = run("s3-download", params, "s3SecretKey", lease.secretKey(), endpoints);
            assertEquals(0, download.code(), download.err()); assertEquals("one", Files.readString(download.directory().resolve("output/one.txt")));
            params.put("all", "true");
            var all = run("s3-download", params, "s3SecretKey", lease.secretKey(), endpoints);
            assertEquals(0, all.code(), all.err());
            for (String name : List.of("one.txt", "two.txt", "three.csv")) assertEquals(lease.read(lease.targetBucket(), name), Files.readString(all.directory().resolve("output").resolve(name)));
            params.put("all", "false");
            assertEquals(0, run("s3-delete", params, "s3SecretKey", lease.secretKey(), endpoints).code());
            assertFalse(lease.exists(lease.targetBucket(), "one.txt"));
            params.put("all", "true");
            assertEquals(0, run("s3-delete", params, "s3SecretKey", lease.secretKey(), endpoints).code()); assertTrue(lease.keys(lease.targetBucket()).isEmpty());
            params.put("sourceBucket", "missing-bucket");
            var missing = run("s3-upload", params, "s3SecretKey", lease.secretKey(), endpoints);
            assertEquals(10, missing.code(), missing.err());
            String report = Files.readString(missing.report()); assertTrue(report.contains("S3TaskException"), report); assertTrue(report.contains("NoSuchBucket"), report);
            assertFalse(report.contains(lease.secretKey()));
        }
    }
    private ArtifactJobRunner.Result run(String target, Map<String, String> values, String secret, String password,
                                         Map<String, ArtifactJobRunner.Endpoint> endpoints) throws Exception {
        Path root = ArtifactJobRunner.directory(target); var params = new LinkedHashMap<>(values); params.put("output", root.resolve("output").toString());
        var config = JobRunner.baseConfig(root); JobRunner.secret(root, config, secret, password);
        return ArtifactJobRunner.run(new ArtifactJobRunner.Request(target, ArtifactJobRunner.fixture("services"), root, List.of(target), params, Map.of(), config, endpoints, Duration.ofSeconds(90)));
    }
}
