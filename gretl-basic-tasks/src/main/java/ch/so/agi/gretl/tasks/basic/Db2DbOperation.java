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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/** Db2Db task: copy rows from a source database into target tables. */
public final class Db2DbOperation {
    public static final String ID = "gretl.db2db";
    public static final String SYMBOL = "Db2Db";

    private Db2DbOperation() {
    }

    public static OperationType<Db2DbRequest, Db2DbResult> type() {
        InputField<Db2DbRequest, String> source = new InputField<>(
                "sourceDatabase", "text", "Alias of the source database connection",
                true, Optional.empty(), List.of(), "sourceDatabase = 'source'",
                Db2DbOperation::text,
                (builder, value) -> ((Db2DbBuilder) builder).setSourceDatabase(value));
        InputField<Db2DbRequest, String> target = new InputField<>(
                "targetDatabase", "text", "Alias of the target database connection",
                true, Optional.empty(), List.of(), "targetDatabase = 'target'",
                Db2DbOperation::text,
                (builder, value) -> ((Db2DbBuilder) builder).setTargetDatabase(value));
        InputField<Db2DbRequest, List<Db2DbTransfer>> transfers = new InputField<>(
                "transfers", "list", "Ordered transfers with sqlFile, targetTable, deleteAllRows and geometryColumns",
                true, Optional.empty(), List.of(),
                "transfers = [[sqlFile: file('select.sql'), targetTable: 'colors', deleteAllRows: true]]",
                Db2DbOperation::transfers,
                (builder, value) -> ((Db2DbBuilder) builder).setTransfers(value));
        InputField<Db2DbRequest, Map<String, String>> parameters = new InputField<>(
                "sqlParameters", "map", "SQL parameters for one execution of all transfers",
                false, Optional.of(Map.of()), List.of(), "sqlParameters = [year: '2024']",
                Db2DbOperation::stringMap,
                (builder, value) -> ((Db2DbBuilder) builder).setSqlParameters(value));
        InputField<Db2DbRequest, List<Map<String, String>>> parameterSets = new InputField<>(
                "sqlParameterSets", "list", "SQL parameter maps; all transfers run for each map",
                false, Optional.of(List.of()), List.of(),
                "sqlParameterSets = [[year: '2024'], [year: '2025']]",
                Db2DbOperation::stringMapList,
                (builder, value) -> ((Db2DbBuilder) builder).setSqlParameterSets(value));
        InputField<Db2DbRequest, Integer> batchSize = new InputField<>(
                "batchSize", "integer", "JDBC batch size for target inserts",
                false, Optional.of(5000), List.of(), "batchSize = 1000",
                Db2DbOperation::positiveInteger,
                (builder, value) -> ((Db2DbBuilder) builder).setBatchSize(value));
        InputField<Db2DbRequest, Integer> fetchSize = new InputField<>(
                "fetchSize", "integer", "JDBC fetch size for source reads",
                false, Optional.of(5000), List.of(), "fetchSize = 1000",
                Db2DbOperation::nonNegativeInteger,
                (builder, value) -> ((Db2DbBuilder) builder).setFetchSize(value));
        OutputField<Db2DbResult, Integer> executionCount = Fields.output(
                "executionCount", "integer", "Number of transfer executions", false,
                Db2DbResult::executionCount);
        OutputField<Db2DbResult, Long> transferredRows = Fields.output(
                "transferredRows", "integer", "Total transferred rows", false,
                Db2DbResult::transferredRows);
        OutputField<Db2DbResult, List<Db2DbExecution>> executions = Fields.output(
                "executions", "list", "Transfer results in execution order", false,
                Db2DbResult::executions);
        return new OperationType<>(
                ID, SYMBOL, "Copy rows from a source database into target tables",
                Db2DbRequest.class, Db2DbResult.class,
                Db2DbBuilder::new, Db2DbHandler::new,
                List.of(source, target, transfers, parameters, parameterSets,
                        batchSize, fetchSize),
                List.of(executionCount, transferredRows, executions),
                """
                task('copy', Db2Db) {
                    sourceDatabase = 'source'
                    targetDatabase = 'target'
                    transfers = [[
                        sqlFile: file('sql/select-colors.sql'),
                        targetTable: 'colors',
                        deleteAllRows: true
                    ]]
                }
                """);
    }

    public record Db2DbTransfer(
            Path sqlFile, String targetTable, boolean deleteAllRows,
            List<String> geometryColumns) {
        public Db2DbTransfer {
            geometryColumns = List.copyOf(geometryColumns);
        }
    }

    public record Db2DbExecution(int index, String targetTable, long rows) {
    }

    public record Db2DbResult(
            int executionCount, long transferredRows, List<Db2DbExecution> executions) {
        public Db2DbResult {
            executions = List.copyOf(executions);
        }
    }

    public static final class Db2DbBuilder
            implements ch.so.agi.gretl.runtime.api.ConfigBuilder<Db2DbRequest> {
        private String sourceDatabase;
        private String targetDatabase;
        private List<Db2DbTransfer> transfers = List.of();
        private Map<String, String> sqlParameters = Map.of();
        private List<Map<String, String>> sqlParameterSets = List.of();
        private int batchSize = 5000;
        private int fetchSize = 5000;

        public Db2DbBuilder setSourceDatabase(String value) {
            sourceDatabase = value;
            return this;
        }

        public Db2DbBuilder setTargetDatabase(String value) {
            targetDatabase = value;
            return this;
        }

        public Db2DbBuilder setTransfers(List<Db2DbTransfer> value) {
            transfers = value == null ? List.of() : value;
            return this;
        }

        public Db2DbBuilder setSqlParameters(Map<String, String> value) {
            sqlParameters = value == null ? Map.of() : value;
            return this;
        }

        public Db2DbBuilder setSqlParameterSets(List<Map<String, String>> value) {
            sqlParameterSets = value == null ? List.of() : value;
            return this;
        }

        public Db2DbBuilder setBatchSize(Integer value) {
            batchSize = value == null ? 5000 : value;
            return this;
        }

        public Db2DbBuilder setFetchSize(Integer value) {
            fetchSize = value == null ? 5000 : value;
            return this;
        }

        @Override
        public Db2DbRequest build() {
            if (sourceDatabase == null || sourceDatabase.isBlank()) {
                throw new IllegalArgumentException("sourceDatabase is missing");
            }
            if (targetDatabase == null || targetDatabase.isBlank()) {
                throw new IllegalArgumentException("targetDatabase is missing");
            }
            if (transfers.isEmpty()) {
                throw new IllegalArgumentException("transfers must not be empty");
            }
            if (!sqlParameters.isEmpty() && !sqlParameterSets.isEmpty()) {
                throw new IllegalArgumentException(
                        "Use either sqlParameters or sqlParameterSets, not both");
            }
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batchSize must be greater than zero");
            }
            if (fetchSize < 0) {
                throw new IllegalArgumentException(
                        "fetchSize must be greater than or equal to zero");
            }
            return new Db2DbRequest(sourceDatabase, targetDatabase, transfers,
                    sqlParameters, sqlParameterSets, batchSize, fetchSize);
        }
    }

    public record Db2DbRequest(
            String sourceDatabase, String targetDatabase,
            List<Db2DbTransfer> transfers,
            Map<String, String> sqlParameters,
            List<Map<String, String>> sqlParameterSets,
            int batchSize, int fetchSize) {
        public Db2DbRequest {
            transfers = List.copyOf(transfers);
            sqlParameters = Map.copyOf(sqlParameters);
            sqlParameterSets = sqlParameterSets.stream()
                    .map(Map::copyOf).toList();
        }
    }

    static final class Db2DbHandler
            implements OperationHandler<Db2DbRequest, Db2DbResult> {
        @Override
        public Db2DbResult execute(Db2DbRequest request, OperationContext context) {
            List<Map<String, String>> parameterSets = request.sqlParameterSets().isEmpty()
                    ? List.of(Map.of()) : request.sqlParameterSets();
            if (!request.sqlParameters().isEmpty()) {
                parameterSets = List.of(request.sqlParameters());
            }
            List<Db2DbExecution> executions = new ArrayList<>();
            long transferredRows = 0;
            int index = 0;
            try {
                Db2DbSupport.DatabaseConfig source =
                        Db2DbSupport.config(context, request.sourceDatabase());
                Db2DbSupport.DatabaseConfig target =
                        Db2DbSupport.config(context, request.targetDatabase());
                if (source.alias().equals(target.alias())) {
                    try (Connection connection = Db2DbSupport.open(source)) {
                        try {
                            for (Map<String, String> parameters : parameterSets) {
                                for (Db2DbTransfer transfer : request.transfers()) {
                                    long rows = process(connection, connection, transfer,
                                            parameters, request, context);
                                    executions.add(new Db2DbExecution(index++,
                                            transfer.targetTable(), rows));
                                    transferredRows += rows;
                                    context.progress(new Progress("db2db", index,
                                            OptionalLong.of(request.transfers().size()
                                                    * parameterSets.size()), "transfers"));
                                }
                            }
                            connection.commit();
                        } catch (Exception exception) {
                            rollback(connection);
                            throw exception;
                        }
                    }
                } else {
                    try (Connection sourceConnection = Db2DbSupport.open(source);
                         Connection targetConnection = Db2DbSupport.open(target)) {
                        try {
                            for (Map<String, String> parameters : parameterSets) {
                                for (Db2DbTransfer transfer : request.transfers()) {
                                    long rows = process(sourceConnection, targetConnection,
                                            transfer, parameters, request, context);
                                    executions.add(new Db2DbExecution(index++,
                                            transfer.targetTable(), rows));
                                    transferredRows += rows;
                                    context.progress(new Progress("db2db", index,
                                            OptionalLong.of(request.transfers().size()
                                                    * parameterSets.size()), "transfers"));
                                }
                            }
                            targetConnection.commit();
                        } catch (Exception exception) {
                            rollback(targetConnection);
                            throw exception;
                        } finally {
                            rollback(sourceConnection);
                        }
                    }
                }
                context.log().log(LogLevel.INFO, "gretl.db2db.finished",
                        msg(context, "task.db2db.finished"),
                        Map.of("db.source", request.sourceDatabase(),
                                "db.target", request.targetDatabase(),
                                "db.executions", Integer.toString(index),
                                "db.rows", Long.toString(transferredRows)), null);
                return new Db2DbResult(index, transferredRows, executions);
            } catch (Db2DbTaskException exception) {
                logFailure(context, exception);
                throw exception;
            } catch (SQLException | IOException exception) {
                Db2DbTaskException wrapped = new Db2DbTaskException("DB2DB_EXECUTION",
                        msg(context, "task.db2db.failed"), "execute", exception);
                logFailure(context, wrapped);
                throw wrapped;
            } catch (RuntimeException exception) {
                Db2DbTaskException wrapped = new Db2DbTaskException("DB2DB_EXECUTION",
                        msg(context, "task.db2db.failed"), "execute", exception);
                logFailure(context, wrapped);
                throw wrapped;
            }
        }

        private static long process(Connection source, Connection target,
                                    Db2DbTransfer transfer, Map<String, String> parameters,
                                    Db2DbRequest request, OperationContext context)
                throws IOException, SQLException {
            context.cancellation().throwIfCancellationRequested();
            Db2DbSupport.validateTargetTable(transfer.targetTable(), context);
            if (!Files.isReadable(transfer.sqlFile())) {
                throw new Db2DbTaskException("DB_FILE",
                        msg(context, "task.db2db.file", transfer.sqlFile()), "prepare", null);
            }
            String sql = Db2DbSupport.substitute(
                    Files.readString(transfer.sqlFile(), StandardCharsets.UTF_8),
                    parameters, context);
            List<String> statements = Db2DbSupport.split(sql);
            if (statements.isEmpty() || statements.size() > 2) {
                throw new Db2DbTaskException("DB_SQL",
                        msg(context, "task.db2db.statements"), "prepare", null);
            }
            if (statements.size() == 2) {
                if (!Db2DbSupport.isSetSearchPath(statements.get(0))) {
                    throw new Db2DbTaskException("DB_SQL",
                            msg(context, "task.db2db.searchPath"), "prepare", null);
                }
                try (Statement statement = source.createStatement()) {
                    statement.execute(statements.get(0));
                }
            }
            if (transfer.deleteAllRows()) {
                try (Statement statement = target.createStatement()) {
                    statement.executeUpdate("DELETE FROM " + transfer.targetTable());
                }
            }
            String select = statements.get(statements.size() - 1);
            Map<String, Db2DbSupport.GeometryTransform> geometries = new LinkedHashMap<>();
            for (String definition : transfer.geometryColumns()) {
                Db2DbSupport.GeometryTransform transform =
                        Db2DbSupport.geometry(definition, context);
                geometries.put(transform.column(), transform);
            }
            try (Statement statement = source.createStatement()) {
                if (request.fetchSize() > 0) {
                    statement.setFetchSize(request.fetchSize());
                }
                applyTimeout(statement, context);
                try (ResultSet rows = statement.executeQuery(select)) {
                    ResultSetMetaData metadata = rows.getMetaData();
                    Map<String, String> targetColumns =
                            Db2DbSupport.targetColumns(target, transfer.targetTable());
                    List<String> columns = new ArrayList<>();
                    List<String> values = new ArrayList<>();
                    List<Db2DbSupport.GeometryTransform> geometryValues = new ArrayList<>();
                    for (int column = 1; column <= metadata.getColumnCount(); column++) {
                        String sourceName = metadata.getColumnLabel(column);
                        if (sourceName == null || sourceName.isBlank()) {
                            sourceName = metadata.getColumnName(column);
                        }
                        String targetName = targetColumns.get(
                                sourceName.toLowerCase(Locale.ROOT));
                        if (targetName == null) {
                            throw new Db2DbTaskException("DB_COLUMN",
                                    msg(context, "task.db2db.column", sourceName,
                                            transfer.targetTable()), "execute", null);
                        }
                        columns.add(Db2DbSupport.quoteIdentifier(target, targetName));
                        Db2DbSupport.GeometryTransform geometry =
                                geometries.get(sourceName.toUpperCase(Locale.ROOT));
                        values.add(geometry == null ? "?" : geometry.valueExpression());
                        geometryValues.add(geometry);
                    }
                    String insertSql = "INSERT INTO " + transfer.targetTable()
                            + " (" + String.join(", ", columns) + ") VALUES ("
                            + String.join(", ", values) + ")";
                    try (PreparedStatement insert = target.prepareStatement(insertSql)) {
                        applyTimeout(insert, context);
                        long count = copyRows(rows, insert, geometryValues,
                                request.batchSize(), context);
                        context.log().log(LogLevel.INFO, "gretl.db2db.transfer",
                                msg(context, "task.db2db.transfer"),
                                Map.of("db.target", transfer.targetTable(),
                                        "db.rows", Long.toString(count)), null);
                        return count;
                    }
                }
            }
        }

        private static long copyRows(ResultSet rows, PreparedStatement insert,
                                     List<Db2DbSupport.GeometryTransform> geometryValues,
                                     int batchSize, OperationContext context)
                throws SQLException {
            long count = 0;
            int pending = 0;
            while (rows.next()) {
                context.cancellation().throwIfCancellationRequested();
                for (int column = 1; column <= geometryValues.size(); column++) {
                    insert.setObject(column, rows.getObject(column));
                }
                insert.addBatch();
                count++;
                pending++;
                if (pending >= batchSize) {
                    insert.executeBatch();
                    insert.clearBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                insert.executeBatch();
            }
            return count;
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
                // Driver may not support query timeouts.
            }
        }

        private static void rollback(Connection connection) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // Rollback is best effort; the primary failure is preserved.
            }
        }

        private static void logFailure(OperationContext context,
                                       Db2DbTaskException exception) {
            context.log().log(LogLevel.WARN, "gretl.db2db.failed",
                    msg(context, "task.db2db.failed"),
                    Map.of("db.phase", exception.phase()), exception);
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }
    }

    private static String text(Object value) {
        if (!(value instanceof CharSequence text) || text.toString().isBlank()) {
            throw new IllegalArgumentException("Text value must not be blank");
        }
        return text.toString();
    }

    private static Integer positiveInteger(Object value) {
        Integer result = nonNegativeInteger(value);
        if (result == 0) {
            throw new IllegalArgumentException("Value must be greater than zero");
        }
        return result;
    }

    private static Integer nonNegativeInteger(Object value) {
        if (value instanceof Number number) {
            long result = number.longValue();
            if (result < 0 || result > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Value is out of range");
            }
            return (int) result;
        }
        if (value instanceof CharSequence text
                && text.toString().matches("[0-9]+")) {
            return Integer.parseInt(text.toString());
        }
        throw new IllegalArgumentException("Non-negative integer expected");
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

    private static List<Db2DbTransfer> transfers(Object value) {
        List<Object> raw = list(value);
        List<Db2DbTransfer> transfers = new ArrayList<>();
        for (Object element : raw) {
            if (!(element instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("transfers contains no map");
            }
            Set<String> allowed = Set.of("sqlFile", "targetTable",
                    "deleteAllRows", "geometryColumns");
            for (Object key : map.keySet()) {
                if (!allowed.contains(String.valueOf(key))) {
                    throw new IllegalArgumentException("Unknown transfer field: " + key);
                }
            }
            Object sqlFile = map.get("sqlFile");
            Object targetTable = map.get("targetTable");
            if (sqlFile == null || targetTable == null) {
                throw new IllegalArgumentException(
                        "transfer requires sqlFile and targetTable");
            }
            String table = text(targetTable);
            if (table.contains(";") || table.contains("--") || table.contains("/*")
                    || table.contains("*/") || table.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Invalid targetTable");
            }
            boolean deleteAllRows = false;
            if (map.get("deleteAllRows") != null) {
                if (!(map.get("deleteAllRows") instanceof Boolean flag)) {
                    throw new IllegalArgumentException("deleteAllRows must be boolean");
                }
                deleteAllRows = flag;
            }
            List<String> geometryColumns = new ArrayList<>();
            if (map.get("geometryColumns") != null) {
                for (Object geometry : list(map.get("geometryColumns"))) {
                    String definition = text(geometry);
                    validateGeometry(definition);
                    geometryColumns.add(definition);
                }
            }
            transfers.add(new Db2DbTransfer(path(sqlFile), table, deleteAllRows,
                    geometryColumns));
        }
        return List.copyOf(transfers);
    }

    private static void validateGeometry(String definition) {
        String[] parts = definition.split(":");
        if (parts.length != 3 || parts[0].isBlank()
                || !Set.of("WKT", "WKB", "GEOJSON").contains(
                        parts[1].toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Invalid geometry column: " + definition);
        }
        try {
            Integer.parseInt(parts[2]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid geometry SRID: " + definition);
        }
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Map expected");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = text(entry.getKey());
            Object raw = entry.getValue();
            if (raw == null) {
                throw new IllegalArgumentException("SQL parameter value must not be null");
            }
            if (!(raw instanceof CharSequence) && !(raw instanceof Number)
                    && !(raw instanceof Boolean)) {
                throw new IllegalArgumentException("SQL parameter value must be scalar");
            }
            result.put(key, raw.toString());
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
