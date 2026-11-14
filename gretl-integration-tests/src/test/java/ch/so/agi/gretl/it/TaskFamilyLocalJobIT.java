package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskFamilyLocalJobIT {
    @Test
    void singleTargetAndAggregateRunInScript() throws Exception {
        run(ArtifactJobRunner.Mode.SCRIPT, List.of("write_b"));
        run(ArtifactJobRunner.Mode.SCRIPT, List.of());
    }

    static void run(ArtifactJobRunner.Mode mode, List<String> targets) throws Exception {
        Path root = ArtifactJobRunner.directory("task-family", mode);
        Path source = mode == ArtifactJobRunner.Mode.JAR
                ? SelectedPackageSupport.fixture(root, "task-family", Map.of(), List.of(), false)
                : ArtifactJobRunner.fixture("task-family");
        var request = new ArtifactJobRunner.Request("task-family", source, root, targets,
                Map.of("output", root.resolve("output")), Map.of(), JobRunner.baseConfig(root), Map.of(),
                Duration.ofSeconds(75));
        var result = mode == ArtifactJobRunner.Mode.JAR ? SelectedPackageSupport.detached(request)
                : ArtifactJobRunner.run(request, mode);
        assertEquals(0, result.code(), result.err());
        Path output = result.directory().resolve("output");
        assertEquals("b", Files.readString(output.resolve("b.txt")));
        if (targets.isEmpty()) {
            assertEquals("a", Files.readString(output.resolve("a.txt")));
            assertEquals("c", Files.readString(output.resolve("c.txt")));
            assertEquals("abc", Files.readString(output.resolve("collected.txt")));
        } else {
            assertFalse(Files.exists(output.resolve("a.txt")));
            assertFalse(Files.exists(output.resolve("c.txt")));
            assertFalse(Files.exists(output.resolve("collected.txt")));
        }
    }
}
