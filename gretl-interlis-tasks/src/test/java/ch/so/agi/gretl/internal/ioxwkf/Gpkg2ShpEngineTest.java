package ch.so.agi.gretl.internal.ioxwkf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Gpkg2ShpEngineTest {
    @TempDir Path root;

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
