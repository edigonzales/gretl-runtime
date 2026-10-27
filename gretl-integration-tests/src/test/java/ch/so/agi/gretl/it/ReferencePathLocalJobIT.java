package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferencePathLocalJobIT {
    @Test void publishedCsv2ExcelUsesLocalModelPathFromForeignCwd() throws Exception {
        Path root = ArtifactJobRunner.directory("reference-csv2excel");
        Path fixture = Files.createDirectory(root.resolve("fixture"));
        Files.createDirectories(fixture.resolve("data"));
        Files.createDirectories(fixture.resolve("models"));
        Files.writeString(fixture.resolve("data/input.csv"), "attr1,attr2,attr3\na1,1,rot\na2,2,blau\n");
        Files.copy(ArtifactJobRunner.fixture("validators").resolve("CsvValidator/CsvModel.ili"),
                fixture.resolve("models/CsvModel.ili"));
        Files.writeString(fixture.resolve("job.groovy"), ReferenceExamplesTest.firstExample("Csv2Excel"));
        Path output = root.resolve("output/result.xlsx");
        var result = ArtifactJobRunner.run(new ArtifactJobRunner.Request("reference-csv2excel", fixture,
                root, List.of(), Map.of("ziel", output), Map.of(), JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(90)));
        assertEquals(0, result.code(), result.err());
        try (var in = Files.newInputStream(output); var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(in)) {
            assertEquals(2, workbook.getSheetAt(0).getLastRowNum());
            assertEquals("a1", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
        }
    }

    @Test void publishedGpkg2ShpWritesToExternalDirectory() throws Exception {
        Path root = ArtifactJobRunner.directory("reference-gpkg2shp");
        Path fixture = Files.createDirectory(root.resolve("fixture"));
        Files.createDirectory(fixture.resolve("data"));
        Files.copy(ArtifactJobRunner.fixture("formats").resolve("admin.gpkg"), fixture.resolve("data/input.gpkg"));
        Files.writeString(fixture.resolve("job.groovy"), ReferenceExamplesTest.firstExample("Gpkg2Shp"));
        Path output = root.resolve("output/shapes");
        var result = ArtifactJobRunner.run(new ArtifactJobRunner.Request("reference-gpkg2shp", fixture,
                root, List.of(), Map.of("ziel", output), Map.of(), JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(90)));
        assertEquals(0, result.code(), result.err());
        assertEquals(109, FileOracles.dbfCount(output.resolve("nachfuehrngskrise_gemeinde.dbf")));
    }
}
