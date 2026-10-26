package ch.so.agi.gretl.tasks.interlis.validation;

import ch.so.agi.gretl.runtime.api.ConfigBuilder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Mutable builder shared by the format validator operation types. */
public final class ValidatorBuilder implements ConfigBuilder<ValidatorRequest> {
    private List<Path> dataFiles = List.of();
    private List<String> modelNames = List.of();
    private List<String> modelDirectories = List.of();
    private Path configFile;
    private String configRepositoryId;
    private Path metaConfigFile;
    private String metaConfigRepositoryId;
    private boolean forceTypeValidation;
    private boolean disableAreaValidation;
    private boolean multiplicityOff;
    private boolean allObjectsAccessible;
    private boolean skipPolygonBuilding;
    private Path logFile;
    private Path xtfLogFile;
    private String proxy;
    private Integer proxyPort;
    private boolean failOnError = true;
    private String tableName;
    private boolean firstLineIsHeader = true;
    private String valueDelimiter;
    private String valueSeparator;
    private String encoding;

    public void setDataFiles(List<Path> value) { dataFiles = value == null ? List.of() : value; }
    public void setModelNames(List<String> value) { modelNames = value == null ? List.of() : value; }
    public void setModelDirectories(List<String> value) { modelDirectories = value == null ? List.of() : value; }
    public void setConfigFile(Path value) { configFile = value; }
    public void setConfigRepositoryId(String value) { configRepositoryId = value; }
    public void setMetaConfigFile(Path value) { metaConfigFile = value; }
    public void setMetaConfigRepositoryId(String value) { metaConfigRepositoryId = value; }
    public void setForceTypeValidation(Boolean value) { forceTypeValidation = Boolean.TRUE.equals(value); }
    public void setDisableAreaValidation(Boolean value) { disableAreaValidation = Boolean.TRUE.equals(value); }
    public void setMultiplicityOff(Boolean value) { multiplicityOff = Boolean.TRUE.equals(value); }
    public void setAllObjectsAccessible(Boolean value) { allObjectsAccessible = Boolean.TRUE.equals(value); }
    public void setSkipPolygonBuilding(Boolean value) { skipPolygonBuilding = Boolean.TRUE.equals(value); }
    public void setLogFile(Path value) { logFile = value; }
    public void setXtfLogFile(Path value) { xtfLogFile = value; }
    public void setProxy(String value) { proxy = value; }
    public void setProxyPort(Integer value) { proxyPort = value; }
    public void setFailOnError(Boolean value) { failOnError = value == null || value; }
    public void setTableName(String value) { tableName = value; }
    public void setFirstLineIsHeader(Boolean value) { firstLineIsHeader = value == null || value; }
    public void setValueDelimiter(String value) { valueDelimiter = value; }
    public void setValueSeparator(String value) { valueSeparator = value; }
    public void setEncoding(String value) { encoding = value; }

    @Override public ValidatorRequest build() {
        if (dataFiles.isEmpty()) {
            throw new IllegalArgumentException("dataFiles must not be empty");
        }
        if (configFile != null && configRepositoryId != null) {
            throw new IllegalArgumentException("Use either configFile or configRepositoryId");
        }
        if (metaConfigFile != null && metaConfigRepositoryId != null) {
            throw new IllegalArgumentException("Use either metaConfigFile or metaConfigRepositoryId");
        }
        return new ValidatorRequest(dataFiles, modelNames, modelDirectories, configFile,
                configRepositoryId, metaConfigFile, metaConfigRepositoryId,
                forceTypeValidation, disableAreaValidation, multiplicityOff,
                allObjectsAccessible, skipPolygonBuilding, logFile, xtfLogFile,
                proxy, proxyPort, failOnError, tableName, firstLineIsHeader,
                valueDelimiter, valueSeparator, encoding);
    }
}
