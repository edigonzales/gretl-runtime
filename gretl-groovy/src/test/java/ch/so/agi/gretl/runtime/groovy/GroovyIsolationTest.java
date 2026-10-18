package ch.so.agi.gretl.runtime.groovy;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class GroovyIsolationTest {
    private static final List<String> FORBIDDEN = List.of(
            "org.gradle", "org.slf4j", "org.geotools", "ch.interlis",
            "org.postgresql", "io.opentelemetry");

    @Test
    void apiAndRunnerRemainGroovyAndProductStackFree() throws IOException {
        assertNoImports(Path.of("..", "gretl-api", "src", "main", "java"), FORBIDDEN);
        assertNoImports(Path.of("..", "gretl-runner", "src", "main", "java"),
                concat(FORBIDDEN, List.of("groovy.")));
    }

    @Test
    void groovyAdapterContainsOnlyTheAdapterStack() throws IOException {
        assertNoImports(Path.of("src", "main", "java"), FORBIDDEN);
    }

    private static List<String> concat(List<String> left, List<String> right) {
        return Stream.concat(left.stream(), right.stream()).toList();
    }

    private static void assertNoImports(Path root, List<String> forbidden) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String token : forbidden) {
                    assertFalse(source.contains("import " + token),
                            () -> file + " must not import " + token);
                }
            }
        }
    }
}
