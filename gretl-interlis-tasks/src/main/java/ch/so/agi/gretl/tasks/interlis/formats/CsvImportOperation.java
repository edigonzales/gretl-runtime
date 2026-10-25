package ch.so.agi.gretl.tasks.interlis.formats;

import ch.so.agi.gretl.internal.formats.FormatTaskSupport;

import ch.so.agi.gretl.internal.InterlisTaskException;
import ch.so.agi.gretl.internal.ioxwkf.IoxWkfDatabaseEngine;
import ch.so.agi.gretl.runtime.api.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** gretl-next CsvImport through iox-wkf Csv2db. */
public final class CsvImportOperation {
    public static final String ID = "gretl.csv-import";
    public static final String SYMBOL = "CsvImport";
    private CsvImportOperation() {}

    public static OperationType<Request, Result> type() {
        InputField<Request, String> db = input("database", "text", "Target database alias", true,
                null, "database = 'db'", FormatTaskSupport::text, (b, v) -> b.database = v);
        InputField<Request, Path> file = input("dataFile", "path", "CSV file to import", true,
                null, "dataFile = file('data/input.csv')", FormatTaskSupport::path,
                (b, v) -> b.dataFile = v);
        InputField<Request, String> table = input("tableName", "text", "Target table", true,
                null, "tableName = 'colors'", FormatTaskSupport::text, (b, v) -> b.tableName = v);
        InputField<Request, Boolean> header = input("firstLineIsHeader", "boolean",
                "Use first CSV line as header", false, true, "firstLineIsHeader = false",
                FormatTaskSupport::bool, (b, v) -> b.firstLineIsHeader = v);
        InputField<Request, String> delimiter = input("valueDelimiter", "text",
                "CSV quote character", false, null, "valueDelimiter = '\"'",
                FormatTaskSupport::text, (b, v) -> b.valueDelimiter = v);
        InputField<Request, String> separator = input("valueSeparator", "text",
                "CSV field separator", false, null, "valueSeparator = ','",
                FormatTaskSupport::text, (b, v) -> b.valueSeparator = v);
        InputField<Request, String> schema = input("schemaName", "text", "Target schema",
                false, null, "schemaName = 'public'", FormatTaskSupport::text,
                (b, v) -> b.schemaName = v);
        InputField<Request, String> encoding = input("encoding", "text", "CSV encoding",
                false, null, "encoding = 'UTF-8'", FormatTaskSupport::text,
                (b, v) -> b.encoding = v);
        InputField<Request, Integer> batch = input("batchSize", "integer", "JDBC batch size",
                false, null, "batchSize = 1000", FormatTaskSupport::integer,
                (b, v) -> b.batchSize = v);
        OutputField<Result, Path> result = Fields.output("file", "path",
                "Imported CSV file", false, Result::file);
        return new OperationType<>(ID, SYMBOL, "Import CSV through iox-wkf",
                Request.class, Result.class, Builder::new, Handler::new,
                List.of(db, file, table, header, delimiter, separator, schema, encoding, batch),
                List.of(result),
                "task('import', CsvImport) {\n    database = 'db'\n    dataFile = file('data/input.csv')\n    tableName = 'colors'\n}\n");
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
            String schemaName, String encoding, Integer batchSize) {}
    public record Result(Path file) {}

    public static final class Builder implements ConfigBuilder<Request> {
        private String database;
        private Path dataFile;
        private String tableName;
        private boolean firstLineIsHeader = true;
        private String valueDelimiter;
        private String valueSeparator;
        private String schemaName;
        private String encoding;
        private Integer batchSize;

        @Override public Request build() {
            if (database == null || database.isBlank()) throw new IllegalArgumentException("database is missing");
            if (dataFile == null) throw new IllegalArgumentException("dataFile is missing");
            if (tableName == null || tableName.isBlank()) throw new IllegalArgumentException("tableName is missing");
            return new Request(database, dataFile, tableName, firstLineIsHeader,
                    valueDelimiter, valueSeparator, schemaName, encoding, batchSize);
        }
    }

    static final class Handler implements OperationHandler<Request, Result> {
        @Override public Result execute(Request request, OperationContext context) {
            try {
                new IoxWkfDatabaseEngine().importCsv(new IoxWkfDatabaseEngine.CsvImportRequest(
                        FormatTaskSupport.database(context, request.database()),
                        request.dataFile(), request.tableName(), request.firstLineIsHeader(),
                        request.valueDelimiter(), request.valueSeparator(), request.schemaName(),
                        request.encoding(), request.batchSize()));
                return new Result(request.dataFile());
            } catch (InterlisTaskException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new InterlisTaskException("CSV_IMPORT", "CsvImport failed",
                        "import", Optional.empty(), exception);
            }
        }
    }
}
