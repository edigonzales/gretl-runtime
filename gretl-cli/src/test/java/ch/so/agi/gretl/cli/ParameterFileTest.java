package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParameterFileTest {
    @TempDir
    Path tempDir;

    @Test
    void readsOnlyStringValuesAndRejectsDuplicates() throws Exception {
        Path file = tempDir.resolve("params.json");
        Files.writeString(file, "{\"a\":\"x\",\"b\":\"\"}");
        assertEquals(Map.of("a", "x", "b", ""), ParameterFile.read(file));

        Files.writeString(file, "{\"a\":1}");
        assertThrows(CliException.class, () -> ParameterFile.read(file));
        Files.writeString(file, "{\"a\":\"x\",\"a\":\"y\"}");
        assertThrows(CliException.class, () -> ParameterFile.read(file));
        Files.writeString(file, "[]");
        assertThrows(CliException.class, () -> ParameterFile.read(file));
    }
}
