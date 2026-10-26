package ch.so.agi.gretl.packager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                  "runtimeVersion": "0.1.0-SNAPSHOT",
                  "entryScript": "job.groovy",
                  "sources": ["helpers"],
                  "resources": [],
                  "dependencies": []
                }
                """);

        PackageDescriptor packageDescriptor = PackageDescriptor.load(descriptor);
        JobCompiler.CompiledJob compiled = new JobCompiler().compile(packageDescriptor, JobPackager.currentClasspath());

        assertEquals("job", compiled.entryClass());
        assertTrue(compiled.classes().containsKey("job.class"));
        assertTrue(compiled.classes().keySet().stream()
                .anyMatch(name -> name.startsWith("helpers/Greeting")));
        assertFalse(Files.exists(marker), "packaging must not execute top-level script code");
        assertEquals(64, compiled.scriptSha256().length());
        assertTrue(compiled.sources().stream()
                .anyMatch(source -> source.relativePath().equals("job.groovy")));
    }
}
