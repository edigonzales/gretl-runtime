package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
class FormatPostgresIT {
    @TempDir Path root;
    private PostgresFixture.Lease lease;

    @BeforeEach
    void setUp() throws Exception {
        lease = PostgresFixture.lease();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("create table shp_points "
                    + "(id int primary key, name text, geom geometry(Point,2056))");
            statement.execute("insert into shp_points values "
                    + "(1, 'Grün', ST_SetSRID(ST_Point(2600000, 1200000), 2056))");
            statement.execute("create table shp_points_import "
                    + "(id int primary key, name text, geom geometry(Point,2056))");
            statement.execute("create table shp_points2 "
                    + "(id int primary key, name text, geom geometry(Point,2056))");
            statement.execute("insert into shp_points2 values "
                    + "(2, 'blau', ST_SetSRID(ST_Point(2600010, 1200010), 2056))");
            statement.execute("create table csv_colors (id int primary key, name text)");
            statement.execute("create table documents (document text)");
            statement.execute("create schema if not exists ili2pgtest");
            statement.execute("create table shp_multipolygons "
                    + "(id int primary key, geom geometry(MultiPolygon,2056))");
            statement.execute("insert into shp_multipolygons values (1, ST_Multi("
                    + "ST_GeomFromText('POLYGON((0 0,0 10,10 10,10 0,0 0),"
                    + "(2 2,2 4,4 4,4 2,2 2))',2056)))");
            statement.execute("create table shp_multipolygons_import "
                    + "(id int primary key, geom geometry(MultiPolygon,2056))");
            statement.execute("create table gpkg_multi_import "
                    + "(id int primary key, geom geometry(MultiPolygon,2056))");
            statement.execute("create table shp_z "
                    + "(id int primary key, geom geometry(PointZ,2056))");
            statement.execute("create table gpkg_z_import "
                    + "(id int primary key, geom geometry(PointZ,2056))");
            statement.execute("insert into shp_z values (1, "
                    + "ST_GeomFromText('POINT Z(2600000 1200000 500)',2056))");
            statement.execute("create table gpkg_attributes ("
                    + "fid bigint, geom geometry(Point,2056), t_id bigint, t_ili_tid text, "
                    + "objektname text, orientierung numeric, hali text, vali text, "
                    + "art integer, art_txt text, herkunft text, bfs_nr integer, "
                    + "importdatum text, nachfuehrung text, status text, aboolean boolean)");
        }
    }

    @AfterEach
    void tearDown() {
        if (lease != null) lease.close();
    }

    @Test
    void exportsAndImportsNativeShapefile() throws Exception {
        JobRunner.Result exported = JobRunner.run(root, """
                task('export', ShpExport) {
                    database = 'db'
                    dataFile = file('points.shp')
                    tableName = 'shp_points'
                }
                defaults 'export'
                """, config());
        assertEquals(0, exported.code(), exported.err());
        assertTrue(Files.isRegularFile(root.resolve("points.shp")));
        assertTrue(Files.isRegularFile(root.resolve("points.shx")));
        assertTrue(Files.isRegularFile(root.resolve("points.dbf")));
        assertTrue(Files.isRegularFile(root.resolve("points.cpg")));

        JobRunner.Result imported = JobRunner.run(root, """
                task('import', ShpImport) {
                    database = 'db'
                    dataFile = file('points.shp')
                    tableName = 'shp_points_import'
                }
                defaults 'import'
                """, config());
        assertEquals(0, imported.code(), imported.err());
        assertEquals(1, scalar("select count(*) from shp_points_import"));
        assertEquals("Grün", string("select name from shp_points_import"));
    }

    @Test
    void importsAndExportsCsvThroughIoxWkf() throws Exception {
        Files.writeString(root.resolve("colors.csv"), """
                id;name
                1;red
                2;blue
                """, StandardCharsets.UTF_8);
        JobRunner.Result imported = JobRunner.run(root, """
                task('import', CsvImport) {
                    database = 'db'
                    dataFile = file('colors.csv')
                    tableName = 'csv_colors'
                    valueSeparator = ';'
                }
                defaults 'import'
                """, config());
        assertEquals(0, imported.code(), imported.err());
        assertEquals(2, scalar("select count(*) from csv_colors"));

        JobRunner.Result exported = JobRunner.run(root, """
                task('export', CsvExport) {
                    database = 'db'
                    dataFile = file('colors-export.csv')
                    tableName = 'csv_colors'
                    valueSeparator = ';'
                }
                defaults 'export'
                """, config());
        assertEquals(0, exported.code(), exported.err());
        String csv = Files.readString(root.resolve("colors-export.csv"));
        assertTrue(csv.contains("red"));
        assertTrue(csv.contains("blue"));
    }

    @Test
    void importsAndExportsGeoPackageThroughIoxWkf() throws Exception {
        Path fixture = Path.of(ClassLoader.getSystemResource(
                "fixtures/gpkg/attributes.gpkg").toURI());
        Files.copy(fixture, root.resolve("attributes.gpkg"));
        JobRunner.Result imported = JobRunner.run(root, """
                task('import', GpkgImport) {
                    database = 'db'
                    dataFile = file('attributes.gpkg')
                    srcTableName = 'Attributes'
                    dstTableName = 'gpkg_attributes'
                }
                defaults 'import'
                """, config());
        assertEquals(0, imported.code(), imported.err());
        assertEquals(1, scalar("select count(*) from gpkg_attributes"));

        JobRunner.Result exported = JobRunner.run(root, """
                task('export', GpkgExport) {
                    database = 'db'
                    dataFile = file('export.gpkg')
                    srcTableName = ['shp_points']
                    dstTableName = ['shp_points']
                }
                defaults 'export'
                """, config());
        assertEquals(0, exported.code(), exported.err());
        assertTrue(Files.isRegularFile(root.resolve("export.gpkg")));
    }

    @Test
    void exportsMultipleGeoPackageLayers() throws Exception {
        JobRunner.Result exported = JobRunner.run(root, """
                task('export', GpkgExport) {
                    database = 'db'
                    dataFile = file('multi.gpkg')
                    srcTableName = ['shp_points', 'shp_points2']
                    dstTableName = ['points', 'points2']
                }
                defaults 'export'
                """, config());
        assertEquals(0, exported.code(), exported.err());
        try (Connection sqlite = DriverManager.getConnection(
                "jdbc:sqlite:" + root.resolve("multi.gpkg"));
             Statement statement = sqlite.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select count(*) from gpkg_contents")) {
            rows.next();
            assertEquals(2, rows.getInt(1));
        }
    }

    @Test
    void roundTripsMultipartPolygonWithHole() throws Exception {
        JobRunner.Result exported = JobRunner.run(root, """
                task('export', ShpExport) {
                    database = 'db'
                    dataFile = file('multipolygon.shp')
                    tableName = 'shp_multipolygons'
                }
                defaults 'export'
                """, config());
        assertEquals(0, exported.code(), exported.err());
        JobRunner.Result imported = JobRunner.run(root, """
                task('import', ShpImport) {
                    database = 'db'
                    dataFile = file('multipolygon.shp')
                    tableName = 'shp_multipolygons_import'
                }
                defaults 'import'
                """, config());
        assertEquals(0, imported.code(), imported.err());
        assertEquals(1, scalar("select ST_NumGeometries(geom) from shp_multipolygons_import"));
        assertEquals(1, scalar("select ST_NumInteriorRings(ST_GeometryN(geom, 1)) "
                + "from shp_multipolygons_import"));
    }

    @Test
    void rejectsZGeometryExportToShapefile() throws Exception {
        JobRunner.Result result = JobRunner.run(root, """
                task('export', ShpExport) {
                    database = 'db'
                    dataFile = file('z.shp')
                    tableName = 'shp_z'
                }
                defaults 'export'
                """, config());
        org.junit.jupiter.api.Assertions.assertNotEquals(0, result.code());
        assertTrue(result.err().contains("Z") || result.err().contains("Z/M"));
    }

    @Test
    void roundTripsMultipartPolygonThroughGeoPackage() throws Exception {
        JobRunner.Result exported = JobRunner.run(root, """
                task('export', GpkgExport) {
                    database = 'db'
                    dataFile = file('multipolygon.gpkg')
                    srcTableName = ['shp_multipolygons']
                    dstTableName = ['multipolygons']
                }
                defaults 'export'
                """, config());
        assertEquals(0, exported.code(), exported.err());
        JobRunner.Result imported = JobRunner.run(root, """
                task('import', GpkgImport) {
                    database = 'db'
                    dataFile = file('multipolygon.gpkg')
                    srcTableName = 'multipolygons'
                    dstTableName = 'gpkg_multi_import'
                }
                defaults 'import'
                """, config());
        assertEquals(0, imported.code(), imported.err());
        assertEquals(1, scalar("select ST_NumGeometries(geom) from gpkg_multi_import"));
    }

    @Test
    void rejectsZPointGeoPackageImport() throws Exception {
        JobRunner.Result exported = JobRunner.run(root, """
                task('export', GpkgExport) {
                    database = 'db'
                    dataFile = file('z.gpkg')
                    srcTableName = ['shp_z']
                    dstTableName = ['zpoints']
                }
                defaults 'export'
                """, config());
        assertEquals(0, exported.code(), exported.err());
        JobRunner.Result imported = JobRunner.run(root, """
                task('import', GpkgImport) {
                    database = 'db'
                    dataFile = file('z.gpkg')
                    srcTableName = 'zpoints'
                    dstTableName = 'gpkg_z_import'
                }
                defaults 'import'
                """, config());
        org.junit.jupiter.api.Assertions.assertNotEquals(0, imported.code());
        assertTrue(imported.err().contains("Z/M"));
    }

    @Test
    void importsJsonIntoTextColumn() throws Exception {
        Files.writeString(root.resolve("data.json"), "[{\"a\":1},{\"b\":2}]",
                StandardCharsets.UTF_8);
        JobRunner.Result imported = JobRunner.run(root, """
                task('import', JsonImport) {
                    database = 'db'
                    jsonFile = file('data.json')
                    qualifiedTableName = 'documents'
                    columnName = 'document'
                }
                defaults 'import'
                """, config());
        assertEquals(0, imported.code(), imported.err());
        assertEquals(2, scalar("select count(*) from documents"));
    }

    @Test
    void runsIli2pgLifecycle() throws Exception {
        Path model = Path.of(ClassLoader.getSystemResource(
                "fixtures/interlis/Beispiel2.ili").toURI());
        Path inputA = Path.of(ClassLoader.getSystemResource(
                "fixtures/interlis/Beispiel2a.xtf").toURI());
        Path inputB = Path.of(ClassLoader.getSystemResource(
                "fixtures/interlis/Beispiel2b.xtf").toURI());
        Files.writeString(root.resolve("DatasetA.txt"), "A");
        Files.writeString(root.resolve("DatasetB.txt"), "B");
        String schemaJob = "task('schema', Ili2pgImportSchema) {\n"
                + "    database = 'db'\n    schema = 'ili2pgtest'\n"
                + "    modelNames = ['Beispiel2']\n"
                + "    modelDirectories = [file('" + model.getParent() + "')]\n"
                + "    defaultSrsCode = '2056'\n"
                + "    createBasketCol = true\n    createTidCol = true\n}\n"
                + "defaults 'schema'\n";
        JobRunner.Result schema = JobRunner.run(root, schemaJob, config());
        assertEquals(0, schema.code(), schema.err());

        String importJob = "task('import', Ili2pgImport) {\n"
                + "    database = 'db'\n    schema = 'ili2pgtest'\n"
                + "    dataFiles = [file('" + inputA + "'), file('" + inputB + "')]\n"
                + "    datasetFiles = [file('DatasetA.txt'), file('DatasetB.txt')]\n"
                + "    importTid = true\n}\n"
                + "defaults 'import'\n";
        JobRunner.Result imported = JobRunner.run(root, importJob, config());
        assertEquals(0, imported.code(), imported.err());

        String exportJob = "task('export', Ili2pgExport) {\n"
                + "    database = 'db'\n    schema = 'ili2pgtest'\n"
                + "    dataFiles = [file('build/DatasetA.xtf')]\n"
                + "    datasetNames = ['DatasetA']\n}\n"
                + "defaults 'export'\n";
        JobRunner.Result exported = JobRunner.run(root, exportJob, config());
        assertEquals(0, exported.code(), exported.err());
        assertTrue(Files.isRegularFile(root.resolve("build/DatasetA.xtf")));

        String replaceJob = "task('replace', Ili2pgReplace) {\n"
                + "    database = 'db'\n    schema = 'ili2pgtest'\n"
                + "    dataFiles = [file('" + inputA + "')]\n"
                + "    datasetNames = ['DatasetA']\n    importTid = true\n}\n"
                + "defaults 'replace'\n";
        assertEquals(0, JobRunner.run(root, replaceJob, config()).code());
        String updateJob = "task('update', Ili2pgUpdate) {\n"
                + "    database = 'db'\n    schema = 'ili2pgtest'\n"
                + "    dataFiles = [file('" + inputA + "')]\n"
                + "    datasetNames = ['DatasetA']\n    importTid = true\n}\n"
                + "defaults 'update'\n";
        assertEquals(0, JobRunner.run(root, updateJob, config()).code());
        String validateJob = "task('validate', Ili2pgValidate) {\n"
                + "    database = 'db'\n    schema = 'ili2pgtest'\n"
                + "    modelNames = ['Beispiel2']\n"
                + "    modelDirectories = [file('" + model.getParent() + "')]\n"
                + "    datasetNames = ['DatasetA']\n}\n"
                + "defaults 'validate'\n";
        assertEquals(0, JobRunner.run(root, validateJob, config()).code());
        String deleteJob = "task('delete', Ili2pgDelete) {\n"
                + "    database = 'db'\n    schema = 'ili2pgtest'\n"
                + "    datasetNames = ['DatasetA', 'DatasetB']\n}\n"
                + "defaults 'delete'\n";
        assertEquals(0, JobRunner.run(root, deleteJob, config()).code());
        assertEquals(0, scalar("select count(*) from ili2pgtest.t_ili2db_dataset"));
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
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private String string(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(lease.jdbcUrl(), lease.user(), lease.password());
    }
}
