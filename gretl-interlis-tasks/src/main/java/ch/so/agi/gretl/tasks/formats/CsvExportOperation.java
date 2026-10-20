package ch.so.agi.gretl.tasks.formats;

import ch.so.agi.gretl.internal.interlis.InterlisTaskException;
import ch.so.agi.gretl.internal.ioxwkf.IoxWkfDatabaseEngine;
import ch.so.agi.gretl.runtime.api.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** gretl-next CsvExport through iox-wkf Db2Csv. */
public final class CsvExportOperation {
    public static final String ID = "gretl.csv-export";
    public static final String SYMBOL = "CsvExport";
    private CsvExportOperation() {}

    public static OperationType<Request, Result> type() {
        InputField<Request, String> db = input("database", "text", "Source database alias", true,
                null, "database = 'db'", FormatTaskSupport::text, (b, v) -> b.database = v);
        InputField<Request, Path> file = input("dataFile", "path", "CSV output file", true,
                null, "dataFile = file('build/export.csv')", FormatTaskSupport::path,
                (b, v) -> b.dataFile = v);
        InputField<Request, String> table = input("tableName", "text", "Source table", true,
                null, "tableName = 'colors'", FormatTaskSupport::text, (b, v) -> b.tableName = v);
        InputField<Request, Boolean> header = input("firstLineIsHeader", "boolean",
                "Write a CSV header", false, true, "firstLineIsHeader = false",
                FormatTaskSupport::bool, (b, v) -> b.firstLineIsHeader = v);
        InputField<Request, String> delimiter = input("valueDelimiter", "text",
                "CSV quote character", false, null, "valueDelimiter = '\"'",
                FormatTaskSupport::text, (b, v) -> b.valueDelimiter = v);
        InputField<Request, String> separator = input("valueSeparator", "text",
                "CSV field separator", false, null, "valueSeparator = ','",
                FormatTaskSupport::text, (b, v) -> b.valueSeparator = v);
        InputField<Request, String> schema = input("schemaName", "text", "Source schema",
                false, null, "schemaName = 'public'", FormatTaskSupport::text,
                (b, v) -> b.schemaName = v);
        InputField<Request, List<String>> attributes = input("attributes", "list",
                "Columns to export", false, List.of(), "attributes = ['id', 'name']",
                FormatTaskSupport::strings, (b, v) -> b.attributes = v);
        InputField<Request, String> encoding = input("encoding", "text", "CSV encoding",
                false, null, "encoding = 'UTF-8'", FormatTaskSupport::text,
                (b, v) -> b.encoding = v);
        OutputField<Result, Path> result = Fields.output("file", "path",
                "Written CSV file", false, Result::file);
        return new OperationType<>(ID, SYMBOL, "Export CSV through iox-wkf",
                Request.class, Result.class, Builder::new, Handler::new,
                List.of(db, file, table, header, delimiter, separator, schema, attributes, encoding),
                List.of(result),
                "task('export', CsvExport) {\n    database = 'db'\n    dataFile = file('build/export.csv')\n    tableName = 'colors'\n}\n");
    }

    private static <T> InputField<Request, T> input(String name, String type, String desc,
            boolean required, T defaultValue, String example,
            Function<Object, T> converter, BiConsumer<Builder, T> setter) {
        return new InputField<>(name, type, desc, required,
                defaultValue == null ? Optional.empty() : Optional.of(defaultValue),
                List.of(), example, converter,
                (builder, value) -> setter.accept((Builder) builder, value));
    }

    public record Request(String database, Path dataFile, String tableName,
            boolean firstLineIsHeader, String valueDelimiter, String valueSeparator,
            String schemaName, List<String> attributes, String encoding) {
        public Request { attributes = List.copyOf(attributes); }
    }
    public record Result(Path file) {}

    public static final class Builder implements ConfigBuilder<Request> {
        private String database;
        private Path dataFile;
        private String tableName;
        private boolean firstLineIsHeader = true;
        private String valueDelimiter;
        private String valueSeparator;
        private String schemaName;
        private List<String> attributes = List.of();
        private String encoding;

        @Override public Request build() {
            if (database == null || database.isBlank()) throw new IllegalArgumentException("database is missing");
            if (dataFile == null) throw new IllegalArgumentException("dataFile is missing");
            if (tableName == null || tableName.isBlank()) throw new IllegalArgumentException("tableName is missing");
            return new Request(database, dataFile, tableName, firstLineIsHeader,
                    valueDelimiter, valueSeparator, schemaName, attributes, encoding);
        }
    }

    static final class Handler implements OperationHandler<Request, Result> {
        @Override public Result execute(Request request, OperationContext context) {
            try {
                new IoxWkfDatabaseEngine().exportCsv(new IoxWkfDatabaseEngine.CsvExportRequest(
                        FormatTaskSupport.database(context, request.database()),
                        request.dataFile(), request.tableName(), request.firstLineIsHeader(),
                        request.valueDelimiter(), request.valueSeparator(), request.schemaName(),
                        request.attributes(), request.encoding()));
                return new Result(request.dataFile());
            } catch (InterlisTaskException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new InterlisTaskException("CSV_EXPORT", "CsvExport failed",
                        "export", Optional.empty(), exception);
            }
        }
    }
}
