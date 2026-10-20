package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.OperationContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** DuckDB spatial helpers for GeoPackage and Shapefile tasks. */
final class GeoFileSupport {
    private GeoFileSupport() {
    }

    static Connection openSpatial(OperationContext context) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:duckdb:");
        String directory = System.getProperty("duckdb.extensionDirectory",
                System.getenv("DUCKDB_EXTENSION_DIRECTORY"));
        try (Statement statement = connection.createStatement()) {
            if (directory != null && !directory.isBlank()) {
                statement.execute("SET extension_directory = " + literal(directory));
            }
            statement.execute("INSTALL spatial");
            statement.execute("LOAD spatial");
        } catch (SQLException exception) {
            connection.close();
            throw new DuckDbTaskException("GEO_EXTENSION",
                    Messages.format(context.locale(), "task.geo.extension"),
                    "extensions", exception);
        }
        return connection;
    }

    static List<String> gpkgLayers(Path gpkg) throws SQLException {
        List<String> layers = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + gpkg);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT table_name FROM gpkg_contents "
                             + "WHERE data_type IN ('features','attributes') "
                             + "ORDER BY table_name")) {
            while (rows.next()) {
                layers.add(rows.getString(1));
            }
        }
        return List.copyOf(layers);
    }

    static long count(Connection connection, Path file, String layer) throws SQLException {
        String sql = "SELECT count(*) FROM ST_Read(" + literal(file.toAbsolutePath().toString());
        if (layer != null && !layer.isBlank()) {
            sql += ", layer = " + literal(layer);
        }
        sql += ")";
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    static void copyToShapefile(Connection connection, Path source, String layer,
                                Path target) throws SQLException, IOException {
        Files.createDirectories(target.getParent());
        deleteSidecars(target);
        String sql = "COPY (SELECT * FROM ST_Read("
                + literal(source.toAbsolutePath().toString())
                + ", layer = " + literal(layer) + ")) TO "
                + literal(target.toAbsolutePath().toString())
                + " WITH (FORMAT GDAL, DRIVER 'ESRI Shapefile')";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    static void deleteSidecars(Path shapefile) throws IOException {
        String name = shapefile.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        for (String extension : List.of(".shp", ".shx", ".dbf", ".prj", ".cpg",
                ".qix", ".sbn", ".sbx", ".shp.xml")) {
            Files.deleteIfExists(shapefile.getParent().resolve(base + extension));
        }
    }

    static String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
