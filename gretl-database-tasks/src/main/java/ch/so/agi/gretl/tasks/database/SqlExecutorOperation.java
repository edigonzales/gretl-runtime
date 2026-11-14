package ch.so.agi.gretl.tasks.database;

import ch.so.agi.gretl.runtime.api.ConfigBuilder;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import ch.so.agi.gretl.runtime.api.Progress;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/** JDBC SqlExecutor with scripts or prepared statements. */
public final class SqlExecutorOperation {
    public static final String ID = "gretl.sql-executor";
    public static final String SYMBOL = "SqlExecutor";

    private SqlExecutorOperation() {
    }

    public static OperationType<SqlRequest, SqlResult> type() {
        InputField<SqlRequest, String> database = new InputField<>(
                "database", "text", "Alias of a configured JDBC connection",
                true, Optional.empty(), List.of(), "database = 'edit'",
                SqlExecutorOperation::text, (builder, value) -> ((SqlBuilder) builder).setDatabase(value));
        InputField<SqlRequest, List<Path>> scripts = new InputField<>(
                "scripts", "list", "Ordered UTF-8 SQL script files", false,
                Optional.empty(), List.of(),
                "scripts = [file('sql/update.sql')]",
                SqlExecutorOperation::paths, (builder, value) -> ((SqlBuilder) builder).setScripts(value));
        InputField<SqlRequest, List<Path>> sqlFiles = new InputField<>(
                "sqlFiles", "list", "Alias for scripts", false, Optional.empty(), List.of(),
                "sqlFiles = [file('sql/update.sql')]",
                SqlExecutorOperation::paths, (builder, value) -> ((SqlBuilder) builder).setSqlFiles(value));
        InputField<SqlRequest, List<Map<String, String>>> sqlParameters = new InputField<>(
                "sqlParameters", "map-or-list", "SQL template parameters as a map or list of maps",
                false, Optional.empty(), List.of(), "sqlParameters = [schema: 'edit']",
                value -> value instanceof Map<?, ?> map ? List.of(parameterMap(map)) : parameterSets(value),
                (builder, value) -> ((SqlBuilder) builder).sqlParameters = value);
        InputField<SqlRequest, List<Map<String, String>>> sqlParameterSets = new InputField<>(
                "sqlParameterSets", "list", "Ordered maps of SQL template parameters",
                false, Optional.empty(), List.of(), "sqlParameterSets = [[schema: 'edit']]",
                SqlExecutorOperation::parameterSets,
                (builder, value) -> ((SqlBuilder) builder).sqlParameterSets = value);
        InputField<SqlRequest, Boolean> logResults = new InputField<>(
                "logResults", "boolean", "Log result rows", false, Optional.of(true), List.of(),
                "logResults = false", SqlExecutorOperation::booleanValue,
                (builder, value) -> ((SqlBuilder) builder).logResults = value);
        InputField<SqlRequest, Integer> maxLoggedRows = new InputField<>(
                "maxLoggedRows", "integer", "Logged rows per result set (0 for unlimited)", false,
                Optional.of(1000), List.of(), "maxLoggedRows = 100",
                SqlExecutorOperation::rowLimit, (builder, value) -> ((SqlBuilder) builder).maxLoggedRows = value);
        InputField<SqlRequest, List<PreparedStatementSpec>> statements = new InputField<>(
                "statements", "list", "Ordered prepared statements", false,
                Optional.of(List.of()), List.of(),
                "statements = [[sql: 'UPDATE t SET x=?', parameters: [1]]]",
                SqlExecutorOperation::statements, (builder, value) -> ((SqlBuilder) builder).setStatements(value));
        OutputField<SqlResult, Integer> executionCount = Fields.output(
                "executionCount", "integer", "Number of script/statement entries", false,
                SqlResult::executionCount);
        OutputField<SqlResult, List<SqlExecutionResult>> executions = Fields.output(
                "executions", "list", "Results in input order", false,
                SqlResult::executions);
        return new OperationType<>(
                ID, SYMBOL, "Execute SQL scripts or prepared statements",
                SqlRequest.class, SqlResult.class,
                SqlBuilder::new, SqlHandler::new,
                List.of(database, scripts, sqlFiles, statements, sqlParameters, sqlParameterSets,
                        logResults, maxLoggedRows),
                List.of(executionCount, executions),
                """
                task('update', SqlExecutor) {
                    database = 'edit'
                    scripts = [file('sql/update.sql')]
                }
                """);
    }

    private static String text(Object value) {
        if (!(value instanceof CharSequence text)) {
            throw new IllegalArgumentException("Textwert erwartet");
        }
        return text.toString();
    }

    private static List<Path> paths(Object value) {
        List<Object> raw = list(value, "scripts");
        List<Path> paths = new ArrayList<>();
        for (Object element : raw) {
            Path path;
            if (element instanceof Path direct) {
                path = direct;
            } else if (element instanceof CharSequence text) {
                path = Path.of(text.toString());
            } else {
                throw new IllegalArgumentException("scripts contains no path");
            }
            paths.add(path.normalize());
        }
        return List.copyOf(paths);
    }

    private static List<PreparedStatementSpec> statements(Object value) {
        List<Object> raw = list(value, "statements");
        List<PreparedStatementSpec> statements = new ArrayList<>();
        for (Object element : raw) {
            if (!(element instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("statements contains no map");
            }
            Set<String> keys = map.keySet().stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.toSet());
            if (!keys.equals(Set.of("sql", "parameters"))) {
                throw new IllegalArgumentException(
                        "Statement requires exactly sql and parameters");
            }
            Object sqlValue = map.get("sql");
            if (!(sqlValue instanceof CharSequence sql)) {
                throw new IllegalArgumentException("Statement.sql must be text");
            }
            List<Object> parameters = parameters(map.get("parameters"));
            statements.add(new PreparedStatementSpec(sql.toString(), parameters));
        }
        return List.copyOf(statements);
    }

    private static List<Object> parameters(Object value) {
        List<Object> raw = list(value, "parameters");
        List<Object> parameters = new ArrayList<>();
        for (Object element : raw) {
            if (element == null || element instanceof String || element instanceof Boolean
                    || element instanceof Byte || element instanceof Short
                    || element instanceof Integer || element instanceof Long
                    || element instanceof BigDecimal) {
                parameters.add(element);
            } else if (element instanceof CharSequence text) {
                parameters.add(text.toString());
            } else {
                throw new IllegalArgumentException(
                        "SQL-Parameter typ " + element.getClass().getName()
                                + " is not supported");
            }
        }
        return java.util.Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    private static List<Object> list(Object value, String field) {
        List<Object> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(result::add);
        } else if (value instanceof Object[] array) {
            java.util.Collections.addAll(result, array);
        } else {
            throw new IllegalArgumentException(field + " must be a list");
        }
        return result;
    }

    private static Boolean booleanValue(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new IllegalArgumentException("logResults must be boolean");
    }

    private static Integer rowLimit(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof java.math.BigInteger) {
            try {
                int limit = Integer.parseInt(value.toString());
                if (limit >= 0) {
                    return limit;
                }
            } catch (NumberFormatException ignored) {
                // Out of range.
            }
        }
        throw new IllegalArgumentException("maxLoggedRows must be a non-negative integer");
    }

    private static List<Map<String, String>> parameterSets(Object value) {
        if (!(value instanceof List<?> sets)) {
            throw new IllegalArgumentException("SQL parameter sets must be a list of maps");
        }
        return sets.stream().map(SqlExecutorOperation::parameterMap).toList();
    }

    private static Map<String, String> parameterMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Each SQL parameter set must be a map");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        map.forEach((key, entry) -> {
            if (!(key instanceof CharSequence name) || !(entry instanceof CharSequence replacement)) {
                throw new IllegalArgumentException("SQL parameter names and values must be text");
            }
            if (normalized.putIfAbsent(name.toString(), replacement.toString()) != null) {
                throw new IllegalArgumentException("Duplicate SQL parameter name");
            }
        });
        return Map.copyOf(normalized);
    }

    public record SqlRequest(
            String database, List<Path> scripts, List<PreparedStatementSpec> statements,
            Optional<List<Map<String, String>>> parameterSets, boolean logResults, int maxLoggedRows) {
        public SqlRequest(String database, List<Path> scripts, List<PreparedStatementSpec> statements) {
            this(database, scripts, statements, Optional.empty(), true, 1000);
        }

        public SqlRequest {
            java.util.Objects.requireNonNull(database, "database");
            scripts = List.copyOf(scripts);
            statements = List.copyOf(statements);
            parameterSets = parameterSets.map(sets -> sets.stream().map(Map::copyOf).toList());
            if (maxLoggedRows < 0) {
                throw new IllegalArgumentException("maxLoggedRows must be non-negative");
            }
        }
    }

    public static final class SqlBuilder implements ConfigBuilder<SqlRequest> {
        private String database;
        private List<Path> scripts;
        private List<Path> sqlFiles;
        private List<PreparedStatementSpec> statements = List.of();
        private List<Map<String, String>> sqlParameters;
        private List<Map<String, String>> sqlParameterSets;
        private boolean logResults = true;
        private int maxLoggedRows = 1000;

        public void setDatabase(String value) { database = value; }
        public void setScripts(List<Path> value) { scripts = List.copyOf(value); }
        public void setSqlFiles(List<Path> value) { sqlFiles = List.copyOf(value); }
        public void setStatements(List<PreparedStatementSpec> value) { statements = List.copyOf(value); }

        @Override
        public SqlRequest build() {
            if (database == null || database.isBlank()) {
                throw new IllegalArgumentException("database is missing");
            }
            if (scripts != null && sqlFiles != null) {
                throw new IllegalArgumentException("scripts and sqlFiles are mutually exclusive");
            }
            if (sqlParameters != null && sqlParameterSets != null) {
                throw new IllegalArgumentException("sqlParameters and sqlParameterSets are mutually exclusive");
            }
            List<Path> files = scripts != null ? scripts : sqlFiles != null ? sqlFiles : List.of();
            if (files.isEmpty() == statements.isEmpty()) {
                throw new IllegalArgumentException(
                        "Exactly one of scripts or statements must be non-empty");
            }
            var sets = Optional.ofNullable(sqlParameters != null ? sqlParameters : sqlParameterSets);
            if (!statements.isEmpty() && sets.isPresent()) {
                throw new IllegalArgumentException("SQL template parameters require SQL files");
            }
            return new SqlRequest(database, files, statements, sets, logResults, maxLoggedRows);
        }
    }

    static final class SqlHandler
            implements ch.so.agi.gretl.runtime.api.OperationHandler<SqlRequest, SqlResult> {
        @Override
        public SqlResult execute(SqlRequest request, OperationContext context) {
            if (request.parameterSets().filter(List::isEmpty).isPresent()) {
                return new SqlResult(0, List.of());
            }
            Map<String, Object> config = context.config(request.database());
            String jdbcUrl = requiredText(config, "jdbcUrl", context);
            String user = optionalText(config, "user", context);
            String password = null;
            Object passwordSecret = config.get("passwordSecret");
            if (passwordSecret != null) {
                if (!(passwordSecret instanceof CharSequence alias)) {
                    throw new SqlTaskException("SQL_CONFIG", msg(context, "task.sql.passwordSecretText"),
                            request.database(), null, 0, "configuration", null, 0, null);
                }
                password = context.sensitive(context.secret(alias.toString()).reveal());
            }
            Set<String> allowed = Set.of("jdbcUrl", "user", "passwordSecret");
            for (String key : config.keySet()) {
                if (!allowed.contains(key)) {
                    throw new SqlTaskException("SQL_CONFIG",
                            msg(context, "task.sql.unknownField", key), request.database(),
                            null, 0, "configuration", null, 0, null);
                }
            }
            long started = System.nanoTime();
            List<SqlExecutionResult> results = new ArrayList<>();
            List<Map<String, String>> sets = request.parameterSets().orElseGet(() -> List.of(Map.of()));
            for (int set = 0; set < sets.size(); set++) {
                context.cancellation().throwIfCancellationRequested();
                executeParameterSet(request, context, jdbcUrl, user, password, sets.get(set), set, results);
            }
            context.log().log(LogLevel.INFO, "gretl.sql.finished",
                    msg(context, "task.sql.finished"),
                    Map.of("db.alias", request.database(),
                            "sql.execution_count", Integer.toString(results.size()),
                            "duration_ms", Long.toString((System.nanoTime() - started) / 1_000_000)), null);
            return new SqlResult(results.size(), results);
        }

        private static void executeParameterSet(SqlRequest request, OperationContext context,
                                                String jdbcUrl, String user, String password,
                                                Map<String, String> parameters, int set,
                                                List<SqlExecutionResult> results) {
            Connection connection = null;
            RuntimeException primary = null;
            try {
                connection = DriverManager.getConnection(jdbcUrl, user, password);
                connection.setAutoCommit(false);
                boolean scriptMode = !request.scripts().isEmpty();
                boolean fetchInBatches = jdbcUrl.startsWith("jdbc:postgresql:");
                int size = scriptMode ? request.scripts().size() : request.statements().size();
                long total = (long) size * request.parameterSets().map(List::size).orElse(1);
                for (int entry = 0; entry < size; entry++) {
                    context.cancellation().throwIfCancellationRequested();
                    int index = results.size();
                    results.add(scriptMode
                            ? executeScript(connection, request.scripts().get(entry), index,
                                    request, parameters, set, fetchInBatches, context)
                            : executeStatement(connection, request.statements().get(entry), index, request,
                                    fetchInBatches, context));
                    context.progress(new Progress("sql", index + 1, OptionalLong.of(total), "entries"));
                }
                context.cancellation().throwIfCancellationRequested();
                try {
                    connection.commit();
                } catch (SQLException exception) {
                    throw sqlError("SQL_COMMIT_UNKNOWN", msg(context, "task.sql.commitUnknown"),
                            request.database(), null, results.size(), "commit", exception);
                }
            } catch (SqlTaskException exception) {
                primary = exception;
                rollbackQuietly(connection, primary);
                logFailure(context, exception);
                throw exception;
            } catch (SQLException exception) {
                SqlTaskException failure = sqlError("SQL_EXECUTION",
                        msg(context, "task.sql.executionFailed"),
                        request.database(), null, 0, "connect", exception);
                primary = failure;
                rollbackQuietly(connection, primary);
                logFailure(context, failure);
                throw failure;
            } catch (RuntimeException exception) {
                primary = exception;
                rollbackQuietly(connection, primary);
                throw exception;
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException exception) {
                        if (primary != null) {
                            primary.addSuppressed(exception);
                        } else {
                            throw new SqlTaskException("SQL_CLOSE",
                                    msg(context, "task.sql.closeFailed"),
                                    request.database(), null, 0, "cleanup",
                                    exception.getSQLState(), exception.getErrorCode(), exception);
                        }
                    }
                }
            }
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }

        private static void logFailure(OperationContext context, SqlTaskException exception) {
            java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
            fields.put("db.alias", exception.databaseAlias());
            if (exception.scriptName() != null) {
                fields.put("sql.script", exception.scriptName());
            }
            fields.put("sql.entry", Integer.toString(exception.entryIndex()));
            fields.put("sql.phase", exception.phase());
            if (exception.sqlState() != null) {
                fields.put("sql.state", exception.sqlState());
            }
            if (exception.vendorCode() != 0) {
                fields.put("sql.vendor_code", Integer.toString(exception.vendorCode()));
            }
            context.log().log(LogLevel.WARN, "gretl.sql.failed",
                    msg(context, "task.sql.executionFailed"), fields, exception);
        }

        private static SqlExecutionResult executeScript(
                Connection connection, Path script, int index, SqlRequest request,
                Map<String, String> parameters, int set, boolean fetchInBatches, OperationContext context) {
            String alias = request.database();
            String name = script.getFileName() == null ? script.toString() : script.getFileName().toString();
            if (!name.toLowerCase(Locale.ROOT).endsWith(".sql")) {
                throw new SqlTaskException("SQL_SCRIPT_READ", msg(context, "task.sql.scriptExtension", name),
                        alias, name, index, "read", null, 0, null);
            }
            String sql;
            try {
                sql = Files.readString(script, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new SqlTaskException("SQL_SCRIPT_READ",
                        msg(context, "task.sql.scriptReadPosition", name, set + 1), alias,
                        name, index, "read", null, 0, exception);
            }
            String hash = sha256(sql);
            context.log().log(LogLevel.DEBUG, "gretl.sql.script",
                    "SQL script is being executed",
                    Map.of("db.alias", alias,
                            "sql.script", name,
                            "sql.entry", Integer.toString(index),
                            "sql.sha256", hash), null);
            var reader = new SqlScriptReader(sql);
            List<Long> updates = new ArrayList<>();
            int resultSets = 0;
            int position = 0;
            String entry;
            while ((entry = reader.next()) != null) {
                context.cancellation().throwIfCancellationRequested();
                int statementPosition = ++position;
                if (request.parameterSets().isPresent()) {
                    entry = SqlScriptReader.substitute(entry, parameters, missing -> new SqlTaskException(
                            "SQL_PARAMETER_MISSING", msg(context, "task.sql.parameterMissing",
                                    name, set + 1, statementPosition, missing),
                            alias, name, index, "template", null, 0, null));
                }
                try (Statement statement = connection.createStatement()) {
                    applyTimeout(statement, context);
                    if (fetchInBatches) {
                        statement.setFetchSize(256);
                    }
                    var result = collect(statement, statement.execute(entry), index, request, context);
                    updates.addAll(result.updateCounts());
                    resultSets += result.resultSetCount();
                } catch (SQLException exception) {
                    // JDBC errors can quote the submitted SQL or data values.
                    SQLException safe = new SQLException(null, exception.getSQLState(), exception.getErrorCode());
                    throw sqlError("SQL_SCRIPT", msg(context, "task.sql.scriptPositionFailed",
                                    name, set + 1, statementPosition, safe.getSQLState()), alias,
                            name, index, "execute", safe);
                }
            }
            return new SqlExecutionResult(index, updates, resultSets);
        }

        private static SqlExecutionResult executeStatement(
                Connection connection, PreparedStatementSpec spec, int index,
                SqlRequest request, boolean fetchInBatches, OperationContext context) {
            try (PreparedStatement statement = connection.prepareStatement(spec.sql())) {
                applyTimeout(statement, context);
                if (fetchInBatches) {
                    statement.setFetchSize(256);
                }
                bind(statement, spec.parameters());
                return collect(statement, statement.execute(), index, request, context);
            } catch (SQLException exception) {
                throw sqlError("SQL_STATEMENT", msg(context, "task.sql.statementFailed"), request.database(),
                        null, index, "execute", exception);
            }
        }

        private static SqlExecutionResult collect(
                Statement statement, boolean hasResult, int index, SqlRequest request,
                OperationContext context) throws SQLException {
            List<Long> updateCounts = new ArrayList<>();
            int resultSets = 0;
            boolean current = hasResult;
            while (true) {
                if (current) {
                    try (ResultSet resultSet = statement.getResultSet()) {
                        if (resultSet != null) {
                            resultSets++;
                            consumeRows(resultSet, request, context);
                        }
                    }
                } else {
                    long updateCount = statement.getUpdateCount();
                    if (updateCount == -1) {
                        break;
                    }
                    updateCounts.add(updateCount);
                }
                current = statement.getMoreResults();
            }
            return new SqlExecutionResult(index, updateCounts, resultSets);
        }

        private static void consumeRows(ResultSet rows, SqlRequest request, OperationContext context)
                throws SQLException {
            long count = 0;
            int limit = request.maxLoggedRows();
            int columns = request.logResults() ? rows.getMetaData().getColumnCount() : 0;
            while (rows.next()) {
                context.cancellation().throwIfCancellationRequested();
                if (!request.logResults()) {
                    continue;
                }
                count++;
                if (limit == 0 || count <= limit) {
                    List<String> values = new ArrayList<>(columns);
                    for (int column = 1; column <= columns; column++) {
                        values.add(String.valueOf(rows.getString(column)));
                    }
                    context.log().log(LogLevel.INFO, "gretl.sql.row", String.join(" | ", values), Map.of(), null);
                } else if (count == (long) limit + 1) {
                    context.log().log(LogLevel.INFO, "gretl.sql.rowsTruncated",
                            msg(context, "task.sql.rowsTruncated", limit), Map.of(), null);
                }
            }
        }

        private static void bind(PreparedStatement statement, List<Object> parameters)
                throws SQLException {
            for (int index = 0; index < parameters.size(); index++) {
                Object value = parameters.get(index);
                int parameter = index + 1;
                switch (value) {
                    case null -> statement.setNull(parameter, Types.NULL);
                    case String text -> statement.setString(parameter, text);
                    case Boolean bool -> statement.setBoolean(parameter, bool);
                    case Byte number -> statement.setByte(parameter, number);
                    case Short number -> statement.setShort(parameter, number);
                    case Integer number -> statement.setInt(parameter, number);
                    case Long number -> statement.setLong(parameter, number);
                    case BigDecimal number -> statement.setBigDecimal(parameter, number);
                    default -> throw new IllegalArgumentException(
                            "Unsupported SQL parameter: " + value.getClass().getName());
                }
            }
        }

        private static void applyTimeout(Statement statement, OperationContext context) {
            Optional<Duration> remaining = context.budget().remaining();
            if (remaining.isEmpty()) {
                return;
            }
            long seconds = Math.max(1, Math.min(Integer.MAX_VALUE,
                    remaining.orElseThrow().toSeconds()));
            try {
                statement.setQueryTimeout((int) seconds);
            } catch (SQLException ignored) {
                // Driver may not support query timeout; cooperative checks remain.
            }
        }

        private static String requiredText(Map<String, Object> config, String key,
                                           OperationContext context) {
            Object value = config.get(key);
            if (!(value instanceof CharSequence text) || text.toString().isBlank()) {
                throw new SqlTaskException("SQL_CONFIG",
                        msg(context, "task.sql.fieldMissing", key),
                        "?", null, 0, "configuration", null, 0, null);
            }
            return text.toString();
        }

        private static String optionalText(Map<String, Object> config, String key,
                                           OperationContext context) {
            Object value = config.get(key);
            if (value == null) {
                return null;
            }
            if (!(value instanceof CharSequence text)) {
                throw new SqlTaskException("SQL_CONFIG",
                        msg(context, "task.sql.fieldNotText", key),
                        "?", null, 0, "configuration", null, 0, null);
            }
            return text.toString();
        }

        private static SqlTaskException sqlError(
                String code, String message, String alias, String script, int index,
                String phase, SQLException exception) {
            SQLException current = exception;
            while (current != null) {
                if (current.getSQLState() != null || current.getErrorCode() != 0) {
                    return new SqlTaskException(code, message, alias, script, index, phase,
                            current.getSQLState(), current.getErrorCode(), exception);
                }
                current = current.getNextException();
            }
            return new SqlTaskException(code, message, alias, script, index, phase,
                    null, 0, exception);
        }

        private static void rollbackQuietly(Connection connection, RuntimeException primary) {
            if (connection == null) {
                return;
            }
            try {
                connection.rollback();
            } catch (SQLException exception) {
                primary.addSuppressed(exception);
            }
        }

        private static String sha256(String text) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(
                        text.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
