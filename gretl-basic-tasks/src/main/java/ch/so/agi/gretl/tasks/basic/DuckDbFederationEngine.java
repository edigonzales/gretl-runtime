package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.Progress;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** DuckDB federation bootstrap, SQL execution and file exports. */
final class DuckDbFederationEngine {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    DuckDbSqlExecutorOperation.DuckDbResult execute(
            DuckDbSqlExecutorOperation.DuckDbRequest request, OperationContext context) {
        List<Source> sources = sources(request.sources(), context);
        List<Target> targets = targets(request.targets(), context);
        List<Export> exports = exports(request.exports(), context);
        List<String> attachments = new ArrayList<>();
        List<DuckDbSqlExecutorOperation.DuckDbExecution> executions = new ArrayList<>();
        List<Path> exportFiles = new ArrayList<>();
        List<PendingExport> pending = new ArrayList<>();
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
                    loadExtensions(connection, request.installExtensions(), sources,
                            targets, exports, context);
                    bootstrapSources(connection, sources, attachments, context);
                    bootstrapTargets(connection, targets, attachments, context);
                    List<Map<String, String>> parameterSets = parameterSets(request);
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
                    // DuckDB cannot write to an attached database in a transaction
                    // that already modified the DuckDB database. Commit SQL first,
                    // then write file exports and PostgreSQL exports separately.
                    connection.commit();
                    prepareFileExports(connection, exports, pending, context);
                    connection.commit();
                    preparePostgresExports(connection, exports, context);
                    connection.commit();
                    // Drop source schemas only after the data has been committed;
                    // DuckDB otherwise reports a dependency conflict between the
                    // source object and a table created from it in the same transaction.
                    cleanupSources(connection, sources, attachments);
                    connection.commit();
                } catch (Exception exception) {
                    rollback(connection);
                    cleanup(pending);
                    throw exception;
                }
            }
            moveExports(pending);
            exportFiles.addAll(pending.stream().map(PendingExport::target).toList());
            context.log().log(LogLevel.INFO, "gretl.duckdb.finished",
                    msg(context, "task.duckdb.finished"),
                    Map.of("duckdb.statements", Integer.toString(index),
                            "duckdb.exports", Integer.toString(exportFiles.size())), null);
            return new DuckDbSqlExecutorOperation.DuckDbResult(index, executions, exportFiles);
        } catch (DuckDbTaskException exception) {
            logFailure(context, exception);
            throw exception;
        } catch (Exception exception) {
            DuckDbTaskException wrapped = new DuckDbTaskException("DUCKDB_EXECUTION",
                    msg(context, "task.duckdb.failed"), "execute", exception);
            logFailure(context, wrapped);
            throw wrapped;
        }
    }

    private static List<Map<String, String>> parameterSets(
            DuckDbSqlExecutorOperation.DuckDbRequest request) {
        if (!request.sqlParameterSets().isEmpty()) {
            return request.sqlParameterSets();
        }
        if (!request.sqlParameters().isEmpty()) {
            return List.of(request.sqlParameters());
        }
        return List.of(Map.of());
    }

    private static void loadExtensions(Connection connection, boolean install,
                                       List<Source> sources, List<Target> targets,
                                       List<Export> exports,
                                       OperationContext context) throws SQLException {
        LinkedHashSet<String> extensions = new LinkedHashSet<>();
        for (Source source : sources) {
            extensions.addAll(source.extensions());
        }
        for (Target target : targets) {
            extensions.addAll(target.extensions());
        }
        for (Export export : exports) {
            extensions.addAll(export.extensions());
        }
        String directory = System.getProperty("duckdb.extensionDirectory",
                System.getenv("DUCKDB_EXTENSION_DIRECTORY"));
        if (directory != null && !directory.isBlank()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET extension_directory = "
                        + literal(directory));
            }
        }
        for (String extension : extensions) {
            try (Statement statement = connection.createStatement()) {
                if (install) {
                    statement.execute("INSTALL " + extension);
                }
                statement.execute("LOAD " + extension);
            } catch (SQLException exception) {
                throw new DuckDbTaskException("DUCKDB_EXTENSION",
                        msg(context, "task.duckdb.extension", extension),
                        "extensions", exception);
            }
        }
    }

    private static void bootstrapSources(Connection connection, List<Source> sources,
                                         List<String> attachments, OperationContext context)
            throws Exception {
        for (Source source : sources) {
            execute(connection, "CREATE SCHEMA IF NOT EXISTS " + identifier(source.alias()));
            if ("POSTGRES".equals(source.type())) {
                Db2DbSupport.DatabaseConfig config = Db2DbSupport.config(context,
                        source.database());
                String rawAlias = "__gretl_" + source.alias() + "_raw";
                attachPostgres(connection, rawAlias, config, true);
                attachments.add(rawAlias);
                for (Table table : source.tables()) {
                    String object = identifier(source.alias()) + "."
                            + identifier(table.alias());
                    dropObject(connection, object);
                    String select = "SELECT * FROM " + identifier(rawAlias) + "."
                            + identifier(table.schema()) + "."
                            + identifier(table.table());
                    if (table.materialize()) {
                        execute(connection, "CREATE TABLE " + object + " AS " + select);
                    } else {
                        execute(connection, "CREATE OR REPLACE VIEW " + object + " AS " + select);
                    }
                }
                continue;
            }
            String object = identifier(source.alias()) + "." + identifier(source.table());
            dropObject(connection, object);
            String select = switch (source.type()) {
                case "CSV" -> csvSelect(source);
                case "GPKG" -> "SELECT * FROM ST_Read(" + literal(source.file().toAbsolutePath().toString())
                        + ", layer = " + literal(source.layer()) + ")";
                default -> throw new IllegalArgumentException(
                        "Unsupported DuckDB source: " + source.type());
            };
            if (source.materialize()) {
                execute(connection, "CREATE TABLE " + object + " AS " + select);
            } else {
                execute(connection, "CREATE OR REPLACE VIEW " + object + " AS " + select);
            }
        }
    }

    private static String csvSelect(Source source) {
        List<String> options = new ArrayList<>();
        if (source.header() != null) {
            options.add("header = " + source.header());
        }
        if (source.delimiter() != null) {
            options.add("delim = " + literal(source.delimiter()));
        }
        if (source.allVarchar()) {
            options.add("all_varchar = true");
        }
        String sql = "SELECT * FROM read_csv("
                + literal(source.file().toAbsolutePath().toString());
        if (!options.isEmpty()) {
            sql += ", " + String.join(", ", options);
        }
        return sql + ")";
    }

    private static void cleanupSources(Connection connection, List<Source> sources,
                                       List<String> attachments) throws SQLException {
        for (Source source : sources) {
            execute(connection, "DROP SCHEMA IF EXISTS " + identifier(source.alias())
                    + " CASCADE");
        }
        execute(connection, "DROP SCHEMA IF EXISTS __gretl_export CASCADE");
        for (String attachment : attachments) {
            execute(connection, "DETACH " + identifier(attachment));
        }
    }

    private static void bootstrapTargets(Connection connection, List<Target> targets,
                                         List<String> attachments, OperationContext context)
            throws Exception {
        for (Target target : targets) {
            Db2DbSupport.DatabaseConfig config = Db2DbSupport.config(context,
                    target.database());
            attachPostgres(connection, target.alias(), config, false);
            attachments.add(target.alias());
        }
    }

    private static void attachPostgres(Connection connection, String alias,
                                       Db2DbSupport.DatabaseConfig config, boolean readOnly)
            throws SQLException {
        PostgresParts parts = PostgresParts.parse(config.jdbcUrl());
        String secret = "__gretl_" + alias + "_secret";
        execute(connection, "CREATE OR REPLACE SECRET " + identifier(secret)
                + " (TYPE postgres, HOST " + literal(parts.host())
                + ", PORT " + parts.port()
                + ", DATABASE " + literal(parts.database())
                + ", USER " + literal(nullToEmpty(config.user()))
                + ", PASSWORD " + literal(nullToEmpty(config.password())) + ")");
        String options = "TYPE postgres, SECRET " + identifier(secret)
                + (readOnly ? ", READ_ONLY" : "");
        execute(connection, "ATTACH '' AS " + identifier(alias)
                + " (" + options + ")");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void prepareFileExports(Connection connection, List<Export> exports,
                                           List<PendingExport> pending,
                                           OperationContext context)
            throws SQLException, IOException {
        for (Export export : exports) {
            if ("POSTGRES".equals(export.type())) {
                continue;
            }
            Path target = export.file().toAbsolutePath().normalize();
            if (Files.exists(target) && !export.overwrite()) {
                throw new DuckDbTaskException("DUCKDB_EXPORT",
                        msg(context, "task.duckdb.exportExists", target),
                        "export", null);
            }
            Files.createDirectories(target.getParent());
            Path temp = temporary(target);
            pending.add(new PendingExport(temp, target));
            switch (export.type()) {
                case "PARQUET" -> execute(connection,
                        "COPY (" + query(export.query()) + ") TO " + literal(temp.toString())
                                + " WITH (FORMAT PARQUET)");
                case "XLSX" -> execute(connection,
                        "COPY (" + query(export.query()) + ") TO " + literal(temp.toString())
                                + " WITH (FORMAT XLSX, HEADER " + export.header()
                                + ", SHEET " + literal(export.sheet())
                                + ", SHEET_ROW_LIMIT " + export.sheetRowLimit() + ")");
                case "GPKG" -> exportGpkg(connection, export, temp);
                default -> throw new IllegalArgumentException(
                        "Unsupported DuckDB export: " + export.type());
            }
        }
    }

    private static void preparePostgresExports(Connection connection,
                                               List<Export> exports,
                                               OperationContext context)
            throws SQLException {
        for (Export export : exports) {
            if ("POSTGRES".equals(export.type())) {
                exportPostgres(connection, export);
            }
        }
    }

    private static void exportPostgres(Connection connection, Export export)
            throws SQLException {
        String[] table = export.table().split("\\.");
        if (table.length != 2) {
            throw new IllegalArgumentException(
                    "PostgreSQL export table must be schema.table: " + export.table());
        }
        String target = identifier(export.target()) + "."
                + identifier(table[0]) + "." + identifier(table[1]);
        String select = query(export.query());
        switch (export.mode()) {
            case "replace" -> {
                execute(connection, "DROP TABLE IF EXISTS " + target);
                execute(connection, "CREATE TABLE " + target + " AS " + select);
            }
            case "truncate" -> {
                if (export.create()) {
                    createEmptyTable(connection, target, select);
                }
                execute(connection, "DELETE FROM " + target);
                execute(connection, "INSERT INTO " + target
                        + " SELECT * FROM (" + select + ")");
            }
            case "append" -> {
                if (export.create()) {
                    createEmptyTable(connection, target, select);
                }
                execute(connection, "INSERT INTO " + target
                        + " SELECT * FROM (" + select + ")");
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported PostgreSQL export mode: " + export.mode());
        }
    }

    private static void createEmptyTable(Connection connection, String target,
                                         String select) throws SQLException {
        execute(connection, "CREATE TABLE IF NOT EXISTS " + target
                + " AS SELECT * FROM (" + select + ") WHERE 1=0");
    }

    private static void exportGpkg(Connection connection, Export export, Path temp)
            throws SQLException {
        execute(connection, "CREATE SCHEMA IF NOT EXISTS __gretl_export");
        String table = identifier("__gretl_export") + "." + identifier(export.layer());
        execute(connection, "DROP TABLE IF EXISTS " + table);
        execute(connection, "CREATE TABLE " + table + " AS " + query(export.query()));
        String options = "FORMAT GDAL, DRIVER 'GPKG'";
        if (export.srs() != null && !export.srs().isBlank()) {
            options += ", SRS " + literal(export.srs());
        }
        execute(connection, "COPY " + table + " TO " + literal(temp.toString())
                + " WITH (" + options + ")");
        execute(connection, "DROP TABLE IF EXISTS " + table);
    }

    private static void moveExports(List<PendingExport> pending) throws IOException {
        for (PendingExport export : pending) {
            try {
                Files.move(export.temp(), export.target(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(export.temp(), export.target(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void cleanup(List<PendingExport> pending) {
        for (PendingExport export : pending) {
            try {
                Files.deleteIfExists(export.temp());
            } catch (IOException ignored) {
                // Preserve the primary failure.
            }
        }
    }

    private static DuckDbSqlExecutorOperation.DuckDbExecution execute(
            Connection connection, String sql, int index) throws SQLException {
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
                                // Consume and close.
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
            return new DuckDbSqlExecutorOperation.DuckDbExecution(index, updates, resultSets);
        }
    }

    private static List<String> statements(Path sqlFile, Map<String, String> parameters,
                                            OperationContext context) throws IOException {
        if (!Files.isReadable(sqlFile)
                || !sqlFile.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql")) {
            throw new DuckDbTaskException("DUCKDB_FILE",
                    msg(context, "task.duckdb.file", sqlFile), "prepare", null);
        }
        String sql = Files.readString(sqlFile, StandardCharsets.UTF_8);
        if (sql.isBlank()) {
            throw new DuckDbTaskException("DUCKDB_FILE",
                    msg(context, "task.duckdb.file", sqlFile), "prepare", null);
        }
        try {
            return Db2DbSupport.split(Db2DbSupport.substitute(sql, parameters, context));
        } catch (Db2DbTaskException exception) {
            throw new DuckDbTaskException("DUCKDB_PARAMETER",
                    exception.getMessage(), "prepare", exception);
        }
    }

    private static List<Source> sources(List<Map<String, Object>> values,
                                        OperationContext context) {
        List<Source> sources = new ArrayList<>();
        Set<String> aliases = new LinkedHashSet<>();
        for (Map<String, Object> value : values) {
            String type = text(value, "type", context).toUpperCase(Locale.ROOT);
            String alias = identifier(value, "alias", context);
            if (!aliases.add(alias)) {
                throw new DuckDbTaskException("DUCKDB_SOURCE",
                        msg(context, "task.duckdb.duplicateAlias", alias),
                        "configuration", null);
            }
            String mode = optionalText(value, "mode");
            if (mode != null && !mode.equalsIgnoreCase("view")
                    && !mode.equalsIgnoreCase("materialize")) {
                throw new DuckDbTaskException("DUCKDB_SOURCE",
                        msg(context, "task.duckdb.mode", mode), "configuration", null);
            }
            boolean materialize = "materialize".equalsIgnoreCase(mode);
            switch (type) {
                case "CSV" -> {
                    Path file = requiredPath(value, "file", context);
                    String table = optionalText(value, "table");
                    if (table == null || table.isBlank()) {
                        table = "data";
                    }
                    identifier(table, context);
                    Boolean header = optionalBoolean(value, "header", context);
                    String delimiter = optionalText(value, "delimiter");
                    if (delimiter != null && delimiter.length() != 1) {
                        throw new DuckDbTaskException("DUCKDB_SOURCE",
                                msg(context, "task.duckdb.delimiter"), "configuration", null);
                    }
                    boolean allVarchar = Boolean.TRUE.equals(
                            optionalBoolean(value, "allVarchar", context));
                    sources.add(new Source(type, alias, file, table, table,
                            materialize, header, delimiter, allVarchar, null, List.of()));
                }
                case "GPKG" -> {
                    Path file = requiredPath(value, "file", context);
                    String layer = optionalText(value, "layer");
                    if (layer == null || layer.isBlank()) {
                        throw new DuckDbTaskException("DUCKDB_SOURCE",
                                msg(context, "task.duckdb.gpkgLayer"), "configuration", null);
                    }
                    String table = optionalText(value, "table");
                    if (table == null || table.isBlank()) {
                        table = layer;
                    }
                    identifier(table, context);
                    sources.add(new Source(type, alias, file, table, layer,
                            materialize, null, null, false, null, List.of()));
                }
                case "POSTGRES" -> {
                    String database = text(value, "database", context);
                    List<Table> tables = tables(value.get("tables"), context);
                    sources.add(new Source(type, alias, null, null, null,
                            materialize, null, null, false, database, tables));
                }
                default -> throw new DuckDbTaskException("DUCKDB_SOURCE",
                        msg(context, "task.duckdb.sourceType", type),
                        "configuration", null);
            }
        }
        return List.copyOf(sources);
    }

    private static List<Table> tables(Object value, OperationContext context) {
        if (!(value instanceof Iterable<?> iterable)) {
            throw new DuckDbTaskException("DUCKDB_SOURCE",
                    msg(context, "task.duckdb.tables"), "configuration", null);
        }
        List<Table> tables = new ArrayList<>();
        for (Object element : iterable) {
            if (!(element instanceof Map<?, ?> map)) {
                throw new DuckDbTaskException("DUCKDB_SOURCE",
                        msg(context, "task.duckdb.tables"), "configuration", null);
            }
            Set<String> allowed = Set.of("table", "alias", "mode");
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (!allowed.contains(key)) {
                    throw new DuckDbTaskException("DUCKDB_SOURCE",
                            msg(context, "task.duckdb.tableField", key),
                            "configuration", null);
                }
                values.put(key, entry.getValue());
            }
            String qualified = text(values, "table", context);
            String[] parts = qualified.split("\\.");
            if (parts.length != 2 || !IDENTIFIER.matcher(parts[0]).matches()
                    || !IDENTIFIER.matcher(parts[1]).matches()) {
                throw new DuckDbTaskException("DUCKDB_SOURCE",
                        msg(context, "task.duckdb.qualifiedTable", qualified),
                        "configuration", null);
            }
            String tableAlias = optionalText(values, "alias");
            if (tableAlias == null || tableAlias.isBlank()) {
                tableAlias = parts[1];
            }
            identifier(tableAlias, context);
            String tableMode = optionalText(values, "mode");
            if (tableMode != null && !tableMode.equalsIgnoreCase("view")
                    && !tableMode.equalsIgnoreCase("materialize")) {
                throw new DuckDbTaskException("DUCKDB_SOURCE",
                        msg(context, "task.duckdb.mode", tableMode),
                        "configuration", null);
            }
            tables.add(new Table(parts[0], parts[1], tableAlias,
                    "materialize".equalsIgnoreCase(tableMode)));
        }
        if (tables.isEmpty()) {
            throw new DuckDbTaskException("DUCKDB_SOURCE",
                    msg(context, "task.duckdb.tables"), "configuration", null);
        }
        return List.copyOf(tables);
    }

    private static List<Target> targets(List<Map<String, Object>> values,
                                        OperationContext context) {
        List<Target> targets = new ArrayList<>();
        Set<String> aliases = new LinkedHashSet<>();
        for (Map<String, Object> value : values) {
            String type = text(value, "type", context).toUpperCase(Locale.ROOT);
            if (!"POSTGRES".equals(type)) {
                throw new DuckDbTaskException("DUCKDB_TARGET",
                        msg(context, "task.duckdb.targetType", type),
                        "configuration", null);
            }
            String alias = identifier(value, "alias", context);
            if (!aliases.add(alias)) {
                throw new DuckDbTaskException("DUCKDB_TARGET",
                        msg(context, "task.duckdb.duplicateAlias", alias),
                        "configuration", null);
            }
            targets.add(new Target(type, alias, text(value, "database", context)));
        }
        return List.copyOf(targets);
    }

    private static List<Export> exports(List<Map<String, Object>> values,
                                        OperationContext context) {
        List<Export> exports = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (Map<String, Object> value : values) {
            String type = text(value, "type", context).toUpperCase(Locale.ROOT);
            String name = identifier(value, "name", context);
            if (!names.add(name)) {
                throw new DuckDbTaskException("DUCKDB_EXPORT",
                        msg(context, "task.duckdb.duplicateExport", name),
                        "configuration", null);
            }
            String query = text(value, "query", context);
            boolean overwrite = Boolean.TRUE.equals(optionalBoolean(value, "overwrite", context));
            switch (type) {
                case "PARQUET" -> exports.add(new Export(type, name, query,
                        requiredPath(value, "file", context), name, null,
                        "Sheet1", true, 1_048_576, overwrite, null, null, null, false));
                case "XLSX" -> {
                    String sheet = optionalText(value, "sheet");
                    if (sheet == null || sheet.isBlank()) {
                        sheet = "Sheet1";
                    }
                    Boolean header = optionalBoolean(value, "header", context);
                    int limit = optionalInteger(value, "sheetRowLimit", 1_048_576, context);
                    exports.add(new Export(type, name, query,
                            requiredPath(value, "file", context), name, null, sheet,
                            header == null || header, limit, overwrite,
                            null, null, null, false));
                }
                case "GPKG" -> {
                    String layer = optionalText(value, "layer");
                    if (layer == null || layer.isBlank()) {
                        layer = name;
                    }
                    identifier(layer, context);
                    String srs = optionalText(value, "srs");
                    exports.add(new Export(type, name, query,
                            requiredPath(value, "file", context), layer, srs,
                            "Sheet1", true, 1_048_576, overwrite,
                            null, null, null, false));
                }
                case "POSTGRES" -> {
                    String target = identifier(value, "target", context);
                    String table = text(value, "table", context);
                    String[] parts = table.split("\\.");
                    if (parts.length != 2 || !IDENTIFIER.matcher(parts[0]).matches()
                            || !IDENTIFIER.matcher(parts[1]).matches()) {
                        throw new DuckDbTaskException("DUCKDB_EXPORT",
                                msg(context, "task.duckdb.qualifiedTable", table),
                                "configuration", null);
                    }
                    String mode = optionalText(value, "mode");
                    if (mode == null || mode.isBlank()) {
                        mode = "append";
                    }
                    mode = mode.toLowerCase(Locale.ROOT);
                    if (!Set.of("append", "truncate", "replace").contains(mode)) {
                        throw new DuckDbTaskException("DUCKDB_EXPORT",
                                msg(context, "task.duckdb.writeMode", mode),
                                "configuration", null);
                    }
                    boolean create = Boolean.TRUE.equals(
                            optionalBoolean(value, "create", context));
                    if ("replace".equals(mode) && !create) {
                        throw new DuckDbTaskException("DUCKDB_EXPORT",
                                msg(context, "task.duckdb.replaceCreate"),
                                "configuration", null);
                    }
                    exports.add(new Export(type, name, query, null, null, null,
                            "Sheet1", true, 1_048_576, overwrite,
                            target, table, mode, create));
                }
                default -> throw new DuckDbTaskException("DUCKDB_EXPORT",
                        msg(context, "task.duckdb.exportType", type),
                        "configuration", null);
            }
        }
        return List.copyOf(exports);
    }

    private static String query(String query) {
        String result = query.trim();
        while (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    private static Path temporary(Path target) throws IOException {
        String name = target.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot);
        return target.getParent().resolve("." + name + "." + UUID.randomUUID()
                + ".tmp" + extension);
    }

    private static void dropObject(Connection connection, String object) throws SQLException {
        execute(connection, "DROP VIEW IF EXISTS " + object);
        execute(connection, "DROP TABLE IF EXISTS " + object);
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve primary failure.
        }
    }

    private static String identifier(String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid DuckDB identifier: " + value);
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String identifier(Map<String, Object> value, String key,
                                     OperationContext context) {
        String identifier = text(value, key, context);
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new DuckDbTaskException("DUCKDB_CONFIG",
                    msg(context, "task.duckdb.identifier", identifier),
                    "configuration", null);
        }
        return identifier;
    }

    private static void identifier(String value, OperationContext context) {
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new DuckDbTaskException("DUCKDB_CONFIG",
                    msg(context, "task.duckdb.identifier", value),
                    "configuration", null);
        }
    }

    private static String text(Map<String, Object> value, String key,
                               OperationContext context) {
        Object raw = value.get(key);
        if (!(raw instanceof CharSequence text) || text.toString().isBlank()) {
            throw new DuckDbTaskException("DUCKDB_CONFIG",
                    msg(context, "task.duckdb.fieldMissing", key),
                    "configuration", null);
        }
        return text.toString();
    }

    private static String optionalText(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof CharSequence text) || text.toString().isBlank()) {
            throw new IllegalArgumentException(key + " must be non-blank text");
        }
        return text.toString();
    }

    private static Path path(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Path path) {
            return path.toAbsolutePath().normalize();
        }
        if (raw instanceof CharSequence text) {
            return Path.of(text.toString()).toAbsolutePath().normalize();
        }
        throw new IllegalArgumentException(key + " must be a path");
    }

    private static Boolean optionalBoolean(Map<String, Object> value, String key,
                                           OperationContext context) {
        Object raw = value.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean flag) {
            return flag;
        }
        throw new DuckDbTaskException("DUCKDB_CONFIG",
                msg(context, "task.duckdb.boolean", key), "configuration", null);
    }

    private static int optionalInteger(Map<String, Object> value, String key,
                                       int fallback, OperationContext context) {
        Object raw = value.get(key);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            int result = number.intValue();
            if (result > 0) {
                return result;
            }
        }
        throw new DuckDbTaskException("DUCKDB_CONFIG",
                msg(context, "task.duckdb.integer", key), "configuration", null);
    }

    private static String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String msg(OperationContext context, String key, Object... args) {
        return Messages.format(context.locale(), key, args);
    }

    private static void logFailure(OperationContext context, DuckDbTaskException exception) {
        context.log().log(LogLevel.WARN, "gretl.duckdb.failed",
                msg(context, "task.duckdb.failed"),
                Map.of("duckdb.phase", exception.phase()), exception);
    }

    private static Path requiredPath(Map<String, Object> value, String key,
                                     OperationContext context) {
        Path path = path(value, key);
        if (path == null) {
            throw new DuckDbTaskException("DUCKDB_CONFIG",
                    msg(context, "task.duckdb.fieldMissing", key),
                    "configuration", null);
        }
        return path;
    }

    private record Table(String schema, String table, String alias,
                         boolean materialize) {
    }

    private record Source(
            String type, String alias, Path file, String table, String layer,
            boolean materialize, Boolean header, String delimiter, boolean allVarchar,
            String database, List<Table> tables) {
        List<String> extensions() {
            return switch (type) {
                case "GPKG" -> List.of("spatial");
                case "POSTGRES" -> List.of("postgres", "spatial");
                default -> List.of();
            };
        }
    }

    private record Target(String type, String alias, String database) {
        List<String> extensions() {
            return List.of("postgres");
        }
    }

    private record Export(
            String type, String name, String query, Path file, String layer,
            String srs, String sheet, boolean header, int sheetRowLimit,
            boolean overwrite, String target, String table, String mode,
            boolean create) {
        List<String> extensions() {
            return switch (type) {
                case "GPKG" -> List.of("spatial");
                case "XLSX" -> List.of("excel");
                case "POSTGRES" -> List.of("postgres");
                default -> List.of();
            };
        }
    }

    private record PendingExport(Path temp, Path target) {
    }

    private record PostgresParts(String host, int port, String database) {
        static PostgresParts parse(String jdbcUrl) {
            String prefix = "jdbc:postgresql://";
            if (jdbcUrl == null || !jdbcUrl.startsWith(prefix)) {
                throw new IllegalArgumentException(
                        "Only jdbc:postgresql:// URLs are supported: " + jdbcUrl);
            }
            String rest = jdbcUrl.substring(prefix.length());
            int query = rest.indexOf('?');
            if (query >= 0) {
                rest = rest.substring(0, query);
            }
            int slash = rest.indexOf('/');
            if (slash <= 0 || slash == rest.length() - 1) {
                throw new IllegalArgumentException(
                        "PostgreSQL URL must contain host and database: " + jdbcUrl);
            }
            String authority = rest.substring(0, slash);
            String database = rest.substring(slash + 1);
            String host = authority;
            int port = 5432;
            int colon = authority.lastIndexOf(':');
            if (colon > 0) {
                host = authority.substring(0, colon);
                port = Integer.parseInt(authority.substring(colon + 1));
            }
            return new PostgresParts(host, port, database);
        }
    }
}
