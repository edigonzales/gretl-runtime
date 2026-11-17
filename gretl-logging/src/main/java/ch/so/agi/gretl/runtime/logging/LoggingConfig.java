package ch.so.agi.gretl.runtime.logging;

import ch.so.agi.gretl.runtime.api.LogLevel;
import java.util.LinkedHashMap;
import java.util.Map;

/** Validated logging section of the runtime configuration. */
public record LoggingConfig(
        Format format, LogLevel level, Map<String, LogLevel> libraryLevels,
        boolean eventFile, int maxRecordBytes, long maxEventFileBytes) {

    public enum Format { TEXT, JSON }

    public static final int DEFAULT_MAX_RECORD_BYTES = 16 * 1024;
    public static final long DEFAULT_MAX_EVENT_FILE_BYTES = 128L * 1024 * 1024;

    public LoggingConfig {
        java.util.Objects.requireNonNull(format, "format");
        java.util.Objects.requireNonNull(level, "level");
        libraryLevels = Map.copyOf(libraryLevels);
        if (maxRecordBytes < 4096 || maxRecordBytes > 65536) {
            throw new IllegalArgumentException("maxRecordBytes must be between 4096 and 65536");
        }
        if (maxEventFileBytes < 1024L * 1024 || maxEventFileBytes > 1024L * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "maxEventFileBytes must be between 1048576 and 1073741824");
        }
        for (Map.Entry<String, LogLevel> entry : libraryLevels.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getKey().chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("libraryLevels prefix must not be blank/control");
            }
            java.util.Objects.requireNonNull(entry.getValue(), "library level");
        }
    }

    public static LoggingConfig defaults() {
        return new LoggingConfig(Format.TEXT, LogLevel.INFO, Map.of(), true,
                DEFAULT_MAX_RECORD_BYTES, DEFAULT_MAX_EVENT_FILE_BYTES);
    }

    public LoggingConfig withOverrides(Format formatOverride, LogLevel levelOverride) {
        return new LoggingConfig(
                formatOverride == null ? format : formatOverride,
                levelOverride == null ? level : levelOverride,
                libraryLevels, eventFile, maxRecordBytes, maxEventFileBytes);
    }

    public LoggingConfig withLibraryLevels(Map<String, LogLevel> levels) {
        return new LoggingConfig(format, level, levels, eventFile, maxRecordBytes,
                maxEventFileBytes);
    }

    public boolean enables(LogLevel messageLevel) {
        return messageLevel.ordinal() >= level.ordinal();
    }

    public static LoggingConfig withEventFile(LoggingConfig base, boolean enabled) {
        return new LoggingConfig(base.format, base.level, base.libraryLevels, enabled,
                base.maxRecordBytes, base.maxEventFileBytes);
    }

    public static Map<String, LogLevel> emptyLibraryLevels() {
        return new LinkedHashMap<>();
    }
}
