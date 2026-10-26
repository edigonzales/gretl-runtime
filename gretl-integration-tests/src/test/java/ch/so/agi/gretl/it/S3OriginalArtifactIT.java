package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class S3OriginalArtifactIT {
    @Test void originalDirectoryFileTreeFileCopyAndDownloadDeleteValues() throws Exception {
        try (var fixture = new S3Fixture(); var lease = fixture.lease()) {
            var params = new LinkedHashMap<>(Map.of("accessKey", lease.accessKey(), "endpoint", lease.endpoint(), "region", lease.region(), "sourceBucket", lease.sourceBucket(), "targetBucket", lease.targetBucket()));
            for (String selection : List.of("file", "tree", "directory")) {
                clear(lease, lease.sourceBucket()); params.put("selection", selection);
                var result = run(lease, "s3-upload", params); assertEquals(0, result.code(), result.err());
                assertEquals(selection.equals("file") ? List.of("bar.txt") : selection.equals("tree") ? List.of("bar.csv", "foo.csv") : List.of("bar.txt", "foo.txt"), lease.keys(lease.sourceBucket()));
                if (selection.equals("file")) assertEquals("bar", lease.read(lease.sourceBucket(), "bar.txt").lines().findFirst().orElseThrow().toLowerCase());
            }
            var copied = run(lease, "s3-copy", params); assertEquals(0, copied.code(), copied.err()); assertEquals(List.of("bar.txt", "foo.txt"), lease.keys(lease.targetBucket()));
            params.put("all", "true"); var directory = run(lease, "s3-download", params); assertEquals(0, directory.code(), directory.err());
            assertEquals("foo", Files.readString(directory.directory().resolve("output/foo.txt")).substring(0, 3)); assertEquals("bar", Files.readString(directory.directory().resolve("output/bar.txt")).substring(0, 3));
            var deleted = run(lease, "s3-delete", params); assertEquals(0, deleted.code(), deleted.err()); assertTrue(lease.keys(lease.targetBucket()).isEmpty());
            String original = Files.readString(ArtifactJobRunner.fixture("services").resolve("original/S3DownloadFile/upload/download.txt"));
            lease.put(lease.targetBucket(), "download.txt", original); params.put("all", "false"); params.put("key", "download.txt");
            var downloaded = run(lease, "s3-download", params); assertEquals(0, downloaded.code(), downloaded.err());
            assertEquals("fubar", Files.readString(downloaded.directory().resolve("output/download.txt")).lines().findFirst().orElseThrow().toLowerCase());
            assertEquals(original, Files.readString(downloaded.directory().resolve("output/download.txt")));
            deleted = run(lease, "s3-delete", params); assertEquals(0, deleted.code(), deleted.err()); assertEquals(0, lease.keys(lease.targetBucket()).size());
            params.put("sourceBucket", "missing-original-bucket");
            var failed = run(lease, "s3-upload", params); assertEquals(10, failed.code(), failed.err()); String report = Files.readString(failed.report());
            assertTrue(report.contains("S3TaskException"), report); assertTrue(report.contains("NoSuchBucket"), report);
        }
    }
    private static void clear(S3Fixture.Lease lease, String bucket) {
        try (var client = lease.client()) { for (String key : lease.keys(bucket)) client.deleteObject(b -> b.bucket(bucket).key(key)); }
    }
    private ArtifactJobRunner.Result run(S3Fixture.Lease lease, String target, Map<String, String> extra) throws Exception {
        Path root = ArtifactJobRunner.directory("original-" + target); var params = new LinkedHashMap<String, Object>(extra); params.put("output", root.resolve("output"));
        var config = JobRunner.baseConfig(root); JobRunner.secret(root, config, "s3SecretKey", lease.secretKey());
        return ArtifactJobRunner.run(new ArtifactJobRunner.Request("original-" + target, ArtifactJobRunner.fixture("services"), root, List.of(target), params, Map.of(), config,
                Map.of("s3", new ArtifactJobRunner.Endpoint(lease.endpoint(), "http://s3:4566")), Duration.ofSeconds(90)));
    }
}
