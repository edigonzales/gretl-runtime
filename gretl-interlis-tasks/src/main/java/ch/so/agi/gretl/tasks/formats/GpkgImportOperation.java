package ch.so.agi.gretl.tasks.formats;

import ch.so.agi.gretl.internal.interlis.InterlisTaskException;
import ch.so.agi.gretl.internal.ioxwkf.IoxWkfDatabaseEngine;
import ch.so.agi.gretl.runtime.api.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** gretl-next GpkgImport through iox-wkf Gpkg2db. */
public final class GpkgImportOperation {
    public static final String ID = "gretl.gpkg-import";
    public static final String SYMBOL = "GpkgImport";
    private GpkgImportOperation() {}

    public static OperationType<Request, Result> type() {
        InputField<Request, String> db = input("database", "text", "Target database alias", true,
                null, "database = 'db'", FormatTaskSupport::text, (b, v) -> b.database = v);
        InputField<Request, Path> file = input("dataFile", "path", "GeoPackage file", true,
                null, "dataFile = file('data/input.gpkg')", FormatTaskSupport::path,
                (b, v) -> b.dataFile = v);
        InputField<Request, String> source = input("srcTableName", "text", "GeoPackage table", true,
                null, "srcTableName = 'abbaustelle'", FormatTaskSupport::text,
                (b, v) -> b.srcTableName = v);
        InputField<Request, String> target = input("dstTableName", "text", "Target table", true,
                null, "dstTableName = 'abbaustelle'", FormatTaskSupport::text,
                (b, v) -> b.dstTableName = v);
        InputField<Request, String> schema = input("schemaName", "text", "Target schema",
                false, null, "schemaName = 'public'", FormatTaskSupport::text,
                (b, v) -> b.schemaName = v);
        InputField<Request, Integer> batch = input("batchSize", "integer", "JDBC batch size",
                false, null, "batchSize = 1000", FormatTaskSupport::integer,
                (b, v) -> b.batchSize = v);
        InputField<Request, Integer> fetch = input("fetchSize", "integer", "Fetch size",
                false, null, "fetchSize = 1000", FormatTaskSupport::integer,
                (b, v) -> b.fetchSize = v);
        OutputField<Result, Path> result = Fields.output("file", "path",
                "Imported GeoPackage", false, Result::file);
        return new OperationType<>(ID, SYMBOL, "Import GeoPackage through iox-wkf",
                Request.class, Result.class, Builder::new, Handler::new,
                List.of(db, file, source, target, schema, batch, fetch), List.of(result),
                "task('import', GpkgImport) {\n    database = 'db'\n    dataFile = file('data/input.gpkg')\n    srcTableName = 'abbaustelle'\n    dstTableName = 'abbaustelle'\n}\n");
    }

    private static <T> InputField<Request, T> input(String name, String type, String desc,
            boolean required, T defaultValue, String example,
            Function<Object, T> converter, BiConsumer<Builder, T> setter) {
        return new InputField<>(name, type, desc, required,
                defaultValue == null ? Optional.empty() : Optional.of(defaultValue),
                List.of(), example, converter,
                (builder, value) -> setter.accept((Builder) builder, value));
    }

    public record Request(String database, Path dataFile, String srcTableName,
                          String dstTableName, String schemaName,
                          Integer batchSize, Integer fetchSize) {}
    public record Result(Path file) {}

    public static final class Builder implements ConfigBuilder<Request> {
        private String database;
        private Path dataFile;
        private String srcTableName;
        private String dstTableName;
        private String schemaName;
        private Integer batchSize;
        private Integer fetchSize;

        @Override public Request build() {
            if (database == null || database.isBlank()) throw new IllegalArgumentException("database is missing");
            if (dataFile == null) throw new IllegalArgumentException("dataFile is missing");
            if (srcTableName == null || srcTableName.isBlank()) throw new IllegalArgumentException("srcTableName is missing");
            if (dstTableName == null || dstTableName.isBlank()) throw new IllegalArgumentException("dstTableName is missing");
            return new Request(database, dataFile, srcTableName, dstTableName, schemaName,
                    batchSize, fetchSize);
        }
    }

    static final class Handler implements OperationHandler<Request, Result> {
        @Override public Result execute(Request request, OperationContext context) {
            try {
                new IoxWkfDatabaseEngine().importGpkg(new IoxWkfDatabaseEngine.GpkgImportRequest(
                        FormatTaskSupport.database(context, request.database()),
                        request.dataFile(), request.srcTableName(), request.dstTableName(),
                        request.schemaName(), request.batchSize(), request.fetchSize()));
                return new Result(request.dataFile());
            } catch (InterlisTaskException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new InterlisTaskException("GPKG_IMPORT", "GpkgImport failed",
                        "import", Optional.empty(), exception);
            }
        }
    }
}
