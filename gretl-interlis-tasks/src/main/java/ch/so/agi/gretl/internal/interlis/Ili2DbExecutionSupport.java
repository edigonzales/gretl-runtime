package ch.so.agi.gretl.internal.interlis;

import ch.ehi.basics.logging.EhiLogger;
import ch.ehi.basics.logging.FileListener;
import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.base.Ili2dbException;
import ch.ehi.ili2db.gui.Config;
import ch.interlis.ili2c.gui.UserSettings;
import ch.interlis.iox_j.logging.FileLogger;
import ch.so.agi.gretl.internal.sql.DatabaseSpec;
import ch.so.agi.gretl.runtime.api.OperationContext;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;

/** Runtime adapter around ili2db for the ported task family. */
public final class Ili2DbExecutionSupport {
    public void executeSchemaImport(Ili2DbRequest request, Ili2DbFlavor flavor) {
        Config config = flavor.createConfig(request);
        applyCommon(config, request, Config.FC_SCHEMAIMPORT);
        applySchema(config, request);
        if (request.iliFile() != null) {
            config.setXtffile(request.iliFile().toString());
        } else if (request.modelNames().isEmpty() && request.metaConfig() == null) {
            throw new IllegalArgumentException("Configure either iliFile or modelNames for schema import");
        }
        if (request.iliMetaAttrsFile() != null) {
            config.setIliMetaAttrsFile(request.iliMetaAttrsFile().toString());
        }
        runOnce(request, flavor, config);
    }

    public void executeImport(Ili2DbRequest request, Ili2DbFlavor flavor, int operation) {
        List<String> inputs;
        if (!request.dataFiles().isEmpty() && !request.repositoryDataIds().isEmpty()) {
            throw new IllegalArgumentException("Use either dataFiles or repositoryDataIds, not both");
        }
        if (!request.dataFiles().isEmpty()) {
            inputs = request.dataFiles().stream().map(Path::toString).toList();
        } else {
            inputs = request.repositoryDataIds();
        }
        if (inputs.isEmpty()) return;
        for (int index = 0; index < inputs.size(); index++) {
            Config config = flavor.createConfig(request);
            applyCommon(config, request, operation);
            if (flavor == Ili2DbFlavor.GEOPACKAGE) {
                applyGpkg(config, request);
            }
            String dataset = dataset(request, index);
            config.setDatasetName(dataset);
            config.setXtffile(inputs.get(index));
            config.setItfTransferfile(!request.dataFiles().isEmpty()
                    && Ili2db.isItfFilename(inputs.get(index)));
            runOnce(request, flavor, config);
        }
    }

    public void executeExport(Ili2DbRequest request, Ili2DbFlavor flavor) {
        if (request.dataFiles().isEmpty()) return;
        for (int index = 0; index < request.dataFiles().size(); index++) {
            Path output = request.dataFiles().get(index).toAbsolutePath().normalize();
            createParent(output);
            Config config = flavor.createConfig(request);
            applyCommon(config, request, Config.FC_EXPORT);
            if (request.export3()) config.setVer3_export(true);
            config.setDatasetName(dataset(request, index));
            config.setXtffile(output.toString());
            config.setItfTransferfile(Ili2db.isItfFilename(output.toString()));
            runOnce(request, flavor, config);
        }
    }

    public void executeDatasetOperation(Ili2DbRequest request, Ili2DbFlavor flavor, int operation) {
        if (request.datasetNames().isEmpty()) {
            Config config = flavor.createConfig(request);
            applyCommon(config, request, operation);
            if (operation == Config.FC_DELETE) config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
            runOnce(request, flavor, config);
            return;
        }
        for (String dataset : request.datasetNames()) {
            Config config = flavor.createConfig(request);
            applyCommon(config, request, operation);
            if (operation == Config.FC_DELETE) config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
            config.setDatasetName(dataset);
            runOnce(request, flavor, config);
        }
    }

    private void applyCommon(Config config, Ili2DbRequest request, int operation) {
        config.setFunction(operation);
        EhiLogger.getInstance().setTraceFilter(!request.trace());
        if (request.schema() != null) config.setDbschema(request.schema());
        if (!request.modelDirectories().isEmpty()) {
            config.setModeldir(String.join(";", request.modelDirectories()));
        }
        if (!request.modelNames().isEmpty()) config.setModels(String.join(";", request.modelNames()));
        if (!request.baskets().isEmpty()) config.setBaskets(String.join(";", request.baskets()));
        if (!request.topics().isEmpty()) config.setTopics(String.join(";", request.topics()));
        if (request.importTid()) config.setImportTid(true);
        if (request.exportTid()) config.setExportTid(true);
        if (request.importBid()) config.setImportBid(true);
        if (request.preScript() != null) config.setPreScript(request.preScript().toString());
        if (request.postScript() != null) config.setPostScript(request.postScript().toString());
        if (request.deleteData()) config.setDeleteMode(Config.DELETE_DATA);
        if (request.validConfigFile() != null) config.setValidConfigFile(request.validConfigFile().toString());
        if (request.disableValidation()) config.setValidation(false);
        if (request.disableAreaValidation()) config.setDisableAreaValidation(true);
        if (request.forceTypeValidation()) config.setOnlyMultiplicityReduction(true);
        if (request.strokeArcs()) config.setStrokeArcs(Config.STROKE_ARCS_ENABLE);
        if (request.skipPolygonBuilding()) Ili2db.setSkipPolygonBuilding(config);
        if (request.skipGeometryErrors()) config.setSkipGeometryErrors(true);
        if (request.iligml20()) config.setTransferFileFormat(Config.ILIGML20);
        if (request.disableRounding()) config.setDisableRounding(true);
    }

    private void applySchema(Config config, Ili2DbRequest request) {
        if (request.oneGeomPerTable()) config.setOneGeomPerTable(true);
        if (request.setupPgExt()) config.setSetupPgExt(true);
        if (request.dropScript() != null) config.setDropscript(request.dropScript().toString());
        if (request.createScript() != null) config.setCreatescript(request.createScript().toString());
        if (request.metaConfig() != null) config.setMetaConfigFile(request.metaConfig());
        if (request.defaultSrsAuth() != null) {
            config.setDefaultSrsAuthority("NULL".equalsIgnoreCase(request.defaultSrsAuth())
                    ? null : request.defaultSrsAuth());
        }
        if (request.defaultSrsCode() != null) config.setDefaultSrsCode(request.defaultSrsCode());
        if (request.createEnumTabs()) config.setCreateEnumDefs(Config.CREATE_ENUM_DEFS_MULTI);
        if (request.createGeomIdx()) config.setValue(Config.CREATE_GEOM_INDEX, Config.TRUE);
        if (request.coalesceJson()) config.setJsonTrafo(Config.JSON_TRAFO_COALESCE);
        if (request.nameByTopic()) config.setNameOptimization(Config.NAME_OPTIMIZATION_TOPIC);
        if (request.createFk()) config.setCreateFk(Config.CREATE_FK_YES);
        if (request.createFkIdx()) config.setCreateFkIdx(Config.CREATE_FKIDX_YES);
        if (request.createUnique()) config.setCreateUniqueConstraints(true);
        if (request.createStdCols()) config.setCreateStdCols(Config.CREATE_STD_COLS_ALL);
        if (request.createTidCol()) config.setTidHandling(Config.TID_HANDLING_PROPERTY);
        if (request.createBasketCol()) config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
        if (request.createDatasetCol()) config.setCreateDatasetCols(Config.CREATE_DATASET_COL);
        if (request.createMetaInfo()) config.setCreateMetaInfo(true);
        if (request.maxNameLength() != null) config.setMaxSqlNameLength(request.maxNameLength().toString());
    }

    private void applyGpkg(Config config, Ili2DbRequest request) {
        config.setDoImplicitSchemaImport(true);
        if (request.createMetaInfo()) config.setCreateMetaInfo(true);
        if (request.createGeomIdx()) config.setValue(Config.CREATE_GEOM_INDEX, Config.TRUE);
        if (request.createEnumTabs()) config.setCreateEnumDefs(Config.CREATE_ENUM_DEFS_MULTI);
        if (request.coalesceJson()) config.setJsonTrafo(Config.JSON_TRAFO_COALESCE);
        if (request.nameByTopic()) config.setNameOptimization(Config.NAME_OPTIMIZATION_TOPIC);
        if (request.defaultSrsCode() != null) config.setDefaultSrsCode(request.defaultSrsCode());
    }

    private String dataset(Ili2DbRequest request, int index) {
        if (request.datasetNames().isEmpty()) return null;
        return request.datasetNames().get(Math.min(index, request.datasetNames().size() - 1));
    }

    private void runOnce(Ili2DbRequest request, Ili2DbFlavor flavor, Config config) {
        FileListener logger = null;
        try {
            if (request.logFile() != null) {
                createParent(request.logFile());
                logger = new FileLogger(request.logFile().toFile());
                EhiLogger.getInstance().addListener(logger);
            }
            if (flavor.usesJdbcConnection()) {
                runPostgis(request.database(), config);
            } else {
                createParent(Path.of(config.getDbfile()));
                Ili2db.readSettingsFromDb(config);
                Ili2db.run(config, null);
            }
        } catch (Exception exception) {
            if (exception instanceof Ili2dbException && !request.failOnException()) {
                return;
            }
            throw new InterlisTaskException("ILI2DB", "ili2db failed",
                    "execute", Optional.ofNullable(request.logFile()), exception);
        } finally {
            if (logger != null) {
                EhiLogger.getInstance().removeListener(logger);
                logger.close();
            }
            EhiLogger.getInstance().setTraceFilter(true);
        }
    }

    private void runPostgis(DatabaseSpec database, Config config) throws Exception {
        try (Connection connection = connect(database)) {
            try {
                config.setDburl(database.jdbcUrl());
                config.setJdbcConnection(connection);
                Ili2db.readSettingsFromDb(config);
                Ili2db.run(config, null);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                config.setJdbcConnection(null);
            }
        }
    }

    private Connection connect(DatabaseSpec database) throws Exception {
        Connection connection = database.username() == null || database.username().isBlank()
                ? DriverManager.getConnection(database.jdbcUrl())
                : DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password());
        connection.setAutoCommit(false);
        return connection;
    }

    private void createParent(Path path) {
        Path parent = path.toAbsolutePath().getParent();
        if (parent == null) return;
        try {
            Files.createDirectories(parent);
        } catch (Exception exception) {
            throw new InterlisTaskException("ILI2DB", "Cannot create " + parent,
                    "prepare", Optional.empty(), exception);
        }
    }
}
