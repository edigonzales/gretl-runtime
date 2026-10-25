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
                  "dependencies": ["ch.so.agi.gretl:gretl-database-tasks:1.0.0"]
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
    @Test
    void versionTwoHasExplicitEmptySelectionAndSupportsClassifiedArtifacts() throws Exception {
        Files.writeString(tempDir.resolve("job.groovy"), "action('run') { }\ndefaults 'run'\n");
        for (String selection : java.util.List.of("", ",\"taskModules\":[]", ",\"taskModules\":[\"io\",\"ilivalidator\"],\"standardFunctions\":true")) {
            PackageDescriptor value = loadSelection(2, selection,
                    "[\"example:provider:1.2.3\",\"example:functions:jar:standard:4.5.6\"]");
            assertEquals(selection.contains("io") ? java.util.List.of("io", "ilivalidator") : java.util.List.of(), value.taskModules());
            assertEquals(selection.contains("true"), value.standardFunctions());
            assertEquals("example:functions:jar:standard:4.5.6", value.dependencies().get(1).coordinate());
        }
    }

    @Test
    void rejectsAmbiguousSelectionAndUnpinnedDependencyDeclarations() throws Exception {
        Files.writeString(tempDir.resolve("job.groovy"), "action('run') { }\ndefaults 'run'\n");
        for (String selection : java.util.List.of(
                ",\"taskModules\":[\"unknown\"]", ",\"taskModules\":[\"io\",\"io\"]",
                ",\"taskModules\":\"io\"", ",\"standardFunctions\":\"true\"")) {
            assertThrows(PackageException.class, () -> loadSelection(2, selection, "[]"), selection);
        }
        for (String selection : java.util.List.of(",\"taskModules\":[]", ",\"standardFunctions\":false")) {
            assertEquals("PACKAGE_SELECTION_VERSION", assertThrows(PackageException.class,
                    () -> loadSelection(1, selection, "[]")).code());
        }
        Path overflow = tempDir.resolve("overflow.json");
        Files.writeString(overflow, "{\"schemaVersion\":4294967297}");
        assertEquals("PACKAGE_FIELD_TYPE", assertThrows(PackageException.class, () -> PackageDescriptor.load(overflow)).code());
        for (String dependencies : java.util.List.of(
                "[\"g:a\"]", "[\"g:a:[]\"]", "[\"g:a:LATEST\"]", "[\"g:a:RELEASE\"]",
                "[\"g:a:1.*\"]", "[\"g:a:1-SNAPSHOT\"]", "[\"g:a:war:classifier:1\"]",
                "[\"g:a:1\",\"g:a:2\"]")) {
            assertThrows(PackageException.class, () -> loadSelection(2, "", dependencies), dependencies);
        }
    }

    private PackageDescriptor loadSelection(int schemaVersion, String selection, String dependencies) throws Exception {
        Path descriptor = tempDir.resolve("selection.json");
        Files.writeString(descriptor, "{\"schemaVersion\":" + schemaVersion
                + ",\"name\":\"selection\",\"version\":\"1\",\"runtimeVersion\":\"1\","
                + "\"entryScript\":\"job.groovy\",\"dependencies\":" + dependencies + selection + "}");
        return PackageDescriptor.load(descriptor);
    }

}
