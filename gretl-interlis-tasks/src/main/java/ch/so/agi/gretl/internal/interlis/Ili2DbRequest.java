package ch.so.agi.gretl.internal.interlis;

import ch.so.agi.gretl.internal.sql.DatabaseSpec;
import java.nio.file.Path;
import java.util.List;

/** Normalized request for the ili2pg/ili2gpkg/ili2duckdb task family. */
public record Ili2DbRequest(
        DatabaseSpec database, Path databaseFile, String schema,
        List<String> modelNames, List<String> modelDirectories,
        List<String> baskets, List<String> topics, List<String> datasetNames,
        List<Path> dataFiles, List<String> repositoryDataIds,
        Path iliFile, Path iliMetaAttrsFile, Path preScript, Path postScript,
        Path logFile, Path validConfigFile,
        boolean importTid, boolean exportTid, boolean importBid,
        boolean deleteData, boolean trace, boolean disableValidation,
        boolean disableAreaValidation, boolean forceTypeValidation,
        boolean strokeArcs, boolean skipPolygonBuilding, boolean skipGeometryErrors,
        boolean iligml20, boolean disableRounding, boolean failOnException,
        boolean export3, boolean oneGeomPerTable, boolean setupPgExt,
        boolean createEnumTabs, boolean createGeomIdx, boolean createMetaInfo,
        boolean coalesceJson, boolean nameByTopic, boolean createBasketCol,
        boolean createTidCol, boolean createDatasetCol, boolean createFk,
        boolean createFkIdx, boolean createUnique, boolean createStdCols,
        String defaultSrsAuth, String defaultSrsCode, Path dropScript,
        Path createScript, String metaConfig, String tidColumnName,
        Integer maxNameLength, Long idSeqMin, Long idSeqMax, String translation,
        java.util.Map<String, Object> options) {
    public Ili2DbRequest {
        modelNames = List.copyOf(modelNames);
        modelDirectories = List.copyOf(modelDirectories);
        baskets = List.copyOf(baskets);
        topics = List.copyOf(topics);
        datasetNames = List.copyOf(datasetNames);
        dataFiles = List.copyOf(dataFiles);
        repositoryDataIds = List.copyOf(repositoryDataIds);
        options = options == null ? java.util.Map.of() : java.util.Map.copyOf(options);
    }
}
