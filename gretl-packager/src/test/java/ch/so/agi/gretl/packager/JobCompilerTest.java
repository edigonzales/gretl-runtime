package ch.so.agi.gretl.packager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JobCompilerTest {
    @TempDir
    Path tempDir;

    @Test
    void compilesEntryAndHelperWithoutRunningTopLevelCode() throws Exception {
        Path marker = tempDir.resolve("executed.txt");
        Files.writeString(tempDir.resolve("job.groovy"), """
                import helpers.Greeting
                new java.io.File('%s').text = 'executed'
                parameter('name', description: 'Name')
                action('run') { return Greeting.text(parameter('name')) }
                defaults 'run'
                """.formatted(marker.toString().replace("\\", "\\\\")));
        Files.createDirectories(tempDir.resolve("helpers"));
        Files.writeString(tempDir.resolve("helpers/Greeting.groovy"), """
                package helpers
                class Greeting {
                    static String text(String name) { "Hallo " + name }
                }
                """);
        Path descriptor = tempDir.resolve("job-package.json");
        Files.writeString(descriptor, """
                {
                  "schemaVersion": 1,
                  "name": "compiler-demo",
                  "version": "2026.10.18",
                  "runtimeVersion": "6.0.0-SNAPSHOT",
                  "entryScript": "job.groovy",
                  "sources": ["helpers"],
                  "resources": [],
                  "dependencies": []
                }
                """);

        PackageDescriptor packageDescriptor = PackageDescriptor.load(descriptor);
        BundleCompiler.Result compiled = BundleCompiler.compile(packageDescriptor, JobPackager.currentClasspath(), java.util.Locale.GERMAN);

        assertTrue(compiled.bundle().edges().isEmpty());
        var entry = compiled.bundle().sources().getFirst();
        assertEquals("job.groovy", entry.sourceId());
        assertTrue(compiled.classes().containsKey(entry.className() + ".class"));
        assertTrue(compiled.classes().keySet().stream()
                .anyMatch(name -> name.startsWith("helpers/Greeting")));
        assertFalse(Files.exists(marker), "packaging must not execute top-level script code");
        assertEquals(64, compiled.bundle().sha256().length());
        assertTrue(compiled.sources().stream()
                .anyMatch(source -> source.relativePath().equals("job.groovy")));
    }

    @Test
    void reportsGroovyCompilerDiagnosticsForSyntaxErrorsWithoutApply() throws Exception {
        Path script = tempDir.resolve("job.groovy");
        Files.writeString(script, "action('run') { }\n}");
        Path descriptor = tempDir.resolve("job-package.json");
        Files.writeString(descriptor, """
                {"schemaVersion":1,"name":"syntax","version":"1","runtimeVersion":"6.0.0-SNAPSHOT","entryScript":"job.groovy"}
                """);
        var error = assertThrows(PackageException.class, () -> BundleCompiler.compile(
                PackageDescriptor.load(descriptor), JobPackager.currentClasspath(), java.util.Locale.ENGLISH));
        assertEquals("PACKAGE_COMPILE_FAILED", error.code());
        assertTrue(error.getMessage().contains("job.groovy: 2"), error.getMessage());
    }
}
