package ch.so.agi.gretl.job;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JobResourcePreparationTest {
    @TempDir Path root;

    private JobJarMain.PackagedJobSource source(String resource) {
        var metadata = new JobJarMain.JobMetadata(1, "fixture", "1", "0.1.0-SNAPSHOT", 25,
                "Fixture", "job.groovy", JobJarMain.RESOURCE_ROOT, List.of(resource), List.of(), "hash");
        ClassLoader loader = new ClassLoader() {
            @Override public InputStream getResourceAsStream(String name) {
                return name.equals(JobJarMain.RESOURCE_ROOT + "models/local.ili")
                        ? new ByteArrayInputStream("resource".getBytes(java.nio.charset.StandardCharsets.UTF_8)) : null;
            }
        };
        return new JobJarMain.PackagedJobSource(metadata, root.resolve("logical"), loader, Locale.ENGLISH);
    }

    @Test void materializesInsideOwnedRunWithPrivateDirectories() throws Exception {
        Path run = Files.createDirectory(root.resolve("run"));
        Path job = source("models/local.ili").prepareRun(run);
        assertEquals(run.resolve("job-resources"), job);
        assertEquals("resource", Files.readString(job.resolve("models/local.ili")));
        if (Files.getFileStore(run).supportsFileAttributeView("posix")) {
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(job));
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(job.resolve("models")));
        }
        assertFalse(Files.exists(root.resolve("logical")));
        assertThrows(IOException.class, () -> source("models/local.ili").prepareRun(run));
    }

    @ParameterizedTest @ValueSource(strings = {"../escape", "/absolute", "models/../../escape", "models\\escape"})
    void rejectsUnsafeResourceEntries(String name) throws Exception {
        Path run = Files.createDirectory(root.resolve("run"));
        var failure = assertThrows(IOException.class, () -> source(name).prepareRun(run));
        assertTrue(failure.getMessage().contains("JOB_RESOURCE_PATH"), failure.getMessage());
        assertFalse(Files.exists(root.resolve("escape")));
    }

    @Test void missingResourceIsAnIoSetupFailure() throws Exception {
        Path run = Files.createDirectory(root.resolve("run"));
        var failure = assertThrows(IOException.class, () -> source("missing.txt").prepareRun(run));
        assertTrue(failure.getMessage().contains("JOB_RESOURCE_MISSING"));
    }
}
