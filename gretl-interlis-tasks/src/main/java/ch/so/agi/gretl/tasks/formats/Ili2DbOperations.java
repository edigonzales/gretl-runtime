package ch.so.agi.gretl.tasks.formats;

import ch.ehi.ili2db.gui.Config;
import ch.so.agi.gretl.internal.interlis.Ili2DbRequest;
import ch.so.agi.gretl.internal.sql.DatabaseSpec;
import ch.so.agi.gretl.internal.interlis.InterlisTaskException;
import ch.so.agi.gretl.runtime.api.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Runtime definitions for the eleven ili2pg/ili2gpkg/ili2duckdb tasks. */
public final class Ili2DbOperations {
    public enum Kind { PG_SCHEMA, PG_IMPORT, PG_REPLACE, PG_UPDATE, PG_EXPORT, PG_DELETE, PG_VALIDATE,
        GPKG_IMPORT, DUCKDB_SCHEMA, DUCKDB_IMPORT, DUCKDB_EXPORT }
    private Ili2DbOperations() {}

    public static OperationType<Request, Result> pgSchema() { return type(Kind.PG_SCHEMA, "gretl.ili2pg-import-schema", "Ili2pgImportSchema", "Import an INTERLIS schema into PostgreSQL/PostGIS"); }
    public static OperationType<Request, Result> pgImport() { return type(Kind.PG_IMPORT, "gretl.ili2pg-import", "Ili2pgImport", "Import INTERLIS transfer files into PostgreSQL/PostGIS"); }
    public static OperationType<Request, Result> pgReplace() { return type(Kind.PG_REPLACE, "gretl.ili2pg-replace", "Ili2pgReplace", "Replace datasets in PostgreSQL/PostGIS"); }
    public static OperationType<Request, Result> pgUpdate() { return type(Kind.PG_UPDATE, "gretl.ili2pg-update", "Ili2pgUpdate", "Update PostgreSQL/PostGIS data"); }
    public static OperationType<Request, Result> pgExport() { return type(Kind.PG_EXPORT, "gretl.ili2pg-export", "Ili2pgExport", "Export PostgreSQL/PostGIS data to INTERLIS"); }
    public static OperationType<Request, Result> pgDelete() { return type(Kind.PG_DELETE, "gretl.ili2pg-delete", "Ili2pgDelete", "Delete datasets from PostgreSQL/PostGIS"); }
    public static OperationType<Request, Result> pgValidate() { return type(Kind.PG_VALIDATE, "gretl.ili2pg-validate", "Ili2pgValidate", "Validate PostgreSQL/PostGIS data"); }
    public static OperationType<Request, Result> gpkgImport() { return type(Kind.GPKG_IMPORT, "gretl.ili2gpkg-import", "Ili2gpkgImport", "Import INTERLIS transfer files into a GeoPackage"); }
    public static OperationType<Request, Result> duckdbSchema() { return type(Kind.DUCKDB_SCHEMA, "gretl.ili2duckdb-import-schema", "Ili2duckdbImportSchema", "Import an INTERLIS schema into DuckDB"); }
    public static OperationType<Request, Result> duckdbImport() { return type(Kind.DUCKDB_IMPORT, "gretl.ili2duckdb-import", "Ili2duckdbImport", "Import INTERLIS transfer files into DuckDB"); }
    public static OperationType<Request, Result> duckdbExport() { return type(Kind.DUCKDB_EXPORT, "gretl.ili2duckdb-export", "Ili2duckdbExport", "Export DuckDB data to INTERLIS"); }

    private static OperationType<Request, Result> type(Kind kind, String id, String symbol, String desc) {
        boolean pg = switch (kind) {
            case PG_SCHEMA, PG_IMPORT, PG_REPLACE, PG_UPDATE, PG_EXPORT, PG_DELETE, PG_VALIDATE -> true;
            default -> false;
        };
        boolean file = !pg;
        boolean schema = kind == Kind.PG_SCHEMA || kind == Kind.DUCKDB_SCHEMA;
        boolean importLike = kind == Kind.PG_IMPORT || kind == Kind.PG_REPLACE || kind == Kind.PG_UPDATE
                || kind == Kind.GPKG_IMPORT || kind == Kind.DUCKDB_IMPORT;
        boolean export = kind == Kind.PG_EXPORT || kind == Kind.DUCKDB_EXPORT;
        boolean datasetOnly = kind == Kind.PG_DELETE || kind == Kind.PG_VALIDATE;
        List<InputField<Request, ?>> fields = new ArrayList<>();
        if (pg) {
            fields.add(field("database", "text", "PostgreSQL database alias", true, null,
                    "database = 'db'", FormatTaskSupport::text, (b, v) -> b.database = v));
            fields.add(field("schema", "text", "Database schema", false, null,
                    "schema = 'public'", FormatTaskSupport::text, (b, v) -> b.schema = v));
        } else {
            fields.add(field("databaseFile", "path", "Database file", true, null,
                    "databaseFile = file('data/db.gpkg')", FormatTaskSupport::path,
                    (b, v) -> b.databaseFile = v));
            if (kind == Kind.GPKG_IMPORT) {
                fields.add(field("coalesceJson", "boolean", "Coalesce JSON structures", false, false,
                        "coalesceJson = true", FormatTaskSupport::bool, (b, v) -> b.coalesceJson = v));
                fields.add(field("nameByTopic", "boolean", "Use topic-qualified names", false, false,
                        "nameByTopic = true", FormatTaskSupport::bool, (b, v) -> b.nameByTopic = v));
                fields.add(field("createEnumTabs", "boolean", "Create enum tables", false, false,
                        "createEnumTabs = true", FormatTaskSupport::bool, (b, v) -> b.createEnumTabs = v));
                fields.add(field("createMetaInfo", "boolean", "Create meta information", false, false,
                        "createMetaInfo = true", FormatTaskSupport::bool, (b, v) -> b.createMetaInfo = v));
                fields.add(field("createGeomIdx", "boolean", "Create geometry index", false, false,
                        "createGeomIdx = true", FormatTaskSupport::bool, (b, v) -> b.createGeomIdx = v));
            }
        }
        if (schema) {
            fields.add(field("modelNames", "list", "INTERLIS model names", false, List.<String>of(),
                    "modelNames = ['Model']", FormatTaskSupport::strings, (b, v) -> b.modelNames = v));
            fields.add(field("modelDirectories", "list", "Model directories", false, List.<String>of(),
                    "modelDirectories = [file('models')]", FormatTaskSupport::pathStrings,
                    (b, v) -> b.modelDirectories = v));
            fields.add(field("iliFile", "path", "INTERLIS model file", false, null,
                    "iliFile = file('model.ili')", FormatTaskSupport::path, (b, v) -> b.iliFile = v));
            fields.add(field("defaultSrsCode", "text", "Default SRS code", false, null,
                    "defaultSrsCode = '2056'", FormatTaskSupport::text, (b, v) -> b.defaultSrsCode = v));
            fields.add(field("createBasketCol", "boolean", "Create basket column", false, false,
                    "createBasketCol = true", FormatTaskSupport::bool, (b, v) -> b.createBasketCol = v));
            fields.add(field("createTidCol", "boolean", "Create t_id column", false, false,
                    "createTidCol = true", FormatTaskSupport::bool, (b, v) -> b.createTidCol = v));
            fields.add(field("createEnumTabs", "boolean", "Create enum tables", false, false,
                    "createEnumTabs = true", FormatTaskSupport::bool, (b, v) -> b.createEnumTabs = v));
            fields.add(field("createGeomIdx", "boolean", "Create geometry index", false, false,
                    "createGeomIdx = true", FormatTaskSupport::bool, (b, v) -> b.createGeomIdx = v));
            fields.add(field("createMetaInfo", "boolean", "Create meta information", false, false,
                    "createMetaInfo = true", FormatTaskSupport::bool, (b, v) -> b.createMetaInfo = v));
        }
        if (importLike || export || datasetOnly) {
            fields.add(field("dataFiles", "list", "Transfer files", export || datasetOnly ? false : true,
                    List.<Path>of(), "dataFiles = [file('data.xtf')]", FormatTaskSupport::paths,
                    (b, v) -> b.dataFiles = v));
            fields.add(field("modelNames", "list", "INTERLIS model names", false, List.<String>of(),
                    "modelNames = ['Model']", FormatTaskSupport::strings, (b, v) -> b.modelNames = v));
            fields.add(field("modelDirectories", "list", "Model directories", false, List.<String>of(),
                    "modelDirectories = [file('models')]", FormatTaskSupport::pathStrings,
                    (b, v) -> b.modelDirectories = v));
            fields.add(field("datasetNames", "list", "Dataset names", false, List.<String>of(),
                    "datasetNames = ['DatasetA']", FormatTaskSupport::strings,
                    (b, v) -> b.datasetNames = v));
        }
        if (kind == Kind.PG_IMPORT || kind == Kind.PG_REPLACE || kind == Kind.PG_UPDATE || kind == Kind.GPKG_IMPORT || kind == Kind.DUCKDB_IMPORT) {
            fields.add(field("repositoryDataIds", "list", "ilidata repository ids", false, List.<String>of(),
                    "repositoryDataIds = ['ilidata:...']", FormatTaskSupport::strings,
                    (b, v) -> b.repositoryDataIds = v));
            fields.add(field("importTid", "boolean", "Import t_id", false, false,
                    "importTid = true", FormatTaskSupport::bool, (b, v) -> b.importTid = v));
            fields.add(field("deleteData", "boolean", "Delete data before import", false, false,
                    "deleteData = true", FormatTaskSupport::bool, (b, v) -> b.deleteData = v));
        }
        if (export) {
            fields.add(field("export3", "boolean", "Export INTERLIS 2.4", false, false,
                    "export3 = true", FormatTaskSupport::bool, (b, v) -> b.export3 = v));
        }
        fields.add(field("logFile", "path", "ili2db log file", false, null,
                "logFile = file('build/ili2db.log')", FormatTaskSupport::path, (b, v) -> b.logFile = v));
        fields.add(field("failOnException", "boolean", "Fail on ili2db exception", false, true,
                "failOnException = false", FormatTaskSupport::bool, (b, v) -> b.failOnException = v));
        addOptions(fields, commonOptions());
        if (schema) {
            addOptions(fields, schemaOptions());
        }
        if (export) {
            addOptions(fields, List.of(new OptionSpec("exportModels", "list",
                    "Export models", List.<String>of(), "exportModels = ['Model']")));
        }
        OutputField<Result, Path> log = Fields.output("logFile", "path", "ili2db log file", false, Result::logFile);
        return new OperationType<>(id, symbol, desc, Request.class, Result.class,
                Builder::new, () -> new Handler(kind), fields, List.of(log),
                "task('run', " + symbol + ") {\n    database = 'db'\n}\n");
    }

    private record OptionSpec(String name, String type, String description, Object defaultValue,
                              String example) {}

    private static List<OptionSpec> commonOptions() {
        return List.of(
                new OptionSpec("baskets", "list", "Baskets", List.<String>of(), "baskets = ['b1']"),
                new OptionSpec("topics", "list", "Topics", List.<String>of(), "topics = ['Topic']"),
                new OptionSpec("exportTid", "boolean", "Export t_id", false, "exportTid = true"),
                new OptionSpec("importBid", "boolean", "Import basket id", false, "importBid = true"),
                new OptionSpec("preScript", "path", "Pre script", null, "preScript = file('pre.sql')"),
                new OptionSpec("postScript", "path", "Post script", null, "postScript = file('post.sql')"),
                new OptionSpec("trace", "boolean", "Enable trace", false, "trace = true"),
                new OptionSpec("disableValidation", "boolean", "Disable validation", false, "disableValidation = true"),
                new OptionSpec("disableAreaValidation", "boolean", "Disable area validation", false, "disableAreaValidation = true"),
                new OptionSpec("forceTypeValidation", "boolean", "Force type validation", false, "forceTypeValidation = true"),
                new OptionSpec("strokeArcs", "boolean", "Stroke arcs", false, "strokeArcs = true"),
                new OptionSpec("skipPolygonBuilding", "boolean", "Skip polygon building", false, "skipPolygonBuilding = true"),
                new OptionSpec("skipGeometryErrors", "boolean", "Skip geometry errors", false, "skipGeometryErrors = true"),
                new OptionSpec("iligml20", "boolean", "ILIGML 2.0", false, "iligml20 = true"),
                new OptionSpec("disableRounding", "boolean", "Disable rounding", false, "disableRounding = true"),
                new OptionSpec("proxy", "text", "HTTP proxy", null, "proxy = 'proxy.example'"),
                new OptionSpec("proxyPort", "integer", "HTTP proxy port", null, "proxyPort = 8080"),
                new OptionSpec("validConfigFile", "path", "Validation config file", null,
                        "validConfigFile = file('validation.toml')"),
                new OptionSpec("datasetNamesFromTransferFiles", "boolean",
                        "Derive dataset names from transfer file names", false,
                        "datasetNamesFromTransferFiles = true"),
                new OptionSpec("datasetNameSliceStart", "integer", "Dataset name slice start", null,
                        "datasetNameSliceStart = 0"),
                new OptionSpec("datasetNameSliceEnd", "integer", "Dataset name slice end", null,
                        "datasetNameSliceEnd = 8"),
                new OptionSpec("datasetSubstring", "list", "Dataset name substring indices", List.<String>of(),
                        "datasetSubstring = ['0', '4']"));
    }

    private static List<OptionSpec> schemaOptions() {
        return List.of(
                new OptionSpec("oneGeomPerTable", "boolean", "One geometry per table", false, "oneGeomPerTable = true"),
                new OptionSpec("setupPgExt", "boolean", "Setup PostGIS extension", false, "setupPgExt = true"),
                new OptionSpec("dropScript", "path", "Drop script", null, "dropScript = file('drop.sql')"),
                new OptionSpec("createScript", "path", "Create script", null, "createScript = file('create.sql')"),
                new OptionSpec("metaConfig", "text", "Meta configuration", null, "metaConfig = file('meta.ini')"),
                new OptionSpec("defaultSrsAuth", "text", "Default SRS authority", null, "defaultSrsAuth = 'EPSG'"),
                new OptionSpec("createSingleEnumTab", "boolean", "Single enum table", false, "createSingleEnumTab = true"),
                new OptionSpec("createEnumTxtCol", "boolean", "Enum text column", false, "createEnumTxtCol = true"),
                new OptionSpec("createEnumColAsItfCode", "boolean", "Enum column as ITF code", false, "createEnumColAsItfCode = true"),
                new OptionSpec("createEnumTabsWithId", "boolean", "Enum tables with id", false, "createEnumTabsWithId = true"),
                new OptionSpec("createImportTabs", "boolean", "Create import tables", false, "createImportTabs = true"),
                new OptionSpec("beautifyEnumDispName", "boolean", "Beautify enum display names", false, "beautifyEnumDispName = true"),
                new OptionSpec("noSmartMapping", "boolean", "No smart mapping", false, "noSmartMapping = true"),
                new OptionSpec("smart1Inheritance", "boolean", "Smart1 inheritance", false, "smart1Inheritance = true"),
                new OptionSpec("smart2Inheritance", "boolean", "Smart2 inheritance", false, "smart2Inheritance = true"),
                new OptionSpec("coalesceCatalogueRef", "boolean", "Coalesce catalogue references", false, "coalesceCatalogueRef = true"),
                new OptionSpec("coalesceMultiSurface", "boolean", "Coalesce multi surfaces", false, "coalesceMultiSurface = true"),
                new OptionSpec("coalesceMultiLine", "boolean", "Coalesce multi lines", false, "coalesceMultiLine = true"),
                new OptionSpec("expandMultilingual", "boolean", "Expand multilingual", false, "expandMultilingual = true"),
                new OptionSpec("expandStruct", "boolean", "Expand structures", false, "expandStruct = true"),
                new OptionSpec("coalesceArray", "boolean", "Coalesce arrays", false, "coalesceArray = true"),
                new OptionSpec("createTypeConstraint", "boolean", "Create type constraints", false, "createTypeConstraint = true"),
                new OptionSpec("createFk", "boolean", "Create foreign keys", false, "createFk = true"),
                new OptionSpec("createFkIdx", "boolean", "Create FK indexes", false, "createFkIdx = true"),
                new OptionSpec("createUnique", "boolean", "Create unique constraints", false, "createUnique = true"),
                new OptionSpec("createStdCols", "boolean", "Create standard columns", false, "createStdCols = true"),
                new OptionSpec("coalesceJson", "boolean", "Coalesce JSON", false, "coalesceJson = true"),
                new OptionSpec("nameByTopic", "boolean", "Name by topic", false, "nameByTopic = true"),
                new OptionSpec("createNumChecks", "boolean", "Numeric checks", false, "createNumChecks = true"),
                new OptionSpec("createTextChecks", "boolean", "Text checks", false, "createTextChecks = true"),
                new OptionSpec("createDateTimeChecks", "boolean", "Date/time checks", false, "createDateTimeChecks = true"),
                new OptionSpec("createTypeDiscriminator", "boolean", "Type discriminator", false, "createTypeDiscriminator = true"),
                new OptionSpec("disableNameOptimization", "boolean", "Disable name optimization", false, "disableNameOptimization = true"),
                new OptionSpec("sqlEnableNull", "boolean", "SQL NULL enabled", false, "sqlEnableNull = true"),
                new OptionSpec("sqlColsAsText", "boolean", "SQL columns as text", false, "sqlColsAsText = true"),
                new OptionSpec("sqlExtRefCols", "boolean", "SQL external ref columns", false, "sqlExtRefCols = true"),
                new OptionSpec("keepAreaRef", "boolean", "Keep area references", false, "keepAreaRef = true"),
                new OptionSpec("createDatasetCol", "boolean", "Create dataset column", false, "createDatasetCol = true"),
                new OptionSpec("translation", "text", "Translation", null, "translation = 'de'"),
                new OptionSpec("tidColumnName", "text", "t_id column name", null, "tidColumnName = 't_id'"),
                new OptionSpec("idSeqMin", "integer", "ID sequence minimum", null, "idSeqMin = 1"),
                new OptionSpec("idSeqMax", "integer", "ID sequence maximum", null, "idSeqMax = 999999"),
                new OptionSpec("maxNameLength", "integer", "Maximum SQL name length", null, "maxNameLength = 60"));
    }

    @SuppressWarnings("unchecked")
    private static void addOptions(List<InputField<Request, ?>> fields, List<OptionSpec> specs) {
        for (OptionSpec spec : specs) {
            fields.add(optionField(spec));
        }
    }

    @SuppressWarnings("unchecked")
    private static InputField<Request, ?> optionField(OptionSpec spec) {
        Object defaultValue = spec.defaultValue();
        Function<Object, Object> converter = switch (spec.type()) {
            case "boolean" -> FormatTaskSupport::bool;
            case "integer" -> FormatTaskSupport::integer;
            case "path" -> FormatTaskSupport::path;
            case "list" -> FormatTaskSupport::strings;
            default -> FormatTaskSupport::text;
        };
        return new InputField<Request, Object>(spec.name(), spec.type(), spec.description(), false,
                defaultValue == null ? Optional.empty() : Optional.of(defaultValue),
                List.of(), spec.example(), converter,
                (builder, value) -> ((Builder) builder).options.put(spec.name(), value));
    }

    @SuppressWarnings("unchecked")
    private static <T> InputField<Request, T> field(String name, String type, String desc,
            boolean required, T defaultValue, String example,
            Function<Object, T> converter, BiConsumer<Builder, T> setter) {
        return new InputField<>(name, type, desc, required,
                defaultValue == null ? Optional.empty() : Optional.of(defaultValue),
                List.of(), example, converter,
                (builder, value) -> setter.accept((Builder) builder, value));
    }

    public record Request(String database, Path databaseFile, String schema,
            Path iliFile, String defaultSrsCode, List<Path> dataFiles,
            List<String> repositoryDataIds, List<String> modelNames,
            List<String> modelDirectories, List<String> datasetNames,
            boolean importTid, boolean deleteData, boolean export3,
            boolean coalesceJson, boolean nameByTopic, boolean createEnumTabs,
            boolean createMetaInfo, boolean createGeomIdx, boolean createBasketCol,
            boolean createTidCol, Path logFile, boolean failOnException,
            java.util.Map<String, Object> options) {
        public Request {
            dataFiles = List.copyOf(dataFiles);
            repositoryDataIds = List.copyOf(repositoryDataIds);
            modelNames = List.copyOf(modelNames);
            modelDirectories = List.copyOf(modelDirectories);
            datasetNames = List.copyOf(datasetNames);
            options = options == null ? java.util.Map.of() : java.util.Map.copyOf(options);
        }
    }
    public record Result(Path logFile) {}

    public static final class Builder implements ConfigBuilder<Request> {
        private String database;
        private Path databaseFile;
        private String schema;
        private Path iliFile;
        private String defaultSrsCode;
        private List<Path> dataFiles = List.of();
        private List<String> repositoryDataIds = List.of();
        private List<String> modelNames = List.of();
        private List<String> modelDirectories = List.of();
        private List<String> datasetNames = List.of();
        private boolean importTid;
        private boolean deleteData;
        private boolean export3;
        private boolean coalesceJson;
        private boolean nameByTopic;
        private boolean createEnumTabs;
        private boolean createMetaInfo;
        private boolean createGeomIdx;
        private boolean createBasketCol;
        private boolean createTidCol;
        private Path logFile;
        private boolean failOnException = true;
        private final java.util.Map<String, Object> options = new java.util.LinkedHashMap<>();

        public void setDatabase(String value) { database = value; }
        public void setDatabaseFile(Path value) { databaseFile = value; }
        public void setSchema(String value) { schema = value; }
        public void setIliFile(Path value) { iliFile = value; }
        public void setDefaultSrsCode(String value) { defaultSrsCode = value; }
        public void setDataFiles(List<Path> value) { dataFiles = value == null ? List.of() : value; }
        public void setRepositoryDataIds(List<String> value) { repositoryDataIds = value == null ? List.of() : value; }
        public void setModelNames(List<String> value) { modelNames = value == null ? List.of() : value; }
        public void setModelDirectories(List<String> value) { modelDirectories = value == null ? List.of() : value; }
        public void setDatasetNames(List<String> value) { datasetNames = value == null ? List.of() : value; }
        public void setImportTid(Boolean value) { importTid = Boolean.TRUE.equals(value); }
        public void setDeleteData(Boolean value) { deleteData = Boolean.TRUE.equals(value); }
        public void setExport3(Boolean value) { export3 = Boolean.TRUE.equals(value); }
        public void setCoalesceJson(Boolean value) { coalesceJson = Boolean.TRUE.equals(value); }
        public void setNameByTopic(Boolean value) { nameByTopic = Boolean.TRUE.equals(value); }
        public void setCreateEnumTabs(Boolean value) { createEnumTabs = Boolean.TRUE.equals(value); }
        public void setCreateMetaInfo(Boolean value) { createMetaInfo = Boolean.TRUE.equals(value); }
        public void setCreateGeomIdx(Boolean value) { createGeomIdx = Boolean.TRUE.equals(value); }
        public void setCreateBasketCol(Boolean value) { createBasketCol = Boolean.TRUE.equals(value); }
        public void setCreateTidCol(Boolean value) { createTidCol = Boolean.TRUE.equals(value); }
        public void setLogFile(Path value) { logFile = value; }
        public void setFailOnException(Boolean value) { failOnException = value == null || value; }

        @Override public Request build() {
            return new Request(database, databaseFile, schema, iliFile, defaultSrsCode,
                    dataFiles, repositoryDataIds, modelNames, modelDirectories, datasetNames,
                    importTid, deleteData, export3, coalesceJson, nameByTopic,
                    createEnumTabs, createMetaInfo, createGeomIdx, createBasketCol,
                    createTidCol, logFile, failOnException, options);
        }
    }

    static final class Handler implements OperationHandler<Request, Result> {
        private final Kind kind;
        Handler(Kind kind) { this.kind = kind; }

        @Override public Result execute(Request request, OperationContext context) {
            boolean pg = kind.name().startsWith("PG_");
            DatabaseSpec database = pg ? FormatTaskSupport.database(context, request.database()) : null;
            ch.so.agi.gretl.internal.interlis.Ili2DbRequestBuilder builder =
                    new ch.so.agi.gretl.internal.interlis.Ili2DbRequestBuilder();
            builder.database = database;
            builder.databaseFile = request.databaseFile();
            builder.schema = request.schema();
            builder.modelNames = request.modelNames();
            builder.modelDirectories = request.modelDirectories();
            builder.datasetNames = request.datasetNames();
            builder.dataFiles = request.dataFiles();
            builder.repositoryDataIds = request.repositoryDataIds();
            builder.iliFile = request.iliFile();
            builder.logFile = request.logFile();
            builder.importTid = request.importTid();
            builder.deleteData = request.deleteData();
            builder.failOnException = request.failOnException();
            builder.export3 = request.export3();
            builder.createEnumTabs = request.createEnumTabs();
            builder.createGeomIdx = request.createGeomIdx();
            builder.createMetaInfo = request.createMetaInfo();
            builder.coalesceJson = request.coalesceJson();
            builder.nameByTopic = request.nameByTopic();
            builder.createBasketCol = request.createBasketCol();
            builder.createTidCol = request.createTidCol();
            builder.defaultSrsCode = request.defaultSrsCode();
            builder.options = request.options();
            ch.so.agi.gretl.internal.interlis.Ili2DbRequest normalized = builder.build();
            ch.so.agi.gretl.internal.interlis.Ili2DbExecutionSupport support =
                    new ch.so.agi.gretl.internal.interlis.Ili2DbExecutionSupport();
            switch (kind) {
                case PG_SCHEMA -> support.executeSchemaImport(normalized,
                        ch.so.agi.gretl.internal.interlis.Ili2DbFlavor.POSTGIS);
                case PG_IMPORT -> support.executeImport(normalized,
                        ch.so.agi.gretl.internal.interlis.Ili2DbFlavor.POSTGIS, Config.FC_IMPORT);
                case PG_REPLACE -> support.executeImport(normalized,
                        ch.so.agi.gretl.internal.interlis.Ili2DbFlavor.POSTGIS, Config.FC_REPLACE);
                case PG_UPDATE -> support.executeImport(normalized,
                        ch.so.agi.gretl.internal.interlis.Ili2DbFlavor.POSTGIS, Config.FC_UPDATE);
                case PG_EXPORT -> support.executeExport(normalized,
                        ch.so.agi.gretl.internal.interlis.Ili2DbFlavor.POSTGIS);
                case PG_DELETE -> support.executeDatasetOperation(normalized,
                        ch.so.agi.gretl.internal.interlis.Ili2DbFlavor.POSTGIS, Config.FC_DELETE);
                case PG_VALIDATE -> support.executeDatasetOperation(normalized,
                        ch.so.agi.gretl.internal.interlis.Ili2DbFlavor.POSTGIS, Config.FC_VALIDATE);
                case GPKG_IMPORT -> support.executeImport(normalized,
                        ch.so.agi.gretl.internal.interlis.Ili2DbFlavor.GEOPACKAGE, Config.FC_IMPORT);
                case DUCKDB_SCHEMA -> support.executeSchemaImport(normalized,
                        ch.so.agi.gretl.internal.interlis.Ili2DbFlavor.DUCKDB);
                case DUCKDB_IMPORT -> support.executeImport(normalized,
                        ch.so.agi.gretl.internal.interlis.Ili2DbFlavor.DUCKDB, Config.FC_IMPORT);
                case DUCKDB_EXPORT -> support.executeExport(normalized,
                        ch.so.agi.gretl.internal.interlis.Ili2DbFlavor.DUCKDB);
            }
            return new Result(request.logFile());
        }
    }
}
