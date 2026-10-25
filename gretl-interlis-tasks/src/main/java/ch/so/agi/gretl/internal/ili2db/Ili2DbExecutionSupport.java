package ch.so.agi.gretl.internal.ili2db;

import ch.so.agi.gretl.internal.InterlisTaskException;

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
        } else if (request.modelNames().isEmpty() && config.getMetaConfigFile() == null) {
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
        java.util.List<String> datasetNames = Ili2DbDatasetNames.resolve(request, inputs);
        for (int index = 0; index < inputs.size(); index++) {
            Config config = flavor.createConfig(request);
            applyCommon(config, request, operation);
            if (flavor == Ili2DbFlavor.GEOPACKAGE) {
                applyGpkg(config, request);
            }
            // Named datasets require basket storage during an implicit schema
            // import as well; GPKG exposes datasetNames but no separate schema task.
            if (!datasetNames.isEmpty() && flavor == Ili2DbFlavor.GEOPACKAGE)
                config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
            config.setDatasetName(datasetNames.isEmpty() ? null : datasetNames.get(index));
            config.setXtffile(inputs.get(index));
            config.setItfTransferfile(!request.dataFiles().isEmpty()
                    && Ili2db.isItfFilename(inputs.get(index)));
            runOnce(request, flavor, config);
        }
    }

    public void executeExport(Ili2DbRequest request, Ili2DbFlavor flavor) {
        if (request.dataFiles().isEmpty()) return;
        java.util.List<String> datasetNames = Ili2DbDatasetNames.resolve(request,
                request.dataFiles().stream().map(Path::toString).toList());
        for (int index = 0; index < request.dataFiles().size(); index++) {
            Path output = request.dataFiles().get(index).toAbsolutePath().normalize();
            createParent(output);
            Config config = flavor.createConfig(request);
            applyCommon(config, request, Config.FC_EXPORT);
            if (request.export3()) config.setVer3_export(true);
            java.util.List<String> exportModels = optionList(request, "exportModels", java.util.List.of());
            if (!exportModels.isEmpty()) config.setExportModels(String.join(";", exportModels));
            config.setDatasetName(datasetNames.isEmpty() ? null : datasetNames.get(index));
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
        java.util.List<String> baskets = optionList(request, "baskets", request.baskets());
        java.util.List<String> topics = optionList(request, "topics", request.topics());
        if (!baskets.isEmpty()) config.setBaskets(String.join(";", baskets));
        if (!topics.isEmpty()) config.setTopics(String.join(";", topics));
        if (request.importTid()) config.setImportTid(true);
        if (request.exportTid() || optionBool(request, "exportTid", false)) config.setExportTid(true);
        if (request.importBid() || optionBool(request, "importBid", false)) config.setImportBid(true);
        Path pre = optionPath(request, "preScript", request.preScript());
        Path post = optionPath(request, "postScript", request.postScript());
        if (pre != null) config.setPreScript(pre.toString());
        if (post != null) config.setPostScript(post.toString());
        if (request.deleteData()) config.setDeleteMode(Config.DELETE_DATA);
        Path validConfig = optionPath(request, "validConfigFile", request.validConfigFile());
        if (validConfig != null) config.setValidConfigFile(validConfig.toString());
        if (request.disableValidation() || optionBool(request, "disableValidation", false)) config.setValidation(false);
        if (request.disableAreaValidation() || optionBool(request, "disableAreaValidation", false)) config.setDisableAreaValidation(true);
        if (request.forceTypeValidation() || optionBool(request, "forceTypeValidation", false)) config.setOnlyMultiplicityReduction(true);
        if (request.strokeArcs() || optionBool(request, "strokeArcs", false)) config.setStrokeArcs(Config.STROKE_ARCS_ENABLE);
        if (request.skipPolygonBuilding() || optionBool(request, "skipPolygonBuilding", false)) Ili2db.setSkipPolygonBuilding(config);
        if (request.skipGeometryErrors() || optionBool(request, "skipGeometryErrors", false)) config.setSkipGeometryErrors(true);
        if (request.iligml20() || optionBool(request, "iligml20", false)) config.setTransferFileFormat(Config.ILIGML20);
        if (request.disableRounding() || optionBool(request, "disableRounding", false)) config.setDisableRounding(true);
        String proxy = optionText(request, "proxy", null);
        Integer proxyPort = optionInt(request, "proxyPort", null);
        if (proxy != null) config.setValue(UserSettings.HTTP_PROXY_HOST, proxy);
        if (proxyPort != null) config.setValue(UserSettings.HTTP_PROXY_PORT, proxyPort.toString());
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
        if (request.createFk() || optionBool(request, "createFk", false)) config.setCreateFk(Config.CREATE_FK_YES);
        if (request.createFkIdx() || optionBool(request, "createFkIdx", false)) config.setCreateFkIdx(Config.CREATE_FKIDX_YES);
        if (request.createUnique() || optionBool(request, "createUnique", false)) config.setCreateUniqueConstraints(true);
        if (request.createStdCols() || optionBool(request, "createStdCols", false)) config.setCreateStdCols(Config.CREATE_STD_COLS_ALL);
        if (optionBool(request, "coalesceJson", false)) config.setJsonTrafo(Config.JSON_TRAFO_COALESCE);
        if (optionBool(request, "nameByTopic", false)) config.setNameOptimization(Config.NAME_OPTIMIZATION_TOPIC);
        if (request.createTidCol()) config.setTidHandling(Config.TID_HANDLING_PROPERTY);
        if (request.createBasketCol()) config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
        if (request.createDatasetCol()) config.setCreateDatasetCols(Config.CREATE_DATASET_COL);
        if (request.createMetaInfo()) config.setCreateMetaInfo(true);
        if (request.maxNameLength() != null) config.setMaxSqlNameLength(request.maxNameLength().toString());
        if (optionBool(request, "oneGeomPerTable", false)) config.setOneGeomPerTable(true);
        if (optionBool(request, "setupPgExt", false)) config.setSetupPgExt(true);
        Path dropScript = optionPath(request, "dropScript", request.dropScript());
        Path createScript = optionPath(request, "createScript", request.createScript());
        if (dropScript != null) config.setDropscript(dropScript.toString());
        if (createScript != null) config.setCreatescript(createScript.toString());
        String metaConfig = optionText(request, "metaConfig", request.metaConfig());
        if (metaConfig != null) config.setMetaConfigFile(metaConfig);
        String srsAuth = optionText(request, "defaultSrsAuth", request.defaultSrsAuth());
        if (srsAuth != null) config.setDefaultSrsAuthority("NULL".equalsIgnoreCase(srsAuth) ? null : srsAuth);
        String srsCode = request.defaultSrsCode();
        if (srsCode != null) config.setDefaultSrsCode(srsCode);
        if (optionBool(request, "createSingleEnumTab", false)) config.setCreateEnumDefs(Config.CREATE_ENUM_DEFS_SINGLE);
        if (optionBool(request, "createEnumTxtCol", false)) config.setCreateEnumCols(Config.CREATE_ENUM_TXT_COL);
        if (optionBool(request, "createEnumColAsItfCode", false)) config.setCreateEnumColAsItfCode(Config.CREATE_ENUMCOL_AS_ITFCODE_YES);
        if (optionBool(request, "createEnumTabsWithId", false)) config.setCreateEnumDefs(Config.CREATE_ENUM_DEFS_MULTI_WITH_ID);
        if (optionBool(request, "createImportTabs", false)) config.setCreateImportTabs(true);
        if (optionBool(request, "beautifyEnumDispName", false)) config.setBeautifyEnumDispName(Config.BEAUTIFY_ENUM_DISPNAME_UNDERSCORE);
        if (optionBool(request, "noSmartMapping", false)) Ili2db.setNoSmartMapping(config);
        if (optionBool(request, "smart1Inheritance", false)) config.setInheritanceTrafo(Config.INHERITANCE_TRAFO_SMART1);
        if (optionBool(request, "smart2Inheritance", false)) config.setInheritanceTrafo(Config.INHERITANCE_TRAFO_SMART2);
        if (optionBool(request, "coalesceCatalogueRef", false)) config.setCatalogueRefTrafo(Config.CATALOGUE_REF_TRAFO_COALESCE);
        if (optionBool(request, "coalesceMultiSurface", false)) config.setMultiSurfaceTrafo(Config.MULTISURFACE_TRAFO_COALESCE);
        if (optionBool(request, "coalesceMultiLine", false)) config.setMultiLineTrafo(Config.MULTILINE_TRAFO_COALESCE);
        if (optionBool(request, "expandMultilingual", false)) config.setMultilingualTrafo(Config.MULTILINGUAL_TRAFO_EXPAND);
        if (optionBool(request, "expandStruct", false)) config.setStructTrafo(Config.STRUCT_TRAFO_EXPAND);
        if (optionBool(request, "coalesceArray", false)) config.setArrayTrafo(Config.ARRAY_TRAFO_COALESCE);
        if (optionBool(request, "createTypeConstraint", false)) config.setCreateTypeConstraint(true);
        if (optionBool(request, "createNumChecks", false)) config.setCreateNumChecks(true);
        if (optionBool(request, "createTextChecks", false)) config.setCreateTextChecks(true);
        if (optionBool(request, "createDateTimeChecks", false)) config.setCreateDateTimeChecks(true);
        if (optionBool(request, "createTypeDiscriminator", false)) config.setCreateTypeDiscriminator(Config.CREATE_TYPE_DISCRIMINATOR_ALWAYS);
        if (optionBool(request, "disableNameOptimization", false)) config.setNameOptimization(Config.NAME_OPTIMIZATION_DISABLE);
        if (optionBool(request, "sqlEnableNull", false)) config.setSqlNull(Config.SQL_NULL_ENABLE);
        if (optionBool(request, "sqlColsAsText", false)) config.setSqlColsAsText(Config.SQL_COLS_AS_TEXT_ENABLE);
        if (optionBool(request, "sqlExtRefCols", false)) config.setSqlExtRefCols(Config.SQL_EXTREF_ENABLE);
        if (optionBool(request, "keepAreaRef", false)) config.setAreaRef(Config.AREA_REF_KEEP);
        if (optionBool(request, "createDatasetCol", false)) config.setCreateDatasetCols(Config.CREATE_DATASET_COL);
        String translation = optionText(request, "translation", null);
        if (translation != null) config.setIli1Translation(translation);
        String tid = optionText(request, "tidColumnName", null);
        if (tid != null) config.setColT_ID(tid);
        Integer idMin = optionInt(request, "idSeqMin", null);
        Integer idMax = optionInt(request, "idSeqMax", null);
        if (idMin != null) config.setMinIdSeqValue(idMin.longValue());
        if (idMax != null) config.setMaxIdSeqValue(idMax.longValue());
        Integer maxName = optionInt(request, "maxNameLength", null);
        if (maxName != null) config.setMaxSqlNameLength(maxName.toString());
    }

    private static boolean optionBool(Ili2DbRequest request, String name, boolean fallback) {
        Object value = request.options().get(name);
        return value instanceof Boolean flag ? flag : fallback;
    }
    private static String optionText(Ili2DbRequest request, String name, String fallback) {
        Object value = request.options().get(name);
        return value instanceof CharSequence text ? text.toString() : fallback;
    }
    private static Integer optionInt(Ili2DbRequest request, String name, Integer fallback) {
        Object value = request.options().get(name);
        return value instanceof Number number ? number.intValue() : fallback;
    }
    private static Path optionPath(Ili2DbRequest request, String name, Path fallback) {
        Object value = request.options().get(name);
        return value instanceof Path path ? path : fallback;
    }
    private static java.util.List<String> optionList(Ili2DbRequest request, String name,
                                                     java.util.List<String> fallback) {
        Object value = request.options().get(name);
        if (value instanceof java.util.List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return fallback;
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
        if (!request.datasetNames().isEmpty()) {
            return request.datasetNames().get(Math.min(index, request.datasetNames().size() - 1));
        }
        if (!optionBool(request, "datasetNamesFromTransferFiles", false)
                || request.dataFiles().isEmpty()) {
            return null;
        }
        String base = request.dataFiles().get(Math.min(index, request.dataFiles().size() - 1))
                .getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        java.util.List<String> substring = optionList(request, "datasetSubstring", java.util.List.of());
        Integer start = optionInt(request, "datasetNameSliceStart", null);
        Integer end = optionInt(request, "datasetNameSliceEnd", null);
        if (substring.size() >= 2) {
            try {
                start = Integer.parseInt(substring.get(0));
                end = Integer.parseInt(substring.get(1));
            } catch (NumberFormatException ignored) {
                // Keep explicitly configured slice if the legacy list is malformed.
            }
        }
        if (start != null) {
            int from = Math.max(0, Math.min(start, base.length()));
            int to = end == null ? base.length() : Math.max(from, Math.min(end, base.length()));
            return base.substring(from, to);
        }
        return base;
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
            if (logger != null && exception instanceof Ili2dbException) EhiLogger.logError(exception.getMessage());
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
