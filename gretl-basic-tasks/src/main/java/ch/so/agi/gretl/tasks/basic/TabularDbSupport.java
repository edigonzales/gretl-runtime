package ch.so.agi.gretl.tasks.basic;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared table metadata and identifier helpers for tabular JDBC tasks. */
final class TabularDbSupport {
    private TabularDbSupport() {
    }

    static String qualifiedTable(String schema, String table) {
        if (table == null || table.isBlank() || table.contains(";")
                || table.contains("--") || table.contains("/*") || table.contains("*/")
                || table.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("tableName is invalid");
        }
        if (schema == null || schema.isBlank()) {
            return table;
        }
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("schemaName is invalid");
        }
        return schema + "." + table;
    }

    static List<Column> columns(Connection connection, String table) throws SQLException {
        List<Column> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT * FROM " + table + " WHERE 0=1")) {
            var metadata = resultSet.getMetaData();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                columns.add(new Column(metadata.getColumnName(index),
                        metadata.getColumnType(index),
                        metadata.getColumnTypeName(index)));
            }
        }
        return List.copyOf(columns);
    }

    static Map<String, Column> byLowerName(List<Column> columns) {
        Map<String, Column> result = new LinkedHashMap<>();
        for (Column column : columns) {
            result.put(column.name().toLowerCase(Locale.ROOT), column);
        }
        return result;
    }

    record Column(String name, int type, String typeName) {
        Column(String name, int type) {
            this(name, type, "");
        }
    }
}
