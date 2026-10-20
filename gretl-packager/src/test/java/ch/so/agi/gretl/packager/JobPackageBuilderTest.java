package ch.so.agi.gretl.packager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JobPackageBuilderTest {
    @TempDir
    Path tempDir;

    @Test
    void mergesServicesAndWritesMetadataWithoutRuntimeDependencies() throws Exception {
        Files.writeString(tempDir.resolve("job.groovy"),
                "action('run') { }\ndefaults 'run'\n");
        Path descriptorFile = tempDir.resolve("job-package.json");
        Files.writeString(descriptorFile, """
                {"schemaVersion":1,"name":"builder-demo","version":"1",
                 "runtimeVersion":"0.1.0-SNAPSHOT","entryScript":"job.groovy",
                 "sources":[],"resources":[],"dependencies":[]}
                """);
        PackageDescriptor descriptor = PackageDescriptor.load(descriptorFile);
        Path first = Files.createDirectories(tempDir.resolve("classes-a"));
        Path second = Files.createDirectories(tempDir.resolve("classes-b"));
        writeMainClass(first);
        writeMainClass(second);
        write(first, "META-INF/services/example.Service", "example.One\n");
        write(second, "META-INF/services/example.Service", "example.Two\n");
        write(first, "META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule",
                "moduleName=one\nmoduleVersion=1\n");

        JobCompiler.CompiledJob compiled = new JobCompiler.CompiledJob("job",
                Map.of("job.class", new byte[] {1, 2, 3}),
                List.of(new JobCompiler.SourceFile("job.groovy",
                        tempDir.resolve("job.groovy"), "action('run') { }")), "a".repeat(64));
        Path output = tempDir.resolve("out/job.jar");
        var result = new JobPackageBuilder(List.of(first, second), "0.1.0-SNAPSHOT")
                .build(descriptor, compiled, output);

        assertEquals("job", result.entryClass());
        assertTrue(Files.isRegularFile(output));
        try (JarFile jar = new JarFile(output.toFile())) {
            assertTrue(jar.getEntry("META-INF/gretl/job.json") != null);
            assertTrue(jar.getEntry("META-INF/gretl/dependencies.json") != null);
            String services = new String(jar.getInputStream(
                    jar.getEntry("META-INF/services/example.Service")).readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(services.contains("example.One"));
            assertTrue(services.contains("example.Two"));
            String manifest = new String(jar.getInputStream(
                    jar.getEntry("META-INF/MANIFEST.MF")).readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(manifest.contains("ch.so.agi.gretl.job.JobJarMain"));
            String groovy = new String(jar.getInputStream(jar.getEntry(
                    "META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule"))
                    .readAllBytes(), StandardCharsets.ISO_8859_1);
            assertTrue(groovy.contains("moduleName"));
        }
    }

    @Test
    void conflictingClassBytesAreRejectedWithBothOrigins() throws Exception {
        Files.writeString(tempDir.resolve("job.groovy"),
                "action('run') { }\ndefaults 'run'\n");
        Path descriptorFile = tempDir.resolve("job-package.json");
        Files.writeString(descriptorFile, """
                {"schemaVersion":1,"name":"conflict-demo","version":"1",
                 "runtimeVersion":"0.1.0-SNAPSHOT","entryScript":"job.groovy",
                 "sources":[],"resources":[],"dependencies":[]}
                """);
        PackageDescriptor descriptor = PackageDescriptor.load(descriptorFile);
        Path first = Files.createDirectories(tempDir.resolve("conflict-a"));
        Path second = Files.createDirectories(tempDir.resolve("conflict-b"));
        writeMainClass(first);
        writeMainClass(second);
        write(first, "same/Thing.class", "first");
        write(second, "same/Thing.class", "second");
        JobCompiler.CompiledJob compiled = new JobCompiler.CompiledJob("job",
                Map.of("job.class", new byte[] {1}), List.of(), "a".repeat(64));

        PackageException error = assertThrows(PackageException.class,
                () -> new JobPackageBuilder(List.of(first, second), "0.1.0-SNAPSHOT")
                        .build(descriptor, compiled, tempDir.resolve("conflict.jar")));
        assertEquals("PACKAGE_CLASS_CONFLICT", error.code());
        assertTrue(error.getMessage().contains("conflict-a"));
        assertTrue(error.getMessage().contains("conflict-b"));
    }

    @Test
    void prefersCanonicalIoxIliModelClassesOverEmbeddedCopies() throws Exception {
        Files.writeString(tempDir.resolve("job.groovy"),
                "action('run') { }\ndefaults 'run'\n");
        Path descriptorFile = tempDir.resolve("job-package.json");
        Files.writeString(descriptorFile, """
                {"schemaVersion":1,"name":"model-copy-demo","version":"1",
                 "runtimeVersion":"0.1.0-SNAPSHOT","entryScript":"job.groovy",
                 "sources":[],"resources":[],"dependencies":[]}
                """);
        PackageDescriptor descriptor = PackageDescriptor.load(descriptorFile);
        Path canonical = Files.createDirectories(tempDir.resolve("iox-ili-1.24.4"));
        Path embedded = Files.createDirectories(tempDir.resolve("av2geobau-1.4.2"));
        writeMainClass(canonical);
        write(embedded, "ch/so/agi/gretl/job/JobJarMain.class", "main");
        write(canonical, "ch/interlis/models/INTERLIS/VALIGNMENT.class", "canonical");
        write(embedded, "ch/interlis/models/INTERLIS/VALIGNMENT.class", "embedded");
        JobCompiler.CompiledJob compiled = new JobCompiler.CompiledJob("job",
                Map.of("job.class", new byte[] {1}), List.of(), "a".repeat(64));
        Path output = tempDir.resolve("model-copy.jar");
        new JobPackageBuilder(List.of(canonical, embedded), "0.1.0-SNAPSHOT")
                .build(descriptor, compiled, output);
        try (JarFile jar = new JarFile(output.toFile())) {
            String value = new String(jar.getInputStream(jar.getEntry(
                    "ch/interlis/models/INTERLIS/VALIGNMENT.class")).readAllBytes(),
                    StandardCharsets.UTF_8);
            assertEquals("canonical", value);
        }
    }

    private static void writeMainClass(Path root) throws Exception {
        write(root, "ch/so/agi/gretl/job/JobJarMain.class", "main");
    }

    private static void write(Path root, String name, String value) throws Exception {
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, value);
    }
}
