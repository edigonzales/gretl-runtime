package ch.so.agi.gretl.runtime.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/** Creates diagnostic files with restricted permissions before any data is written. */
final class PrivateDiagnosticFiles {
    private PrivateDiagnosticFiles() {
    }

    static void createParents(Path parent) throws IOException {
        try {
            Files.createDirectories(parent, PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwx------")));
        } catch (UnsupportedOperationException unsupported) {
            Files.createDirectories(parent);
        }
    }

    static OutputStream newEventFile(Path path) throws IOException {
        var options = Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            return Channels.newOutputStream(Files.newByteChannel(path, options,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))));
        } catch (UnsupportedOperationException unsupported) {
            return Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
    }

    static Path newReportTemporary(Path parent, String prefix) throws IOException {
        try {
            return Files.createTempFile(parent, prefix, ".tmp", PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException unsupported) {
            return Files.createTempFile(parent, prefix, ".tmp");
        }
    }
}
