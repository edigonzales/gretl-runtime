package ch.so.agi.gretl.cli;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Strict string-only parameter file; duplicate JSON keys are rejected by Json. */
public final class ParameterFile {
    private static final long MAX_BYTES = 16L * 1024 * 1024;

    private ParameterFile() {
    }

    public static Map<String, String> read(Path file) {
        if (file == null) {
            return Map.of();
        }
        if (!Files.isRegularFile(file)) {
            throw new CliException(2, "Parameterdatei nicht gefunden: " + file);
        }
        Object parsed;
        try {
            parsed = Json.parseFile(file, MAX_BYTES);
        } catch (IOException | IllegalArgumentException exception) {
            throw new CliException(2, "Parameterdatei ist ungueltig: "
                    + exception.getMessage(), exception);
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new CliException(2, "Parameterdatei muss ein JSON-Objekt sein");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String name)) {
                throw new CliException(2, "Parameterdatei enthaelt einen Nichtstring-Schluessel");
            }
            if (!(entry.getValue() instanceof String value)) {
                throw new CliException(2, "Parameterdatei." + name + " muss ein String sein");
            }
            result.put(name, value);
        }
        return Map.copyOf(result);
    }
}
