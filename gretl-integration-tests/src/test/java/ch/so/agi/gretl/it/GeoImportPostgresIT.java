package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
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
class GeoImportPostgresIT {
    @TempDir
    Path root;

    private PostgresFixture.Lease lease;

    @BeforeEach
    void setUp() throws Exception {
        lease = PostgresFixture.lease();
    }

    @AfterEach
    void tearDown() {
        if (lease != null) {
            lease.close();
        }
    }

    @Test
    void importsGpkgAndShapefileIntoPostgis() throws Exception {
        Path gpkg = root.resolve("source.gpkg");
        try (var connection = DriverManager.getConnection("jdbc:duckdb:");
             var statement = connection.createStatement()) {
            statement.execute("install spatial");
            statement.execute("load spatial");
            statement.execute("copy (select 1 as id, 'red' as name, "
                    + "ST_Point(8, 47) as geom) to '" + gpkg
                    + "' with (format gdal, driver 'GPKG', srs '4326')");
        }
        String layer;
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + gpkg);
             var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "select table_name from gpkg_contents where data_type='features'")) {
            rows.next();
            layer = rows.getString(1);
        }
        JobRunner.Result gpkgImport = JobRunner.run(root,
                "task('import', GpkgImport) {\n"
                        + "    dataFile = file('source.gpkg')\n"
                        + "    database = 'db'\n"
                        + "    srcTableName = '" + layer + "'\n"
                        + "    dstTableName = 'gpkg_points'\n"
                        + "}\n"
                        + "defaults 'import'\n", config());
        assertEquals(0, gpkgImport.code(), gpkgImport.err());
        assertEquals(1, scalar("select count(*) from gpkg_points"));
        assertEquals("POINT(8 47)", string(
                "select ST_AsText(geom) from gpkg_points"));

        Path shp = root.resolve("shape/points.shp");
        Files.createDirectories(shp.getParent());
        try (var connection = DriverManager.getConnection("jdbc:duckdb:");
             var statement = connection.createStatement()) {
            statement.execute("install spatial");
            statement.execute("load spatial");
            statement.execute("copy (select 2 as id, 'blue' as name, "
                    + "ST_Point(9, 48) as geom) to '" + shp
                    + "' with (format gdal, driver 'ESRI Shapefile')");
        }
        JobRunner.Result shpImport = JobRunner.run(root, """
                task('import', ShpImport) {
                    dataFile = file('shape/points.shp')
                    database = 'db'
                    tableName = 'shp_points'
                }
                defaults 'import'
                """, config());
        assertEquals(0, shpImport.code(), shpImport.err());
        assertEquals(1, scalar("select count(*) from shp_points"));
        assertEquals("POINT(9 48)", string("select ST_AsText(geom) from shp_points"));
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

    private int scalar(String sql) throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private String string(String sql) throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(lease.jdbcUrl(), lease.user(), lease.password());
    }
}
