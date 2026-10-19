package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(300)
class S3TaskFamilyIT {
    @TempDir
    Path root;

    private S3Fixture fixture;
    private S3Fixture.Lease lease;

    @BeforeAll
    void startS3() {
        fixture = new S3Fixture();
        S3Fixture.container();
    }

    @AfterAll
    void stopS3() {
        if (fixture != null) {
            fixture.close();
        }
    }

    @AfterEach
    void closeLease() {
        S3Fixture.Lease current = lease;
        lease = null;
        if (current != null) {
            current.close();
        }
    }

    @Test
    void uploadsCopiesDownloadsAndDeletesWithFloci() throws Exception {
        lease = fixture.lease();
        Files.createDirectories(root.resolve("data/dir"));
        Files.createDirectories(root.resolve("data/tree"));
        Files.writeString(root.resolve("data/one.txt"), "one", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("data/dir/two.txt"), "two", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("data/tree/three.csv"), "three",
                StandardCharsets.UTF_8);
        Files.writeString(root.resolve("data/tree/ignored.txt"), "ignored",
                StandardCharsets.UTF_8);
        Map<String, Object> config = config();

        JobRunner.Result upload = JobRunner.run(root, s3Job("upload", "S3Upload",
                "    bucketName = '" + lease.sourceBucket() + "'\n"
                        + "    sourceFile = file('data/one.txt')\n"
                        + "    sourceDir = path('data/dir')\n"
                        + "    sourceFiles = [file('data/tree/three.csv')]\n"
                        + "    acl = 'private'\n"
                        + "    contentType = 'text/plain'\n"
                        + "    metadata = [source: 'test']\n"), config);
        assertEquals(0, upload.code(), upload.err());
        assertEquals(List.of("one.txt", "three.csv", "two.txt"),
                lease.keys(lease.sourceBucket()));
        assertEquals("one", lease.read(lease.sourceBucket(), "one.txt"));
        HeadObjectResponse uploaded = lease.head(lease.sourceBucket(), "one.txt");
        assertEquals("text/plain", uploaded.contentType());
        assertEquals("test", uploaded.metadata().get("source"));
        assertFalse(lease.keys(lease.sourceBucket()).contains("ignored.txt"));

        JobRunner.Result copy = JobRunner.run(root, s3Job("copy", "S3Bucket2Bucket",
                "    sourceBucket = '" + lease.sourceBucket() + "'\n"
                        + "    targetBucket = '" + lease.targetBucket() + "'\n"
                        + "    acl = 'private'\n"
                        + "    metadata = [copied: 'true']\n"), config);
        assertEquals(0, copy.code(), copy.err());
        assertEquals(List.of("one.txt", "three.csv", "two.txt"),
                lease.keys(lease.targetBucket()));
        assertEquals("true", lease.head(lease.targetBucket(), "one.txt")
                .metadata().get("copied"));

        JobRunner.Result downloadKey = JobRunner.run(root, s3Job("downloadKey", "S3Download",
                "    bucketName = '" + lease.targetBucket() + "'\n"
                        + "    key = 'one.txt'\n"
                        + "    downloadDir = path('download-key')\n"), config);
        assertEquals(0, downloadKey.code(), downloadKey.err());
        assertEquals("one", Files.readString(root.resolve("download-key/one.txt"),
                StandardCharsets.UTF_8));

        JobRunner.Result downloadAll = JobRunner.run(root, s3Job("downloadAll", "S3Download",
                "    bucketName = '" + lease.targetBucket() + "'\n"
                        + "    downloadDir = path('download-all')\n"), config);
        assertEquals(0, downloadAll.code(), downloadAll.err());
        assertEquals("one", Files.readString(root.resolve("download-all/one.txt"),
                StandardCharsets.UTF_8));
        assertEquals("two", Files.readString(root.resolve("download-all/two.txt"),
                StandardCharsets.UTF_8));
        assertEquals("three", Files.readString(root.resolve("download-all/three.csv"),
                StandardCharsets.UTF_8));

        JobRunner.Result deleteKey = JobRunner.run(root, s3Job("deleteKey", "S3Delete",
                "    bucketName = '" + lease.targetBucket() + "'\n"
                        + "    key = 'one.txt'\n"), config);
        assertEquals(0, deleteKey.code(), deleteKey.err());
        assertFalse(lease.exists(lease.targetBucket(), "one.txt"));

        JobRunner.Result deleteAll = JobRunner.run(root, s3Job("deleteAll", "S3Delete",
                "    bucketName = '" + lease.targetBucket() + "'\n"), config);
        assertEquals(0, deleteAll.code(), deleteAll.err());
        assertTrue(lease.keys(lease.targetBucket()).isEmpty());
    }


    @Test
    void downloadsNestedSpecialKeysAndHonorsOverwrite() throws Exception {
        lease = fixture.lease();
        lease.put(lease.sourceBucket(), "folder/a b.txt", "alpha");
        lease.put(lease.sourceBucket(), "folder/ümlaut.txt", "umlaut");
        Path existing = root.resolve("downloads/folder/a b.txt");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "old", StandardCharsets.UTF_8);
        Map<String, Object> config = config();

        JobRunner.Result blocked = JobRunner.run(root, s3Job("downloadBlocked", "S3Download",
                "    bucketName = '" + lease.sourceBucket() + "'\n"
                        + "    downloadDir = path('downloads')\n"
                        + "    overwrite = false\n"), config);
        assertEquals(10, blocked.code(), blocked.out());
        assertEquals("old", Files.readString(existing, StandardCharsets.UTF_8));
        try (var paths = Files.walk(root.resolve("downloads"))) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString()
                    .endsWith(".part")));
        }

        JobRunner.Result overwrite = JobRunner.run(root, s3Job("downloadOverwrite", "S3Download",
                "    bucketName = '" + lease.sourceBucket() + "'\n"
                        + "    downloadDir = path('downloads')\n"
                        + "    overwrite = true\n"), config);
        assertEquals(0, overwrite.code(), overwrite.err());
        assertEquals("alpha", Files.readString(existing, StandardCharsets.UTF_8));
        assertEquals("umlaut", Files.readString(
                root.resolve("downloads/folder/ümlaut.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void bucketCopyPreservesSpecialKeysAndMetadata() throws Exception {
        lease = fixture.lease();
        Files.writeString(root.resolve("räumlich.txt"), "special", StandardCharsets.UTF_8);
        lease.put(lease.sourceBucket(), "folder/with space.txt", "nested");
        Map<String, Object> config = config();

        JobRunner.Result upload = JobRunner.run(root, s3Job("uploadSpecial", "S3Upload",
                "    bucketName = '" + lease.sourceBucket() + "'\n"
                        + "    sourceFile = file('räumlich.txt')\n"
                        + "    acl = 'private'\n"
                        + "    metadata = [source: 'special']\n"), config);
        assertEquals(0, upload.code(), upload.err());

        JobRunner.Result copy = JobRunner.run(root, s3Job("copySpecial", "S3Bucket2Bucket",
                "    sourceBucket = '" + lease.sourceBucket() + "'\n"
                        + "    targetBucket = '" + lease.targetBucket() + "'\n"
                        + "    acl = 'private'\n"), config);
        assertEquals(0, copy.code(), copy.err());
        assertEquals(List.of("folder/with space.txt", "räumlich.txt"),
                lease.keys(lease.targetBucket()));
        assertEquals("special", lease.head(lease.targetBucket(), "räumlich.txt")
                .metadata().get("source"));
        assertEquals("nested", lease.read(lease.targetBucket(), "folder/with space.txt"));
    }

    @Test
    void uploadsAndDownloadsLargeObject() throws Exception {
        lease = fixture.lease();
        byte[] data = new byte[8 * 1024 * 1024];
        for (int index = 0; index < data.length; index++) {
            data[index] = (byte) (index * 31);
        }
        Files.write(root.resolve("large.bin"), data);
        Map<String, Object> config = config();

        JobRunner.Result upload = JobRunner.run(root, s3Job("uploadLarge", "S3Upload",
                "    bucketName = '" + lease.sourceBucket() + "'\n"
                        + "    sourceFile = file('large.bin')\n"
                        + "    acl = 'private'\n"), config);
        assertEquals(0, upload.code(), upload.err());
        JobRunner.Result download = JobRunner.run(root, s3Job("downloadLarge", "S3Download",
                "    bucketName = '" + lease.sourceBucket() + "'\n"
                        + "    key = 'large.bin'\n"
                        + "    downloadDir = path('large-download')\n"), config);
        assertEquals(0, download.code(), download.err());
        assertTrue(java.util.Arrays.equals(data,
                Files.readAllBytes(root.resolve("large-download/large.bin"))));
    }

    @Test
    void missingBucketFailsWithoutLeakingSecret() throws Exception {
        lease = fixture.lease();
        Files.writeString(root.resolve("one.txt"), "one", StandardCharsets.UTF_8);
        String missing = "missing-" + UUID.randomUUID().toString().replace("-", "");
        JobRunner.Result result = JobRunner.run(root, s3Job("upload", "S3Upload",
                "    bucketName = '" + missing + "'\n"
                        + "    sourceFile = file('one.txt')\n"
                        + "    acl = 'private'\n"), config());

        assertEquals(10, result.code(), result.out());
        assertFalse(result.out().contains(lease.secretKey()));
        assertFalse(result.err().contains(lease.secretKey()));
        if (Files.exists(result.report())) {
            assertFalse(Files.readString(result.report()).contains(lease.secretKey()));
        }
    }

    private String s3Job(String id, String task, String extra) {
        return "task('" + id + "', " + task + ") {\n"
                + "    accessKey = '" + lease.accessKey() + "'\n"
                + "    secretKey = secret('s3SecretKey')\n"
                + "    endpoint = '" + lease.endpoint() + "'\n"
                + "    region = '" + lease.region() + "'\n"
                + extra
                + "}\n"
                + "defaults '" + id + "'\n";
    }

    private Map<String, Object> config() throws Exception {
        Map<String, Object> config = JobRunner.baseConfig(root);
        JobRunner.secret(root, config, "s3SecretKey", lease.secretKey());
        return config;
    }
}
