package ch.so.agi.gretl.tasks.io.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.api.SecretValue;
import ch.so.agi.gretl.tasks.io.TestOperationContext;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
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
    void refusesTargetCreatedDuringDownload() throws Exception {
        Path local = Files.createDirectory(root.resolve("download"));
        Path target = local.resolve("a.txt");
        var context = new TestOperationContext(root, Map.of(), Map.of(), Map.of());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bucket/a.txt", exchange -> {
            try (exchange) {
                Files.writeString(target, "foreign");
                byte[] payload = "remote".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, payload.length);
                exchange.getResponseBody().write(payload);
            }
        });
        server.start();
        try {
            var builder = new S3DownloadOperation.S3DownloadBuilder();
            configure(builder);
            builder.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
            builder.setBucketName("bucket").setDownloadDir(local).setKey("a.txt").setOverwrite(false);
            S3TaskException failure = assertThrows(S3TaskException.class,
                    () -> new S3DownloadOperation.S3DownloadHandler().execute(builder.build(), context));
            assertEquals("S3_TARGET_EXISTS", failure.code());
            assertEquals("publish", failure.phase());
            assertEquals("foreign", Files.readString(target));
            try (var entries = Files.list(local)) {
                assertEquals(List.of(target), entries.toList());
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void acceptsAConfiguredRootSymlinkAndRejectsTraversalDuringMapping() throws Exception {
        Path actual = Files.createDirectory(root.resolve("actual")).toRealPath();
        Path configured = Files.createSymbolicLink(root.resolve("configured"), actual);
        Path realRoot = S3Support.prepareDownloadRoot(configured);
        assertEquals(actual, realRoot);
        assertEquals(actual.resolve("nested/file.txt"),
                S3Support.downloadTarget(realRoot, "./nested/file.txt", false));
        assertEquals(actual.resolve("file.txt"),
                S3Support.downloadTarget(realRoot, "nested/file.txt", true));
        for (String invalid : List.of("nested/../file.txt", "/absolute/file.txt",
                "nested/", "nested/.", "nul\0file")) {
            assertThrows(IllegalArgumentException.class,
                    () -> S3Support.downloadTarget(realRoot, invalid, false), invalid);
        }
    }

    @Test
    void directDownloadRejectsExistingUnsafeParentsBeforeOpeningTheObject() throws Exception {
        Path realRoot = S3Support.prepareDownloadRoot(root.resolve("downloads"));
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path empty = Files.createDirectory(outside.resolve("empty"));
        Path sentinel = Files.writeString(outside.resolve("sentinel.txt"), "keep");
        Path deep = Files.createDirectory(realRoot.resolve("deep"));
        Path first = Files.createSymbolicLink(realRoot.resolve("first"), outside);
        Path nested = Files.createSymbolicLink(deep.resolve("nested"), empty);
        Path dangling = Files.createSymbolicLink(realRoot.resolve("dangling"), root.resolve("missing"));
        Path file = Files.writeString(realRoot.resolve("file"), "regular file");
        var context = new TestOperationContext(root, Map.of(), Map.of(), Map.of());

        for (Path target : List.of(first.resolve("sentinel.txt"), nested.resolve("new/file.txt"),
                dangling.resolve("file.txt"), file.resolve("file.txt"), outside.resolve("other.txt"))) {
            S3TaskException failure = assertThrows(S3TaskException.class,
                    () -> S3Support.download(null, "bucket", "key", realRoot, target, true, context));
            assertEquals("S3_LOCAL_PATH", failure.code());
            assertEquals("prepare", failure.phase());
        }
        assertEquals("keep", Files.readString(sentinel));
        assertEquals("regular file", Files.readString(file));
        assertFalse(Files.exists(root.resolve("missing")));
        try (var entries = Files.list(empty)) {
            assertEquals(List.of(), entries.toList());
        }
        try (var entries = Files.list(outside)) {
            assertEquals(java.util.Set.of(empty, sentinel), new java.util.HashSet<>(entries.toList()));
        }
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
    void explicitUploadKeyRequiresOneSourceFileAndRejectsInvalidKeys() throws Exception {
        Path file = Files.writeString(root.resolve("one.txt"), "one");
        Path other = Files.writeString(root.resolve("two.txt"), "two");
        var builder = new S3UploadOperation.S3UploadBuilder();
        configure(builder);
        builder.setBucketName("bucket").setKey("downloads/snapshot.csv");

        assertThrows(IllegalArgumentException.class, () -> builder.setSourceDir(root).build());
        builder.setSourceDir(null);
        assertThrows(IllegalArgumentException.class,
                () -> builder.setSourceFiles(List.of(file)).build());
        assertThrows(IllegalArgumentException.class,
                () -> builder.setSourceFile(file).setSourceFiles(List.of(file, other)).build());
        builder.setSourceFiles(List.of());
        assertEquals("downloads/snapshot.csv", builder.build().key());
        assertThrows(IllegalArgumentException.class, () -> builder.setKey(" ").build());
        assertThrows(IllegalArgumentException.class, () -> builder.setKey("bad\0key").build());
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
