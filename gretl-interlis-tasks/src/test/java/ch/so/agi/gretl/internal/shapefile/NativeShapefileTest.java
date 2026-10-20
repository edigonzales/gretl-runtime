package ch.so.agi.gretl.internal.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox_j.ObjectEvent;
import ch.so.agi.gretl.internal.shapefile.core.DbfField;
import ch.so.agi.gretl.internal.shapefile.core.DbfFieldType;
import ch.so.agi.gretl.internal.shapefile.core.ShapeType;
import ch.so.agi.gretl.internal.shapefile.core.ShapeRecord;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeShapefileTest {
    @TempDir Path root;

    @Test
    void writesReadsAttributesAndSidecars() throws Exception {
        Path shp = root.resolve("values.shp");
        List<ShapefileAttributeDescriptor> descriptors = List.of(
                new ShapefileAttributeDescriptor("name", "NAME", DbfFieldType.CHARACTER,
                        20, 0, null, null, false),
                new ShapefileAttributeDescriptor("day", "DAY", DbfFieldType.DATE,
                        8, 0, null, null, false),
                new ShapefileAttributeDescriptor("number", "NUMBER", DbfFieldType.NUMERIC,
                        12, 2, null, null, false),
                new ShapefileAttributeDescriptor("the_geom", "the_geom", null, 0, 0,
                        ShapeType.POINT, 2056, false));
        ShapefileIoxWriter writer = new ShapefileIoxWriter(shp, StandardCharsets.UTF_8,
                Optional.empty());
        writer.setAttributeDescriptors(descriptors);
        Iom_jObject object = new Iom_jObject("Topic.Class1", "o1");
        object.setattrvalue("name", "Grün");
        object.setattrvalue("day", "2024-02-29");
        object.setattrvalue("number", "12.50");
        object.addattrobj("the_geom", point(8, 47));
        writer.write(new ObjectEvent(object));
        writer.write(new ch.interlis.iox_j.EndTransferEvent());
        writer.close();

        assertTrue(Files.isRegularFile(shp));
        assertTrue(Files.isRegularFile(root.resolve("values.shx")));
        assertTrue(Files.isRegularFile(root.resolve("values.dbf")));
        assertTrue(Files.isRegularFile(root.resolve("values.cpg")));
        assertEquals("UTF-8", Files.readString(root.resolve("values.cpg")).trim());
        assertFalse(Files.exists(root.resolve("values.shp.tmp")));

        ShapefileIoxReader reader = new ShapefileIoxReader(shp.toFile(),
                new ch.ehi.basics.settings.Settings());
        IomObject read = null;
        for (int i = 0; i < 20; i++) {
            IoxEvent event = reader.read();
            if (event == null) break;
            if (event instanceof ObjectEvent objectEvent) {
                read = objectEvent.getIomObject();
                break;
            }
        }
        reader.close();
        assertNotNull(read);
        assertEquals("Grün", read.getattrvalue("NAME"));
        assertEquals("2024-02-29", read.getattrvalue("DAY"));
        assertEquals("12.50", read.getattrvalue("NUMBER"));
        assertEquals("COORD", read.getattrobj("the_geom", 0).getobjecttag());
    }

    @Test
    void writesPolygonWithHole() throws Exception {
        Path shp = root.resolve("polygon.shp");
        ShapefileIoxWriter writer = new ShapefileIoxWriter(shp, StandardCharsets.UTF_8,
                Optional.empty());
        writer.setAttributeDescriptors(List.of(
                new ShapefileAttributeDescriptor("name", "NAME", DbfFieldType.CHARACTER,
                        10, 0, null, null, false),
                new ShapefileAttributeDescriptor("the_geom", "the_geom", null, 0, 0,
                        ShapeType.POLYGON, 2056, false)));
        Iom_jObject object = new Iom_jObject("Topic.Class1", "o1");
        object.setattrvalue("name", "poly");
        object.addattrobj("the_geom", polygon());
        writer.write(new ObjectEvent(object));
        writer.write(new ch.interlis.iox_j.EndTransferEvent());
        writer.close();

        ShapefileIoxReader reader = new ShapefileIoxReader(shp.toFile(),
                new ch.ehi.basics.settings.Settings());
        IomObject read = null;
        for (int i = 0; i < 20; i++) {
            IoxEvent event = reader.read();
            if (event instanceof ObjectEvent objectEvent) {
                read = objectEvent.getIomObject();
                break;
            }
        }
        reader.close();
        assertNotNull(read);
        assertNotNull(read.getattrobj("the_geom", 0));
    }

    @Test
    void writesMultipartPolylineAndPolygonWithHole() throws Exception {
        Path shp = root.resolve("multipart.shp");
        ShapefileIoxWriter writer = new ShapefileIoxWriter(shp, StandardCharsets.UTF_8,
                Optional.empty());
        writer.setAttributeDescriptors(List.of(
                new ShapefileAttributeDescriptor("name", "NAME", DbfFieldType.CHARACTER,
                        10, 0, null, null, false),
                new ShapefileAttributeDescriptor("the_geom", "the_geom", null, 0, 0,
                        ShapeType.POLYGON, 2056, false)));
        Iom_jObject object = new Iom_jObject("Topic.Class1", "o1");
        object.setattrvalue("name", "multi");
        object.addattrobj("the_geom", multiPolygon());
        writer.write(new ObjectEvent(object));
        writer.write(new ch.interlis.iox_j.EndTransferEvent());
        writer.close();

        ShapefileIoxReader reader = new ShapefileIoxReader(shp.toFile(),
                new ch.ehi.basics.settings.Settings());
        IomObject read = null;
        for (int i = 0; i < 20; i++) {
            IoxEvent event = reader.read();
            if (event instanceof ObjectEvent objectEvent) {
                read = objectEvent.getIomObject();
                break;
            }
        }
        reader.close();
        assertNotNull(read);
        IomObject geometry = read.getattrobj("the_geom", 0);
        assertNotNull(geometry);
        assertEquals("MULTISURFACE", geometry.getobjecttag());
    }

    @Test
    void writesMultiPointAndMultiLine() throws Exception {
        GeometryFactory factory = new GeometryFactory();
        String multiPoint = writeAndReadGeometry("multipoint.shp", ShapeType.MULTIPOINT,
                ch.interlis.iox_j.jts.Jts2iox.JTS2multicoord(new Coordinate[]{
                        new Coordinate(1, 2), new Coordinate(3, 4)}));
        assertEquals("MULTICOORD", multiPoint);
        String multiLine = writeAndReadGeometry("multiline.shp", ShapeType.POLYLINE,
                ch.interlis.iox_j.jts.Jts2iox.JTS2multipolyline(
                        factory.createMultiLineString(new com.vividsolutions.jts.geom.LineString[]{
                                factory.createLineString(new Coordinate[]{
                                        new Coordinate(0, 0), new Coordinate(1, 1)}),
                                factory.createLineString(new Coordinate[]{
                                        new Coordinate(2, 2), new Coordinate(3, 3)})})));
        assertEquals("MULTIPOLYLINE", multiLine);
    }

    @Test
    void rejectsZCoordinatesInWriter() throws Exception {
        ShapefileIoxWriter writer = new ShapefileIoxWriter(root.resolve("z.shp"),
                StandardCharsets.UTF_8, Optional.empty());
        writer.setAttributeDescriptors(List.of(
                new ShapefileAttributeDescriptor("the_geom", "the_geom", null, 0, 0,
                        ShapeType.POINT, 2056, false)));
        Iom_jObject object = new Iom_jObject("Topic.Class1", "o1");
        IomObject coord = new Iom_jObject("COORD", null);
        coord.setattrvalue("C1", "1");
        coord.setattrvalue("C2", "2");
        coord.setattrvalue("C3", "3");
        object.addattrobj("the_geom", coord);
        org.junit.jupiter.api.Assertions.assertThrows(ch.interlis.iox.IoxException.class,
                () -> writer.write(new ObjectEvent(object)));
        writer.close();
    }

    @Test
    void rejectsZShapeTypeInDecoder() {
        java.nio.ByteBuffer content = java.nio.ByteBuffer.allocate(4);
        content.putInt(11);
        content.flip();
        ShapeRecord record = new ShapeRecord(1, ShapeType.POINT_Z,
                content, null);
        org.junit.jupiter.api.Assertions.assertThrows(ShapefileMappingException.class,
                () -> new ch.so.agi.gretl.internal.shapefile.geom.ShpGeometryDecoder()
                        .decode(record));
        ShapeRecord m = new ShapeRecord(1, ShapeType.POINT_M,
                java.nio.ByteBuffer.allocate(4).putInt(21).flip(), null);
        org.junit.jupiter.api.Assertions.assertThrows(ShapefileMappingException.class,
                () -> new ch.so.agi.gretl.internal.shapefile.geom.ShpGeometryDecoder()
                        .decode(m));
    }

    private String writeAndReadGeometry(String name, ShapeType type, IomObject geometry)
            throws Exception {
        Path shp = root.resolve(name);
        ShapefileIoxWriter writer = new ShapefileIoxWriter(shp, StandardCharsets.UTF_8,
                Optional.empty());
        writer.setAttributeDescriptors(List.of(
                new ShapefileAttributeDescriptor("the_geom", "the_geom", null, 0, 0,
                        type, 2056, false)));
        Iom_jObject object = new Iom_jObject("Topic.Class1", "o1");
        object.addattrobj("the_geom", geometry);
        writer.write(new ObjectEvent(object));
        writer.write(new ch.interlis.iox_j.EndTransferEvent());
        writer.close();
        ShapefileIoxReader reader = new ShapefileIoxReader(shp.toFile(),
                new ch.ehi.basics.settings.Settings());
        IomObject read = null;
        for (int i = 0; i < 20; i++) {
            IoxEvent event = reader.read();
            if (event instanceof ObjectEvent objectEvent) {
                read = objectEvent.getIomObject();
                break;
            }
        }
        reader.close();
        assertNotNull(read);
        return read.getattrobj("the_geom", 0).getobjecttag();
    }

    private static IomObject point(double x, double y) {
        IomObject coord = new Iom_jObject("COORD", null);
        coord.setattrvalue("C1", Double.toString(x));
        coord.setattrvalue("C2", Double.toString(y));
        return coord;
    }

    private static IomObject multiPolygon() {
        GeometryFactory factory = new GeometryFactory();
        Polygon first = factory.createPolygon(factory.createLinearRing(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(0, 10), new Coordinate(10, 10),
                new Coordinate(10, 0), new Coordinate(0, 0)}));
        Polygon second = factory.createPolygon(factory.createLinearRing(new Coordinate[]{
                new Coordinate(20, 0), new Coordinate(20, 10), new Coordinate(30, 10),
                new Coordinate(30, 0), new Coordinate(20, 0)}));
        return ch.interlis.iox_j.jts.Jts2iox.JTS2multisurface(
                factory.createMultiPolygon(new Polygon[]{first, second}));
    }

    private static IomObject polygon() {
        GeometryFactory factory = new GeometryFactory();
        LinearRing shell = factory.createLinearRing(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(0, 10), new Coordinate(10, 10),
                new Coordinate(10, 0), new Coordinate(0, 0)});
        LinearRing hole = factory.createLinearRing(new Coordinate[]{
                new Coordinate(2, 2), new Coordinate(2, 4), new Coordinate(4, 4),
                new Coordinate(4, 2), new Coordinate(2, 2)});
        Polygon polygon = factory.createPolygon(shell, new LinearRing[]{hole});
        return ch.interlis.iox_j.jts.Jts2iox.JTS2surface(polygon);
    }
}
