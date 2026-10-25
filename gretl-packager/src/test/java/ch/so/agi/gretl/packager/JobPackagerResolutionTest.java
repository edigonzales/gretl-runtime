package ch.so.agi.gretl.packager;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Selection contracts; genuine Maven extension compilation is qualified by the artifact suite. */
class JobPackagerResolutionTest {
    @TempDir Path work;
    private Path catalog() throws Exception {
        var artifacts = new java.util.ArrayList<Map<String, Object>>();
        for (String name : List.of("runtime", "io", "foreign")) {
            Path jar = work.resolve("renamed-" + name + ".jar");
            try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
                output.putNextEntry(new JarEntry("marker/" + name)); output.write(name.getBytes()); output.closeEntry();
            }
            artifacts.add(Map.of("groupId", "test", "artifactId", name, "version", "1", "type", "jar", "classifier", "",
                    "file", jar.getFileName().toString(), "sha256", ResolvedArtifact.hash(jar), "dependencies", List.of()));
        }
        Path file = work.resolve("package-catalog.json");
        Files.writeString(file, Json.write(Map.of("schemaVersion", 1, "runtimeVersion", "0.1.0-SNAPSHOT", "runtime", "test:runtime:1",
                "taskModules", Map.of("io", "test:io:1"), "standardFunctions", List.of(), "artifacts", artifacts)));
        return file;
    }
    private PackageDescriptor descriptor(String fields) throws Exception {
        Files.writeString(work.resolve("job.groovy"), "action('run') { }\ndefaults 'run'\n");
        Path file = work.resolve("job-package.json");
        Files.writeString(file, "{\"schemaVersion\":2,\"name\":\"selection\",\"version\":\"1\",\"runtimeVersion\":\"0.1.0-SNAPSHOT\",\"entryScript\":\"job.groovy\"" + fields + "}");
        return PackageDescriptor.load(file, true);
    }
    @Test void missingSelectionIsMinimalAndRenamedInputsRetainMavenIdentity() throws Exception {
        var selected = new PackageCatalog(catalog(), "0.1.0-SNAPSHOT").select(descriptor(""));
        assertEquals(List.of("test:runtime:1"), selected.stream().map(a -> a.identity().coordinate()).toList());
        assertEquals("renamed-runtime.jar", selected.getFirst().file().getFileName().toString());
    }
    @Test void explicitSelectionIgnoresForeignHostClasspath() throws Exception {
        Path catalog = catalog(); var descriptor = descriptor(",\"taskModules\":[\"io\"]");
        String old = System.getProperty("java.class.path");
        try {
            var before = new PackageCatalog(catalog, "0.1.0-SNAPSHOT").select(descriptor);
            System.setProperty("java.class.path", old + java.io.File.pathSeparator + work.resolve("renamed-foreign.jar"));
            assertEquals(before, new PackageCatalog(catalog, "0.1.0-SNAPSHOT").select(descriptor));
            assertEquals(List.of("test:io:1", "test:runtime:1"), before.stream().map(a -> a.identity().coordinate()).toList());
        } finally { System.setProperty("java.class.path", old); }
    }
    @Test void changedQualifiedBytesAreRejectedBeforeCompilation() throws Exception {
        Path catalog = catalog(); Files.writeString(work.resolve("renamed-runtime.jar"), "changed");
        var error = assertThrows(PackageException.class, () -> new PackageCatalog(catalog, "0.1.0-SNAPSHOT").select(descriptor("")));
        assertEquals("PACKAGE_ARTIFACT_CHECKSUM", error.code());
    }
}
