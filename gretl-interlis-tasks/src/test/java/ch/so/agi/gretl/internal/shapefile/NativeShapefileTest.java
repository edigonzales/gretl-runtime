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

    private static IomObject point(double x, double y) {
        IomObject coord = new Iom_jObject("COORD", null);
        coord.setattrvalue("C1", Double.toString(x));
        coord.setattrvalue("C2", Double.toString(y));
        return coord;
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
