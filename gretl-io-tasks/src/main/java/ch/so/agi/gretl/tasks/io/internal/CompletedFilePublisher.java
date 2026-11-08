package ch.so.agi.gretl.tasks.io.internal;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/** Publishes a closed, task-owned file staged beside its destination. */
public final class CompletedFilePublisher {
    private CompletedFilePublisher() {
    }

    public static void publish(Path completedFile, Path target, boolean overwrite)
            throws IOException {
        publish(completedFile, target, overwrite, Files::delete);
    }

    static void publish(Path completedFile, Path target, boolean overwrite, Unlink unlink)
            throws IOException {
        Path source = completedFile.toAbsolutePath().normalize();
        Path destination = target.toAbsolutePath().normalize();
        if (source.equals(destination) || source.getParent() == null
                || !source.getParent().equals(destination.getParent())) {
            throw new IOException("Publish requires distinct source and target in the same directory");
        }
        if (!Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
                .isRegularFile()) {
            throw new IOException("Publish source must be a regular file");
        }
        if (overwrite) {
            replace(source, destination);
            return;
        }
        try {
            Files.createLink(destination, source);
        } catch (UnsupportedOperationException exception) {
            throw new IOException("File system provider does not support safe non-replacing publication",
                    exception);
        }
        try {
            unlink.delete(source);
        } catch (IOException exception) {
            throw new IOException("File was published, but temporary file cleanup failed", exception);
        }
    }

    @FunctionalInterface
    interface Unlink {
        void delete(Path source) throws IOException;
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            if (!Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
                    .isRegularFile()) {
                throw new IOException("Publish target must be a regular file when it exists");
            }
        } catch (NoSuchFileException absent) {
            // A new target is also valid when overwrite is enabled.
        }
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
