package ch.so.agi.gretl.runtime.qualification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.api.ConfigBuilder;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationProvider;
import ch.so.agi.gretl.runtime.api.OperationType;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TC-A02/M0: the FileSize operation must be discovered from a separately built
 * JAR via the real ServiceLoader mechanism, not from test source classes.
 */
class FileSizeExtensionTest {

    @TempDir
    Path tempDir;

    @Test
    void extensionJarIsDiscoveredByServiceLoaderAndMeasuresARealFile() throws Exception {
        Path jar = extensionJar();
        assertTrue(Files.isRegularFile(jar), "separately built extension JAR exists: " + jar);

        try (JarFile jarFile = new JarFile(jar.toFile())) {
            assertNotNull(jarFile.getEntry(
                    "META-INF/services/ch.so.agi.gretl.runtime.api.OperationProvider"));
            assertNotNull(jarFile.getEntry("example/ExampleProvider.class"));
            assertFalse(jarFile.stream().anyMatch(entry ->
                    entry.getName().startsWith("ch/so/agi/gretl/runtime/api/")),
                    "extension JAR must not embed the API classes");
        }

        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("example.ExampleProvider", false,
                        getClass().getClassLoader()),
                "provider class must not be on the test classpath");

        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{jar.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> providerClass = Class.forName("example.ExampleProvider", true, loader);
            assertEquals(loader, providerClass.getClassLoader());

            List<OperationProvider> providers = ServiceLoader
                    .load(OperationProvider.class, loader)
                    .stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();
            assertEquals(1, providers.size());
            OperationProvider provider = providers.get(0);
            assertEquals("example.ExampleProvider", provider.getClass().getName());

            OperationType<?, ?> operation = provider.operations().iterator().next();
            assertEquals("example.file-size", operation.id());
            assertEquals("FileSize", operation.symbol());
            assertFalse(operation.description().isBlank());
            assertEquals("input", operation.inputs().get(0).name());
            assertEquals("path", operation.inputs().get(0).valueType());
            assertTrue(operation.inputs().get(0).required());
            assertEquals("Datei, deren Grösse gelesen wird",
                    operation.inputs().get(0).description());
            assertTrue(operation.inputs().get(0).example().contains("input"));
            assertEquals("bytes", operation.outputs().get(0).name());
            assertEquals("integer", operation.outputs().get(0).valueType());
            assertFalse(operation.outputs().get(0).nullable());
            assertFalse(operation.example().isBlank());

            byte[] content = "Äpfel & Öl\n".getBytes(StandardCharsets.UTF_8);
            Path file = tempDir.resolve("messung.txt");
            Files.write(file, content);
            Object measured = execute(operation, file);
            assertEquals((long) content.length, measured);
            assertEquals(Files.readAllBytes(file).length, ((Long) measured).longValue());
            assertEquals(Files.size(file), ((Long) measured).longValue());

            Path second = tempDir.resolve("zweite-messung.txt");
            Files.writeString(second, "zweite Messung", StandardCharsets.UTF_8);
            assertEquals(Files.size(second), ((Long) execute(operation, second)).longValue());
        }
    }

    @Test
    void handlerAndBuilderFactoriesReturnFreshInstances() {
        Path jar = extensionJar();
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{jar.toUri().toURL()}, getClass().getClassLoader())) {
            OperationProvider provider = ServiceLoader.load(OperationProvider.class, loader)
                    .iterator().next();
            OperationType<?, ?> operation = provider.operations().iterator().next();
            assertNotSame(operation.builderFactory().get(), operation.builderFactory().get());
            assertNotSame(operation.handlerFactory().get(), operation.handlerFactory().get());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private Path extensionJar() {
        String property = System.getProperty("m0.extension.jar");
        assertNotNull(property, "m0.extension.jar system property");
        return Path.of(property).toAbsolutePath().normalize();
    }

    private static <C, R> Object execute(OperationType<C, R> operation, Path input) throws Exception {
        ConfigBuilder<C> builder = operation.builderFactory().get();
        InputField<C, ?> field = operation.inputs().get(0);
        apply(field, builder, input);
        C request = builder.build();
        assertInstanceOf(operation.requestType(), request);
        R result = operation.handlerFactory().get().execute(request,
                new TestOperationContext(Path.of("target", "work"), Map.of()));
        assertInstanceOf(operation.resultType(), result);
        return operation.outputs().get(0).read().apply(result);
    }

    private static <C, V> void apply(InputField<C, V> field, ConfigBuilder<C> builder, Object raw) {
        field.assign().accept(builder, field.convert().apply(raw));
    }
}
