package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** ShpExport task: export a database table into a Shapefile. */
public final class ShpExportOperation {
    public static final String ID = "gretl.shp-export";
    public static final String SYMBOL = "ShpExport";

    private ShpExportOperation() {
    }

    public static OperationType<ShpExportRequest, ShpExportResult> type() {
        InputField<ShpExportRequest, String> database = new InputField<>(
                "database", "text", "Alias of the source database connection", true,
                Optional.empty(), List.of(), "database = 'db'",
                ShpExportOperation::text,
                (builder, value) -> ((ShpExportBuilder) builder).setDatabase(value));
        InputField<ShpExportRequest, Path> dataFile = new InputField<>(
                "dataFile", "path", "Shapefile output path", true,
                Optional.empty(), List.of(), "dataFile = file('build/output.shp')",
                ShpExportOperation::path,
                (builder, value) -> ((ShpExportBuilder) builder).setDataFile(value));
        InputField<ShpExportRequest, String> table = new InputField<>(
                "tableName", "text", "Source table name", true,
                Optional.empty(), List.of(), "tableName = 'abbaustelle'",
                ShpExportOperation::text,
                (builder, value) -> ((ShpExportBuilder) builder).setTableName(value));
        InputField<ShpExportRequest, String> schema = new InputField<>(
                "schemaName", "text", "Optional source schema", false,
                Optional.empty(), List.of(), "schemaName = 'public'",
                ShpExportOperation::text,
                (builder, value) -> ((ShpExportBuilder) builder).setSchemaName(value));
        InputField<ShpExportRequest, String> encoding = new InputField<>(
                "encoding", "text", "Shapefile encoding", false,
                Optional.of("UTF-8"), List.of(), "encoding = 'ISO-8859-1'",
                ShpExportOperation::text,
                (builder, value) -> ((ShpExportBuilder) builder).setEncoding(value));
        OutputField<ShpExportResult, Path> file = Fields.output(
                "file", "path", "Written Shapefile", false, ShpExportResult::file);
        OutputField<ShpExportResult, Long> rows = Fields.output(
                "rows", "integer", "Exported rows", false, ShpExportResult::rows);
        return new OperationType<>(
                ID, SYMBOL, "Export a database table into a Shapefile",
                ShpExportRequest.class, ShpExportResult.class,
                ShpExportBuilder::new, ShpExportHandler::new,
                List.of(database, dataFile, table, schema, encoding),
                List.of(file, rows),
                """
                task('export', ShpExport) {
                    database = 'db'
                    dataFile = file('build/output.shp')
                    tableName = 'abbaustelle'
                }
                """);
    }

    public record ShpExportRequest(
            String database, Path dataFile, String tableName,
            String schemaName, String encoding) {
    }

    public record ShpExportResult(Path file, long rows) {
    }

    public static final class ShpExportBuilder
            implements ch.so.agi.gretl.runtime.api.ConfigBuilder<ShpExportRequest> {
        private String database;
        private Path dataFile;
        private String tableName;
        private String schemaName;
        private String encoding = "UTF-8";

        public ShpExportBuilder setDatabase(String value) { database = value; return this; }
        public ShpExportBuilder setDataFile(Path value) { dataFile = value; return this; }
        public ShpExportBuilder setTableName(String value) { tableName = value; return this; }
        public ShpExportBuilder setSchemaName(String value) { schemaName = value; return this; }
        public ShpExportBuilder setEncoding(String value) { encoding = value; return this; }

        @Override
        public ShpExportRequest build() {
            if (database == null || database.isBlank()) {
                throw new IllegalArgumentException("database is missing");
            }
            if (dataFile == null) { throw new IllegalArgumentException("dataFile is missing"); }
            if (tableName == null || tableName.isBlank()) {
                throw new IllegalArgumentException("tableName is missing");
            }
            return new ShpExportRequest(database, dataFile.toAbsolutePath().normalize(),
                    tableName, schemaName, encoding == null ? "UTF-8" : encoding);
        }
    }

    static final class ShpExportHandler
            implements OperationHandler<ShpExportRequest, ShpExportResult> {
        @Override
        public ShpExportResult execute(ShpExportRequest request, OperationContext context) {
            long rows = GeoExportSupport.export(context, request.database(),
                    request.schemaName(), request.tableName(), request.dataFile(),
                    false, null, request.encoding());
            context.log().log(LogLevel.INFO, "gretl.shpexport.finished",
                    msg(context, "task.shpexport.finished"),
                    Map.of("geo.rows", Long.toString(rows)), null);
            return new ShpExportResult(request.dataFile(), rows);
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
}
