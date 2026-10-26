package ch.so.agi.gretl.runtime.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticFilePermissionsTest {
    @TempDir
    Path directory;

    @Test
    void eventFileIsPrivateAndExistingParentPermissionsStayUnchanged() throws Exception {
        boolean posix = Files.getFileStore(directory).supportsFileAttributeView("posix");
        if (posix) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        Path eventFile = directory.resolve("run.events.jsonl");
        try (EventFileSink ignored = new EventFileSink(eventFile, LoggingConfig.defaults(), context())) {
            if (posix) {
                assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(eventFile)));
                assertEquals("rwxr-xr-x", PosixFilePermissions.toString(Files.getPosixFilePermissions(directory)));
            }
        }
    }

    @Test
    void refusesExistingEventFileWithoutTruncatingIt() throws Exception {
        Path eventFile = Files.writeString(directory.resolve("run.events.jsonl"), "preserve");
        assertThrows(IOException.class, () -> new EventFileSink(eventFile, LoggingConfig.defaults(), context()));
        assertEquals("preserve", Files.readString(eventFile));
    }

    private static LogContext context() {
        return new LogContext("permissions", UUID.randomUUID(), "job", "test", Optional.empty());
    }
}
