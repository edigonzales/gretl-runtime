package ch.so.agi.gretl.internal.ioxwkf;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox_j.jts.Iox2jts;
import ch.interlis.iox_j.jts.Jts2iox;
import ch.interlis.ioxwkf.gpkg.GeoPackageReader;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateList;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import org.interlis2.av2geobau.impl.DxfUtil;
import org.interlis2.av2geobau.impl.DxfWriter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class Gpkg2DxfEngine {
    private static final Charset DXF_CHARSET = Charset.forName("ISO-8859-1");
    private static final String COORD = "COORD";
    private static final String MULTICOORD = "MULTICOORD";
    private static final String POLYLINE = "POLYLINE";
    private static final String MULTIPOLYLINE = "MULTIPOLYLINE";
    private static final String SURFACE = "SURFACE";
    private static final String MULTISURFACE = "MULTISURFACE";

    private final GeometryFactory geometryFactory = new GeometryFactory();

    public void convert(Gpkg2DxfRequest request) throws Exception {
        if (request.gpkgFile() == null || !Files.isRegularFile(request.gpkgFile())) {
            throw new IllegalArgumentException("dataFile must reference an existing GeoPackage");
        }
        if (request.outputDirectory() == null) {
            throw new IllegalArgumentException("outputDir must not be null");
        }
        Files.createDirectories(request.outputDirectory());

        for (DxfLayerInfo layerInfo : readLayerInfo(request.gpkgFile())) {
            writeDxf(request.gpkgFile(), request.outputDirectory(), layerInfo);
        }
    }

    private List<DxfLayerInfo> readLayerInfo(Path gpkgFile) throws Exception {
        String sql = """
                SELECT
                    table_prop.tablename,
                    gpkg_geometry_columns.column_name,
                    gpkg_geometry_columns.srs_id AS crs,
                    gpkg_geometry_columns.geometry_type_name AS geometry_type_name,
                    classname.IliName AS classname,
                    attrname.SqlName AS dxf_layer_attr
                FROM
                    T_ILI2DB_TABLE_PROP AS table_prop
                    LEFT JOIN gpkg_geometry_columns
                    ON table_prop.tablename = gpkg_geometry_columns.table_name
                    LEFT JOIN T_ILI2DB_CLASSNAME AS classname
                    ON table_prop.tablename = classname.SqlName
                    LEFT JOIN (
                        SELECT ilielement, attr_name, attr_value
                        FROM T_ILI2DB_META_ATTRS
                        WHERE attr_name = 'dxflayer'
                    ) AS meta_attrs
                    ON instr(meta_attrs.ilielement, classname.IliName) > 0
                    LEFT JOIN T_ILI2DB_ATTRNAME AS attrname
                    ON meta_attrs.ilielement = attrname.IliName
                WHERE
                    setting = 'CLASS'
                    AND column_name IS NOT NULL
                """;

        List<DxfLayerInfo> result = new ArrayList<>();
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + gpkgFile.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                result.add(new DxfLayerInfo(
                        rs.getString("tablename"),
                        rs.getString("column_name"),
                        rs.getInt("crs"),
                        rs.getString("geometry_type_name"),
                        rs.getString("classname"),
                        rs.getString("dxf_layer_attr")
                ));
            }
        }
        return result;
    }

    private void writeDxf(Path gpkgFile, Path outputDirectory, DxfLayerInfo layerInfo) throws Exception {
        Path dxfFile = outputDirectory.resolve(layerInfo.tableName() + ".dxf");
        GeoPackageReader reader = null;
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(dxfFile), DXF_CHARSET))) {
            reader = new GeoPackageReader(gpkgFile.toFile(), layerInfo.tableName());
            writeBlocks(writer);
            writer.write(DxfUtil.toString(0, "SECTION"));
            writer.write(DxfUtil.toString(2, "ENTITIES"));

            IoxEvent event = reader.read();
            while (event != null) {
                if (event instanceof ObjectEvent objectEvent) {
                    writeObject(writer, objectEvent.getIomObject(), layerInfo);
                }
                event = reader.read();
            }

            writer.write(DxfUtil.toString(0, "ENDSEC"));
            writer.write(DxfUtil.toString(0, "EOF"));
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private void writeObject(BufferedWriter writer, IomObject object, DxfLayerInfo layerInfo) throws Exception {
        IomObject geometry = object.getattrobj(layerInfo.geomColumnName(), 0);
        if (geometry == null) {
            return;
        }
        String layer = layerName(object, layerInfo.dxfLayerAttr());
        String tag = geometry.getobjecttag();
        if (MULTISURFACE.equals(tag)) {
            writeMultiPolygon(writer, object, layer, Iox2jts.multisurface2JTS(geometry, 0, layerInfo.crs()));
        } else if (SURFACE.equals(tag)) {
            writePolygon(writer, object, layer, Iox2jts.surface2JTS(geometry, 0));
        } else if (MULTIPOLYLINE.equals(tag)) {
            writeMultiLine(writer, object, layer, Iox2jts.multipolyline2JTS(geometry, 0));
        } else if (POLYLINE.equals(tag)) {
            CoordinateList coordinates = Iox2jts.polyline2JTS(geometry, false, 0);
            writeLine(writer, object, layer, geometryFactory.createLineString(coordinates.toCoordinateArray()));
        } else if (MULTICOORD.equals(tag)) {
            writeMultiPoint(writer, object, layer, Iox2jts.multicoord2JTS(geometry));
        } else if (COORD.equals(tag)) {
            Coordinate coordinate = Iox2jts.coord2JTS(geometry);
            writePoint(writer, object, layer, geometryFactory.createPoint(coordinate));
        }
    }

    private String layerName(IomObject object, String dxfLayerAttribute) {
        if (dxfLayerAttribute == null || dxfLayerAttribute.isBlank()) {
            return "default";
        }
        String value = object.getattrvalue(dxfLayerAttribute);
        if (value == null || value.isBlank()) {
            return "default";
        }
        return value.replaceAll("\\s+", "");
    }

    private void writeMultiPolygon(BufferedWriter writer, IomObject source, String layer, Geometry geometry) throws Exception {
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            writePolygon(writer, source, layer, (Polygon) geometry.getGeometryN(i));
        }
    }

    private void writePolygon(BufferedWriter writer, IomObject source, String layer, Polygon polygon) throws Exception {
        IomObject dxfObject = dxfObject(DxfWriter.IOM_2D_POLYGON, source, layer);
        dxfObject.addattrobj(DxfWriter.IOM_ATTR_GEOM, Jts2iox.JTS2surface(polygon));
        writer.write(DxfWriter.feature2Dxf(dxfObject));
    }

    private void writeMultiLine(BufferedWriter writer, IomObject source, String layer, Geometry geometry) throws Exception {
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            writeLine(writer, source, layer, (LineString) geometry.getGeometryN(i));
        }
    }

    private void writeLine(BufferedWriter writer, IomObject source, String layer, LineString line) throws Exception {
        IomObject dxfObject = dxfObject(DxfWriter.IOM_2D_POLYLINE, source, layer);
        dxfObject.addattrobj(DxfWriter.IOM_ATTR_GEOM, Jts2iox.JTS2polyline(line));
        writer.write(DxfWriter.feature2Dxf(dxfObject));
    }

    private void writeMultiPoint(BufferedWriter writer, IomObject source, String layer, Geometry geometry) throws Exception {
        for (int i = 0; i < geometry.getNumGeometries(); i++) {
            writePoint(writer, source, layer, (Point) geometry.getGeometryN(i));
        }
    }

    private void writePoint(BufferedWriter writer, IomObject source, String layer, Point point) throws Exception {
        IomObject dxfObject = dxfObject(DxfWriter.IOM_BLOCKINSERT, source, layer);
        dxfObject.setattrvalue(DxfWriter.IOM_ATTR_BLOCK, "GPBOL");
        dxfObject.addattrobj(DxfWriter.IOM_ATTR_GEOM, Jts2iox.JTS2coord(point.getCoordinate()));
        writer.write(DxfWriter.feature2Dxf(dxfObject));
    }

    private IomObject dxfObject(String tag, IomObject source, String layer) {
        IomObject dxfObject = new Iom_jObject(tag, null);
        dxfObject.setobjectoid(source.getobjectoid());
        dxfObject.setattrvalue(DxfWriter.IOM_ATTR_LAYERNAME, layer);
        return dxfObject;
    }

    private void writeBlocks(java.io.Writer writer) throws java.io.IOException {
        writer.write(DxfUtil.toString(0, "SECTION"));
        writer.write(DxfUtil.toString(2, "BLOCKS"));
        writer.write(DxfUtil.toString(0, "BLOCK"));
        writer.write(DxfUtil.toString(8, "0"));
        writer.write(DxfUtil.toString(70, "0"));
        writer.write(DxfUtil.toString(10, "0.0"));
        writer.write(DxfUtil.toString(20, "0.0"));
        writer.write(DxfUtil.toString(30, "0.0"));
        writer.write(DxfUtil.toString(2, "GPBOL"));
        writer.write(DxfUtil.toString(0, "CIRCLE"));
        writer.write(DxfUtil.toString(8, "0"));
        writer.write(DxfUtil.toString(10, "0.0"));
        writer.write(DxfUtil.toString(20, "0.0"));
        writer.write(DxfUtil.toString(30, "0.0"));
        writer.write(DxfUtil.toString(40, "0.5"));
        writer.write(DxfUtil.toString(0, "ENDBLK"));
        writer.write(DxfUtil.toString(8, "0"));
        writer.write(DxfUtil.toString(0, "ENDSEC"));
    }

    private record DxfLayerInfo(
            String tableName,
            String geomColumnName,
            int crs,
            String geometryTypeName,
            String className,
            String dxfLayerAttr) {
    }

    public record Gpkg2DxfRequest(Path gpkgFile, Path outputDirectory) {
    }
}
