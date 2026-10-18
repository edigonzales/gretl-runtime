package ch.so.agi.gretl.tasks.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtractOperationTest {
    @TempDir
    Path tempDir;

    @Test
    void unzipsKnownContentWithSortedFilesAndByteSum() throws Exception {
        Path zip = tempDir.resolve("data.zip");
        writeZip(zip, Map.of(
                "b.txt", "BB".getBytes(StandardCharsets.UTF_8),
                "a/x.txt", "AAA".getBytes(StandardCharsets.UTF_8)));
        Path out = tempDir.resolve("out");
        ExtractOperation.ExtractRequest request = zipRequest(zip, out);
        ExtractResult result = new ExtractOperation.ExtractHandler()
                .execute(request, context());

        assertEquals(List.of(out.resolve("a/x.txt"), out.resolve("b.txt")),
                result.files());
        assertEquals(5, result.bytes());
        assertEquals(out, result.directory());
        assertNull(result.file());
        assertEquals("AAA", Files.readString(out.resolve("a/x.txt")));
        assertEquals("BB", Files.readString(out.resolve("b.txt")));
    }

    @Test
    void emptyZipIsValid() throws Exception {
        Path zip = tempDir.resolve("empty.zip");
        writeZip(zip, Map.of());
        ExtractResult result = new ExtractOperation.ExtractHandler()
                .execute(zipRequest(zip, tempDir.resolve("empty-out")), context());
        assertTrue(result.files().isEmpty());
        assertEquals(0, result.bytes());
    }

    @Test
    void gzipWritesExactFileAndResult() throws Exception {
        Path gzip = tempDir.resolve("data.gz");
        byte[] content = "GZIP-content".getBytes(StandardCharsets.UTF_8);
        try (OutputStream raw = Files.newOutputStream(gzip);
             GZIPOutputStream output = new GZIPOutputStream(raw)) {
            output.write(content);
        }
        Path target = tempDir.resolve("out.csv");
        ExtractOperation.ExtractBuilder builder = new ExtractOperation.ExtractBuilder();
        builder.setArchive(gzip);
        builder.setCompression("GZIP");
        builder.setOutputFile(target);
        ExtractResult result = new ExtractOperation.ExtractHandler()
                .execute(builder.build(), context());

        assertEquals(content.length, result.bytes());
        assertEquals(target, result.file());
        assertEquals(List.of(target), result.files());
        assertNull(result.directory());
        assertEquals("GZIP-content", Files.readString(target));
    }

    @Test
    void traversalSymlinkCollisionAndLimitsFailWithoutTouchingSentinel() throws Exception {
        Path sentinel = tempDir.resolve("outside.txt");
        Files.writeString(sentinel, "keep");
        Path traversal = tempDir.resolve("traversal.zip");
        writeZip(traversal, Map.of("../outside.txt", "bad".getBytes(StandardCharsets.UTF_8)));
        assertThrows(ExtractTaskException.class, () -> new ExtractOperation.ExtractHandler()
                .execute(zipRequest(traversal, tempDir.resolve("traversal-out")), context()));
        assertEquals("keep", Files.readString(sentinel));

        Path symlink = tempDir.resolve("symlink.zip");
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(
                Files.newOutputStream(symlink))) {
            ZipArchiveEntry entry = new ZipArchiveEntry("link");
            entry.setUnixMode(UnixStat.LINK_FLAG | 0777);
            output.putArchiveEntry(entry);
            output.write("outside.txt".getBytes(StandardCharsets.UTF_8));
            output.closeArchiveEntry();
        }
        assertThrows(ExtractTaskException.class, () -> new ExtractOperation.ExtractHandler()
                .execute(zipRequest(symlink, tempDir.resolve("symlink-out")), context()));

        Path collision = tempDir.resolve("collision.zip");
        writeZip(collision, new LinkedHashMap<>(Map.of(
                "a.txt", "a".getBytes(StandardCharsets.UTF_8),
                "A.TXT", "b".getBytes(StandardCharsets.UTF_8))));
        assertThrows(ExtractTaskException.class, () -> new ExtractOperation.ExtractHandler()
                .execute(zipRequest(collision, tempDir.resolve("collision-out")), context()));

        Path limit = tempDir.resolve("limit.zip");
        writeZip(limit, Map.of("x", "12345".getBytes(StandardCharsets.UTF_8)));
        ExtractOperation.ExtractBuilder builder = new ExtractOperation.ExtractBuilder();
        builder.setArchive(limit);
        builder.setCompression("ZIP");
        builder.setOutputDirectory(tempDir.resolve("limit-out"));
        builder.setMaxExpandedBytes(2L);
        assertThrows(ExtractTaskException.class, () -> new ExtractOperation.ExtractHandler()
                .execute(builder.build(), context()));

        Path entries = tempDir.resolve("entries.zip");
        writeZip(entries, Map.of(
                "a", "a".getBytes(StandardCharsets.UTF_8),
                "b", "b".getBytes(StandardCharsets.UTF_8)));
        ExtractOperation.ExtractBuilder many = new ExtractOperation.ExtractBuilder();
        many.setArchive(entries);
        many.setCompression("ZIP");
        many.setOutputDirectory(tempDir.resolve("entries-out"));
        many.setMaxEntries(1);
        assertThrows(ExtractTaskException.class, () -> new ExtractOperation.ExtractHandler()
                .execute(many.build(), context()));
    }

    @Test
    void existingTargetAndMalformedArchiveFail() throws Exception {
        Path zip = tempDir.resolve("one.zip");
        writeZip(zip, Map.of("x.txt", "x".getBytes(StandardCharsets.UTF_8)));
        Path out = tempDir.resolve("existing-out");
        Files.createDirectories(out);
        Files.writeString(out.resolve("x.txt"), "keep");
        assertThrows(ExtractTaskException.class, () -> new ExtractOperation.ExtractHandler()
                .execute(zipRequest(zip, out), context()));
        assertEquals("keep", Files.readString(out.resolve("x.txt")));

        Path broken = tempDir.resolve("broken.zip");
        Files.writeString(broken, "not a zip");
        assertThrows(ExtractTaskException.class, () -> new ExtractOperation.ExtractHandler()
                .execute(zipRequest(broken, tempDir.resolve("broken-out")), context()));
    }

    @Test
    void builderValidatesModeSpecificOutput() {
        ExtractOperation.ExtractBuilder zip = new ExtractOperation.ExtractBuilder();
        zip.setArchive(tempDir.resolve("a.zip"));
        zip.setCompression("ZIP");
        zip.setOutputFile(tempDir.resolve("out"));
        assertThrows(IllegalArgumentException.class, zip::build);

        ExtractOperation.ExtractBuilder gzip = new ExtractOperation.ExtractBuilder();
        gzip.setArchive(tempDir.resolve("a.gz"));
        gzip.setCompression("GZIP");
        gzip.setOutputDirectory(tempDir.resolve("out"));
        assertThrows(IllegalArgumentException.class, gzip::build);
    }

    private ExtractOperation.ExtractRequest zipRequest(Path zip, Path out) {
        ExtractOperation.ExtractBuilder builder = new ExtractOperation.ExtractBuilder();
        builder.setArchive(zip);
        builder.setCompression("ZIP");
        builder.setOutputDirectory(out);
        return builder.build();
    }

    private static void writeZip(Path path, Map<String, byte[]> entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }

    private TestOperationContext context() {
        return new TestOperationContext(tempDir, Map.of(), Map.of(), Map.of());
    }
}
