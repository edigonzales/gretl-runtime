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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** GpkgExport task: export database tables into a GeoPackage. */
public final class GpkgExportOperation {
    public static final String ID = "gretl.gpkg-export";
    public static final String SYMBOL = "GpkgExport";

    private GpkgExportOperation() {
    }

    public static OperationType<GpkgExportRequest, GpkgExportResult> type() {
        InputField<GpkgExportRequest, String> database = new InputField<>(
                "database", "text", "Alias of the source database connection", true,
                Optional.empty(), List.of(), "database = 'db'",
                GpkgExportOperation::text,
                (builder, value) -> ((GpkgExportBuilder) builder).setDatabase(value));
        InputField<GpkgExportRequest, Path> dataFile = new InputField<>(
                "dataFile", "path", "GeoPackage output file", true,
                Optional.empty(), List.of(), "dataFile = file('build/output.gpkg')",
                GpkgExportOperation::path,
                (builder, value) -> ((GpkgExportBuilder) builder).setDataFile(value));
        InputField<GpkgExportRequest, List<String>> source = new InputField<>(
                "srcTableNames", "list", "Source tables", true,
                Optional.empty(), List.of(), "srcTableNames = ['abbaustelle']",
                GpkgExportOperation::texts,
                (builder, value) -> ((GpkgExportBuilder) builder).setSrcTableNames(value));
        InputField<GpkgExportRequest, List<String>> target = new InputField<>(
                "dstTableNames", "list", "Destination layer names", true,
                Optional.empty(), List.of(), "dstTableNames = ['abbaustelle']",
                GpkgExportOperation::texts,
                (builder, value) -> ((GpkgExportBuilder) builder).setDstTableNames(value));
        InputField<GpkgExportRequest, String> schema = new InputField<>(
                "schemaName", "text", "Optional source schema", false,
                Optional.empty(), List.of(), "schemaName = 'public'",
                GpkgExportOperation::text,
                (builder, value) -> ((GpkgExportBuilder) builder).setSchemaName(value));
        InputField<GpkgExportRequest, Integer> batch = new InputField<>(
                "batchSize", "integer", "JDBC batch size", false,
                Optional.of(5000), List.of(), "batchSize = 1000",
                GpkgExportOperation::positiveInteger,
                (builder, value) -> ((GpkgExportBuilder) builder).setBatchSize(value));
        InputField<GpkgExportRequest, Integer> fetch = new InputField<>(
                "fetchSize", "integer", "JDBC fetch size", false,
                Optional.of(5000), List.of(), "fetchSize = 1000",
                GpkgExportOperation::nonNegativeInteger,
                (builder, value) -> ((GpkgExportBuilder) builder).setFetchSize(value));
        OutputField<GpkgExportResult, Path> file = Fields.output(
                "file", "path", "Written GeoPackage", false, GpkgExportResult::file);
        OutputField<GpkgExportResult, Long> rows = Fields.output(
                "rows", "integer", "Exported rows", false, GpkgExportResult::rows);
        return new OperationType<>(
                ID, SYMBOL, "Export database tables into a GeoPackage",
                GpkgExportRequest.class, GpkgExportResult.class,
                GpkgExportBuilder::new, GpkgExportHandler::new,
                List.of(database, dataFile, source, target, schema, batch, fetch),
                List.of(file, rows),
                """
                task('export', GpkgExport) {
                    database = 'db'
                    dataFile = file('build/output.gpkg')
                    srcTableNames = ['abbaustelle']
                    dstTableNames = ['abbaustelle']
                }
                """);
    }

    public record GpkgExportRequest(
            String database, Path dataFile, List<String> srcTableNames,
            List<String> dstTableNames, String schemaName,
            int batchSize, int fetchSize) {
        public GpkgExportRequest {
            srcTableNames = List.copyOf(srcTableNames);
            dstTableNames = List.copyOf(dstTableNames);
        }
    }

    public record GpkgExportResult(Path file, long rows) {
    }

    public static final class GpkgExportBuilder
            implements ch.so.agi.gretl.runtime.api.ConfigBuilder<GpkgExportRequest> {
        private String database;
        private Path dataFile;
        private List<String> srcTableNames = List.of();
        private List<String> dstTableNames = List.of();
        private String schemaName;
        private int batchSize = 5000;
        private int fetchSize = 5000;

        public GpkgExportBuilder setDatabase(String value) { database = value; return this; }
        public GpkgExportBuilder setDataFile(Path value) { dataFile = value; return this; }
        public GpkgExportBuilder setSrcTableNames(List<String> value) {
            srcTableNames = value == null ? List.of() : value; return this;
        }
        public GpkgExportBuilder setDstTableNames(List<String> value) {
            dstTableNames = value == null ? List.of() : value; return this;
        }
        public GpkgExportBuilder setSchemaName(String value) { schemaName = value; return this; }
        public GpkgExportBuilder setBatchSize(Integer value) {
            batchSize = value == null ? 5000 : value; return this;
        }
        public GpkgExportBuilder setFetchSize(Integer value) {
            fetchSize = value == null ? 5000 : value; return this;
        }

        @Override
        public GpkgExportRequest build() {
            if (database == null || database.isBlank()) {
                throw new IllegalArgumentException("database is missing");
            }
            if (dataFile == null) { throw new IllegalArgumentException("dataFile is missing"); }
            if (srcTableNames.isEmpty()) {
                throw new IllegalArgumentException("srcTableNames must not be empty");
            }
            if (dstTableNames.size() != srcTableNames.size()) {
                throw new IllegalArgumentException(
                        "srcTableNames and dstTableNames must have the same size");
            }
            return new GpkgExportRequest(database, dataFile.toAbsolutePath().normalize(),
                    srcTableNames, dstTableNames, schemaName, batchSize, fetchSize);
        }
    }

    static final class GpkgExportHandler
            implements OperationHandler<GpkgExportRequest, GpkgExportResult> {
        @Override
        public GpkgExportResult execute(GpkgExportRequest request, OperationContext context) {
            List<java.nio.file.Path> temporaryLayers = new ArrayList<>();
            try {
                long rows = 0;
                for (int index = 0; index < request.srcTableNames().size(); index++) {
                    java.nio.file.Path temporary = request.dataFile().getParent()
                            .resolve(".gretl-w6-" + java.util.UUID.randomUUID()
                                    + "-" + index + ".gpkg");
                    temporaryLayers.add(temporary);
                    rows += GeoExportSupport.export(context, request.database(),
                            request.schemaName(), request.srcTableNames().get(index),
                            temporary, true, request.dstTableNames().get(index), null);
                }
                GeoPackageMerger.merge(temporaryLayers, request.dataFile());
                context.log().log(LogLevel.INFO, "gretl.gpkgexport.finished",
                        msg(context, "task.gpkgexport.finished"),
                        Map.of("geo.rows", Long.toString(rows)), null);
                return new GpkgExportResult(request.dataFile(), rows);
            } catch (TabularTaskException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new TabularTaskException("GPKG_EXPORT",
                        msg(context, "task.gpkgexport.failed"), "export", exception);
            } finally {
                for (java.nio.file.Path temporary : temporaryLayers) {
                    try {
                        java.nio.file.Files.deleteIfExists(temporary);
                    } catch (java.io.IOException ignored) {
                        // Best-effort cleanup of intermediate layers.
                    }
                }
            }
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

    private static List<String> texts(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) { result.add(text(element)); }
        } else if (value instanceof Object[] array) {
            for (Object element : array) { result.add(text(element)); }
        } else {
            throw new IllegalArgumentException("List expected");
        }
        return List.copyOf(result);
    }

    private static Integer positiveInteger(Object value) {
        int result = number(value);
        if (result <= 0) { throw new IllegalArgumentException("Value must be greater than zero"); }
        return result;
    }

    private static Integer nonNegativeInteger(Object value) {
        int result = number(value);
        if (result < 0) { throw new IllegalArgumentException("Value must not be negative"); }
        return result;
    }

    private static int number(Object value) {
        if (value instanceof Number number) { return number.intValue(); }
        if (value instanceof CharSequence text && text.toString().matches("[0-9]+")) {
            return Integer.parseInt(text.toString());
        }
        throw new IllegalArgumentException("Integer expected");
    }
}
