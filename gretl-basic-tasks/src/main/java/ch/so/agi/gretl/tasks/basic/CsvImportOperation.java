package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.csv.CSVRecord;

/** CsvImport task: import a CSV file into a database table. */
public final class CsvImportOperation {
    public static final String ID = "gretl.csv-import";
    public static final String SYMBOL = "CsvImport";

    private CsvImportOperation() {
    }

    public static OperationType<CsvImportRequest, CsvImportResult> type() {
        InputField<CsvImportRequest, String> database = new InputField<>(
                "database", "text", "Alias of the target database connection", true,
                Optional.empty(), List.of(), "database = 'db'",
                CsvImportOperation::text,
                (builder, value) -> ((CsvImportBuilder) builder).setDatabase(value));
        InputField<CsvImportRequest, Path> dataFile = new InputField<>(
                "dataFile", "path", "CSV file to import", true,
                Optional.empty(), List.of(), "dataFile = file('data/input.csv')",
                CsvImportOperation::path,
                (builder, value) -> ((CsvImportBuilder) builder).setDataFile(value));
        InputField<CsvImportRequest, String> tableName = new InputField<>(
                "tableName", "text", "Target table name", true,
                Optional.empty(), List.of(), "tableName = 'colors'",
                CsvImportOperation::text,
                (builder, value) -> ((CsvImportBuilder) builder).setTableName(value));
        InputField<CsvImportRequest, Boolean> header = new InputField<>(
                "firstLineIsHeader", "boolean", "Use the first CSV line as header", false,
                Optional.of(true), List.of(), "firstLineIsHeader = false",
                CsvImportOperation::bool,
                (builder, value) -> ((CsvImportBuilder) builder).setFirstLineIsHeader(value));
        InputField<CsvImportRequest, String> delimiter = new InputField<>(
                "valueDelimiter", "text", "CSV field delimiter", false,
                Optional.of(CsvSupport.DEFAULT_DELIMITER), List.of(),
                "valueDelimiter = ','", CsvImportOperation::text,
                (builder, value) -> ((CsvImportBuilder) builder).setValueDelimiter(value));
        InputField<CsvImportRequest, String> separator = new InputField<>(
                "valueSeparator", "text", "CSV quote character", false,
                Optional.of(CsvSupport.DEFAULT_QUOTE), List.of(),
                "valueSeparator = '\"'", CsvImportOperation::text,
                (builder, value) -> ((CsvImportBuilder) builder).setValueSeparator(value));
        InputField<CsvImportRequest, String> schema = new InputField<>(
                "schemaName", "text", "Optional target schema", false,
                Optional.empty(), List.of(), "schemaName = 'public'",
                CsvImportOperation::text,
                (builder, value) -> ((CsvImportBuilder) builder).setSchemaName(value));
        InputField<CsvImportRequest, String> encoding = new InputField<>(
                "encoding", "text", "CSV encoding", false,
                Optional.of(CsvSupport.DEFAULT_ENCODING), List.of(),
                "encoding = 'UTF-8'", CsvImportOperation::text,
                (builder, value) -> ((CsvImportBuilder) builder).setEncoding(value));
        InputField<CsvImportRequest, Integer> batchSize = new InputField<>(
                "batchSize", "integer", "JDBC batch size", false,
                Optional.of(5000), List.of(), "batchSize = 1000",
                CsvImportOperation::positiveInteger,
                (builder, value) -> ((CsvImportBuilder) builder).setBatchSize(value));
        OutputField<CsvImportResult, Long> rows = Fields.output(
                "rows", "integer", "Imported rows", false, CsvImportResult::rows);
        return new OperationType<>(
                ID, SYMBOL, "Import a CSV file into a database table",
                CsvImportRequest.class, CsvImportResult.class,
                CsvImportBuilder::new, CsvImportHandler::new,
                List.of(database, dataFile, tableName, header, delimiter, separator,
                        schema, encoding, batchSize),
                List.of(rows),
                """
                task('import', CsvImport) {
                    database = 'db'
                    dataFile = file('data/input.csv')
                    tableName = 'colors'
                }
                """);
    }

    public record CsvImportRequest(
            String database, Path dataFile, String tableName,
            boolean firstLineIsHeader, String valueDelimiter, String valueSeparator,
            String schemaName, String encoding, int batchSize) {
    }

    public record CsvImportResult(long rows) {
    }

    public static final class CsvImportBuilder
            implements ch.so.agi.gretl.runtime.api.ConfigBuilder<CsvImportRequest> {
        private String database;
        private Path dataFile;
        private String tableName;
        private boolean firstLineIsHeader = true;
        private String valueDelimiter = CsvSupport.DEFAULT_DELIMITER;
        private String valueSeparator = CsvSupport.DEFAULT_QUOTE;
        private String schemaName;
        private String encoding = CsvSupport.DEFAULT_ENCODING;
        private int batchSize = 5000;

        public CsvImportBuilder setDatabase(String value) {
            database = value;
            return this;
        }

        public CsvImportBuilder setDataFile(Path value) {
            dataFile = value;
            return this;
        }

        public CsvImportBuilder setTableName(String value) {
            tableName = value;
            return this;
        }

        public CsvImportBuilder setFirstLineIsHeader(Boolean value) {
            firstLineIsHeader = value == null || value;
            return this;
        }

        public CsvImportBuilder setValueDelimiter(String value) {
            valueDelimiter = value;
            return this;
        }

        public CsvImportBuilder setValueSeparator(String value) {
            valueSeparator = value;
            return this;
        }

        public CsvImportBuilder setSchemaName(String value) {
            schemaName = value;
            return this;
        }

        public CsvImportBuilder setEncoding(String value) {
            encoding = value;
            return this;
        }

        public CsvImportBuilder setBatchSize(Integer value) {
            batchSize = value == null ? 5000 : value;
            return this;
        }

        @Override
        public CsvImportRequest build() {
            if (database == null || database.isBlank()) {
                throw new IllegalArgumentException("database is missing");
            }
            if (dataFile == null) {
                throw new IllegalArgumentException("dataFile is missing");
            }
            if (tableName == null || tableName.isBlank()) {
                throw new IllegalArgumentException("tableName is missing");
            }
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batchSize must be greater than zero");
            }
            return new CsvImportRequest(database, dataFile.toAbsolutePath().normalize(),
                    tableName, firstLineIsHeader, valueDelimiter, valueSeparator,
                    schemaName, CsvSupport.encoding(encoding), batchSize);
        }
    }

    static final class CsvImportHandler
            implements OperationHandler<CsvImportRequest, CsvImportResult> {
        @Override
        public CsvImportResult execute(CsvImportRequest request, OperationContext context) {
            if (!Files.isRegularFile(request.dataFile())) {
                throw new TabularTaskException("CSV_FILE",
                        msg(context, "task.csv.file", request.dataFile()), "prepare", null);
            }
            String table = TabularDbSupport.qualifiedTable(request.schemaName(),
                    request.tableName());
            Db2DbSupport.DatabaseConfig config = Db2DbSupport.config(context, request.database());
            Connection connection = null;
            try {
                CsvSupport.ParsedCsv csv = CsvSupport.read(request.dataFile(),
                        request.encoding(), request.valueDelimiter(),
                        request.valueSeparator(), request.firstLineIsHeader());
                connection = Db2DbSupport.open(config);
                List<TabularDbSupport.Column> targetColumns =
                        TabularDbSupport.columns(connection, table);
                Map<String, TabularDbSupport.Column> byName =
                        TabularDbSupport.byLowerName(targetColumns);
                List<TabularDbSupport.Column> insertColumns = new ArrayList<>();
                if (request.firstLineIsHeader()) {
                    if (csv.headers().isEmpty()) {
                        throw new TabularTaskException("CSV_HEADER",
                                msg(context, "task.csv.header"), "prepare", null);
                    }
                    Map<String, Boolean> seen = new LinkedHashMap<>();
                    for (String header : csv.headers()) {
                        String normalized = header.toLowerCase(Locale.ROOT);
                        if (seen.put(normalized, Boolean.TRUE) != null) {
                            throw new TabularTaskException("CSV_HEADER",
                                    msg(context, "task.csv.duplicateHeader", header),
                                    "prepare", null);
                        }
                        TabularDbSupport.Column column = byName.get(normalized);
                        if (column == null) {
                            throw new TabularTaskException("CSV_COLUMN",
                                    msg(context, "task.csv.column", header, table),
                                    "prepare", null);
                        }
                        insertColumns.add(column);
                    }
                } else {
                    insertColumns.addAll(targetColumns);
                }
                String sql = insertSql(connection, table, insertColumns);
                long count = 0;
                int pending = 0;
                try (PreparedStatement insert = connection.prepareStatement(sql)) {
                    for (CSVRecord record : csv.records()) {
                        if (record.size() != insertColumns.size()) {
                            throw new TabularTaskException("CSV_RECORD",
                                    msg(context, "task.csv.recordSize", record.getRecordNumber(),
                                            insertColumns.size(), record.size()),
                                    "import", null);
                        }
                        for (int index = 0; index < insertColumns.size(); index++) {
                            bind(insert, index + 1, CsvSupport.stripBom(record.get(index)),
                                    insertColumns.get(index), record.getRecordNumber(), context);
                        }
                        insert.addBatch();
                        count++;
                        pending++;
                        if (pending >= request.batchSize()) {
                            insert.executeBatch();
                            insert.clearBatch();
                            pending = 0;
                        }
                    }
                    if (pending > 0) {
                        insert.executeBatch();
                    }
                }
                connection.commit();
                context.log().log(LogLevel.INFO, "gretl.csvimport.finished",
                        msg(context, "task.csvimport.finished"),
                        Map.of("tabular.table", table, "tabular.rows", Long.toString(count)),
                        null);
                return new CsvImportResult(count);
            } catch (TabularTaskException exception) {
                rollback(connection);
                throw exception;
            } catch (Exception exception) {
                rollback(connection);
                throw new TabularTaskException("CSV_IMPORT",
                        msg(context, "task.csvimport.failed"), "import", exception);
            } finally {
                close(connection);
            }
        }

        private static String insertSql(Connection connection, String table,
                                        List<TabularDbSupport.Column> columns)
                throws SQLException {
            List<String> names = new ArrayList<>();
            List<String> values = new ArrayList<>();
            for (TabularDbSupport.Column column : columns) {
                names.add(Db2DbSupport.quoteIdentifier(connection, column.name()));
                values.add("?");
            }
            return "INSERT INTO " + table + " (" + String.join(", ", names)
                    + ") VALUES (" + String.join(", ", values) + ")";
        }

        private static void bind(PreparedStatement statement, int index, String value,
                                 TabularDbSupport.Column column, long row,
                                 OperationContext context) throws SQLException {
            int type = column.type();
            boolean text = type == Types.CHAR || type == Types.VARCHAR
                    || type == Types.LONGVARCHAR || type == Types.NCHAR
                    || type == Types.NVARCHAR || type == Types.LONGNVARCHAR;
            if (value == null || value.isEmpty()) {
                if (text) {
                    statement.setString(index, value == null ? "" : value);
                } else {
                    statement.setNull(index, type);
                }
                return;
            }
            try {
                switch (type) {
                    case Types.TINYINT, Types.SMALLINT, Types.INTEGER ->
                            statement.setInt(index, Integer.parseInt(value));
                    case Types.BIGINT -> statement.setLong(index, Long.parseLong(value));
                    case Types.NUMERIC, Types.DECIMAL ->
                            statement.setBigDecimal(index, new BigDecimal(value));
                    case Types.REAL -> statement.setFloat(index, Float.parseFloat(value));
                    case Types.FLOAT, Types.DOUBLE ->
                            statement.setDouble(index, Double.parseDouble(value));
                    case Types.BOOLEAN, Types.BIT ->
                            statement.setBoolean(index, booleanValue(value));
                    case Types.DATE -> statement.setDate(index, java.sql.Date.valueOf(value));
                    case Types.TIME -> statement.setTime(index, java.sql.Time.valueOf(value));
                    case Types.TIMESTAMP ->
                            statement.setTimestamp(index, java.sql.Timestamp.valueOf(value));
                    default -> statement.setString(index, value);
                }
            } catch (RuntimeException exception) {
                throw new TabularTaskException("CSV_VALUE",
                        msg(context, "task.csv.value", column.name(), row, value),
                        "import", exception);
            }
        }

        private static boolean booleanValue(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "true", "1", "yes", "y" -> true;
                case "false", "0", "no", "n" -> false;
                default -> throw new IllegalArgumentException("Not a boolean: " + value);
            };
        }

        private static void rollback(Connection connection) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // Preserve primary failure.
                }
            }
        }

        private static void close(Connection connection) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
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

    private static Integer positiveInteger(Object value) {
        int result;
        if (value instanceof Number number) {
            result = number.intValue();
        } else if (value instanceof CharSequence text
                && text.toString().matches("[0-9]+")) {
            result = Integer.parseInt(text.toString());
        } else {
            throw new IllegalArgumentException("Positive integer expected");
        }
        if (result <= 0) {
            throw new IllegalArgumentException("Value must be greater than zero");
        }
        return result;
    }
}
