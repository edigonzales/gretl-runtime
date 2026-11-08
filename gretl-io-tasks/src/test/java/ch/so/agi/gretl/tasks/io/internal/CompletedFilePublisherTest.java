package ch.so.agi.gretl.tasks.io.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompletedFilePublisherTest {
    @TempDir
    Path root;

    @Test
    void publishesCompletedBytesAndRemovesStagingName() throws IOException {
        Path source = Files.writeString(root.resolve("file.part"), "complete");
        Path target = root.resolve("file");
        CompletedFilePublisher.publish(source, target, false);
        assertEquals("complete", Files.readString(target));
        assertFalse(Files.exists(source));
    }

    @Test
    void refusesExistingAndDanglingTargetsWithoutModifyingThem() throws IOException {
        Path source = Files.writeString(root.resolve("file.part"), "complete");
        Path sentinel = Files.writeString(root.resolve("sentinel"), "foreign");
        Path target = root.resolve("file");
        assertThrows(FileAlreadyExistsException.class,
                () -> CompletedFilePublisher.publish(source, sentinel, false));
        for (Path referent : List.of(sentinel, root.resolve("missing"))) {
            Files.createSymbolicLink(target, referent);
            assertThrows(FileAlreadyExistsException.class,
                    () -> CompletedFilePublisher.publish(source, target, false));
            assertEquals(referent, Files.readSymbolicLink(target));
            Files.delete(target);
        }
        assertEquals("foreign", Files.readString(sentinel));
        assertEquals("complete", Files.readString(source));
    }

    @Test
    void onlyOneConcurrentPublisherCanWin() throws Exception {
        Path first = Files.writeString(root.resolve("first.part"), "first");
        Path second = Files.writeString(root.resolve("second.part"), "second");
        Path target = root.resolve("file");
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var firstWon = workers.submit(() -> publishAtBarrier(first, target, barrier));
            var secondWon = workers.submit(() -> publishAtBarrier(second, target, barrier));
            boolean won = firstWon.get(5, TimeUnit.SECONDS);
            assertEquals(!won, secondWon.get(5, TimeUnit.SECONDS));
            assertEquals(won ? "first" : "second", Files.readString(target));
            assertFalse(Files.exists(won ? first : second));
            assertTrue(Files.exists(won ? second : first));
        }
    }

    @Test
    void replacesRegularTargetWhenEnabled() throws IOException {
        Path source = Files.writeString(root.resolve("file.part"), "complete");
        Path target = Files.writeString(root.resolve("file"), "old");
        CompletedFilePublisher.publish(source, target, true);
        assertEquals("complete", Files.readString(target));
        assertFalse(Files.exists(source));
    }

    @Test
    void refusesNonRegularSourcesAndIdenticalPaths() throws IOException {
        Path directory = Files.createDirectory(root.resolve("directory"));
        Path link = Files.createSymbolicLink(root.resolve("link"), directory);
        Path source = Files.writeString(root.resolve("file.part"), "complete");
        Path target = root.resolve("file");
        for (Path invalid : List.of(directory, link)) {
            assertThrows(IOException.class, () -> CompletedFilePublisher.publish(invalid, target, false));
            assertFalse(Files.exists(target));
        }
        assertThrows(IOException.class, () -> CompletedFilePublisher.publish(source, source, true));
        assertEquals("complete", Files.readString(source));
    }

    @Test
    void failsClosedWhenProviderDoesNotSupportHardLinks() throws IOException {
        URI archive = URI.create("jar:" + root.resolve("staging.zip").toUri());
        try (var fs = FileSystems.newFileSystem(archive, Map.of("create", "true"))) {
            Path source = Files.writeString(fs.getPath("/source"), "complete");
            Path target = fs.getPath("/target");
            IOException failure = assertThrows(IOException.class,
                    () -> CompletedFilePublisher.publish(source, target, false));
            assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
            assertFalse(Files.exists(target));
            assertEquals("complete", Files.readString(source));
        }
    }

    @Test
    void retainsPublishedFileWhenUnlinkFails() throws IOException {
        Path source = Files.writeString(root.resolve("file.part"), "complete");
        Path target = root.resolve("file");
        IOException failure = assertThrows(IOException.class,
                () -> CompletedFilePublisher.publish(source, target, false, path -> {
                    assertEquals(source, path);
                    assertTrue(Files.isSameFile(source, target));
                    throw new IOException("fixture unlink failure");
                }));
        assertTrue(failure.getMessage().contains("was published"));
        assertEquals("fixture unlink failure", failure.getCause().getMessage());
        assertEquals("complete", Files.readString(target));
        assertEquals("complete", Files.readString(source));
    }

    private static boolean publishAtBarrier(Path source, Path target, CyclicBarrier barrier)
            throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            CompletedFilePublisher.publish(source, target, false);
            return true;
        } catch (FileAlreadyExistsException expected) {
            return false;
        }
    }
}
