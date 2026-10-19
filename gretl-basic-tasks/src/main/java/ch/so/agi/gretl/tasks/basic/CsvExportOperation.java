package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.csv.CSVPrinter;

/** CsvExport task: export a database table into a CSV file. */
public final class CsvExportOperation {
    public static final String ID = "gretl.csv-export";
    public static final String SYMBOL = "CsvExport";

    private CsvExportOperation() {
    }

    public static OperationType<CsvExportRequest, CsvExportResult> type() {
        InputField<CsvExportRequest, String> database = new InputField<>(
                "database", "text", "Alias of the source database connection", true,
                Optional.empty(), List.of(), "database = 'db'",
                CsvExportOperation::text,
                (builder, value) -> ((CsvExportBuilder) builder).setDatabase(value));
        InputField<CsvExportRequest, Path> dataFile = new InputField<>(
                "dataFile", "path", "CSV output file", true,
                Optional.empty(), List.of(), "dataFile = file('build/export.csv')",
                CsvExportOperation::path,
                (builder, value) -> ((CsvExportBuilder) builder).setDataFile(value));
        InputField<CsvExportRequest, String> tableName = new InputField<>(
                "tableName", "text", "Source table name", true,
                Optional.empty(), List.of(), "tableName = 'colors'",
                CsvExportOperation::text,
                (builder, value) -> ((CsvExportBuilder) builder).setTableName(value));
        InputField<CsvExportRequest, Boolean> header = new InputField<>(
                "firstLineIsHeader", "boolean", "Write a CSV header line", false,
                Optional.of(true), List.of(), "firstLineIsHeader = false",
                CsvExportOperation::bool,
                (builder, value) -> ((CsvExportBuilder) builder).setFirstLineIsHeader(value));
        InputField<CsvExportRequest, String> delimiter = new InputField<>(
                "valueDelimiter", "text", "CSV field delimiter", false,
                Optional.of(CsvSupport.DEFAULT_DELIMITER), List.of(),
                "valueDelimiter = ','", CsvExportOperation::text,
                (builder, value) -> ((CsvExportBuilder) builder).setValueDelimiter(value));
        InputField<CsvExportRequest, String> separator = new InputField<>(
                "valueSeparator", "text", "CSV quote character", false,
                Optional.of(CsvSupport.DEFAULT_QUOTE), List.of(),
                "valueSeparator = '\"'", CsvExportOperation::text,
                (builder, value) -> ((CsvExportBuilder) builder).setValueSeparator(value));
        InputField<CsvExportRequest, String> schema = new InputField<>(
                "schemaName", "text", "Optional source schema", false,
                Optional.empty(), List.of(), "schemaName = 'public'",
                CsvExportOperation::text,
                (builder, value) -> ((CsvExportBuilder) builder).setSchemaName(value));
        InputField<CsvExportRequest, List<String>> attributes = new InputField<>(
                "attributes", "list", "Columns to export; empty means all", false,
                Optional.of(List.of()), List.of(), "attributes = ['id', 'name']",
                CsvExportOperation::texts,
                (builder, value) -> ((CsvExportBuilder) builder).setAttributes(value));
        InputField<CsvExportRequest, String> encoding = new InputField<>(
                "encoding", "text", "CSV encoding", false,
                Optional.of(CsvSupport.DEFAULT_ENCODING), List.of(),
                "encoding = 'UTF-8'", CsvExportOperation::text,
                (builder, value) -> ((CsvExportBuilder) builder).setEncoding(value));
        OutputField<CsvExportResult, Path> file = Fields.output(
                "file", "path", "Written CSV file", false, CsvExportResult::file);
        OutputField<CsvExportResult, Long> rows = Fields.output(
                "rows", "integer", "Exported rows", false, CsvExportResult::rows);
        return new OperationType<>(
                ID, SYMBOL, "Export a database table into a CSV file",
                CsvExportRequest.class, CsvExportResult.class,
                CsvExportBuilder::new, CsvExportHandler::new,
                List.of(database, dataFile, tableName, header, delimiter, separator,
                        schema, attributes, encoding),
                List.of(file, rows),
                """
                task('export', CsvExport) {
                    database = 'db'
                    dataFile = file('build/export.csv')
                    tableName = 'colors'
                }
                """);
    }

    public record CsvExportRequest(
            String database, Path dataFile, String tableName, boolean firstLineIsHeader,
            String valueDelimiter, String valueSeparator, String schemaName,
            List<String> attributes, String encoding) {
        public CsvExportRequest {
            attributes = List.copyOf(attributes);
        }
    }

    public record CsvExportResult(Path file, long rows) {
    }

    public static final class CsvExportBuilder
            implements ch.so.agi.gretl.runtime.api.ConfigBuilder<CsvExportRequest> {
        private String database;
        private Path dataFile;
        private String tableName;
        private boolean firstLineIsHeader = true;
        private String valueDelimiter = CsvSupport.DEFAULT_DELIMITER;
        private String valueSeparator = CsvSupport.DEFAULT_QUOTE;
        private String schemaName;
        private List<String> attributes = List.of();
        private String encoding = CsvSupport.DEFAULT_ENCODING;

        public CsvExportBuilder setDatabase(String value) {
            database = value;
            return this;
        }

        public CsvExportBuilder setDataFile(Path value) {
            dataFile = value;
            return this;
        }

        public CsvExportBuilder setTableName(String value) {
            tableName = value;
            return this;
        }

        public CsvExportBuilder setFirstLineIsHeader(Boolean value) {
            firstLineIsHeader = value == null || value;
            return this;
        }

        public CsvExportBuilder setValueDelimiter(String value) {
            valueDelimiter = value;
            return this;
        }

        public CsvExportBuilder setValueSeparator(String value) {
            valueSeparator = value;
            return this;
        }

        public CsvExportBuilder setSchemaName(String value) {
            schemaName = value;
            return this;
        }

        public CsvExportBuilder setAttributes(List<String> value) {
            attributes = value == null ? List.of() : value;
            return this;
        }

        public CsvExportBuilder setEncoding(String value) {
            encoding = value;
            return this;
        }

        @Override
        public CsvExportRequest build() {
            if (database == null || database.isBlank()) {
                throw new IllegalArgumentException("database is missing");
            }
            if (dataFile == null) {
                throw new IllegalArgumentException("dataFile is missing");
            }
            if (tableName == null || tableName.isBlank()) {
                throw new IllegalArgumentException("tableName is missing");
            }
            return new CsvExportRequest(database, dataFile.toAbsolutePath().normalize(),
                    tableName, firstLineIsHeader, valueDelimiter, valueSeparator,
                    schemaName, attributes, CsvSupport.encoding(encoding));
        }
    }

    static final class CsvExportHandler
            implements OperationHandler<CsvExportRequest, CsvExportResult> {
        @Override
        public CsvExportResult execute(CsvExportRequest request, OperationContext context) {
            String table = TabularDbSupport.qualifiedTable(request.schemaName(),
                    request.tableName());
            Db2DbSupport.DatabaseConfig config = Db2DbSupport.config(context, request.database());
            Connection connection = null;
            Path temporary = null;
            try {
                connection = Db2DbSupport.open(config);
                List<TabularDbSupport.Column> available =
                        TabularDbSupport.columns(connection, table);
                Map<String, TabularDbSupport.Column> byName =
                        TabularDbSupport.byLowerName(available);
                List<TabularDbSupport.Column> selected = new ArrayList<>();
                if (request.attributes().isEmpty()) {
                    selected.addAll(available);
                } else {
                    for (String attribute : request.attributes()) {
                        TabularDbSupport.Column column = byName.get(
                                attribute.toLowerCase(Locale.ROOT));
                        if (column == null) {
                            throw new TabularTaskException("CSV_COLUMN",
                                    msg(context, "task.csv.column", attribute, table),
                                    "prepare", null);
                        }
                        selected.add(column);
                    }
                }
                List<String> quoted = new ArrayList<>();
                for (TabularDbSupport.Column column : selected) {
                    quoted.add(Db2DbSupport.quoteIdentifier(connection, column.name()));
                }
                String select = "SELECT " + String.join(", ", quoted) + " FROM " + table;
                Files.createDirectories(request.dataFile().getParent());
                temporary = Files.createTempFile(request.dataFile().getParent(),
                        "." + request.dataFile().getFileName() + ".", ".csv");
                long count = 0;
                Charset charset = Charset.forName(request.encoding());
                try (BufferedWriter writer = Files.newBufferedWriter(temporary, charset);
                     CSVPrinter printer = CsvSupport.printer(writer,
                             request.valueDelimiter(), request.valueSeparator());
                     Statement statement = connection.createStatement();
                     ResultSet rows = statement.executeQuery(select)) {
                    if (request.firstLineIsHeader()) {
                        List<String> header = new ArrayList<>();
                        for (TabularDbSupport.Column column : selected) {
                            header.add(column.name());
                        }
                        printer.printRecord(header);
                    }
                    while (rows.next()) {
                        List<String> values = new ArrayList<>();
                        for (int index = 1; index <= selected.size(); index++) {
                            Object value = rows.getObject(index);
                            values.add(value == null ? "" : value.toString());
                        }
                        printer.printRecord(values);
                        count++;
                    }
                }
                move(temporary, request.dataFile());
                connection.commit();
                context.log().log(LogLevel.INFO, "gretl.csvexport.finished",
                        msg(context, "task.csvexport.finished"),
                        Map.of("tabular.table", table, "tabular.rows", Long.toString(count)),
                        null);
                return new CsvExportResult(request.dataFile(), count);
            } catch (TabularTaskException exception) {
                rollback(connection);
                deleteQuietly(temporary);
                throw exception;
            } catch (Exception exception) {
                rollback(connection);
                deleteQuietly(temporary);
                throw new TabularTaskException("CSV_EXPORT",
                        msg(context, "task.csvexport.failed"), "export", exception);
            } finally {
                close(connection);
            }
        }

        private static void move(Path temporary, Path target) throws IOException {
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        private static void deleteQuietly(Path path) {
            if (path == null) {
                return;
            }
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Preserve primary failure.
            }
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

    private static List<String> texts(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                result.add(text(element));
            }
        } else if (value instanceof Object[] array) {
            for (Object element : array) {
                result.add(text(element));
            }
        } else {
            throw new IllegalArgumentException("List expected");
        }
        return List.copyOf(result);
    }
}
