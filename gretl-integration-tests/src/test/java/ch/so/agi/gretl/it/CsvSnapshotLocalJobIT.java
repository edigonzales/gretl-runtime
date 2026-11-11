package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CsvSnapshotLocalJobIT {
    private static final String HEADER = "BFS_Nr;Jahr;Anzahl";

    @Test
    void validatesTheCompleteAppendInInputOrder() throws Exception {
        var base = List.of("2602;2025;200", "2601;2025;100");
        var incoming = List.of("2601;2026;110");
        String log = validate("snapshot-append", "SnapshotMinimum", base, incoming,
                List.of(HEADER, base.get(0), base.get(1), incoming.getFirst()), 0);
        assertTrue(log.contains("object count 3 (structured elements 0)"), log);
        assertTrue(log.contains("validation done"), log);
    }

    @Test
    void detectsModelledUniqueConstraintAcrossBaseAndIncoming() throws Exception {
        var base = List.of("2601;2025;100");
        var incoming = List.of("2601;2025;999");
        String log = validate("snapshot-duplicate", "SnapshotMinimum", base, incoming,
                List.of(HEADER, base.getFirst(), incoming.getFirst()), 10);
        assertTrue(log.contains("Unique constraint SnapshotMinimum.Data.Observation.Constraint1 is violated! Values 2601, 2025 already exist"), log);
    }

    @ParameterizedTest
    @CsvSource({"SnapshotMinimum,10", "SnapshotOptional,0"})
    void headerOnlySnapshotUsesTheModelledMinimum(String model, int expectedExit) throws Exception {
        String log = validate("snapshot-empty-" + model, model, List.of(), List.of(),
                List.of(HEADER), expectedExit);
        if (expectedExit == 10) {
            assertTrue(log.contains("Set Constraint SnapshotMinimum.Data.Observation.Minimum is not true."), log);
        } else {
            assertTrue(log.contains("object count 0 (structured elements 0)"), log);
            assertTrue(log.contains("validation done"), log);
        }
    }

    private static String validate(String id, String model, List<String> base, List<String> incoming,
                                   List<String> expectedSnapshot, int expectedExit) throws Exception {
        Path root = ArtifactJobRunner.directory(id);
        Path output = root.resolve("output");
        writeCsv(root.resolve("base.csv"), base);
        writeCsv(root.resolve("incoming.csv"), incoming);
        var result = ArtifactJobRunner.run(new ArtifactJobRunner.Request(id, ArtifactJobRunner.fixture("csv-snapshot"),
                root, List.of(), Map.of("input", root, "output", output, "model", model),
                Map.of(), JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(60)));
        assertEquals(expectedExit, result.code(), result.err());
        assertEquals(expectedSnapshot, Files.readAllLines(output.resolve("snapshot.csv")));
        return Files.readString(output.resolve("validation.log"));
    }

    private static void writeCsv(Path path, List<String> rows) throws Exception {
        var csv = new ArrayList<String>();
        csv.add(HEADER);
        csv.addAll(rows);
        Files.write(path, csv);
    }
}
