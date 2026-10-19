package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** JsonImport task: import a JSON document into a database text column. */
public final class JsonImportOperation {
    public static final String ID = "gretl.json-import";
    public static final String SYMBOL = "JsonImport";

    private JsonImportOperation() {
    }

    public static OperationType<JsonImportRequest, JsonImportResult> type() {
        InputField<JsonImportRequest, String> database = new InputField<>(
                "database", "text", "Alias of the target database connection", true,
                Optional.empty(), List.of(), "database = 'db'",
                JsonImportOperation::text,
                (builder, value) -> ((JsonImportBuilder) builder).setDatabase(value));
        InputField<JsonImportRequest, Path> jsonFile = new InputField<>(
                "jsonFile", "path", "JSON file to import", true,
                Optional.empty(), List.of(), "jsonFile = file('data/input.json')",
                JsonImportOperation::path,
                (builder, value) -> ((JsonImportBuilder) builder).setJsonFile(value));
        InputField<JsonImportRequest, String> table = new InputField<>(
                "qualifiedTableName", "text", "Target table name", true,
                Optional.empty(), List.of(), "qualifiedTableName = 'public.documents'",
                JsonImportOperation::text,
                (builder, value) -> ((JsonImportBuilder) builder)
                        .setQualifiedTableName(value));
        InputField<JsonImportRequest, String> column = new InputField<>(
                "columnName", "text", "Target text column", true,
                Optional.empty(), List.of(), "columnName = 'document'",
                JsonImportOperation::text,
                (builder, value) -> ((JsonImportBuilder) builder).setColumnName(value));
        InputField<JsonImportRequest, Boolean> delete = new InputField<>(
                "deleteAllRows", "boolean", "Delete all target rows first", false,
                Optional.of(false), List.of(), "deleteAllRows = true",
                JsonImportOperation::bool,
                (builder, value) -> ((JsonImportBuilder) builder).setDeleteAllRows(value));
        OutputField<JsonImportResult, Long> rows = Fields.output(
                "rows", "integer", "Imported JSON records", false, JsonImportResult::rows);
        return new OperationType<>(
                ID, SYMBOL, "Import JSON records into a database text column",
                JsonImportRequest.class, JsonImportResult.class,
                JsonImportBuilder::new, JsonImportHandler::new,
                List.of(database, jsonFile, table, column, delete),
                List.of(rows),
                """
                task('import', JsonImport) {
                    database = 'db'
                    jsonFile = file('data/input.json')
                    qualifiedTableName = 'public.documents'
                    columnName = 'document'
                }
                """);
    }

    public record JsonImportRequest(
            String database, Path jsonFile, String qualifiedTableName,
            String columnName, boolean deleteAllRows) {
    }

    public record JsonImportResult(long rows) {
    }

    public static final class JsonImportBuilder
            implements ch.so.agi.gretl.runtime.api.ConfigBuilder<JsonImportRequest> {
        private String database;
        private Path jsonFile;
        private String qualifiedTableName;
        private String columnName;
        private boolean deleteAllRows;

        public JsonImportBuilder setDatabase(String value) {
            database = value;
            return this;
        }

        public JsonImportBuilder setJsonFile(Path value) {
            jsonFile = value;
            return this;
        }

        public JsonImportBuilder setQualifiedTableName(String value) {
            qualifiedTableName = value;
            return this;
        }

        public JsonImportBuilder setColumnName(String value) {
            columnName = value;
            return this;
        }

        public JsonImportBuilder setDeleteAllRows(Boolean value) {
            deleteAllRows = Boolean.TRUE.equals(value);
            return this;
        }

        @Override
        public JsonImportRequest build() {
            if (database == null || database.isBlank()) {
                throw new IllegalArgumentException("database is missing");
            }
            if (jsonFile == null) {
                throw new IllegalArgumentException("jsonFile is missing");
            }
            if (qualifiedTableName == null || qualifiedTableName.isBlank()) {
                throw new IllegalArgumentException("qualifiedTableName is missing");
            }
            if (columnName == null || columnName.isBlank()) {
                throw new IllegalArgumentException("columnName is missing");
            }
            return new JsonImportRequest(database, jsonFile.toAbsolutePath().normalize(),
                    qualifiedTableName, columnName, deleteAllRows);
        }
    }

    static final class JsonImportHandler
            implements OperationHandler<JsonImportRequest, JsonImportResult> {
        @Override
        public JsonImportResult execute(JsonImportRequest request, OperationContext context) {
            if (!Files.isRegularFile(request.jsonFile())) {
                throw new TabularTaskException("JSON_FILE",
                        msg(context, "task.json.file", request.jsonFile()), "prepare", null);
            }
            if (!request.columnName().matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new TabularTaskException("JSON_COLUMN",
                        msg(context, "task.json.column"), "prepare", null);
            }
            String table = TabularDbSupport.qualifiedTable(null,
                    request.qualifiedTableName());
            Db2DbSupport.DatabaseConfig config = Db2DbSupport.config(context, request.database());
            Connection connection = null;
            try {
                JsonNode root = new ObjectMapper().readTree(
                        Files.readString(request.jsonFile(), StandardCharsets.UTF_8));
                connection = Db2DbSupport.open(config);
                if (request.deleteAllRows()) {
                    try (var statement = connection.createStatement()) {
                        statement.executeUpdate("DELETE FROM " + table);
                    }
                }
                String column = request.columnName();
                String sql = "INSERT INTO " + table + " (" + column + ") VALUES (?)";
                long count = 0;
                try (PreparedStatement insert = connection.prepareStatement(sql)) {
                    if (root.isArray()) {
                        for (JsonNode node : root) {
                            insert.setString(1, node.toString());
                            insert.addBatch();
                            count++;
                        }
                        if (count > 0) {
                            insert.executeBatch();
                        }
                    } else {
                        insert.setString(1, root.toString());
                        count = insert.executeUpdate();
                    }
                }
                connection.commit();
                context.log().log(LogLevel.INFO, "gretl.jsonimport.finished",
                        msg(context, "task.jsonimport.finished"),
                        Map.of("tabular.table", table, "tabular.rows", Long.toString(count)),
                        null);
                return new JsonImportResult(count);
            } catch (TabularTaskException exception) {
                rollback(connection);
                throw exception;
            } catch (Exception exception) {
                rollback(connection);
                throw new TabularTaskException("JSON_IMPORT",
                        msg(context, "task.jsonimport.failed"), "import", exception);
            } finally {
                close(connection);
            }
        }

        private static void rollback(Connection connection) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (Exception ignored) {
                    // Preserve primary failure.
                }
            }
        }

        private static void close(Connection connection) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {
                    // Best-effort cleanup.
                }
            }
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }
    }

    private static Path path(Object value) {
        if (value instanceof Path path) {
            return path.normalize();
        }
        if (value instanceof CharSequence text) {
            return Path.of(text.toString()).normalize();
        }
        throw new IllegalArgumentException("Path expected");
    }

    private static String text(Object value) {
        if (value instanceof CharSequence text && !text.toString().isBlank()) {
            return text.toString();
        }
        throw new IllegalArgumentException("Non-blank text expected");
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new IllegalArgumentException("Boolean expected");
    }
}
