package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParameterFileTest {
    @TempDir
    Path tempDir;

    @Test
    void parameterFileErrorsFollowTheRequestedLocale() throws Exception {
        Path file = tempDir.resolve("missing-params.json");
        CliException english = assertThrows(CliException.class,
                () -> ParameterFile.read(file, Locale.ENGLISH));
        assertTrue(english.getMessage().contains("Parameter file not found"),
                english.getMessage());
        CliException german = assertThrows(CliException.class,
                () -> ParameterFile.read(file, Locale.GERMAN));
        assertTrue(german.getMessage().contains("Parameterdatei nicht gefunden"),
                german.getMessage());
    }

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
