package ch.so.agi.gretl.internal.interlis;

import ch.so.agi.gretl.internal.sql.DatabaseSpec;
import java.nio.file.Path;
import java.util.List;

/** Mutable builder for the wide ili2db request record. */
public final class Ili2DbRequestBuilder {
    public DatabaseSpec database;
    public Path databaseFile;
    public String schema;
    public List<String> modelNames = List.of();
    public List<String> modelDirectories = List.of();
    public List<String> baskets = List.of();
    public List<String> topics = List.of();
    public List<String> datasetNames = List.of();
    public List<Path> dataFiles = List.of();
    public List<String> repositoryDataIds = List.of();
    public Path iliFile;
    public Path iliMetaAttrsFile;
    public Path preScript;
    public Path postScript;
    public Path logFile;
    public Path validConfigFile;
    public boolean importTid;
    public boolean exportTid;
    public boolean importBid;
    public boolean deleteData;
    public boolean trace;
    public boolean disableValidation;
    public boolean disableAreaValidation;
    public boolean forceTypeValidation;
    public boolean strokeArcs;
    public boolean skipPolygonBuilding;
    public boolean skipGeometryErrors;
    public boolean iligml20;
    public boolean disableRounding;
    public boolean failOnException = true;
    public boolean export3;
    public boolean oneGeomPerTable;
    public boolean setupPgExt;
    public boolean createEnumTabs;
    public boolean createGeomIdx;
    public boolean createMetaInfo;
    public boolean coalesceJson;
    public boolean nameByTopic;
    public boolean createBasketCol;
    public boolean createTidCol;
    public boolean createDatasetCol;
    public boolean createFk;
    public boolean createFkIdx;
    public boolean createUnique;
    public boolean createStdCols;
    public String defaultSrsAuth;
    public String defaultSrsCode;
    public Path dropScript;
    public Path createScript;
    public String metaConfig;
    public String tidColumnName;
    public Integer maxNameLength;
    public Long idSeqMin;
    public Long idSeqMax;
    public String translation;
    public java.util.Map<String, Object> options = java.util.Map.of();

    public Ili2DbRequest build() {
        return new Ili2DbRequest(database, databaseFile, schema, modelNames, modelDirectories,
                baskets, topics, datasetNames, dataFiles, repositoryDataIds, iliFile,
                iliMetaAttrsFile, preScript, postScript, logFile, validConfigFile,
                importTid, exportTid, importBid, deleteData, trace, disableValidation,
                disableAreaValidation, forceTypeValidation, strokeArcs, skipPolygonBuilding,
                skipGeometryErrors, iligml20, disableRounding, failOnException, export3,
                oneGeomPerTable, setupPgExt, createEnumTabs, createGeomIdx, createMetaInfo,
                coalesceJson, nameByTopic, createBasketCol, createTidCol, createDatasetCol,
                createFk, createFkIdx, createUnique, createStdCols, defaultSrsAuth,
                defaultSrsCode, dropScript, createScript, metaConfig, tidColumnName,
                maxNameLength, idSeqMin, idSeqMax, translation, options);
    }
}
