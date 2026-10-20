package ch.so.agi.gretl.internal.ioxwkf;

import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.ioxwkf.gpkg.GeoPackageReader;
import ch.so.agi.gretl.internal.shapefile.ShapefileConstants;
import ch.so.agi.gretl.internal.shapefile.ShapefileDescriptorMapper;
import ch.so.agi.gretl.internal.shapefile.ShapefileIoxWriter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Gpkg2ShpEngine {
    public void convert(Gpkg2ShpRequest request) throws Exception {
        if (request.gpkgFile() == null || !Files.isRegularFile(request.gpkgFile())) {
            throw new IllegalArgumentException("dataFile must reference an existing GeoPackage");
        }
        if (request.outputDirectory() == null) {
            throw new IllegalArgumentException("outputDir must not be null");
        }
        Files.createDirectories(request.outputDirectory());

        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + request.gpkgFile().toAbsolutePath())) {
            DatabaseMetaData metaData = connection.getMetaData();
            for (String tableName : classTables(connection)) {
                GeometryInfo geometryInfo = geometryInfo(connection, tableName);
                Path target = request.outputDirectory().resolve(tableName + ".shp");
                ShapefileIoxWriter writer = new ShapefileIoxWriter(
                        target, StandardCharsets.UTF_8, Optional.of(ShapefileConstants.LV95_PRJ));
                writer.setAttributeDescriptors(new ShapefileDescriptorMapper().fromGeoPackage(
                        metaData,
                        tableName,
                        geometryInfo == null ? null : geometryInfo.columnName(),
                        geometryInfo == null ? null : geometryInfo.geometryType(),
                        geometryInfo == null ? null : geometryInfo.srid()));
                copyTable(request.gpkgFile(), tableName, writer);
            }
        }
    }

    private List<String> classTables(Connection connection) throws Exception {
        List<String> result = new ArrayList<>();
        String sql = "SELECT tablename FROM T_ILI2DB_TABLE_PROP WHERE setting = 'CLASS' ORDER BY tablename";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        }
        return result;
    }

    private GeometryInfo geometryInfo(Connection connection, String tableName) throws Exception {
        String sql = """
                SELECT column_name, geometry_type_name, srs_id
                FROM gpkg_geometry_columns
                WHERE lower(table_name) = lower('%s')
                """.formatted(tableName.replace("'", "''"));
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                return new GeometryInfo(rs.getString("column_name"),
                        rs.getString("geometry_type_name"),
                        rs.getInt("srs_id"));
            }
        }
        return null;
    }

    private void copyTable(Path gpkgFile, String tableName, ShapefileIoxWriter writer) throws Exception {
        GeoPackageReader reader = null;
        try {
            reader = new GeoPackageReader(gpkgFile.toFile(), tableName);
            IoxEvent event = reader.read();
            while (event != null) {
                if (event instanceof ObjectEvent) {
                    writer.write(event);
                }
                event = reader.read();
            }
            writer.write(new EndTransferEvent());
        } finally {
            if (reader != null) {
                reader.close();
            }
            writer.close();
        }
    }

    private record GeometryInfo(String columnName, String geometryType, Integer srid) {
    }

    public record Gpkg2ShpRequest(Path gpkgFile, Path outputDirectory) {
    }
}
