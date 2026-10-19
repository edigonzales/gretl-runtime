package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import ch.so.agi.gretl.runtime.api.Progress;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/** DuckDbSqlExecutor task: execute SQL files against a DuckDB database. */
public final class DuckDbSqlExecutorOperation {
    public static final String ID = "gretl.duckdb-sql-executor";
    public static final String SYMBOL = "DuckDbSqlExecutor";

    private DuckDbSqlExecutorOperation() {
    }

    public static OperationType<DuckDbRequest, DuckDbResult> type() {
        InputField<DuckDbRequest, Path> database = new InputField<>(
                "database", "path", "DuckDB database file", false,
                Optional.empty(), List.of(), "database = file('build/work.duckdb')",
                DuckDbSqlExecutorOperation::path,
                (builder, value) -> ((DuckDbBuilder) builder).setDatabase(value));
        InputField<DuckDbRequest, Boolean> inMemory = new InputField<>(
                "inMemory", "boolean", "Use an in-memory DuckDB database", false,
                Optional.of(false), List.of(), "inMemory = true",
                DuckDbSqlExecutorOperation::bool,
                (builder, value) -> ((DuckDbBuilder) builder).setInMemory(value));
        InputField<DuckDbRequest, List<Path>> sqlFiles = new InputField<>(
                "sqlFiles", "list", "Ordered SQL files", true,
                Optional.empty(), List.of(), "sqlFiles = [file('sql/analyse.sql')]",
                DuckDbSqlExecutorOperation::paths,
                (builder, value) -> ((DuckDbBuilder) builder).setSqlFiles(value));
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
        return new OperationType<>(
                ID, SYMBOL, "Execute SQL files against DuckDB",
                DuckDbRequest.class, DuckDbResult.class,
                DuckDbBuilder::new, DuckDbHandler::new,
                List.of(database, inMemory, sqlFiles, parameters, parameterSets),
                List.of(executionCount, executions),
                """
                task('analyse', DuckDbSqlExecutor) {
                    database = file('build/work.duckdb')
                    sqlFiles = [file('sql/analyse.sql')]
                }
                """);
    }

    public record DuckDbExecution(int index, List<Long> updateCounts, int resultSets) {
        public DuckDbExecution {
            updateCounts = List.copyOf(updateCounts);
        }
    }

    public record DuckDbResult(int executionCount, List<DuckDbExecution> executions) {
        public DuckDbResult {
            executions = List.copyOf(executions);
        }
    }

    public record DuckDbRequest(
            Path database, boolean inMemory, List<Path> sqlFiles,
            Map<String, String> sqlParameters,
            List<Map<String, String>> sqlParameterSets) {
        public DuckDbRequest {
            sqlFiles = List.copyOf(sqlFiles);
            sqlParameters = Map.copyOf(sqlParameters);
            sqlParameterSets = sqlParameterSets.stream().map(Map::copyOf).toList();
        }
    }

    public static final class DuckDbBuilder
            implements ch.so.agi.gretl.runtime.api.ConfigBuilder<DuckDbRequest> {
        private Path database;
        private boolean inMemory;
        private List<Path> sqlFiles = List.of();
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

        public DuckDbBuilder setSqlFiles(List<Path> value) {
            sqlFiles = value == null ? List.of() : value;
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
                    : database.toAbsolutePath().normalize(), inMemory, sqlFiles,
                    sqlParameters, sqlParameterSets);
        }
    }

    static final class DuckDbHandler
            implements OperationHandler<DuckDbRequest, DuckDbResult> {
        @Override
        public DuckDbResult execute(DuckDbRequest request, OperationContext context) {
            List<Map<String, String>> parameterSets = request.sqlParameterSets().isEmpty()
                    ? List.of(Map.of()) : request.sqlParameterSets();
            if (!request.sqlParameters().isEmpty()) {
                parameterSets = List.of(request.sqlParameters());
            }
            List<DuckDbExecution> executions = new ArrayList<>();
            int index = 0;
            try {
                if (request.database() != null) {
                    Files.createDirectories(request.database().getParent());
                }
                String url = request.inMemory()
                        ? "jdbc:duckdb:" : "jdbc:duckdb:" + request.database();
                try (Connection connection = DriverManager.getConnection(url)) {
                    connection.setAutoCommit(false);
                    try {
                        for (Map<String, String> parameters : parameterSets) {
                            for (Path sqlFile : request.sqlFiles()) {
                                List<String> statements = statements(sqlFile, parameters, context);
                                for (String statement : statements) {
                                    context.cancellation().throwIfCancellationRequested();
                                    executions.add(execute(connection, statement, index++));
                                    context.progress(new Progress("duckdb", index,
                                            OptionalLong.empty(), "statements"));
                                }
                            }
                        }
                        connection.commit();
                    } catch (Exception exception) {
                        rollback(connection);
                        throw exception;
                    }
                }
                context.log().log(LogLevel.INFO, "gretl.duckdb.finished",
                        msg(context, "task.duckdb.finished"),
                        Map.of("duckdb.statements", Integer.toString(index)), null);
                return new DuckDbResult(index, executions);
            } catch (DuckDbTaskException exception) {
                logFailure(context, exception);
                throw exception;
            } catch (SQLException | IOException exception) {
                DuckDbTaskException wrapped = new DuckDbTaskException("DUCKDB_EXECUTION",
                        msg(context, "task.duckdb.failed"), "execute", exception);
                logFailure(context, wrapped);
                throw wrapped;
            }
        }

        private static List<String> statements(Path sqlFile, Map<String, String> parameters,
                                               OperationContext context) throws IOException {
            if (!Files.isReadable(sqlFile)
                    || !sqlFile.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                    .endsWith(".sql")) {
                throw new DuckDbTaskException("DUCKDB_FILE",
                        msg(context, "task.duckdb.file", sqlFile), "prepare", null);
            }
            String sql = Files.readString(sqlFile, StandardCharsets.UTF_8);
            if (sql.isBlank()) {
                throw new DuckDbTaskException("DUCKDB_FILE",
                        msg(context, "task.duckdb.file", sqlFile), "prepare", null);
            }
            try {
                return Db2DbSupport.split(
                        Db2DbSupport.substitute(sql, parameters, context));
            } catch (Db2DbTaskException exception) {
                throw new DuckDbTaskException("DUCKDB_PARAMETER",
                        exception.getMessage(), "prepare", exception);
            }
        }

        private static DuckDbExecution execute(Connection connection, String sql, int index)
                throws SQLException {
            try (Statement statement = connection.createStatement()) {
                boolean hasResult = statement.execute(sql);
                List<Long> updates = new ArrayList<>();
                int resultSets = 0;
                boolean current = hasResult;
                while (true) {
                    if (current) {
                        try (ResultSet rows = statement.getResultSet()) {
                            if (rows != null) {
                                resultSets++;
                                while (rows.next()) {
                                    // Consume and close result sets.
                                }
                            }
                        }
                    } else {
                        long updateCount = statement.getUpdateCount();
                        if (updateCount < 0) {
                            break;
                        }
                        updates.add(updateCount);
                    }
                    current = statement.getMoreResults();
                }
                return new DuckDbExecution(index, updates, resultSets);
            }
        }

        private static void rollback(Connection connection) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // Preserve the primary failure.
            }
        }

        private static void logFailure(OperationContext context,
                                       DuckDbTaskException exception) {
            context.log().log(LogLevel.WARN, "gretl.duckdb.failed",
                    msg(context, "task.duckdb.failed"),
                    Map.of("duckdb.phase", exception.phase()), exception);
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
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
}
