package ch.so.agi.gretl.tasks.io.archive;

import static org.junit.jupiter.api.Assertions.*;

import ch.so.agi.gretl.runtime.api.ConfigBuilder;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.tasks.io.TestOperationContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipOperationTest {
    @TempDir
    Path root;

    @Test
    void combinesExplicitFilesWithSortedFilteredDirectoryEntries() throws Exception {
        Path sources = Files.createDirectory(root.resolve("inputs"));
        Files.writeString(sources.resolve("z.log"), "z");
        Files.writeString(sources.resolve("a.log"), "a");
        Files.writeString(sources.resolve("x.txt"), "ignored");
        Path xtf = Files.writeString(root.resolve("original.xtf"), "<data/>");
        Path target = root.resolve("delivery.zip");
        var result = run(target, List.of(Map.of("file", xtf, "entryName", "data.xtf"),
                Map.of("directory", sources, "includes", List.of("**/*.log"), "prefix", "logs")),
                false, false);
        assertEquals(3, result.filesCount());
        assertEquals(target, result.file());
        assertEquals(Files.size(target), result.bytes());
        try (var archive = new ZipFile(target.toFile())) {
            assertEquals(List.of("data.xtf", "logs/a.log", "logs/z.log"),
                    archive.stream().map(ZipEntry::getName).toList());
            assertContent(archive, "data.xtf", "<data/>");
            assertContent(archive, "logs/a.log", "a");
            assertContent(archive, "logs/z.log", "z");
        }
    }

    @Test
    void rejectsDuplicateUnsafeAndSelfIncludingEntriesWithoutReplacingOutput() throws Exception {
        Path source = Files.writeString(root.resolve("source.txt"), "source");
        Path target = Files.writeString(root.resolve("existing.zip"), "keep");
        assertThrows(IOException.class,
                () -> run(target, List.of(Map.of("file", source), Map.of("file", source)), true, false));
        assertThrows(IllegalArgumentException.class,
                () -> run(target, List.of(Map.of("file", source, "entryName", "../outside")), true, false));
        assertThrows(IOException.class,
                () -> run(target, List.of(Map.of("directory", root)), true, false));
        Path link = Files.createSymbolicLink(root.resolve("link.txt"), source);
        assertThrows(IOException.class,
                () -> run(target, List.of(Map.of("file", link)), true, false));
        assertThrows(IOException.class,
                () -> run(target, List.of(Map.of("file", root.resolve("missing"))), true, false));
        assertEquals("keep", Files.readString(target));
        try (var paths = Files.list(root)) {
            assertFalse(paths.anyMatch(path -> path.toString().endsWith(".part")));
        }
    }

    @Test
    void requiresExplicitPermissionForAnEmptyArchive() throws Exception {
        Path input = Files.createDirectory(root.resolve("inputs"));
        Path target = root.resolve("empty.zip");
        var sources = List.<Map<String, ?>>of(Map.of("directory", input, "includes", List.of("*.xtf")));
        assertThrows(IOException.class, () -> run(target, sources, false, false));
        assertFalse(Files.exists(target));
        assertEquals(0, run(target, sources, false, true).filesCount());
        try (var archive = new ZipFile(target.toFile())) {
            assertEquals(0, archive.size());
        }
    }

    private ZipOperation.ZipResult run(Path output, List<? extends Map<String, ?>> sources,
                                       boolean overwrite, boolean allowEmpty) throws Exception {
        var operation = ZipOperation.type();
        var builder = operation.builderFactory().get();
        var values = Map.of("outputFile", output, "sources", sources,
                "overwrite", overwrite, "allowEmpty", allowEmpty);
        for (var field : operation.inputs()) {
            assign(builder, field, values.get(field.name()));
        }
        return operation.handlerFactory().get().execute(builder.build(),
                new TestOperationContext(root, Map.of(), Map.of(), Map.of()));
    }

    private static <T> void assign(ConfigBuilder<ZipOperation.ZipRequest> builder,
                                   InputField<ZipOperation.ZipRequest, T> field, Object value) {
        field.assign().accept(builder, field.convert().apply(value));
    }

    private static void assertContent(ZipFile archive, String name, String expected) throws IOException {
        try (var input = archive.getInputStream(archive.getEntry(name))) {
            assertArrayEquals(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8), input.readAllBytes());
        }
    }
}
