package ch.so.agi.gretl.tasks.io.archive;

import ch.so.agi.gretl.tasks.io.TestOperationContext;

import ch.so.agi.gretl.tasks.io.http.*;
import ch.so.agi.gretl.tasks.io.archive.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GzipOperationTest {
    @TempDir
    Path tempDir;

    @Test
    void compressesAndReturnsFileAndBytes() throws Exception {
        Path source = tempDir.resolve("input.xml");
        Files.writeString(source, "hello gzip", StandardCharsets.UTF_8);
        Path target = tempDir.resolve("out/input.xml.gz");

        GzipOperation.GzipResult result = new GzipOperation.GzipHandler().execute(
                request(source, target, true), context());

        assertEquals(target.toAbsolutePath().normalize(), result.file());
        assertTrue(result.bytes() > 0);
        assertEquals("hello gzip", gunzip(target));
    }

    @Test
    void compressesAnEmptyFile() throws Exception {
        Path source = tempDir.resolve("empty.txt");
        Files.createFile(source);
        Path target = tempDir.resolve("empty.txt.gz");

        new GzipOperation.GzipHandler().execute(request(source, target, true), context());

        assertEquals("", gunzip(target));
    }

    @Test
    void refusesExistingTargetWithoutOverwriteAndReplacesWithOverwrite() throws Exception {
        Path source = tempDir.resolve("input.txt");
        Files.writeString(source, "new", StandardCharsets.UTF_8);
        Path target = tempDir.resolve("input.txt.gz");
        Files.writeString(target, "old", StandardCharsets.UTF_8);

        GzipTaskException exists = assertThrows(GzipTaskException.class,
                () -> new GzipOperation.GzipHandler().execute(
                        request(source, target, false), context()));
        assertEquals("GZIP_TARGET_EXISTS", exists.code());
        assertEquals("old", Files.readString(target, StandardCharsets.UTF_8));

        new GzipOperation.GzipHandler().execute(request(source, target, true), context());
        assertEquals("new", gunzip(target));
    }

    @Test
    void rejectsMissingInputSamePathAndDirectoryTarget() throws Exception {
        Path missing = tempDir.resolve("missing.txt");
        GzipTaskException input = assertThrows(GzipTaskException.class,
                () -> new GzipOperation.GzipHandler().execute(
                        request(missing, tempDir.resolve("out.gz"), true), context()));
        assertEquals("GZIP_INPUT", input.code());

        Path source = tempDir.resolve("same.txt");
        Files.writeString(source, "x", StandardCharsets.UTF_8);
        GzipTaskException same = assertThrows(GzipTaskException.class,
                () -> new GzipOperation.GzipHandler().execute(
                        request(source, source, true), context()));
        assertEquals("GZIP_TARGET_INVALID", same.code());

        Path directory = Files.createDirectory(tempDir.resolve("directory"));
        GzipTaskException unsafe = assertThrows(GzipTaskException.class,
                () -> new GzipOperation.GzipHandler().execute(
                        request(source, directory, true), context()));
        assertEquals("GZIP_TARGET_UNSAFE", unsafe.code());
    }

    @Test
    void createsParentDirectoriesAndLeavesNoTemporaryFile() throws Exception {
        Path source = tempDir.resolve("nested-input.txt");
        Files.writeString(source, "data", StandardCharsets.UTF_8);
        Path target = tempDir.resolve("a/b/c/nested-input.txt.gz");

        new GzipOperation.GzipHandler().execute(request(source, target, true), context());

        assertTrue(Files.isRegularFile(target));
        try (var files = Files.list(target.getParent())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".part")));
        }
    }

    @Test
    void metadataCarriesRequiredFieldsAndOutputs() {
        var type = GzipOperation.type();
        assertTrue(type.inputs().stream().anyMatch(field -> field.name().equals("dataFile")
                && field.required()));
        assertTrue(type.inputs().stream().anyMatch(field -> field.name().equals("gzipFile")
                && field.required()));
        assertEquals(true, type.inputs().stream()
                .filter(field -> field.name().equals("overwrite"))
                .findFirst().orElseThrow().defaultValue().orElseThrow());
        assertTrue(type.outputs().stream().anyMatch(field -> field.name().equals("file")));
        assertTrue(type.outputs().stream().anyMatch(field -> field.name().equals("bytes")));
        assertTrue(type.example().contains("Gzip"));
    }

    private GzipOperation.GzipRequest request(Path dataFile, Path gzipFile,
                                              boolean overwrite) {
        GzipOperation.GzipBuilder builder = new GzipOperation.GzipBuilder();
        builder.setDataFile(dataFile);
        builder.setGzipFile(gzipFile);
        builder.setOverwrite(overwrite);
        return builder.build();
    }

    private TestOperationContext context() {
        return new TestOperationContext(tempDir, Map.of(), Map.of(), Map.of());
    }

    private static String gunzip(Path path) throws IOException {
        try (InputStream input = new GZIPInputStream(Files.newInputStream(path))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
