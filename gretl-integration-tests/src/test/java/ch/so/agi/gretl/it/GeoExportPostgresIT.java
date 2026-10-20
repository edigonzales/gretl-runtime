package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(300)
class GeoExportPostgresIT {
    @TempDir
    Path root;

    private PostgresFixture.Lease lease;

    @BeforeEach
    void setUp() throws Exception {
        lease = PostgresFixture.lease();
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("create table export_points "
                    + "(id int primary key, name text, geom geometry(Point,4326))");
            statement.execute("insert into export_points values "
                    + "(1, 'red', ST_SetSRID(ST_Point(8, 47), 4326))");
        }
    }

    @AfterEach
    void tearDown() {
        if (lease != null) {
            lease.close();
        }
    }

    @Test
    void exportsGpkgAndShapefileFromPostgis() throws Exception {
        JobRunner.Result gpkg = JobRunner.run(root, """
                task('export', GpkgExport) {
                    database = 'db'
                    dataFile = file('export.gpkg')
                    srcTableNames = ['export_points']
                    dstTableNames = ['export_points']
                }
                defaults 'export'
                """, config());
        assertEquals(0, gpkg.code(), gpkg.err());
        Path gpkgFile = root.resolve("export.gpkg");
        assertTrue(Files.isRegularFile(gpkgFile));
        try (Connection duck = DriverManager.getConnection("jdbc:duckdb:");
             Statement statement = duck.createStatement()) {
            statement.execute("install spatial");
            statement.execute("load spatial");
            try (ResultSet rows = statement.executeQuery(
                    "select count(*) from ST_Read('" + gpkgFile + "')")) {
                rows.next();
                assertEquals(1, rows.getInt(1));
            }
        }

        JobRunner.Result shp = JobRunner.run(root, """
                task('export', ShpExport) {
                    database = 'db'
                    dataFile = file('export_points.shp')
                    tableName = 'export_points'
                }
                defaults 'export'
                """, config());
        assertEquals(0, shp.code(), shp.err());
        Path shapefile = root.resolve("export_points.shp");
        assertTrue(Files.isRegularFile(shapefile));
        assertTrue(Files.isRegularFile(root.resolve("export_points.shx")));
        assertTrue(Files.isRegularFile(root.resolve("export_points.dbf")));
    }

    @Test
    void exportsMultipleLayersIntoOneGeoPackage() throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("create table export_extra "
                    + "(id int primary key, label text, geom geometry(Point,4326))");
            statement.execute("insert into export_extra values "
                    + "(7, 'extra', ST_SetSRID(ST_Point(7, 46), 4326))");
        }
        JobRunner.Result result = JobRunner.run(root, """
                task('export', GpkgExport) {
                    database = 'db'
                    dataFile = file('multi.gpkg')
                    srcTableNames = ['export_points', 'export_extra']
                    dstTableNames = ['points', 'extra']
                }
                defaults 'export'
                """, config());
        assertEquals(0, result.code(), result.err());
        Path gpkg = root.resolve("multi.gpkg");
        assertTrue(Files.isRegularFile(gpkg));
        try (Connection duck = DriverManager.getConnection("jdbc:duckdb:");
             Statement statement = duck.createStatement()) {
            statement.execute("install spatial");
            statement.execute("load spatial");
            for (String layer : new String[]{"points", "extra"}) {
                try (ResultSet rows = statement.executeQuery(
                        "select count(*) from ST_Read('" + gpkg + "', layer='"
                                + layer + "')")) {
                    rows.next();
                    assertEquals(1, rows.getInt(1), layer);
                }
            }
        }
    }

    @Test
    void roundTripsNullAndUtf8AttributesThroughShapefile() throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("create table null_points "
                    + "(id int primary key, name text, geom geometry(Point,4326))");
            statement.execute("insert into null_points values "
                    + "(1, NULL, ST_SetSRID(ST_Point(8, 47), 4326)), "
                    + "(2, 'Grün', ST_SetSRID(ST_Point(9, 48), 4326))");
        }
        JobRunner.Result exported = JobRunner.run(root, """
                task('export', ShpExport) {
                    database = 'db'
                    dataFile = file('null_points.shp')
                    tableName = 'null_points'
                    encoding = 'UTF-8'
                }
                defaults 'export'
                """, config());
        assertEquals(0, exported.code(), exported.err());
        assertTrue(Files.isRegularFile(root.resolve("null_points.cpg")));

        JobRunner.Result imported = JobRunner.run(root, """
                task('import', ShpImport) {
                    dataFile = file('null_points.shp')
                    database = 'db'
                    tableName = 'null_points_roundtrip'
                }
                defaults 'import'
                """, config());
        assertEquals(0, imported.code(), imported.err());
        assertEquals(1, scalar("select count(*) from null_points_roundtrip "
                + "where name is null"));
        assertEquals(1, scalar("select count(*) from null_points_roundtrip "
                + "where name = 'Grün'"));
    }

    private int scalar(String sql) throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private Map<String, Object> config() throws Exception {
        Map<String, Object> config = JobRunner.baseConfig(root);
        JobRunner.secret(root, config, "dbPassword", lease.password());
        Map<String, Object> db = new LinkedHashMap<>();
        db.put("jdbcUrl", lease.jdbcUrl());
        db.put("user", lease.user());
        db.put("passwordSecret", "dbPassword");
        JobRunner.taskConfig(config, "db", db);
        return config;
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(lease.jdbcUrl(), lease.user(), lease.password());
    }
}
