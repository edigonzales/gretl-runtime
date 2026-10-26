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

/** gretl-next JsonImport. */
public final class JsonImportOperation {
    public static final String ID = "gretl.json-import";
    public static final String SYMBOL = "JsonImport";
    private JsonImportOperation() {}

    public static OperationType<Request, Result> type() {
        InputField<Request, String> db = input("database", "text", "Target database alias", true,
                null, "database = 'db'", FormatTaskSupport::text, (b, v) -> b.database = v);
        InputField<Request, Path> file = input("jsonFile", "path", "JSON file to import", true,
                null, "jsonFile = file('data/input.json')", FormatTaskSupport::path,
                (b, v) -> b.jsonFile = v);
        InputField<Request, String> table = input("qualifiedTableName", "text", "Target table", true,
                null, "qualifiedTableName = 'public.documents'", FormatTaskSupport::text,
                (b, v) -> b.qualifiedTableName = v);
        InputField<Request, String> column = input("columnName", "text", "Target text column", true,
                null, "columnName = 'document'", FormatTaskSupport::text,
                (b, v) -> b.columnName = v);
        InputField<Request, Boolean> delete = input("deleteAllRows", "boolean", "Delete rows first",
                false, false, "deleteAllRows = true", FormatTaskSupport::bool,
                (b, v) -> b.deleteAllRows = v);
        OutputField<Result, Long> rows = Fields.output("rows", "integer", "Imported records",
                false, Result::rows);
        return new OperationType<>(ID, SYMBOL, "Import JSON records",
                Request.class, Result.class, Builder::new, Handler::new,
                List.of(db, file, table, column, delete), List.of(rows),
                "task('import', JsonImport) {\n    database = 'db'\n    jsonFile = file('data/input.json')\n    qualifiedTableName = 'public.documents'\n    columnName = 'document'\n}\n");
    }

    private static <T> InputField<Request, T> input(String name, String type, String desc,
            boolean required, T defaultValue, String example,
            Function<Object, T> converter, BiConsumer<Builder, T> setter) {
        return new InputField<>(name, type, desc, required,
                defaultValue == null ? Optional.empty() : Optional.of(defaultValue),
                List.of(), example, converter,
                (builder, value) -> setter.accept((Builder) builder, value));
    }

    public record Request(String database, Path jsonFile, String qualifiedTableName,
                          String columnName, boolean deleteAllRows) {}
    public record Result(long rows) {}

    public static final class Builder implements ConfigBuilder<Request> {
        private String database;
        private Path jsonFile;
        private String qualifiedTableName;
        private String columnName;
        private boolean deleteAllRows;

        @Override public Request build() {
            if (database == null || database.isBlank()) throw new IllegalArgumentException("database is missing");
            if (jsonFile == null) throw new IllegalArgumentException("jsonFile is missing");
            if (qualifiedTableName == null || qualifiedTableName.isBlank()) throw new IllegalArgumentException("qualifiedTableName is missing");
            if (columnName == null || columnName.isBlank()) throw new IllegalArgumentException("columnName is missing");
            return new Request(database, jsonFile, qualifiedTableName, columnName, deleteAllRows);
        }
    }

    static final class Handler implements OperationHandler<Request, Result> {
        @Override public Result execute(Request request, OperationContext context) {
            try {
                int rows = new IoxWkfDatabaseEngine().importJson(
                        new IoxWkfDatabaseEngine.JsonImportRequest(
                                FormatTaskSupport.database(context, request.database()),
                                request.jsonFile(), request.qualifiedTableName(),
                                request.columnName(), request.deleteAllRows()));
                return new Result(rows);
            } catch (InterlisTaskException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new InterlisTaskException("JSON_IMPORT", "JsonImport failed",
                        "import", Optional.empty(), exception);
            }
        }
    }
}
