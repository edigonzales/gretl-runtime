package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The checkout is canonical; shipped copies must agree except documented CWD instructions. */
class ExampleConsistencyTest {
    @TempDir Path temp;
    private static final Set<String> REQUIRED = Set.of(
            "action-nodata/nodata.groovy", "action-nodata/optional.groovy",
            "download-extract/data.zip", "download-extract/job.groovy", "project-pipeline/README.md",
            "project-pipeline/checks/local/job.groovy", "project-pipeline/job-package.json",
            "project-pipeline/job.groovy", "project-pipeline/prepare/data/input.txt",
            "project-pipeline/prepare/job.groovy", "project-pipeline/publish/job.groovy",
            "project-pipeline/settings.gretl", "sql/job.groovy", "sql/runtime.json.example",
            "sql/sql/update.sql", "token-catalog/job.groovy");

    @Test void shippedExamplesMatchCanonicalCheckoutSources() throws Exception {
        compare(Path.of("../examples"), Path.of("../gretl-dist/src/main/dist/examples"), REQUIRED);
    }

    @Test void detectsScriptDriftAndMissingShippedSources() throws Exception {
        Path source = Files.createDirectories(temp.resolve("source"));
        Path shipped = Files.createDirectories(temp.resolve("shipped"));
        Files.writeString(source.resolve("job.groovy"), "action('hello') {}\n");
        Files.writeString(shipped.resolve("job.groovy"), "action('different') {}\n");
        assertThrows(AssertionError.class, () -> compare(source, shipped, Set.of("job.groovy")));
        Files.delete(shipped.resolve("job.groovy"));
        assertThrows(AssertionError.class, () -> compare(source, shipped, Set.of("job.groovy")));
    }

    private static void compare(Path source, Path shipped, Set<String> required) throws Exception {
        for (String name : required) {
            assertTrue(Files.isRegularFile(source.resolve(name)), "Missing canonical example: " + name);
            assertTrue(Files.isRegularFile(shipped.resolve(name)), "Missing shipped example: " + name);
        }
        Set<String> common = new TreeSet<>();
        try (var paths = Files.walk(source)) {
            paths.filter(Files::isRegularFile).map(source::relativize).map(Path::toString)
                    .filter(name -> Files.isRegularFile(shipped.resolve(name))).forEach(common::add);
        }
        assertFalse(common.isEmpty());
        for (String name : common) {
            if (name.equals("project-pipeline/README.md")) {
                assertTrue(Files.readString(source.resolve(name)).contains("Vom Repository-Root:"));
                assertTrue(Files.readString(shipped.resolve(name)).contains("Vom entpackten Distributionsroot:"));
            } else {
                assertArrayEquals(Files.readAllBytes(source.resolve(name)), Files.readAllBytes(shipped.resolve(name)),
                        "Example drift; update canonical checkout source and its shipped copy: " + name);
            }
        }
    }
}
