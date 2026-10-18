package ch.so.agi.gretl.packager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageDescriptorTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsStrictDescriptorAndRejectsTraversalAndUnknownFields() throws Exception {
        Files.writeString(tempDir.resolve("job.groovy"), "action('run') { }\ndefaults 'run'\n");
        Files.createDirectories(tempDir.resolve("sql"));
        Files.writeString(tempDir.resolve("sql/update.sql"), "SELECT 1;\n");
        Path descriptor = tempDir.resolve("job-package.json");
        Files.writeString(descriptor, """
                {
                  "schemaVersion": 1,
                  "name": "demo",
                  "version": "2026.10.18",
                  "runtimeVersion": "0.1.0-SNAPSHOT",
                  "entryScript": "job.groovy",
                  "sources": [],
                  "resources": ["sql"],
                  "dependencies": ["ch.so.agi.gretl:gretl-basic-tasks:1.0.0"]
                }
                """);

        PackageDescriptor value = PackageDescriptor.load(descriptor);
        assertEquals("demo", value.name());
        assertEquals(1, value.resources().size());
        assertEquals("ch.so.agi.gretl", value.dependencies().getFirst().groupId());

        Path bad = tempDir.resolve("bad.json");
        Files.writeString(bad, """
                {"schemaVersion":1,"name":"demo","version":"1","runtimeVersion":"1",
                 "entryScript":"../job.groovy","sources":[],"resources":[],"dependencies":[]}
                """);
        PackageException error = assertThrows(PackageException.class,
                () -> PackageDescriptor.load(bad));
        assertEquals("PACKAGE_PATH_INVALID", error.code());

        Path unknown = tempDir.resolve("unknown.json");
        Files.writeString(unknown, """
                {"schemaVersion":1,"name":"demo","version":"1","runtimeVersion":"1",
                 "entryScript":"job.groovy","sources":[],"resources":[],"dependencies":[],
                 "extra":true}
                """);
        PackageException unknownError = assertThrows(PackageException.class,
                () -> PackageDescriptor.load(unknown));
        assertEquals("PACKAGE_DESCRIPTOR_UNKNOWN_FIELD", unknownError.code());
        assertTrue(unknownError.getMessage().contains("extra"));
    }
}
