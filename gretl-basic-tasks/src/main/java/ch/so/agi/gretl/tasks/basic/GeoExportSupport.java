package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.OperationContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Export JDBC geometry tables to GeoPackage/Shapefile via DuckDB GDAL. */
final class GeoExportSupport {
    private GeoExportSupport() {
    }

    static long export(OperationContext context, String databaseAlias, String schemaName,
                       String tableName, Path target, boolean gpkg, String layer,
                       String encoding) {
        String table = TabularDbSupport.qualifiedTable(schemaName, tableName);
        Db2DbSupport.DatabaseConfig config = Db2DbSupport.config(context, databaseAlias);
        try (Connection source = Db2DbSupport.open(config);
             Connection duck = GeoFileSupport.openSpatial(context)) {
            List<TabularDbSupport.Column> columns = TabularDbSupport.columns(source, table);
            TabularDbSupport.Column geometry = columns.stream()
                    .filter(column -> "geom".equalsIgnoreCase(column.name())
                            || column.typeName().toUpperCase(Locale.ROOT).contains("GEOM"))
                    .findFirst().orElseThrow(() -> new TabularTaskException(
                            "GEO_GEOMETRY", msg(context, "task.geo.geometry", table),
                            "prepare", null));
            List<TabularDbSupport.Column> attributes = new ArrayList<>();
            for (TabularDbSupport.Column column : columns) {
                if (!column.name().equalsIgnoreCase(geometry.name())) {
                    attributes.add(column);
                }
            }
            String select = selectSql(source, table, attributes, geometry);
            createTempTable(duck, attributes);
            long rows = copyRows(duck, source, select, attributes);
            Files.createDirectories(target.toAbsolutePath().normalize().getParent());
            if (!gpkg) {
                GeoFileSupport.deleteSidecars(target);
            } else {
                Files.deleteIfExists(target);
            }
            String sql = "COPY (SELECT "
                    + selectColumns(attributes)
                    + (attributes.isEmpty() ? "" : ", ")
                    + "ST_GeomFromText(__gretl_wkt) AS \"geom\" FROM __gretl_export) TO "
                    + GeoFileSupport.literal(target.toAbsolutePath().normalize().toString())
                    + " WITH (FORMAT GDAL, DRIVER '"
                    + (gpkg ? "GPKG" : "ESRI Shapefile") + "'";
            sql += ")";
            try (Statement statement = duck.createStatement()) {
                statement.execute(sql);
            }
            if (gpkg && layer != null && !layer.isBlank()) {
                renameLayer(target, layer);
            }
            if (!gpkg && encoding != null && !encoding.isBlank()) {
                Path cpg = GeoFileSupport.sibling(target, ".cpg");
                Files.writeString(cpg, "ISO-8859-1");
            }
            return rows;
        } catch (TabularTaskException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TabularTaskException("GEO_EXPORT",
                    msg(context, "task.geo.export.failed"), "export", exception);
        }
    }

    private static void renameLayer(Path gpkg, String layer) throws SQLException {
        if (!layer.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new SQLException("Invalid GeoPackage layer name: " + layer);
        }
        try (Connection sqlite = java.sql.DriverManager.getConnection("jdbc:sqlite:" + gpkg);
             Statement statement = sqlite.createStatement()) {
            String actual;
            try (ResultSet rows = statement.executeQuery(
                    "SELECT table_name FROM gpkg_contents "
                            + "WHERE data_type IN ('features','attributes')")) {
                if (!rows.next()) {
                    return;
                }
                actual = rows.getString(1);
                if (rows.next()) {
                    throw new SQLException(
                            "GeoPackage contains more than one layer; rename is ambiguous");
                }
            }
            if (!actual.equals(layer)) {
                statement.execute("ALTER TABLE \"" + actual + "\" RENAME TO \""
                        + layer + "\"");
                statement.executeUpdate("UPDATE gpkg_contents SET table_name = '"
                        + layer + "', identifier = '" + layer
                        + "' WHERE table_name = '" + actual + "'");
                statement.executeUpdate("UPDATE gpkg_geometry_columns SET table_name = '"
                        + layer + "' WHERE table_name = '" + actual + "'");
            }
        }
    }

    private static String selectSql(Connection source, String table,
                                    List<TabularDbSupport.Column> attributes,
                                    TabularDbSupport.Column geometry) throws SQLException {
        List<String> names = new ArrayList<>();
        for (TabularDbSupport.Column attribute : attributes) {
            names.add(Db2DbSupport.quoteIdentifier(source, attribute.name()));
        }
        return "SELECT ST_AsText(" + Db2DbSupport.quoteIdentifier(source, geometry.name())
                + ") AS __gretl_wkt"
                + (names.isEmpty() ? "" : ", " + String.join(", ", names))
                + " FROM " + table;
    }

    private static String selectColumns(List<TabularDbSupport.Column> attributes) {
        List<String> names = new ArrayList<>();
        for (TabularDbSupport.Column attribute : attributes) {
            names.add("\"" + attribute.name().replace("\"", "\"\"") + "\"");
        }
        return String.join(", ", names);
    }

    private static void createTempTable(Connection duck,
                                        List<TabularDbSupport.Column> attributes)
            throws SQLException {
        List<String> definitions = new ArrayList<>();
        definitions.add("\"__gretl_wkt\" VARCHAR");
        for (TabularDbSupport.Column attribute : attributes) {
            definitions.add("\"" + attribute.name().replace("\"", "\"\"") + "\" "
                    + duckType(attribute.type()));
        }
        try (Statement statement = duck.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS __gretl_export");
            statement.execute("CREATE TABLE __gretl_export ("
                    + String.join(", ", definitions) + ")");
        }
    }

    private static long copyRows(Connection duck, Connection source, String select,
                                 List<TabularDbSupport.Column> attributes)
            throws SQLException {
        String insert = "INSERT INTO __gretl_export VALUES ("
                + String.join(", ", java.util.Collections.nCopies(
                        attributes.size() + 1, "?")) + ")";
        long rows = 0;
        try (Statement statement = source.createStatement();
             ResultSet result = statement.executeQuery(select);
             PreparedStatement prepared = duck.prepareStatement(insert)) {
            while (result.next()) {
                prepared.setString(1, result.getString(1));
                for (int index = 0; index < attributes.size(); index++) {
                    prepared.setObject(index + 2, result.getObject(index + 2));
                }
                prepared.addBatch();
                rows++;
                if (rows % 5000 == 0) {
                    prepared.executeBatch();
                }
            }
            prepared.executeBatch();
        }
        return rows;
    }

    private static String duckType(int type) {
        return switch (type) {
            case Types.INTEGER -> "INTEGER";
            case Types.BIGINT -> "BIGINT";
            case Types.SMALLINT, Types.TINYINT -> "SMALLINT";
            case Types.REAL, Types.FLOAT, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> "DOUBLE";
            case Types.BOOLEAN, Types.BIT -> "BOOLEAN";
            case Types.DATE -> "DATE";
            case Types.TIME -> "TIME";
            case Types.TIMESTAMP -> "TIMESTAMP";
            default -> "VARCHAR";
        };
    }

    private static String msg(OperationContext context, String key, Object... args) {
        return Messages.format(context.locale(), key, args);
    }
}
