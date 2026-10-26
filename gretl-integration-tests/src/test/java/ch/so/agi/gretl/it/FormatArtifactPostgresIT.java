package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormatArtifactPostgresIT {
    @Test void originalTypedCsvBothBatchSizesAndExactExport() throws Exception {
        try (var lease = PostgresFixture.lease(); var db = connection(lease); var statement = db.createStatement()) {
            statement.execute("create table csv_import(t_id serial, \"Aint\" integer, adec decimal(7,1), atext varchar(40), aenum varchar(120), adate date, atimestamp timestamp, aboolean boolean, aextra varchar(40))");
            for (String batch : List.of("2000", "1")) {
                statement.execute("truncate csv_import restart identity");
                var imported = run(lease, "csv-import", Map.of("batch", batch)); assertEquals(0, imported.code(), imported.err());
                try (var rows = statement.executeQuery("select \"Aint\", adec, atext, aenum, adate, atimestamp, aboolean from csv_import where t_id=1")) {
                    assertTrue(rows.next()); assertEquals(2, rows.getInt(1)); assertEquals(new BigDecimal("3.1"), rows.getBigDecimal(2));
                    assertEquals("abc", rows.getString(3)); assertEquals("rot", rows.getString(4)); assertEquals(java.sql.Date.valueOf("2017-09-21"), rows.getDate(5));
                    assertEquals(java.sql.Timestamp.valueOf("2016-08-22 13:15:22.450"), rows.getTimestamp(6)); assertTrue(rows.getBoolean(7)); assertFalse(rows.next());
                }
            }
            statement.execute("create table csv_export(t_id serial, \"Aint\" integer, adec decimal(7,1), atext varchar(40), aenum varchar(120), adate date, atimestamp timestamp, aboolean boolean, aextra varchar(40))");
            statement.execute("insert into csv_export(t_id,\"Aint\",adec,atext,adate,atimestamp,aboolean) values(1,2,3.4,'abc','2013-10-21','2015-02-16T08:35:45.000',true),(2,null,null,null,null,null,null)");
            var exported = run(lease, "csv-export", Map.of()); assertEquals(0, exported.code(), exported.err());
            assertEquals(List.of("\"t_id\",\"Aint\",\"adec\",\"atext\",\"aenum\",\"adate\",\"atimestamp\",\"aboolean\"",
                    "\"1\",\"2\",\"3.4\",\"abc\",\"\",\"2013-10-21\",\"2015-02-16T08:35:45.000\",\"true\"",
                    "\"2\",\"\",\"\",\"\",\"\",\"\",\"\",\"\""), Files.readAllLines(exported.directory().resolve("output/data.csv")));
        }
    }
    @Test void originalShapefileImportAndIndependentExportAttributesGeometryAndCrs() throws Exception {
        try (var lease = PostgresFixture.lease(); var db = connection(lease); var statement = db.createStatement()) {
            statement.execute("create table shp_import(t_id serial, \"Aint\" integer, adec decimal(7,1), atext varchar(40), aenum varchar(120), adate date, geometrie geometry(Point,2056), aextra varchar(40))");
            var imported = run(lease, "shp-import", Map.of("batch", "1")); assertEquals(0, imported.code(), imported.err());
            try (var rows = statement.executeQuery("select \"Aint\",adec,atext,aenum,adate,ST_X(geometrie),ST_Y(geometrie),ST_SRID(geometrie) from shp_import where t_id=1")) {
                assertTrue(rows.next()); assertEquals(2, rows.getInt(1)); assertEquals(new BigDecimal("3.4"), rows.getBigDecimal(2)); assertEquals("abc", rows.getString(3)); assertEquals("rot", rows.getString(4));
                assertEquals(java.sql.Date.valueOf("2013-10-21"), rows.getDate(5)); assertEquals(2638000.0, rows.getDouble(6)); assertEquals(1175250.0, rows.getDouble(7)); assertEquals(2056, rows.getInt(8)); assertFalse(rows.next());
            }
            statement.execute("create table shp_export(t_id serial, \"Aint\" integer, adec decimal(7,1), atext varchar(40), aenum varchar(120), adate date, atimstamp timestamp, aboolean boolean, geom_so geometry(Point,2056))");
            statement.execute("insert into shp_export(t_id,\"Aint\",adec,atext,adate,atimstamp,aboolean,geom_so) values(1,2,3.4,'abc','2013-10-21','2015-02-16T08:35:45.000',true,ST_GeomFromText('POINT(2638000 1175250)',2056)),(2,null,null,null,null,null,null,null)");
            var exported = run(lease, "shp-export", Map.of()); assertEquals(0, exported.code(), exported.err()); Path output = exported.directory().resolve("output");
            var row = FileOracles.firstDbfRow(output.resolve("data.dbf"));
            assertEquals("2", row.get("Aint")); assertEquals("abc", row.get("atext")); assertEquals(new BigDecimal("3.4"), new BigDecimal(row.get("adec")));
            assertEquals("20131021", row.get("adate")); assertEquals("T", row.get("aboolean")); assertEquals("2015-02-16T08:35:45.000", row.get("atimstamp"));
            assertEquals(List.of(2638000.0, 1175250.0), FileOracles.firstPoint(output.resolve("data.shp"))); assertEquals(2, FileOracles.dbfCount(output.resolve("data.dbf")));
            for (int index = 1; index <= 2; index++) {
                statement.execute("create table exportdata" + index + "(attr varchar,the_geom geometry(Point,2056))");
                statement.execute("insert into exportdata" + index + " values('coord2d','0101000020080800001CD4411DD441CDBF0E69626CDD33E23F')");
            }
            for (String variant : List.of("single", "multi")) {
                var gpkg = run(lease, "gpkg-export", Map.of("variant", variant)); assertEquals(0, gpkg.code(), gpkg.err());
                try (var sqlite = DriverManager.getConnection("jdbc:sqlite:" + gpkg.directory().resolve("output/data.gpkg")); var query = sqlite.createStatement()) {
                    try (var rows = query.executeQuery("select count(*) from gpkg_contents")) { assertTrue(rows.next()); assertEquals(variant.equals("multi") ? 2 : 1, rows.getInt(1)); }
                    try (var rows = query.executeQuery("select distinct srs_id from gpkg_geometry_columns")) { assertTrue(rows.next()); assertEquals(2056, rows.getInt(1)); assertFalse(rows.next()); }
                    for (int index = 1; index <= (variant.equals("multi") ? 2 : 1); index++) {
                        try (var rows = query.executeQuery("select attr,the_geom from exportdata" + index)) {
                            assertTrue(rows.next()); assertEquals("coord2d", rows.getString(1));
                            assertEquals(List.of(-0.22857142857142854, 0.5688311688311687), FileOracles.geoPackagePoint(rows.getBytes(2)));
                            assertFalse(rows.next());
                        }
                    }
                }
            }
        }
    }
    @Test void originalGeoPackageImportCoordinatesAndJsonObjectAndArray() throws Exception {
        try (var lease = PostgresFixture.lease(); var db = connection(lease); var statement = db.createStatement()) {
            statement.execute("create table gpkg_import(fid integer,idname varchar,geom geometry(Point,2056))");
            var gpkg = run(lease, "gpkg-import", Map.of()); assertEquals(0, gpkg.code(), gpkg.err());
            try (var rows = statement.executeQuery("select fid,idname,ST_AsEWKT(geom) from gpkg_import")) {
                assertEquals(3, rows.getMetaData().getColumnCount()); assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); assertEquals("12", rows.getString(2));
                assertEquals("SRID=2056;POINT(-0.228571428571429 0.568831168831169)", rows.getString(3)); assertFalse(rows.next());
            }
            statement.execute("create table documents(id serial,document text)");
            var object = run(lease, "json-import", Map.of()); assertEquals(0, object.code(), object.err());
            try (var rows = statement.executeQuery("select (document::jsonb->'features'->1->>'id')::int,ROUND(ST_XMin(ST_SetSRID(ST_GeomFromGeoJSON(document::jsonb->'features'->1->'geometry'),2056))) from documents")) {
                assertTrue(rows.next()); assertEquals(821114211, rows.getInt(1)); assertEquals(2626724, rows.getInt(2)); assertFalse(rows.next());
            }
            statement.execute("truncate documents");
            var array = run(lease, "json-import", Map.of("variant", "array")); assertEquals(0, array.code(), array.err());
            try (var rows = statement.executeQuery("select document::jsonb->'surname' from documents order by id")) {
                assertTrue(rows.next()); assertEquals("\"Doe\"", rows.getString(1)); assertTrue(rows.next()); assertEquals("\"Doe\"", rows.getString(1)); assertFalse(rows.next());
            }
        }
    }
    private ArtifactJobRunner.Result run(PostgresFixture.Lease lease, String target, Map<String, String> extra) throws Exception {
        Path root = ArtifactJobRunner.directory("format-" + target); var config = JobRunner.baseConfig(root); JobRunner.secret(root, config, "dbPassword", lease.password());
        JobRunner.taskConfig(config, "db", Map.of("jdbcUrl", lease.jdbcUrl(), "user", lease.user(), "passwordSecret", "dbPassword"));
        var params = new LinkedHashMap<String, Object>(extra); params.put("output", root.resolve("output"));
        return ArtifactJobRunner.run(new ArtifactJobRunner.Request("format-" + target, ArtifactJobRunner.fixture("postgres-formats"), root, List.of(target), params, Map.of(), config,
                Map.of("postgres", new ArtifactJobRunner.Endpoint(lease.jdbcUrl(), lease.imageJdbcUrl())), Duration.ofSeconds(90)));
    }
    private Connection connection(PostgresFixture.Lease lease) throws Exception { return DriverManager.getConnection(lease.jdbcUrl(), lease.user(), lease.password()); }
}
