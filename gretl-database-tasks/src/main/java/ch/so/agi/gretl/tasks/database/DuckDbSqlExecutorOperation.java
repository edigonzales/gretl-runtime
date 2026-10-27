package ch.so.agi.gretl.tasks.database;

import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** DuckDbSqlExecutor task: DuckDB SQL plus federated sources and exports. */
public final class DuckDbSqlExecutorOperation {
    public static final String ID = "gretl.duckdb-sql-executor";
    public static final String SYMBOL = "DuckDbSqlExecutor";
    private static final Set<String> SOURCE_KEYS = Set.of(
            "type", "alias", "file", "table", "layer", "mode", "header",
            "delimiter", "allVarchar", "database", "tables");
    private static final Set<String> TARGET_KEYS = Set.of(
            "type", "alias", "database");
    private static final Set<String> EXPORT_KEYS = Set.of(
            "type", "name", "query", "file", "layer", "srs", "sheet",
            "header", "sheetRowLimit", "overwrite", "target", "table",
            "mode", "create", "writePath");

    private DuckDbSqlExecutorOperation() {
    }

    public static OperationType<DuckDbRequest, DuckDbResult> type() {
        InputField<DuckDbRequest, Path> database = new InputField<>(
                "database", "path", "DuckDB database file", false,
                Optional.empty(), List.of(), "database = workPath('work.duckdb')",
                DuckDbSqlExecutorOperation::path,
                (builder, value) -> ((DuckDbBuilder) builder).setDatabase(value));
        InputField<DuckDbRequest, Boolean> inMemory = new InputField<>(
                "inMemory", "boolean", "Use an in-memory DuckDB database", false,
                Optional.of(false), List.of(), "inMemory = true",
                DuckDbSqlExecutorOperation::bool,
                (builder, value) -> ((DuckDbBuilder) builder).setInMemory(value));
        InputField<DuckDbRequest, Boolean> installExtensions = new InputField<>(
                "installExtensions", "boolean",
                "Install required DuckDB extensions before loading them", false,
                Optional.of(false), List.of(), "installExtensions = true",
                DuckDbSqlExecutorOperation::bool,
                (builder, value) -> ((DuckDbBuilder) builder)
                        .setInstallExtensions(value));
        InputField<DuckDbRequest, List<Path>> sqlFiles = new InputField<>(
                "sqlFiles", "list", "Ordered SQL files", true,
                Optional.empty(), List.of(), "sqlFiles = [file('sql/analyse.sql')]",
                DuckDbSqlExecutorOperation::paths,
                (builder, value) -> ((DuckDbBuilder) builder).setSqlFiles(value));
        InputField<DuckDbRequest, List<Map<String, Object>>> sources = new InputField<>(
                "sources", "list",
                "Federated CSV, GeoPackage or PostgreSQL sources",
                false, Optional.of(List.of()), List.of(),
                "sources = [[type: 'csv', alias: 'input', file: file('data.csv')]]",
                value -> mapList(value, SOURCE_KEYS, "sources"),
                (builder, value) -> ((DuckDbBuilder) builder).setSources(value));
        InputField<DuckDbRequest, List<Map<String, Object>>> exports = new InputField<>(
                "exports", "list",
                "Parquet, GeoPackage, XLSX or PostgreSQL exports",
                false, Optional.of(List.of()), List.of(),
                "exports = [[type: 'parquet', name: 'out', query: 'select * from input.data', "
                        + "file: file('out.parquet'), overwrite: true]]",
                value -> mapList(value, EXPORT_KEYS, "exports"),
                (builder, value) -> ((DuckDbBuilder) builder).setExports(value));
        InputField<DuckDbRequest, List<Map<String, Object>>> targets = new InputField<>(
                "targets", "list",
                "Writable PostgreSQL targets",
                false, Optional.of(List.of()), List.of(),
                "targets = [[type: 'postgres', alias: 'target', database: 'pg']]",
                value -> mapList(value, TARGET_KEYS, "targets"),
                (builder, value) -> ((DuckDbBuilder) builder).setTargets(value));
        InputField<DuckDbRequest, Map<String, String>> parameters = new InputField<>(
                "sqlParameters", "map", "SQL parameters for one execution of all SQL files",
                false, Optional.of(Map.of()), List.of(), "sqlParameters = [year: '2024']",
                DuckDbSqlExecutorOperation::stringMap,
                (builder, value) -> ((DuckDbBuilder) builder).setSqlParameters(value));
        InputField<DuckDbRequest, List<Map<String, String>>> parameterSets = new InputField<>(
                "sqlParameterSets", "list", "SQL parameter maps; all SQL files run for each map",
                false, Optional.of(List.of()), List.of(),
                "sqlParameterSets = [[year: '2024'], [year: '2025']]",
                DuckDbSqlExecutorOperation::stringMapList,
                (builder, value) -> ((DuckDbBuilder) builder).setSqlParameterSets(value));
        OutputField<DuckDbResult, Integer> executionCount = Fields.output(
                "executionCount", "integer", "Number of executed SQL statements", false,
                DuckDbResult::executionCount);
        OutputField<DuckDbResult, List<DuckDbExecution>> executions = Fields.output(
                "executions", "list", "Statement results in execution order", false,
                DuckDbResult::executions);
        OutputField<DuckDbResult, List<Path>> exportFiles = Fields.output(
                "exportFiles", "list", "Written export files", false,
                DuckDbResult::exportFiles);
        return new OperationType<>(
                ID, SYMBOL, "Execute SQL with DuckDB federation and exports",
                DuckDbRequest.class, DuckDbResult.class,
                DuckDbBuilder::new, DuckDbHandler::new,
                List.of(database, inMemory, installExtensions, sqlFiles, sources,
                        targets, exports, parameters, parameterSets),
                List.of(executionCount, executions, exportFiles),
                """
                parameter('ziel', type: 'path', required: true, description: 'Parquet output file')
                task('analyse', DuckDbSqlExecutor) {
                    database = workPath('work.duckdb')
                    sqlFiles = [file('sql/analyse.sql')]
                    sources = [[
                        type: 'csv',
                        alias: 'input',
                        file: file('data/input.csv'),
                        table: 'data'
                    ]]
                    targets = [[
                        type: 'postgres',
                        alias: 'target',
                        database: 'pg'
                    ]]
                    exports = [[
                        type: 'parquet',
                        name: 'result',
                        query: 'SELECT * FROM input.data',
                        file: parameter('ziel'),
                        overwrite: true
                    ]]
                }
                defaults 'analyse'
                """);
    }

    public record DuckDbExecution(int index, List<Long> updateCounts, int resultSets) {
        public DuckDbExecution {
            updateCounts = List.copyOf(updateCounts);
        }
    }

    public record DuckDbResult(
            int executionCount, List<DuckDbExecution> executions, List<Path> exportFiles) {
        public DuckDbResult {
            executions = List.copyOf(executions);
            exportFiles = List.copyOf(exportFiles);
        }
    }

    public record DuckDbRequest(
            Path database, boolean inMemory, boolean installExtensions,
            List<Path> sqlFiles, List<Map<String, Object>> sources,
            List<Map<String, Object>> targets, List<Map<String, Object>> exports,
            Map<String, String> sqlParameters,
            List<Map<String, String>> sqlParameterSets) {
        public DuckDbRequest {
            sqlFiles = List.copyOf(sqlFiles);
            sources = copyMaps(sources);
            targets = copyMaps(targets);
            exports = copyMaps(exports);
            sqlParameters = Map.copyOf(sqlParameters);
            sqlParameterSets = sqlParameterSets.stream().map(Map::copyOf).toList();
        }
    }

    public static final class DuckDbBuilder
            implements ch.so.agi.gretl.runtime.api.ConfigBuilder<DuckDbRequest> {
        private Path database;
        private boolean inMemory;
        private boolean installExtensions;
        private List<Path> sqlFiles = List.of();
        private List<Map<String, Object>> sources = List.of();
        private List<Map<String, Object>> targets = List.of();
        private List<Map<String, Object>> exports = List.of();
        private Map<String, String> sqlParameters = Map.of();
        private List<Map<String, String>> sqlParameterSets = List.of();

        public DuckDbBuilder setDatabase(Path value) {
            database = value;
            return this;
        }

        public DuckDbBuilder setInMemory(Boolean value) {
            inMemory = Boolean.TRUE.equals(value);
            return this;
        }

        public DuckDbBuilder setInstallExtensions(Boolean value) {
            installExtensions = Boolean.TRUE.equals(value);
            return this;
        }

        public DuckDbBuilder setSqlFiles(List<Path> value) {
            sqlFiles = value == null ? List.of() : value;
            return this;
        }

        public DuckDbBuilder setSources(List<Map<String, Object>> value) {
            sources = value == null ? List.of() : value;
            return this;
        }

        public DuckDbBuilder setTargets(List<Map<String, Object>> value) {
            targets = value == null ? List.of() : value;
            return this;
        }

        public DuckDbBuilder setExports(List<Map<String, Object>> value) {
            exports = value == null ? List.of() : value;
            return this;
        }

        public DuckDbBuilder setSqlParameters(Map<String, String> value) {
            sqlParameters = value == null ? Map.of() : value;
            return this;
        }

        public DuckDbBuilder setSqlParameterSets(List<Map<String, String>> value) {
            sqlParameterSets = value == null ? List.of() : value;
            return this;
        }

        @Override
        public DuckDbRequest build() {
            if (inMemory == (database != null)) {
                throw new IllegalArgumentException(
                        "Use exactly one of database or inMemory");
            }
            if (sqlFiles.isEmpty()) {
                throw new IllegalArgumentException("sqlFiles must not be empty");
            }
            if (!sqlParameters.isEmpty() && !sqlParameterSets.isEmpty()) {
                throw new IllegalArgumentException(
                        "Use either sqlParameters or sqlParameterSets, not both");
            }
            return new DuckDbRequest(database == null ? null
                    : database.toAbsolutePath().normalize(), inMemory, installExtensions,
                    sqlFiles, sources, targets, exports, sqlParameters, sqlParameterSets);
        }
    }

    static final class DuckDbHandler
            implements OperationHandler<DuckDbRequest, DuckDbResult> {
        @Override
        public DuckDbResult execute(DuckDbRequest request, OperationContext context) {
            return new DuckDbFederationEngine().execute(request, context);
        }
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new IllegalArgumentException("Boolean value expected");
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

    private static List<Path> paths(Object value) {
        List<Object> raw = list(value);
        List<Path> result = new ArrayList<>();
        for (Object element : raw) {
            result.add(path(element));
        }
        return List.copyOf(result);
    }

    private static List<Map<String, Object>> mapList(Object value, Set<String> allowed,
                                                      String field) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object element : list(value)) {
            if (!(element instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException(field + " contains no map");
            }
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (!allowed.contains(key)) {
                    throw new IllegalArgumentException(
                            field + " contains unknown field: " + key);
                }
                if (entry.getValue() == null) {
                    throw new IllegalArgumentException(
                            field + " field must not be null: " + key);
                }
                converted.put(key, entry.getValue());
            }
            result.add(Map.copyOf(converted));
        }
        return List.copyOf(result);
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Map expected");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof CharSequence key)
                    || key.toString().isBlank() || entry.getValue() == null) {
                throw new IllegalArgumentException("SQL parameter map is invalid");
            }
            Object raw = entry.getValue();
            if (!(raw instanceof CharSequence) && !(raw instanceof Number)
                    && !(raw instanceof Boolean)) {
                throw new IllegalArgumentException("SQL parameter value must be scalar");
            }
            result.put(key.toString(), raw.toString());
        }
        return Map.copyOf(result);
    }

    private static List<Map<String, String>> stringMapList(Object value) {
        List<Map<String, String>> result = new ArrayList<>();
        for (Object element : list(value)) {
            result.add(stringMap(element));
        }
        return List.copyOf(result);
    }

    private static List<Object> list(Object value) {
        List<Object> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(result::add);
        } else if (value instanceof Object[] array) {
            java.util.Collections.addAll(result, array);
        } else {
            throw new IllegalArgumentException("List expected");
        }
        return result;
    }

    private static List<Map<String, Object>> copyMaps(List<Map<String, Object>> values) {
        return values.stream().map(map -> {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (value == null) {
                    throw new IllegalArgumentException(
                            "DuckDB map value must not be null: " + key);
                }
                copy.put(key, value);
            });
            return Map.copyOf(copy);
        }).toList();
    }
}
