package ch.so.agi.gretl.tasks.formats;

import ch.so.agi.gretl.internal.interlis.InterlisTaskException;
import ch.so.agi.gretl.internal.ioxwkf.IoxWkfDatabaseEngine;
import ch.so.agi.gretl.runtime.api.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** gretl-next GpkgExport through iox-wkf Db2Gpkg. */
public final class GpkgExportOperation {
    public static final String ID = "gretl.gpkg-export";
    public static final String SYMBOL = "GpkgExport";
    private GpkgExportOperation() {}

    public static OperationType<Request, Result> type() {
        InputField<Request, String> db = input("database", "text", "Source database alias", true,
                null, "database = 'db'", FormatTaskSupport::text, (b, v) -> b.database = v);
        InputField<Request, Path> file = input("dataFile", "path", "GeoPackage output file", true,
                null, "dataFile = file('build/output.gpkg')", FormatTaskSupport::path,
                (b, v) -> b.dataFile = v);
        InputField<Request, List<String>> source = input("srcTableName", "list", "Source tables",
                true, null, "srcTableName = ['abbaustelle']", FormatTaskSupport::strings,
                (b, v) -> b.srcTableNames = v);
        InputField<Request, List<String>> target = input("dstTableName", "list",
                "Destination tables", true, null, "dstTableName = ['abbaustelle']",
                FormatTaskSupport::strings, (b, v) -> b.dstTableNames = v);
        InputField<Request, String> schema = input("schemaName", "text", "Source schema",
                false, null, "schemaName = 'public'", FormatTaskSupport::text,
                (b, v) -> b.schemaName = v);
        InputField<Request, Integer> batch = input("batchSize", "integer", "JDBC batch size",
                false, null, "batchSize = 1000", FormatTaskSupport::integer,
                (b, v) -> b.batchSize = v);
        InputField<Request, Integer> fetch = input("fetchSize", "integer", "Fetch size",
                false, null, "fetchSize = 1000", FormatTaskSupport::integer,
                (b, v) -> b.fetchSize = v);
        OutputField<Result, Path> result = Fields.output("file", "path",
                "Written GeoPackage", false, Result::file);
        return new OperationType<>(ID, SYMBOL, "Export GeoPackage through iox-wkf",
                Request.class, Result.class, Builder::new, Handler::new,
                List.of(db, file, source, target, schema, batch, fetch), List.of(result),
                "task('export', GpkgExport) {\n    database = 'db'\n    dataFile = file('build/output.gpkg')\n    srcTableName = ['abbaustelle']\n    dstTableName = ['abbaustelle']\n}\n");
    }

    private static <T> InputField<Request, T> input(String name, String type, String desc,
            boolean required, T defaultValue, String example,
            Function<Object, T> converter, BiConsumer<Builder, T> setter) {
        return new InputField<>(name, type, desc, required,
                defaultValue == null ? Optional.empty() : Optional.of(defaultValue),
                List.of(), example, converter,
                (builder, value) -> setter.accept((Builder) builder, value));
    }

    public record Request(String database, Path dataFile, List<String> srcTableNames,
                          List<String> dstTableNames, String schemaName,
                          Integer batchSize, Integer fetchSize) {
        public Request {
            srcTableNames = List.copyOf(srcTableNames);
            dstTableNames = List.copyOf(dstTableNames);
        }
    }
    public record Result(Path file) {}

    public static final class Builder implements ConfigBuilder<Request> {
        private String database;
        private Path dataFile;
        private List<String> srcTableNames = List.of();
        private List<String> dstTableNames = List.of();
        private String schemaName;
        private Integer batchSize;
        private Integer fetchSize;

        @Override public Request build() {
            if (database == null || database.isBlank()) throw new IllegalArgumentException("database is missing");
            if (dataFile == null) throw new IllegalArgumentException("dataFile is missing");
            if (srcTableNames.isEmpty()) throw new IllegalArgumentException("srcTableName is missing");
            if (srcTableNames.size() != dstTableNames.size()) throw new IllegalArgumentException("srcTableName and dstTableName must have the same size");
            return new Request(database, dataFile, srcTableNames, dstTableNames, schemaName,
                    batchSize, fetchSize);
        }
    }

    static final class Handler implements OperationHandler<Request, Result> {
        @Override public Result execute(Request request, OperationContext context) {
            try {
                new IoxWkfDatabaseEngine().exportGpkg(new IoxWkfDatabaseEngine.GpkgExportRequest(
                        FormatTaskSupport.database(context, request.database()),
                        request.dataFile(), request.srcTableNames(), request.dstTableNames(),
                        request.schemaName(), request.batchSize(), request.fetchSize()));
                return new Result(request.dataFile());
            } catch (InterlisTaskException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new InterlisTaskException("GPKG_EXPORT", "GpkgExport failed",
                        "export", Optional.empty(), exception);
            }
        }
    }
}
