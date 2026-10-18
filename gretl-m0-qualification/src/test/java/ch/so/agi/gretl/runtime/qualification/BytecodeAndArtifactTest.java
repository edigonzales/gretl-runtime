package ch.so.agi.gretl.runtime.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

/**
 * M0 artifact check: the two built JARs are real Java 25 artifacts, the API JAR
 * contains exactly the public SPI surface, and the extension JAR carries only
 * its own provider plus the ServiceLoader metadata.
 */
class BytecodeAndArtifactTest {

    private static final int JAVA_25_MAJOR = 69;

    @Test
    void apiJarIsJava25AndContainsTheTwentySevenPublicApiClasses() throws Exception {
        Path apiJar = Path.of("..", "gretl-api", "target", "gretl-api-0.1.0-SNAPSHOT.jar")
                .toAbsolutePath().normalize();
        assertTrue(java.nio.file.Files.isRegularFile(apiJar), "API JAR exists: " + apiJar);

        try (JarFile jar = new JarFile(apiJar.toFile())) {
            List<String> classes = jar.stream()
                    .map(JarEntry::getName)
                    .filter(name -> name.endsWith(".class"))
                    .filter(name -> !name.startsWith("META-INF/"))
                    .sorted()
                    .toList();
            assertEquals(27, classes.size(), "chapter 14 defines 27 API files: " + classes);
            for (String entry : classes) {
                assertEquals(JAVA_25_MAJOR, majorVersion(jar, entry), entry);
            }
            assertEquals("ch.so.agi.gretl.runtime.api",
                    jar.getManifest().getMainAttributes().getValue("Automatic-Module-Name"));
            assertFalse(jar.stream().anyMatch(entry ->
                            entry.getName().startsWith("META-INF/services/")),
                    "the API JAR must not register a provider");
        }
    }

    @Test
    void extensionJarIsJava25AndCarriesExactlyTheServiceMetadata() throws Exception {
        String property = System.getProperty("m0.extension.jar");
        assertNotNull(property);
        Path extensionJar = Path.of(property).toAbsolutePath().normalize();
        assertTrue(java.nio.file.Files.isRegularFile(extensionJar));

        try (JarFile jar = new JarFile(extensionJar.toFile())) {
            List<String> classes = new ArrayList<>();
            for (JarEntry entry : jar.stream().toList()) {
                if (entry.getName().endsWith(".class")) {
                    classes.add(entry.getName());
                    assertEquals(JAVA_25_MAJOR, majorVersion(jar, entry.getName()), entry.getName());
                }
            }
            assertTrue(classes.contains("example/ExampleProvider.class"));
            assertTrue(classes.stream().noneMatch(name ->
                    name.startsWith("ch/so/agi/gretl/runtime/api/")));
            JarEntry service = jar.getJarEntry(
                    "META-INF/services/ch.so.agi.gretl.runtime.api.OperationProvider");
            assertNotNull(service);
            try (InputStream input = jar.getInputStream(service)) {
                assertEquals("example.ExampleProvider",
                        new String(input.readAllBytes(), StandardCharsets.UTF_8).trim());
            }
        }
    }

    private static int majorVersion(JarFile jar, String entry) throws IOException {
        try (InputStream input = jar.getInputStream(jar.getJarEntry(entry));
             DataInputStream data = new DataInputStream(input)) {
            assertEquals(0xCAFEBABE, data.readInt(), entry + " has a class-file magic number");
            data.readUnsignedShort(); // minor
            return data.readUnsignedShort();
        }
    }
}
