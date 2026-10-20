package ch.so.agi.gretl.internal.ioxwkf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Gpkg2ShpEngineTest {
    @TempDir Path root;

    @Test
    void rejectsZGeoPackageLayers() throws Exception {
        Path gpkg = root.resolve("z.gpkg");
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + gpkg);
             var statement = connection.createStatement()) {
            statement.execute("create table T_ILI2DB_TABLE_PROP (tablename text, setting text)");
            statement.execute("insert into T_ILI2DB_TABLE_PROP values ('z', 'CLASS')");
            statement.execute("create table gpkg_geometry_columns "
                    + "(table_name text, column_name text, geometry_type_name text, "
                    + "srs_id int, z int, m int)");
            statement.execute("insert into gpkg_geometry_columns values "
                    + "('z', 'geom', 'POINT', 2056, 1, 0)");
        }
        org.junit.jupiter.api.Assertions.assertThrows(
                ch.so.agi.gretl.internal.shapefile.ShapefileMappingException.class,
                () -> new Gpkg2ShpEngine().convert(new Gpkg2ShpEngine.Gpkg2ShpRequest(
                        gpkg, root.resolve("out"))));
    }

    @Test
    void convertsIli2gpkgClassTables() throws Exception {
        Path gpkg = Path.of(getClass().getResource(
                "/fixtures/iox-wkf/Gpkg2Shp/aggloprogramme.gpkg").toURI());
        Path output = root.resolve("shp");
        new Gpkg2ShpEngine().convert(new Gpkg2ShpEngine.Gpkg2ShpRequest(gpkg, output));
        assertTrue(Files.isDirectory(output));
        try (var files = Files.list(output)) {
            assertTrue(files.anyMatch(path -> path.toString().endsWith(".shp")));
        }
    }
}
