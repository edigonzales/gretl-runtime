package ch.so.agi.gretl.tasks.basic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoFileTaskTest {
    @TempDir
    Path root;

    @Test
    void convertsGpkgToShapefileAndValidatesBoth() throws Exception {
        Path gpkg = root.resolve("source.gpkg");
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
             Statement statement = connection.createStatement()) {
            statement.execute("install spatial");
            statement.execute("load spatial");
            statement.execute("copy (select 1 as id, 'red' as name, "
                    + "ST_Point(8, 47) as geom) to '" + gpkg
                    + "' with (format gdal, driver 'GPKG', srs '4326')");
        }
        String layer;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + gpkg);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "select table_name from gpkg_contents where data_type='features'")) {
            rows.next();
            layer = rows.getString(1);
        }

        Gpkg2ShpOperation.Gpkg2ShpBuilder convert =
                new Gpkg2ShpOperation.Gpkg2ShpBuilder();
        convert.setDataFile(gpkg).setOutputDir(root.resolve("shp"));
        Gpkg2ShpOperation.Gpkg2ShpResult result =
                new Gpkg2ShpOperation.Gpkg2ShpHandler()
                        .execute(convert.build(), context());
        assertFalse(result.files().isEmpty());
        Path shp = result.files().get(0);
        assertTrue(Files.isRegularFile(shp));
        assertTrue(Files.isRegularFile(shp.resolveSibling(
                shp.getFileName().toString().replace(".shp", ".shx"))));
        assertTrue(Files.isRegularFile(shp.resolveSibling(
                shp.getFileName().toString().replace(".shp", ".dbf"))));

        GpkgValidatorOperation.GpkgValidatorBuilder gpkgValidator =
                new GpkgValidatorOperation.GpkgValidatorBuilder();
        gpkgValidator.setDataFiles(List.of(gpkg)).setTableName(layer);
        assertTrue(new GpkgValidatorOperation.GpkgValidatorHandler()
                .execute(gpkgValidator.build(), context()).valid());

        ShpValidatorOperation.ShpValidatorBuilder shpValidator =
                new ShpValidatorOperation.ShpValidatorBuilder();
        shpValidator.setDataFiles(List.of(shp));
        assertTrue(new ShpValidatorOperation.ShpValidatorHandler()
                .execute(shpValidator.build(), context()).valid());

        Files.deleteIfExists(shp.resolveSibling(
                shp.getFileName().toString().replace(".shp", ".dbf")));
        ShpValidatorOperation.ShpValidatorBuilder missing =
                new ShpValidatorOperation.ShpValidatorBuilder();
        missing.setDataFiles(List.of(shp)).setFailOnError(false);
        assertFalse(new ShpValidatorOperation.ShpValidatorHandler()
                .execute(missing.build(), context()).valid());
    }

    private TestOperationContext context() {
        return new TestOperationContext(root, Map.of(), Map.of(), Map.of());
    }
}
