package ch.so.agi.gretl.packager;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectPackageDescriptorTest {
    @TempDir Path root;
    @Test void requiresExactlyOneEntryFieldForSchemaOne() throws Exception {
        Files.writeString(root.resolve("settings.gretl"), ""); Files.writeString(root.resolve("job.groovy"), "action('run') {}\n");
        Path file = root.resolve("job-package.json");
        var fields = new LinkedHashMap<String,Object>(Map.of("schemaVersion",1,"name","project","version","1","runtimeVersion","6.0.0-SNAPSHOT","settingsFile","settings.gretl"));
        Files.writeString(file,Json.write(fields)); var descriptor=PackageDescriptor.load(file);
        assertTrue(descriptor.projectPackage());
        assertThrows(IllegalStateException.class,descriptor::entryPath);
        for (String invalid : List.of("other.gretl","../settings.gretl","/settings.gretl")) {
            fields.put("settingsFile",invalid);Files.writeString(file,Json.write(fields));
            assertEquals("PACKAGE_SETTINGS_INVALID",assertThrows(PackageException.class,()->PackageDescriptor.load(file)).code());
        }
        fields.put("settingsFile","settings.gretl"); fields.put("entryScript","job.groovy"); Files.writeString(file,Json.write(fields));
        assertEquals("PACKAGE_DESCRIPTOR_SCHEMA",assertThrows(PackageException.class,()->PackageDescriptor.load(file)).code());
        fields.remove("entryScript"); fields.put("unknown",true);Files.writeString(file,Json.write(fields));
        assertEquals("PACKAGE_DESCRIPTOR_UNKNOWN_FIELD",assertThrows(PackageException.class,()->PackageDescriptor.load(file)).code());
        fields.remove("unknown");fields.remove("settingsFile");Files.writeString(file,Json.write(fields));
        assertEquals("PACKAGE_DESCRIPTOR_SCHEMA",assertThrows(PackageException.class,()->PackageDescriptor.load(file)).code());
        fields.put("entryScript","job.groovy");fields.put("schemaVersion",2);Files.writeString(file,Json.write(fields));
        assertEquals("PACKAGE_DESCRIPTOR_VERSION",assertThrows(PackageException.class,()->PackageDescriptor.load(file)).code());
        fields.put("schemaVersion",1);Files.writeString(file,Json.write(fields));assertEquals(root.resolve("job.groovy"),PackageDescriptor.load(file).entryPath());
    }
}
