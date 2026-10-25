package ch.so.agi.gretl.internal.validation;

import java.nio.file.Path;
import java.util.List;

/** Normalized request for the four INTERLIS format validators. */
public record InterlisValidationRequest(
        List<Path> dataFiles,
        List<String> modelNames,
        List<String> modelDirectories,
        Path configFile,
        String configRepositoryId,
        Path metaConfigFile,
        String metaConfigRepositoryId,
        boolean forceTypeValidation,
        boolean disableAreaValidation,
        boolean multiplicityOff,
        boolean allObjectsAccessible,
        boolean skipPolygonBuilding,
        Path logFile,
        Path xtfLogFile,
        String proxy,
        Integer proxyPort,
        String tableName,
        boolean firstLineIsHeader,
        String valueDelimiter,
        String valueSeparator,
        String encoding) {
    public InterlisValidationRequest {
        dataFiles = List.copyOf(dataFiles);
        modelNames = List.copyOf(modelNames);
        modelDirectories = List.copyOf(modelDirectories);
    }
}
