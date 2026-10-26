package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Strict string-only parameter file; duplicate JSON keys are rejected by Json. */
public final class ParameterFile {
    private static final long MAX_BYTES = 16L * 1024 * 1024;

    private ParameterFile() {
    }

    public static Map<String, String> read(Path file) {
        return read(file, Locale.GERMAN);
    }

    public static Map<String, String> read(Path file, Locale locale) {
        if (file == null) {
            return Map.of();
        }
        if (!Files.isRegularFile(file)) {
            throw new StartException(2, Messages.format(locale,
                    "cli.error.parameterFile.notFound", file));
        }
        Object parsed;
        try {
            parsed = Json.parseFile(file, MAX_BYTES);
        } catch (IOException | IllegalArgumentException exception) {
            throw new StartException(2, Messages.format(locale,
                    "cli.error.parameterFile.invalid", exception.getMessage()), exception);
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new StartException(2, Messages.format(locale,
                    "cli.error.parameterFile.object"));
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String name)) {
                throw new StartException(2, Messages.format(locale,
                        "cli.error.parameterFile.nonStringKey"));
            }
            if (!(entry.getValue() instanceof String value)) {
                throw new StartException(2, Messages.format(locale,
                        "cli.error.parameterFile.nonStringValue", name));
            }
            result.put(name, value);
        }
        return Map.copyOf(result);
    }
}
