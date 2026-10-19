package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.OperationContext;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared JDBC, SQL and identifier support for Db2Db. */
final class Db2DbSupport {
    private static final Pattern PARAMETER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_.-]*)}");

    private Db2DbSupport() {
    }

    static DatabaseConfig config(OperationContext context, String alias) {
        Map<String, Object> config = context.config(alias);
        Set<String> allowed = Set.of("jdbcUrl", "user", "passwordSecret");
        for (String key : config.keySet()) {
            if (!allowed.contains(key)) {
                throw new Db2DbTaskException("DB_CONFIG",
                        msg(context, "task.db2db.unknownField", key), "configuration", null);
            }
        }
        String jdbcUrl = requiredText(config, "jdbcUrl", context);
        String user = optionalText(config, "user", context);
        String password = null;
        Object passwordSecret = config.get("passwordSecret");
        if (passwordSecret != null) {
            if (!(passwordSecret instanceof CharSequence secretAlias)) {
                throw new Db2DbTaskException("DB_CONFIG",
                        msg(context, "task.db2db.passwordSecret"), "configuration", null);
            }
            password = context.sensitive(context.secret(secretAlias.toString()).reveal());
        }
        return new DatabaseConfig(alias, jdbcUrl, user, password);
    }

    static Connection open(DatabaseConfig config) throws SQLException {
        Connection connection = DriverManager.getConnection(
                config.jdbcUrl(), config.user(), config.password());
        connection.setAutoCommit(false);
        return connection;
    }

    static String substitute(String sql, Map<String, String> parameters,
                             OperationContext context) {
        Matcher matcher = PARAMETER.matcher(sql);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = parameters.get(name);
            if (value == null) {
                throw new Db2DbTaskException("DB_PARAMETER",
                        msg(context, "task.db2db.parameterMissing", name),
                        "prepare", null);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static List<String> split(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int index = 0;
        while (index < sql.length()) {
            char character = sql.charAt(index);
            if (character == '\'') {
                int end = copyQuoted(sql, index, '\'', current);
                index = end;
                continue;
            }
            if (character == '"') {
                int end = copyQuoted(sql, index, '"', current);
                index = end;
                continue;
            }
            if (character == '-' && index + 1 < sql.length()
                    && sql.charAt(index + 1) == '-') {
                int end = sql.indexOf('\n', index + 2);
                if (end < 0) {
                    current.append(sql, index, sql.length());
                    index = sql.length();
                } else {
                    current.append(sql, index, end + 1);
                    index = end + 1;
                }
                continue;
            }
            if (character == '/' && index + 1 < sql.length()
                    && sql.charAt(index + 1) == '*') {
                int end = sql.indexOf("*/", index + 2);
                if (end < 0) {
                    current.append(sql, index, sql.length());
                    index = sql.length();
                } else {
                    current.append(sql, index, end + 2);
                    index = end + 2;
                }
                continue;
            }
            if (character == '$') {
                int end = dollarQuoteEnd(sql, index);
                if (end > index) {
                    current.append(sql, index, end);
                    index = end;
                    continue;
                }
            }
            if (character == ';') {
                addStatement(statements, current);
                current.setLength(0);
                index++;
                continue;
            }
            current.append(character);
            index++;
        }
        addStatement(statements, current);
        return List.copyOf(statements);
    }

    static boolean isSetSearchPath(String statement) {
        return statement.stripLeading().toLowerCase(Locale.ROOT)
                .startsWith("set search_path");
    }

    static Map<String, String> targetColumns(Connection target, String targetTable)
            throws SQLException {
        Map<String, String> columns = new LinkedHashMap<>();
        try (Statement statement = target.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT * FROM " + targetTable + " WHERE 0=1")) {
            var metadata = resultSet.getMetaData();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                String name = metadata.getColumnName(index);
                columns.put(name.toLowerCase(Locale.ROOT), name);
            }
        }
        return columns;
    }

    static String quoteIdentifier(Connection connection, String identifier)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String quote = metadata.getIdentifierQuoteString();
        if (quote == null || quote.isBlank()) {
            return identifier;
        }
        return quote + identifier.replace(quote, quote + quote) + quote;
    }

    static void validateTargetTable(String targetTable, OperationContext context) {
        if (targetTable == null || targetTable.isBlank()
                || targetTable.contains(";") || targetTable.contains("--")
                || targetTable.contains("/*") || targetTable.contains("*/")
                || targetTable.indexOf('\0') >= 0) {
            throw new Db2DbTaskException("DB_TARGET",
                    msg(context, "task.db2db.targetTable"), "configuration", null);
        }
    }

    static GeometryTransform geometry(String definition, OperationContext context) {
        String[] parts = definition.split(":");
        if (parts.length != 3 || parts[0].isBlank()) {
            throw new Db2DbTaskException("DB_GEOMETRY",
                    msg(context, "task.db2db.geometry", definition), "configuration", null);
        }
        String column = parts[0].toUpperCase(Locale.ROOT);
        String type = parts[1].toUpperCase(Locale.ROOT);
        if (!Set.of("WKT", "WKB", "GEOJSON").contains(type)) {
            throw new Db2DbTaskException("DB_GEOMETRY",
                    msg(context, "task.db2db.geometry", definition), "configuration", null);
        }
        int srid;
        try {
            srid = Integer.parseInt(parts[2]);
        } catch (NumberFormatException exception) {
            throw new Db2DbTaskException("DB_GEOMETRY",
                    msg(context, "task.db2db.geometry", definition), "configuration", exception);
        }
        return new GeometryTransform(column, type, srid);
    }

    static String msg(OperationContext context, String key, Object... args) {
        return Messages.format(context.locale(), key, args);
    }

    private static int copyQuoted(String sql, int start, char quote, StringBuilder output) {
        int index = start;
        output.append(sql.charAt(index++));
        while (index < sql.length()) {
            char character = sql.charAt(index);
            output.append(character);
            index++;
            if (character == quote) {
                if (index < sql.length() && sql.charAt(index) == quote) {
                    output.append(sql.charAt(index++));
                } else {
                    break;
                }
            }
        }
        return index;
    }

    private static int dollarQuoteEnd(String sql, int start) {
        int tagEnd = start + 1;
        while (tagEnd < sql.length()) {
            char character = sql.charAt(tagEnd);
            if (character == '$') {
                break;
            }
            if (!(Character.isLetterOrDigit(character) || character == '_')) {
                return -1;
            }
            tagEnd++;
        }
        if (tagEnd >= sql.length() || sql.charAt(tagEnd) != '$') {
            return -1;
        }
        String tag = sql.substring(start, tagEnd + 1);
        int close = sql.indexOf(tag, tagEnd + 1);
        return close < 0 ? sql.length() : close + tag.length();
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().strip();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
    }

    private static String requiredText(Map<String, Object> config, String key,
                                       OperationContext context) {
        Object value = config.get(key);
        if (!(value instanceof CharSequence text) || text.toString().isBlank()) {
            throw new Db2DbTaskException("DB_CONFIG",
                    msg(context, "task.db2db.fieldMissing", key), "configuration", null);
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
            throw new Db2DbTaskException("DB_CONFIG",
                    msg(context, "task.db2db.fieldNotText", key), "configuration", null);
        }
        return text.toString();
    }

    record DatabaseConfig(String alias, String jdbcUrl, String user, String password) {
    }

    record GeometryTransform(String column, String type, int srid) {
        String valueExpression() {
            return switch (type) {
                case "WKT" -> "ST_GeomFromText(?, " + srid + ")";
                case "WKB" -> "ST_GeomFromWKB(?, " + srid + ")";
                case "GEOJSON" -> "ST_SetSRID(ST_GeomFromGeoJSON(?), " + srid + ")";
                default -> throw new IllegalArgumentException(
                        "Unsupported geometry standard: " + type);
            };
        }
    }
}
