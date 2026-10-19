package ch.so.agi.gretl.tasks.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.api.SecretValue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class S3OperationTest {
    @TempDir
    Path root;

    private static SecretValue secret() {
        return SecretValue.deferred(() -> "secret-key");
    }

    @Test
    void downloadDefaultsAndRejectsUnsafeKeys() {
        S3DownloadOperation.S3DownloadBuilder builder =
                new S3DownloadOperation.S3DownloadBuilder();
        configure(builder);
        builder.setBucketName("bucket").setDownloadDir(root.resolve("download"));
        S3DownloadOperation.S3DownloadRequest request = builder.build();

        assertNull(request.key());
        assertTrue(request.overwrite());
        assertEquals(root.resolve("download").toAbsolutePath().normalize(),
                request.downloadDir());
        assertEquals(root.resolve("download/file.txt"),
                S3Support.downloadTarget(root.resolve("download"), "dir/file.txt", true));
        assertEquals(root.resolve("download/dir/file.txt"),
                S3Support.downloadTarget(root.resolve("download"), "dir/file.txt", false));
        assertThrows(IllegalArgumentException.class,
                () -> S3Support.downloadTarget(root, "../escape.txt", false));
        assertThrows(IllegalArgumentException.class,
                () -> S3Support.downloadTarget(root, "/absolute.txt", false));
    }

    @Test
    void uploadCombinesSourcesAndRejectsDuplicateObjectKeys() throws Exception {
        Path file = Files.writeString(root.resolve("one.txt"), "one", StandardCharsets.UTF_8);
        Path directory = Files.createDirectories(root.resolve("dir"));
        Files.writeString(directory.resolve("two.txt"), "two");
        Path third = Files.writeString(root.resolve("three.txt"), "three");

        S3UploadOperation.S3UploadBuilder builder =
                new S3UploadOperation.S3UploadBuilder();
        configure(builder);
        builder.setBucketName("bucket")
                .setSourceFile(file)
                .setSourceDir(directory)
                .setSourceFiles(List.of(third))
                .setMetadata(Map.of("source", "test"));
        S3UploadOperation.S3UploadRequest request = builder.build();

        assertEquals(List.of(file.toAbsolutePath(), directory.resolve("two.txt"),
                third.toAbsolutePath()), request.sourceFiles());
        assertEquals("private", request.acl());
        assertEquals("test", request.metadata().get("source"));

        Path first = Files.createDirectories(root.resolve("a")).resolve("same.txt");
        Path second = Files.createDirectories(root.resolve("b")).resolve("same.txt");
        Files.writeString(first, "a");
        Files.writeString(second, "b");
        S3UploadOperation.S3UploadBuilder duplicate =
                new S3UploadOperation.S3UploadBuilder();
        configure(duplicate);
        duplicate.setBucketName("bucket").setSourceFiles(List.of(first, second));
        assertThrows(IllegalArgumentException.class, duplicate::build);
    }

    @Test
    void uploadRequiresAtLeastOneSource() {
        S3UploadOperation.S3UploadBuilder builder = new S3UploadOperation.S3UploadBuilder();
        configure(builder);
        builder.setBucketName("bucket");
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void deleteAcceptsAllObjectsAndBucketCopyRequiresBothBuckets() {
        S3DeleteOperation.S3DeleteBuilder delete = new S3DeleteOperation.S3DeleteBuilder();
        configure(delete);
        delete.setBucketName("bucket");
        assertNull(delete.build().key());

        S3Bucket2BucketOperation.S3Bucket2BucketBuilder copy =
                new S3Bucket2BucketOperation.S3Bucket2BucketBuilder();
        configure(copy);
        copy.setSourceBucket("source").setTargetBucket("target");
        assertEquals("private", copy.build().acl());

        S3Bucket2BucketOperation.S3Bucket2BucketBuilder missing =
                new S3Bucket2BucketOperation.S3Bucket2BucketBuilder();
        configure(missing);
        missing.setSourceBucket("source");
        assertThrows(IllegalArgumentException.class, missing::build);
    }

    @Test
    void validatesAclMetadataAndRequiredConnectionFields() {
        assertEquals("public-read", S3Fields.acl("PUBLIC-READ"));
        assertThrows(IllegalArgumentException.class, () -> S3Fields.acl("not-an-acl"));
        assertEquals(Map.of("key", "value"),
                S3Fields.metadata(Map.of("key", "value")));
        assertThrows(IllegalArgumentException.class,
                () -> S3Fields.metadata(Map.of("key", 1)));

        S3DeleteOperation.S3DeleteBuilder builder = new S3DeleteOperation.S3DeleteBuilder();
        builder.setBucketName("bucket");
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    private static void configure(S3Fields.BaseS3Builder<?> builder) {
        builder.setAccessKey("access-key");
        builder.setSecretKey(secret());
        builder.setEndpoint("http://127.0.0.1:1");
        builder.setRegion("us-east-1");
    }
}
