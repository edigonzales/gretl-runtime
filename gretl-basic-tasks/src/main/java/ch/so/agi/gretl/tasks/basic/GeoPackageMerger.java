package ch.so.agi.gretl.tasks.basic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Merge single-layer GeoPackages produced by GDAL into one multi-layer file. */
final class GeoPackageMerger {
    private GeoPackageMerger() {
    }

    static void merge(List<Path> layers, Path target) throws IOException, SQLException {
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("No GeoPackage layers");
        }
        Files.createDirectories(target.getParent());
        Files.deleteIfExists(target);
        Files.copy(layers.get(0), target, StandardCopyOption.REPLACE_EXISTING);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + target);
             Statement statement = connection.createStatement()) {
            for (int index = 1; index < layers.size(); index++) {
                Path sourceFile = layers.get(index);
                String alias = "src" + index;
                statement.execute("ATTACH DATABASE "
                        + GeoFileSupport.literal(sourceFile.toAbsolutePath().toString())
                        + " AS " + alias);
                try {
                    Layer layer = layer(statement, alias);
                    statement.execute("CREATE TABLE main."
                            + quote(layer.name()) + " AS SELECT * FROM " + alias + "."
                            + quote(layer.table()));
                    statement.executeUpdate("INSERT INTO main.gpkg_contents "
                            + "(table_name, data_type, identifier, description, last_change, "
                            + "min_x, min_y, max_x, max_y, srs_id) "
                            + "SELECT " + GeoFileSupport.literal(layer.name())
                            + ", data_type, " + GeoFileSupport.literal(layer.name())
                            + ", description, last_change, min_x, min_y, max_x, max_y, srs_id "
                            + "FROM " + alias + ".gpkg_contents WHERE table_name = "
                            + GeoFileSupport.literal(layer.table()));
                    statement.executeUpdate("INSERT INTO main.gpkg_geometry_columns "
                            + "(table_name, column_name, geometry_type_name, srs_id, z, m) "
                            + "SELECT " + GeoFileSupport.literal(layer.name())
                            + ", column_name, geometry_type_name, srs_id, z, m FROM "
                            + alias + ".gpkg_geometry_columns WHERE table_name = "
                            + GeoFileSupport.literal(layer.table()));
                    statement.executeUpdate("INSERT OR IGNORE INTO main.gpkg_spatial_ref_sys "
                            + "SELECT * FROM " + alias + ".gpkg_spatial_ref_sys");
                } finally {
                    statement.execute("DETACH DATABASE " + alias);
                }
            }
        }
    }

    private static Layer layer(Statement statement, String alias) throws SQLException {
        try (ResultSet rows = statement.executeQuery(
                "SELECT table_name FROM " + alias + ".gpkg_contents "
                        + "WHERE data_type IN ('features','attributes')")) {
            if (!rows.next()) {
                throw new SQLException("GeoPackage layer has no gpkg_contents entry");
            }
            String table = rows.getString(1);
            String name = table;
            try (ResultSet content = statement.executeQuery(
                    "SELECT identifier FROM " + alias + ".gpkg_contents "
                            + "WHERE table_name = " + GeoFileSupport.literal(table))) {
                if (content.next() && content.getString(1) != null) {
                    name = content.getString(1);
                }
            }
            return new Layer(table, name);
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record Layer(String table, String name) {
    }
}
