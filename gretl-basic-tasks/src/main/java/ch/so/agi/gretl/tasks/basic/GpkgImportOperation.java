package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** GpkgImport task: import a GeoPackage layer into a database table. */
public final class GpkgImportOperation {
    public static final String ID = "gretl.gpkg-import";
    public static final String SYMBOL = "GpkgImport";

    private GpkgImportOperation() {
    }

    public static OperationType<GpkgImportRequest, GpkgImportResult> type() {
        InputField<GpkgImportRequest, Path> dataFile = new InputField<>(
                "dataFile", "path", "GeoPackage file to import", true,
                Optional.empty(), List.of(), "dataFile = file('data/input.gpkg')",
                GpkgImportOperation::path,
                (builder, value) -> ((GpkgImportBuilder) builder).setDataFile(value));
        InputField<GpkgImportRequest, String> database = new InputField<>(
                "database", "text", "Alias of the target database connection", true,
                Optional.empty(), List.of(), "database = 'db'",
                GpkgImportOperation::text,
                (builder, value) -> ((GpkgImportBuilder) builder).setDatabase(value));
        InputField<GpkgImportRequest, String> source = new InputField<>(
                "srcTableName", "text", "GeoPackage layer name", true,
                Optional.empty(), List.of(), "srcTableName = 'abbaustelle'",
                GpkgImportOperation::text,
                (builder, value) -> ((GpkgImportBuilder) builder).setSrcTableName(value));
        InputField<GpkgImportRequest, String> target = new InputField<>(
                "dstTableName", "text", "Target table name", true,
                Optional.empty(), List.of(), "dstTableName = 'abbaustelle'",
                GpkgImportOperation::text,
                (builder, value) -> ((GpkgImportBuilder) builder).setDstTableName(value));
        InputField<GpkgImportRequest, String> schema = new InputField<>(
                "schemaName", "text", "Optional target schema", false,
                Optional.empty(), List.of(), "schemaName = 'public'",
                GpkgImportOperation::text,
                (builder, value) -> ((GpkgImportBuilder) builder).setSchemaName(value));
        InputField<GpkgImportRequest, Integer> batch = new InputField<>(
                "batchSize", "integer", "JDBC batch size", false,
                Optional.of(5000), List.of(), "batchSize = 1000",
                GpkgImportOperation::positiveInteger,
                (builder, value) -> ((GpkgImportBuilder) builder).setBatchSize(value));
        InputField<GpkgImportRequest, Integer> fetch = new InputField<>(
                "fetchSize", "integer", "DuckDB fetch size", false,
                Optional.of(5000), List.of(), "fetchSize = 1000",
                GpkgImportOperation::nonNegativeInteger,
                (builder, value) -> ((GpkgImportBuilder) builder).setFetchSize(value));
        OutputField<GpkgImportResult, Long> rows = Fields.output(
                "rows", "integer", "Imported rows", false, GpkgImportResult::rows);
        return new OperationType<>(
                ID, SYMBOL, "Import a GeoPackage layer into a database table",
                GpkgImportRequest.class, GpkgImportResult.class,
                GpkgImportBuilder::new, GpkgImportHandler::new,
                List.of(dataFile, database, source, target, schema, batch, fetch),
                List.of(rows),
                """
                task('import', GpkgImport) {
                    dataFile = file('data/input.gpkg')
                    database = 'db'
                    srcTableName = 'abbaustelle'
                    dstTableName = 'abbaustelle'
                }
                """);
    }

    public record GpkgImportRequest(
            Path dataFile, String database, String srcTableName,
            String dstTableName, String schemaName, int batchSize, int fetchSize) {
    }

    public record GpkgImportResult(long rows) {
    }

    public static final class GpkgImportBuilder
            implements ch.so.agi.gretl.runtime.api.ConfigBuilder<GpkgImportRequest> {
        private Path dataFile;
        private String database;
        private String srcTableName;
        private String dstTableName;
        private String schemaName;
        private int batchSize = 5000;
        private int fetchSize = 5000;

        public GpkgImportBuilder setDataFile(Path value) { dataFile = value; return this; }
        public GpkgImportBuilder setDatabase(String value) { database = value; return this; }
        public GpkgImportBuilder setSrcTableName(String value) { srcTableName = value; return this; }
        public GpkgImportBuilder setDstTableName(String value) { dstTableName = value; return this; }
        public GpkgImportBuilder setSchemaName(String value) { schemaName = value; return this; }
        public GpkgImportBuilder setBatchSize(Integer value) {
            batchSize = value == null ? 5000 : value; return this;
        }
        public GpkgImportBuilder setFetchSize(Integer value) {
            fetchSize = value == null ? 5000 : value; return this;
        }

        @Override
        public GpkgImportRequest build() {
            if (dataFile == null) { throw new IllegalArgumentException("dataFile is missing"); }
            if (database == null || database.isBlank()) {
                throw new IllegalArgumentException("database is missing");
            }
            if (srcTableName == null || srcTableName.isBlank()) {
                throw new IllegalArgumentException("srcTableName is missing");
            }
            if (dstTableName == null || dstTableName.isBlank()) {
                throw new IllegalArgumentException("dstTableName is missing");
            }
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batchSize must be greater than zero");
            }
            if (fetchSize < 0) {
                throw new IllegalArgumentException("fetchSize must not be negative");
            }
            return new GpkgImportRequest(dataFile.toAbsolutePath().normalize(), database,
                    srcTableName, dstTableName, schemaName, batchSize, fetchSize);
        }
    }

    static final class GpkgImportHandler
            implements OperationHandler<GpkgImportRequest, GpkgImportResult> {
        @Override
        public GpkgImportResult execute(GpkgImportRequest request, OperationContext context) {
            if (!Files.isRegularFile(request.dataFile())) {
                throw new TabularTaskException("GPKG_FILE",
                        msg(context, "task.geo.file", request.dataFile()), "prepare", null);
            }
            long rows = GeoImportSupport.importFile(context, request.database(),
                    request.dataFile(), request.srcTableName(), request.schemaName(),
                    request.dstTableName(), request.batchSize(), null);
            context.log().log(LogLevel.INFO, "gretl.gpkgimport.finished",
                    msg(context, "task.gpkgimport.finished"),
                    Map.of("geo.rows", Long.toString(rows)), null);
            return new GpkgImportResult(rows);
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }
    }

    private static Path path(Object value) {
        if (value instanceof Path path) { return path.normalize(); }
        if (value instanceof CharSequence text) { return Path.of(text.toString()).normalize(); }
        throw new IllegalArgumentException("Path expected");
    }

    private static String text(Object value) {
        if (value instanceof CharSequence text && !text.toString().isBlank()) {
            return text.toString();
        }
        throw new IllegalArgumentException("Non-blank text expected");
    }

    private static Integer positiveInteger(Object value) {
        int result = number(value);
        if (result <= 0) { throw new IllegalArgumentException("Value must be greater than zero"); }
        return result;
    }

    private static Integer nonNegativeInteger(Object value) {
        int result = number(value);
        if (result < 0) { throw new IllegalArgumentException("Value must not be negative"); }
        return result;
    }

    private static int number(Object value) {
        if (value instanceof Number number) { return number.intValue(); }
        if (value instanceof CharSequence text && text.toString().matches("[0-9]+")) {
            return Integer.parseInt(text.toString());
        }
        throw new IllegalArgumentException("Integer expected");
    }
}
