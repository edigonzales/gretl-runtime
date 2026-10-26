package ch.so.agi.gretl.tasks.interlis.validation;

import java.nio.file.Path;
import java.util.List;

/** Common request for the four INTERLIS format validators. */
public record ValidatorRequest(
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
        boolean failOnError,
        String tableName,
        boolean firstLineIsHeader,
        String valueDelimiter,
        String valueSeparator,
        String encoding) {
    public ValidatorRequest {
        dataFiles = List.copyOf(dataFiles);
        modelNames = List.copyOf(modelNames);
        modelDirectories = List.copyOf(modelDirectories);
    }
}
