package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

/** Independent class/resource checks of the actual JAR, in addition to input provenance. */
final class PackageArtifactAssertions {
    private PackageArtifactAssertions() { }
    static final Map<String, String> PROVIDERS = Map.of(
            "io", "ch.so.agi.gretl.tasks.io.IoTaskProvider",
            "database", "ch.so.agi.gretl.tasks.database.DatabaseTaskProvider",
            "interlis", "ch.so.agi.gretl.tasks.interlis.InterlisTaskProvider",
            "ilivalidator", "ch.so.agi.gretl.tasks.ilivalidator.IliValidatorProvider");
    static void verify(Path file, Path descriptorFile) throws Exception {
        var descriptor = (Map<?, ?>) Json.parseFile(descriptorFile, 1L << 20);
        boolean legacy = Long.valueOf(1).equals(descriptor.get("schemaVersion"));
        Set<String> selected = new HashSet<>();
        if (legacy) selected.addAll(PROVIDERS.keySet());
        else if (descriptor.get("taskModules") instanceof List<?> list) for (Object value : list) selected.add((String) value);
        try (JarFile jar = new JarFile(file.toFile())) {
            Set<String> expectedProviders = new HashSet<>(); selected.forEach(module -> expectedProviders.add(PROVIDERS.get(module)));
            var service = jar.getJarEntry("META-INF/services/ch.so.agi.gretl.runtime.api.OperationProvider");
            Set<String> actual = new HashSet<>();
            if (service != null) new String(jar.getInputStream(service).readAllBytes(), StandardCharsets.UTF_8)
                    .lines().filter(line -> !line.isBlank() && !line.startsWith("#")).forEach(actual::add);
            // Explicit external providers are checked by their own extension qualification.
            if (!(descriptor.get("dependencies") instanceof List<?> dependencies) || dependencies.isEmpty()) assertEquals(expectedProviders, actual);
            else assertTrue(actual.containsAll(expectedProviders));
            for (var provider : PROVIDERS.entrySet()) assertEquals(selected.contains(provider.getKey()),
                    jar.getJarEntry(provider.getValue().replace('.', '/') + ".class") != null, provider.getKey());
            assertNull(jar.getJarEntry("ch/so/agi/gretl/cli/GretlCli.class"));
            assertNull(jar.getJarEntry("ch/so/agi/gretl/packager/JobPackager.class"));
            assertNull(jar.getJarEntry("org/apache/maven/cli/MavenCli.class"));
            assertNotNull(jar.getJarEntry("ch/so/agi/gretl/job/JobJarMain.class"));
            assertNotNull(jar.getJarEntry("ch/so/agi/gretl/i18n/messages.properties"));
            var logging = jar.getJarEntry("META-INF/services/org.slf4j.spi.SLF4JServiceProvider");
            assertNotNull(logging);
            assertEquals(List.of("ch.qos.logback.classic.spi.LogbackServiceProvider"),
                    new String(jar.getInputStream(logging).readAllBytes(), StandardCharsets.UTF_8).lines().filter(line -> !line.isBlank()).toList());
            if (selected.equals(Set.of("io"))) {
                present(jar, "software/amazon/awssdk/services/s3/S3Client.class", "net/sf/saxon/TransformerFactoryImpl.class",
                        "org/apache/commons/net/ftp/FTPClient.class", "xslt/eCH0132_to_SO_AGI_SGV_Meldungen_20221109.xsl");
                absent(jar, "org/postgresql/Driver.class", "org/duckdb/DuckDBDriver.class", "ch/interlis/ili2c/Main.class");
            }
            if (selected.equals(Set.of("database"))) {
                present(jar, "org/postgresql/Driver.class", "org/duckdb/DuckDBDriver.class");
                absent(jar, "software/amazon/awssdk/services/s3/S3Client.class", "net/sf/saxon/TransformerFactoryImpl.class", "ch/interlis/ili2c/Main.class");
            }
            if (selected.equals(Set.of("interlis"))) {
                present(jar, "org/postgresql/Driver.class", "org/sqlite/JDBC.class", "org/duckdb/DuckDBDriver.class");
                absent(jar, "software/amazon/awssdk/services/s3/S3Client.class", "net/sf/saxon/TransformerFactoryImpl.class");
            }
            if (selected.equals(Set.of("ilivalidator"))) {
                present(jar, "org/interlis2/validator/Validator.class");
                absent(jar, "ch/interlis/ili2db/base/AbstractMain.class", "org/duckdb/DuckDBDriver.class", "org/apache/poi/xssf/usermodel/XSSFWorkbook.class");
            }
            if (selected.isEmpty()) absent(jar, "org/postgresql/Driver.class", "org/duckdb/DuckDBDriver.class",
                    "ch/interlis/ili2c/Main.class", "software/amazon/awssdk/services/s3/S3Client.class", "net/sf/saxon/TransformerFactoryImpl.class");
            Map<?, ?> inventory = (Map<?, ?>) Json.parse(new String(jar.getInputStream(jar.getJarEntry("META-INF/gretl/dependencies.json")).readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(2L, inventory.get("schemaVersion"));
            Set<String> identities = new HashSet<>();
            for (Object value : (List<?>) inventory.get("included")) {
                Map<?, ?> artifact = (Map<?, ?>) value;
                assertTrue(identities.add((String) artifact.get("coordinate")), "Unique Maven input identity");
                assertTrue(((String) artifact.get("sha256")).matches("[0-9a-f]{64}"));
                assertEquals("jar", artifact.get("kind"));
                assertFalse(Set.of("gretl-cli", "gretl-packager").contains(artifact.get("artifactId")));
            }
            var metadata = (Map<?, ?>) Json.parse(new String(jar.getInputStream(jar.getJarEntry("META-INF/gretl/job.json")).readAllBytes(), StandardCharsets.UTF_8));
            boolean project = Long.valueOf(3).equals(descriptor.get("schemaVersion"));
            assertEquals(project ? 2L : 1L, metadata.get("schemaVersion"));
            if (project) {
                assertEquals(true, metadata.get("project")); assertFalse(metadata.containsKey("entryClass"));
                assertFalse(metadata.containsKey("entryScript")); assertEquals("settings.gretl", metadata.get("settingsFile"));
                Set<Object> classes = new HashSet<>();
                for (Object value : (List<?>) metadata.get("entries")) {
                    var entry = (Map<?, ?>) value;
                    assertTrue(classes.add(entry.get("entryClass")));
                    present(jar, entry.get("entryClass").toString().replace('.', '/') + ".class",
                            "META-INF/gretl/sources/" + entry.get("entryScript"));
                }
                assertFalse(classes.isEmpty());
            } else present(jar, metadata.get("entryClass").toString().replace('.', '/') + ".class");
            for (Object value : (List<?>) metadata.get("resourceFiles")) present(jar, "META-INF/gretl/job-resources/" + value);
            java.nio.file.Files.write(file.getParent().resolve("package-inventory.json"),
                    jar.getInputStream(jar.getJarEntry("META-INF/gretl/dependencies.json")).readAllBytes());
            java.nio.file.Files.write(file.getParent().resolve("job-metadata.json"),
                    jar.getInputStream(jar.getJarEntry("META-INF/gretl/job.json")).readAllBytes());
            java.nio.file.Files.copy(descriptorFile, file.getParent().resolve("package-descriptor.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private static void present(JarFile jar, String... entries) { for (String entry : entries) assertNotNull(jar.getJarEntry(entry), entry); }
    private static void absent(JarFile jar, String... entries) { for (String entry : entries) assertNull(jar.getJarEntry(entry), entry); }
}
