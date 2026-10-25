package ch.so.agi.gretl.internal.validation;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.internal.InterlisTaskException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLClassLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ValidationFunctionsTest {
    @TempDir Path root;
    private String previous;
    @BeforeEach void remember() { previous = System.getProperty(ValidationFunctions.PROPERTY); }
    @AfterEach void restore() {
        if (previous == null) System.clearProperty(ValidationFunctions.PROPERTY);
        else System.setProperty(ValidationFunctions.PROPERTY, previous);
    }
    @Test void standardSelectionComesFromAServiceResource() {
        System.clearProperty(ValidationFunctions.PROPERTY);
        var functions = ValidationFunctions.load(getClass().getClassLoader());
        assertEquals(18, functions.size());
        assertEquals(ch.so.agi.ilivalidator.ext.AreaIoxPlugin.class, functions.get("SO_FunctionsExt.area"));
    }
    @Test void explicitEmptySelectionDoesNotLoadTheDefaultBundles() {
        System.setProperty(ValidationFunctions.PROPERTY, "");
        assertTrue(ValidationFunctions.load(getClass().getClassLoader()).isEmpty());
    }
    @Test void nonexistentSelectedClassIsNotIgnored() {
        System.setProperty(ValidationFunctions.PROPERTY, "missing.FunctionIoxPlugin");
        var error = assertThrows(InterlisTaskException.class, () -> ValidationFunctions.load(getClass().getClassLoader()));
        assertEquals("VALIDATION_FUNCTION", error.code());
        assertTrue(error.getMessage().contains("missing.FunctionIoxPlugin"));
        assertInstanceOf(ClassNotFoundException.class, error.getCause());
    }
    @Test void brokenSelectedConstructorIsNotIgnored() {
        System.setProperty(ValidationFunctions.PROPERTY, Broken.class.getName());
        var error = assertThrows(InterlisTaskException.class, () -> ValidationFunctions.load(getClass().getClassLoader()));
        assertEquals("VALIDATION_FUNCTION", error.code());
        assertTrue(error.getMessage().contains(Broken.class.getName()));
    }
    @Test void differentImplementationsCannotClaimTheSameQualifiedName() {
        System.setProperty(ValidationFunctions.PROPERTY,
                ch.so.agi.ilivalidator.ext.AreaIoxPlugin.class.getName() + "," + Alias.class.getName());
        var error = assertThrows(InterlisTaskException.class, () -> ValidationFunctions.load(getClass().getClassLoader()));
        assertEquals("VALIDATION_FUNCTION_DUPLICATE", error.code());
        assertTrue(error.getMessage().contains("SO_FunctionsExt.area"));
        assertTrue(error.getMessage().contains(Alias.class.getName()));
    }
    @Test void defectiveServiceDescriptorIsNotIgnored() throws Exception {
        System.clearProperty(ValidationFunctions.PROPERTY);
        Path resource = root.resolve("META-INF/services/ch.interlis.iox_j.plugins.IoxPlugin");
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, "missing.ServiceIoxPlugin\n");
        try (var loader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()}, getClass().getClassLoader())) {
            var error = assertThrows(InterlisTaskException.class, () -> ValidationFunctions.load(loader));
            assertEquals("VALIDATION_FUNCTION", error.code());
            assertTrue(error.getMessage().contains("missing.ServiceIoxPlugin"));
        }
    }
    public static class Alias extends ch.so.agi.ilivalidator.ext.AreaIoxPlugin {}
    public static class Broken extends ch.so.agi.ilivalidator.ext.AreaIoxPlugin {
        public Broken() { throw new IllegalStateException("deliberate constructor failure"); }
    }
}
