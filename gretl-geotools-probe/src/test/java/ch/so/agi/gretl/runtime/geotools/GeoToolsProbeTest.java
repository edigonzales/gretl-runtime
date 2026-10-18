package ch.so.agi.gretl.runtime.geotools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import groovy.lang.GroovySystem;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.geotools.api.data.FeatureWriter;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.data.Transaction;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.AttributeTypeBuilder;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

/**
 * Spec 07 section 7 / TC-G01..G04: isolated GeoTools 35.2 compatibility probe.
 * The additional GeoTools test dependencies are declared only in this profile
 * module and are not part of the standard distribution or task catalog.
 */
class GeoToolsProbeTest {

    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsPointShapefileWithExplicitSchemaCrsAndUmlauts() throws Exception {
        assertEquals("25", System.getProperty("java.specification.version"));
        assertEquals("6.0.0", GroovySystem.getVersion());

        CoordinateReferenceSystem lv95 = CRS.decode("EPSG:2056");
        assertEquals(CRS.AxisOrder.EAST_NORTH, CRS.getAxisOrder(lv95),
                "LV95 must be used as easting/northing");
        assertEquals(CRS.AxisOrder.EAST_NORTH, CRS.getAxisOrder(CRS.decode("EPSG:4326", true)),
                "geographic target must be used as longitude/latitude");

        SimpleFeatureType type = pointType(lv95);
        Path shp = tempDir.resolve("m0-points.shp");
        GeometryFactory geometryFactory = new GeometryFactory();

        ShapefileDataStore store = new ShapefileDataStore(shp.toUri().toURL());
        store.setCharset(StandardCharsets.UTF_8);
        store.setIndexCreationEnabled(false);
        try {
            store.createSchema(type);
            try (FeatureWriter<SimpleFeatureType, SimpleFeature> writer =
                         store.getFeatureWriterAppend(Transaction.AUTO_COMMIT)) {
                write(writer, geometryFactory, 2600000.125, 1200000.25,
                        "Äpfel & Öl", null, 12.5);
                write(writer, geometryFactory, 2600010.0, 1200000.0,
                        "Mühle", "gesetzt", 0.0);
                write(writer, geometryFactory, 2600010.0, 1200010.0,
                        "Test", null, -3.25);
            }
        } finally {
            store.dispose();
        }

        assertTrue(Files.isRegularFile(shp));
        assertTrue(Files.isRegularFile(tempDir.resolve("m0-points.shx")));
        assertTrue(Files.isRegularFile(tempDir.resolve("m0-points.dbf")));

        ShapefileDataStore readStore = new ShapefileDataStore(shp.toUri().toURL());
        readStore.setCharset(StandardCharsets.UTF_8);
        List<SimpleFeature> features = new ArrayList<>();
        try {
            SimpleFeatureType readType = readStore.getSchema();
            assertNotNull(readType.getCoordinateReferenceSystem());
            assertTrue(CRS.equalsIgnoreMetadata(lv95, readType.getCoordinateReferenceSystem()),
                    "CRS must survive the shapefile round trip");
            assertTrue(readType.getDescriptor("name").getType().getBinding() == String.class);
            assertTrue(readType.getDescriptor("value").getType().getBinding() == Double.class);

            SimpleFeatureSource source = readStore.getFeatureSource();
            try (SimpleFeatureIterator iterator = source.getFeatures().features()) {
                while (iterator.hasNext()) {
                    features.add(iterator.next());
                }
            }
        } finally {
            readStore.dispose();
        }

        assertEquals(3, features.size());
        assertPoint(features.get(0), 2600000.125, 1200000.25, "Äpfel & Öl", null, 12.5);
        assertPoint(features.get(1), 2600010.0, 1200000.0, "Mühle", "gesetzt", 0.0);
        assertPoint(features.get(2), 2600010.0, 1200010.0, "Test", null, -3.25);
        assertDbfFieldLengths(tempDir.resolve("m0-points.dbf"));

        // TC-G04: after disposing the stores, every output file is reopenable and removable.
        try (var files = Files.list(tempDir)) {
            List<Path> outputs = files.toList();
            assertFalse(outputs.isEmpty());
            for (Path output : outputs) {
                assertTrue(Files.isReadable(output), "reopenable after dispose: " + output);
            }
        }
        for (String suffix : List.of(".shp", ".shx", ".dbf", ".prj", ".cpg", ".fix", ".qix", ".shp.xml")) {
            Files.deleteIfExists(tempDir.resolve("m0-points" + suffix));
        }
        try (var files = Files.list(tempDir)) {
            assertEquals(List.of(), files.map(path -> path.getFileName().toString()).toList(),
                    "temporary shapefile resources were released");
        }
    }

    @Test
    void transformsIndependentControlPointWithExplicitLongitudeLatitudeAxisOrder() throws Exception {
        CoordinateReferenceSystem lv95 = CRS.decode("EPSG:2056");
        CoordinateReferenceSystem wgs84 = CRS.decode("EPSG:4326", true);
        MathTransform transform = CRS.findMathTransform(lv95, wgs84, true);

        GeometryFactory geometryFactory = new GeometryFactory();
        Point source = geometryFactory.createPoint(new Coordinate(2_700_000.0, 1_100_000.0));
        Point transformed = (Point) JTS.transform(source, transform);

        // swisstopo approximation reference, converted to decimal degrees:
        // E=2'700'000, N=1'100'000 -> 8.7304972222 E, 46.0441305556 N.
        assertEquals(8.7304972222, transformed.getX(), 0.00005,
                "longitude must not be swapped with latitude");
        assertEquals(46.0441305556, transformed.getY(), 0.00005,
                "latitude must not be swapped with longitude");
    }

    @Test
    void writesGeographicShapefileWithExplicitGeographicCrs() throws Exception {
        CoordinateReferenceSystem wgs84 = CRS.decode("EPSG:4326", true);
        assertEquals(CRS.AxisOrder.EAST_NORTH, CRS.getAxisOrder(wgs84));
        SimpleFeatureType type = pointType(wgs84);
        Path shp = tempDir.resolve("geographic.shp");

        ShapefileDataStore store = new ShapefileDataStore(shp.toUri().toURL());
        store.setCharset(StandardCharsets.UTF_8);
        store.setIndexCreationEnabled(false);
        try {
            store.createSchema(type);
            try (FeatureWriter<SimpleFeatureType, SimpleFeature> writer =
                         store.getFeatureWriterAppend(Transaction.AUTO_COMMIT)) {
                write(writer, new GeometryFactory(), 8.7304972222, 46.0441305556,
                        "Kontrollpunkt", null, 1.0);
            }
        } finally {
            store.dispose();
        }

        ShapefileDataStore readStore = new ShapefileDataStore(shp.toUri().toURL());
        readStore.setCharset(StandardCharsets.UTF_8);
        try {
            SimpleFeatureType readType = readStore.getSchema();
            assertNotNull(readType.getCoordinateReferenceSystem());
            assertTrue(CRS.equalsIgnoreMetadata(wgs84, readType.getCoordinateReferenceSystem()));
            assertEquals(CRS.AxisOrder.EAST_NORTH,
                    CRS.getAxisOrder(readType.getCoordinateReferenceSystem()),
                    "geographic data must not be silently relabelled as LV95");
            try (SimpleFeatureIterator iterator = readStore.getFeatureSource().getFeatures().features()) {
                assertTrue(iterator.hasNext());
                assertPoint(iterator.next(), 8.7304972222, 46.0441305556,
                        "Kontrollpunkt", null, 1.0);
            }
        } finally {
            readStore.dispose();
        }
    }

    @Test
    void missingEpsgProviderFailsVisiblyInNegativeClasspath() throws Exception {
        String filteredClasspath = java.util.Arrays.stream(
                        System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                .filter(entry -> !entry.contains("gt-epsg"))
                .filter(entry -> !entry.toLowerCase(java.util.Locale.ROOT).contains("hsqldb"))
                .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));

        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", filteredClasspath,
                NegativeCrsProbe.class.getName(), "EPSG:2056");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        assertEquals(0, exit, "negative CRS process output:\n" + output);
        assertTrue(output.contains("No code") || output.contains("NoSuchAuthorityCodeException"),
                "missing EPSG provider must fail visibly, not silently fall back:\n" + output);
    }

    private static SimpleFeatureType pointType(CoordinateReferenceSystem crs) {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("gretl_m0_points");
        builder.setCRS(crs);
        builder.add("the_geom", Point.class);
        addString(builder, "name", 50);
        addString(builder, "note", 20);
        builder.add("value", Double.class);
        builder.setDefaultGeometry("the_geom");
        return builder.buildFeatureType();
    }

    private static void addString(SimpleFeatureTypeBuilder builder, String name, int length) {
        AttributeTypeBuilder attribute = new AttributeTypeBuilder();
        attribute.setBinding(String.class);
        attribute.setLength(length);
        builder.add(attribute.buildDescriptor(name));
    }

    private static void write(FeatureWriter<SimpleFeatureType, SimpleFeature> writer,
                              GeometryFactory geometryFactory, double x, double y,
                              String name, String note, double value) throws Exception {
        SimpleFeature feature = writer.next();
        feature.setAttribute("the_geom", geometryFactory.createPoint(new Coordinate(x, y)));
        feature.setAttribute("name", name);
        feature.setAttribute("note", note);
        feature.setAttribute("value", value);
        writer.write();
    }

    private static void assertPoint(SimpleFeature feature, double x, double y,
                                    String name, String note, double value) {
        SimpleFeatureType type = feature.getFeatureType();
        assertNotNull(type);
        Point point = (Point) feature.getDefaultGeometry();
        assertNotNull(point);
        assertEquals(x, point.getX(), 0.0);
        assertEquals(y, point.getY(), 0.0);
        assertEquals(name, feature.getAttribute("name"));
        assertEquals(note, feature.getAttribute("note"));
        assertEquals(value, ((Number) feature.getAttribute("value")).doubleValue(), 0.0);
    }

    /** Verifies the explicitly requested DBF field widths, independent of GeoTools metadata. */
    private static void assertDbfFieldLengths(Path dbf) throws Exception {
        byte[] bytes = Files.readAllBytes(dbf);
        int headerLength = (bytes[8] & 0xff) | ((bytes[9] & 0xff) << 8);
        int fields = (headerLength - 33) / 32;
        java.util.Map<String, Integer> lengths = new java.util.HashMap<>();
        for (int index = 0; index < fields; index++) {
            int offset = 32 + index * 32;
            int end = offset;
            while (end < offset + 11 && bytes[end] != 0) {
                end++;
            }
            String name = new String(bytes, offset, end - offset, StandardCharsets.US_ASCII)
                    .toLowerCase(java.util.Locale.ROOT);
            lengths.put(name, bytes[offset + 16] & 0xff);
        }
        assertEquals(50, lengths.get("name"), "DBF field width for name: " + lengths);
        assertEquals(20, lengths.get("note"), "DBF field width for note: " + lengths);
    }
}
