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

/** ShpImport task: import a Shapefile into a database table. */
public final class ShpImportOperation {
    public static final String ID = "gretl.shp-import";
    public static final String SYMBOL = "ShpImport";

    private ShpImportOperation() {
    }

    public static OperationType<ShpImportRequest, ShpImportResult> type() {
        InputField<ShpImportRequest, Path> dataFile = new InputField<>(
                "dataFile", "path", "Shapefile .shp path to import", true,
                Optional.empty(), List.of(), "dataFile = file('data/input.shp')",
                ShpImportOperation::path,
                (builder, value) -> ((ShpImportBuilder) builder).setDataFile(value));
        InputField<ShpImportRequest, String> database = new InputField<>(
                "database", "text", "Alias of the target database connection", true,
                Optional.empty(), List.of(), "database = 'db'",
                ShpImportOperation::text,
                (builder, value) -> ((ShpImportBuilder) builder).setDatabase(value));
        InputField<ShpImportRequest, String> table = new InputField<>(
                "tableName", "text", "Target table name", true,
                Optional.empty(), List.of(), "tableName = 'abbaustelle'",
                ShpImportOperation::text,
                (builder, value) -> ((ShpImportBuilder) builder).setTableName(value));
        InputField<ShpImportRequest, String> schema = new InputField<>(
                "schemaName", "text", "Optional target schema", false,
                Optional.empty(), List.of(), "schemaName = 'public'",
                ShpImportOperation::text,
                (builder, value) -> ((ShpImportBuilder) builder).setSchemaName(value));
        InputField<ShpImportRequest, String> encoding = new InputField<>(
                "encoding", "text", "Optional Shapefile encoding", false,
                Optional.empty(), List.of(), "encoding = 'UTF-8'",
                ShpImportOperation::text,
                (builder, value) -> ((ShpImportBuilder) builder).setEncoding(value));
        InputField<ShpImportRequest, Integer> batch = new InputField<>(
                "batchSize", "integer", "JDBC batch size", false,
                Optional.of(5000), List.of(), "batchSize = 1000",
                ShpImportOperation::positiveInteger,
                (builder, value) -> ((ShpImportBuilder) builder).setBatchSize(value));
        OutputField<ShpImportResult, Long> rows = Fields.output(
                "rows", "integer", "Imported rows", false, ShpImportResult::rows);
        return new OperationType<>(
                ID, SYMBOL, "Import a Shapefile into a database table",
                ShpImportRequest.class, ShpImportResult.class,
                ShpImportBuilder::new, ShpImportHandler::new,
                List.of(dataFile, database, table, schema, encoding, batch),
                List.of(rows),
                """
                task('import', ShpImport) {
                    dataFile = file('data/input.shp')
                    database = 'db'
                    tableName = 'abbaustelle'
                }
                """);
    }

    public record ShpImportRequest(
            Path dataFile, String database, String tableName,
            String schemaName, String encoding, int batchSize) {
    }

    public record ShpImportResult(long rows) {
    }

    public static final class ShpImportBuilder
            implements ch.so.agi.gretl.runtime.api.ConfigBuilder<ShpImportRequest> {
        private Path dataFile;
        private String database;
        private String tableName;
        private String schemaName;
        private String encoding;
        private int batchSize = 5000;

        public ShpImportBuilder setDataFile(Path value) { dataFile = value; return this; }
        public ShpImportBuilder setDatabase(String value) { database = value; return this; }
        public ShpImportBuilder setTableName(String value) { tableName = value; return this; }
        public ShpImportBuilder setSchemaName(String value) { schemaName = value; return this; }
        public ShpImportBuilder setEncoding(String value) { encoding = value; return this; }
        public ShpImportBuilder setBatchSize(Integer value) {
            batchSize = value == null ? 5000 : value; return this;
        }

        @Override
        public ShpImportRequest build() {
            if (dataFile == null) { throw new IllegalArgumentException("dataFile is missing"); }
            if (database == null || database.isBlank()) {
                throw new IllegalArgumentException("database is missing");
            }
            if (tableName == null || tableName.isBlank()) {
                throw new IllegalArgumentException("tableName is missing");
            }
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batchSize must be greater than zero");
            }
            return new ShpImportRequest(dataFile.toAbsolutePath().normalize(), database,
                    tableName, schemaName, encoding, batchSize);
        }
    }

    static final class ShpImportHandler
            implements OperationHandler<ShpImportRequest, ShpImportResult> {
        @Override
        public ShpImportResult execute(ShpImportRequest request, OperationContext context) {
            if (!Files.isRegularFile(request.dataFile())) {
                throw new TabularTaskException("SHP_FILE",
                        msg(context, "task.geo.file", request.dataFile()), "prepare", null);
            }
            long rows = GeoImportSupport.importFile(context, request.database(),
                    request.dataFile(), null, request.schemaName(), request.tableName(),
                    request.batchSize(), request.encoding());
            context.log().log(LogLevel.INFO, "gretl.shpimport.finished",
                    msg(context, "task.shpimport.finished"),
                    Map.of("geo.rows", Long.toString(rows)), null);
            return new ShpImportResult(rows);
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
        int result = value instanceof Number number ? number.intValue()
                : Integer.parseInt(value.toString());
        if (result <= 0) { throw new IllegalArgumentException("Value must be greater than zero"); }
        return result;
    }
}
