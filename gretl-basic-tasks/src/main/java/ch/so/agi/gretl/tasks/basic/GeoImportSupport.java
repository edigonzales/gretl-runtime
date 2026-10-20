package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.OperationContext;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Import GPKG/Shapefile rows into a JDBC table via DuckDB spatial. */
final class GeoImportSupport {
    private GeoImportSupport() {
    }

    static long importFile(OperationContext context, String databaseAlias,
                           Path file, String layer, String schemaName, String tableName,
                           int batchSize, String encoding) {
        String table = TabularDbSupport.qualifiedTable(schemaName, tableName);
        Db2DbSupport.DatabaseConfig config = Db2DbSupport.config(context, databaseAlias);
        try (Connection duck = GeoFileSupport.openSpatial(context);
             Connection target = Db2DbSupport.open(config)) {
            List<Column> sourceColumns = sourceColumns(duck, file, layer);
            String geometryColumn = sourceColumns.stream()
                    .filter(column -> "GEOMETRY".equalsIgnoreCase(column.typeName())
                            || column.typeName().toUpperCase(Locale.ROOT).contains("GEOM")
                            || "geom".equalsIgnoreCase(column.name()))
                    .map(Column::name).findFirst().orElse(null);
            List<TabularDbSupport.Column> targetColumns = ensureTargetColumns(target, table,
                    sourceColumns, geometryColumn, context);
            Map<String, TabularDbSupport.Column> targetByName = new LinkedHashMap<>();
            for (TabularDbSupport.Column column : targetColumns) {
                targetByName.put(column.name().toLowerCase(Locale.ROOT), column);
            }
            List<TabularDbSupport.Column> attributes = new ArrayList<>();
            for (Column source : sourceColumns) {
                if (!source.name().equalsIgnoreCase(geometryColumn)) {
                    TabularDbSupport.Column targetColumn = targetByName.get(
                            source.name().toLowerCase(Locale.ROOT));
                    if (targetColumn == null) {
                        throw new TabularTaskException("GEO_COLUMN",
                                msg(context, "task.geo.column", source.name(), table),
                                "prepare", null);
                    }
                    attributes.add(targetColumn);
                }
            }
            TabularDbSupport.Column targetGeometry = geometryColumn == null ? null
                    : targetByName.get(geometryColumn.toLowerCase(Locale.ROOT));
            if (geometryColumn != null && targetGeometry == null) {
                throw new TabularTaskException("GEO_COLUMN",
                        msg(context, "task.geo.column", geometryColumn, table),
                        "prepare", null);
            }
            String select = selectSql(file, layer, sourceColumns, geometryColumn);
            String insertSql = insertSql(target, table, attributes, targetGeometry);
            long count = 0;
            try (Statement selectStatement = duck.createStatement();
                 ResultSet rows = selectStatement.executeQuery(select);
                 PreparedStatement insert = target.prepareStatement(insertSql)) {
                while (rows.next()) {
                    int parameter = 1;
                    int resultIndex = geometryColumn == null ? 1 : 2;
                    for (TabularDbSupport.Column attribute : attributes) {
                        insert.setObject(parameter++, rows.getObject(resultIndex++));
                    }
                    if (targetGeometry != null) {
                        insert.setString(parameter, rows.getString(1));
                    }
                    insert.addBatch();
                    count++;
                    if (count % batchSize == 0) {
                        insert.executeBatch();
                    }
                }
                insert.executeBatch();
            }
            target.commit();
            return count;
        } catch (TabularTaskException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TabularTaskException("GEO_IMPORT",
                    msg(context, "task.geo.import.failed"), "import", exception);
        }
    }

    private static List<Column> sourceColumns(Connection duck, Path file, String layer)
            throws SQLException {
        String sql = "SELECT * FROM ST_Read(" + GeoFileSupport.literal(
                file.toAbsolutePath().toString());
        if (layer != null && !layer.isBlank()) {
            sql += ", layer = " + GeoFileSupport.literal(layer);
        }
        sql += ") LIMIT 0";
        try (Statement statement = duck.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            ResultSetMetaData metadata = rows.getMetaData();
            List<Column> columns = new ArrayList<>();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                columns.add(new Column(metadata.getColumnName(index),
                        metadata.getColumnTypeName(index)));
            }
            return List.copyOf(columns);
        }
    }

    private static List<TabularDbSupport.Column> ensureTargetColumns(
            Connection target, String table, List<Column> sourceColumns,
            String geometryColumn, OperationContext context) throws SQLException {
        java.sql.Savepoint savepoint = target.setSavepoint();
        try {
            return TabularDbSupport.columns(target, table);
        } catch (SQLException missing) {
            target.rollback(savepoint);
            List<String> definitions = new ArrayList<>();
            for (Column source : sourceColumns) {
                String type = source.name().equalsIgnoreCase(geometryColumn)
                        ? "geometry" : postgresType(source.typeName());
                definitions.add(Db2DbSupport.quoteIdentifier(target, source.name())
                        + " " + type);
            }
            try (Statement statement = target.createStatement()) {
                statement.execute("CREATE TABLE " + table + " ("
                        + String.join(", ", definitions) + ")");
            }
            return TabularDbSupport.columns(target, table);
        }
    }

    private static String selectSql(Path file, String layer, List<Column> columns,
                                    String geometryColumn) {
        StringBuilder attributes = new StringBuilder();
        for (Column column : columns) {
            if (!column.name().equalsIgnoreCase(geometryColumn)) {
                if (attributes.length() > 0) {
                    attributes.append(", ");
                }
                attributes.append('"').append(column.name().replace("\"", "\"\"")).append('"');
            }
        }
        String source = "ST_Read(" + GeoFileSupport.literal(file.toAbsolutePath().toString());
        if (layer != null && !layer.isBlank()) {
            source += ", layer = " + GeoFileSupport.literal(layer);
        }
        source += ")";
        if (geometryColumn == null) {
            return "SELECT " + attributes + " FROM " + source;
        }
        String geometry = "ST_AsText(\"" + geometryColumn.replace("\"", "\"\"")
                + "\") AS __gretl_wkt";
        return "SELECT " + geometry
                + (attributes.length() == 0 ? "" : ", " + attributes)
                + " FROM " + source;
    }

    private static String insertSql(Connection target, String table,
                                    List<TabularDbSupport.Column> attributes,
                                    TabularDbSupport.Column geometry)
            throws SQLException {
        List<String> names = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (TabularDbSupport.Column column : attributes) {
            names.add(Db2DbSupport.quoteIdentifier(target, column.name()));
            values.add("?");
        }
        if (geometry != null) {
            names.add(Db2DbSupport.quoteIdentifier(target, geometry.name()));
            values.add("ST_GeomFromText(?)");
        }
        return "INSERT INTO " + table + " (" + String.join(", ", names)
                + ") VALUES (" + String.join(", ", values) + ")";
    }

    private static int indexOf(List<Column> columns, String name) {
        for (int index = 0; index < columns.size(); index++) {
            if (columns.get(index).name().equalsIgnoreCase(name)) {
                return index;
            }
        }
        throw new IllegalArgumentException("Column not found: " + name);
    }

    private static String postgresType(String typeName) {
        return switch (typeName.toUpperCase(Locale.ROOT)) {
            case "TINYINT", "SMALLINT", "INTEGER" -> "integer";
            case "BIGINT" -> "bigint";
            case "REAL", "FLOAT", "DOUBLE" -> "double precision";
            case "BOOLEAN" -> "boolean";
            case "DATE" -> "date";
            case "TIMESTAMP" -> "timestamp";
            default -> "text";
        };
    }

    private static String msg(OperationContext context, String key, Object... args) {
        return Messages.format(context.locale(), key, args);
    }

    private record Column(String name, String typeName) {
    }
}
