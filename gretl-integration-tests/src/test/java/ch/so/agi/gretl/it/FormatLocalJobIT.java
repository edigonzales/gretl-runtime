package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FormatLocalJobIT {
    @ParameterizedTest @ValueSource(strings = {"single", "empty"})
    void originalCsv2ExcelRowsAndHeaders(String variant) throws Exception {
        var result = run("excel", variant); assertEquals(0, result.code(), result.err());
        try (var in = Files.newInputStream(result.directory().resolve("output/converted.xlsx")); var workbook = new XSSFWorkbook(in)) {
            var sheet = workbook.getSheetAt(0);
            if (variant.equals("empty")) {
                assertEquals("egid", sheet.getRow(0).getCell(0).getStringCellValue());
                assertEquals("xkoordinaten", sheet.getRow(0).getCell(1).getStringCellValue()); assertEquals(0, sheet.getLastRowNum());
            } else {
                assertEquals(7, sheet.getRow(0).getLastCellNum()); assertEquals(7, sheet.getRow(1).getLastCellNum()); assertEquals(308, sheet.getLastRowNum());
            }
        }
    }
    @ParameterizedTest @ValueSource(strings = {"single", "set", "fail"})
    void originalAv2chObjectsAndFileSets(String variant) throws Exception {
        var result = run("av2ch", variant);
        if (variant.equals("fail")) {
            assertEquals(10, result.code(), result.err()); String report = Files.readString(result.report());
            assertTrue(report.contains("InterlisTaskException"), report); assertTrue(report.contains("AV_CONVERT"), report); return;
        }
        assertEquals(0, result.code(), result.err());
        Path output = result.directory().resolve("output/254900.itf");
        assertTrue(Files.size(output) > 580000);
        String text = Files.readString(output, java.nio.charset.StandardCharsets.ISO_8859_1);
        for (String expected : List.of("DM01 Interlis Converter", "MODL DM01AVCH24LV95D", "TABL LFP3Nachfuehrung", "OBJE 2540 2514 2611693.294 1233674.211 111.9 1 3 1")) assertTrue(text.contains(expected), expected);
        if (variant.equals("set")) assertTrue(Files.isRegularFile(result.directory().resolve("output/252400.itf")));
    }
    @ParameterizedTest @ValueSource(strings = {"single", "set"})
    void originalAv2geobauSingleAndFileSet(String variant) throws Exception {
        var result = run("av2geobau", variant); assertEquals(0, result.code(), result.err());
        for (String name : variant.equals("set") ? List.of("empty1.dxf", "empty2.dxf") : List.of("empty.dxf")) {
            String text = Files.readString(result.directory().resolve("output").resolve(name)); assertTrue(text.contains("SECTION"), text); assertTrue(text.contains("EOF"), text);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"gpkg2shp", "gpkg2dxf"})
    void convertsGeoPackageUsingIndependentFileChecks(String target) throws Exception {
        var result = run(target, "single"); assertEquals(0, result.code(), result.err());
        Path output = result.directory().resolve("output");
        if (target.equals("gpkg2shp")) {
            assertEquals(109, FileOracles.dbfCount(output.resolve("nachfuehrngskrise_gemeinde.dbf")));
            assertEquals(127, FileOracles.dbfCount(output.resolve("grundbuchkreise_grundbuchkreis.dbf")));
            for (String table : List.of("nachfuehrngskrise_gemeinde", "grundbuchkreise_grundbuchkreis"))
                assertTrue(Files.readString(output.resolve(table + ".prj")).contains("LV95"));
        } else {
            String municipality = Files.readString(output.resolve("nachfuehrngskrise_gemeinde.dxf"), java.nio.charset.StandardCharsets.ISO_8859_1);
            assertTrue(municipality.contains("LerchWeberAG")); assertTrue(municipality.contains("2638171.578"));
            assertTrue(Files.readString(output.resolve("grundbuchkreise_grundbuchkreis.dxf"), java.nio.charset.StandardCharsets.ISO_8859_1).contains("2619682.201"));
        }
        try (var files = Files.list(result.directory().resolve("output"))) {
            var outputs = files.filter(p -> p.toString().endsWith(target.equals("gpkg2shp") ? ".shp" : ".dxf")).toList();
            assertFalse(outputs.isEmpty());
            for (Path file : outputs) {
                if (target.equals("gpkg2shp")) {
                    byte[] header = Files.readAllBytes(file); assertEquals(9994, java.nio.ByteBuffer.wrap(header).getInt());
                    assertTrue(Files.isRegularFile(Path.of(file.toString().replace(".shp", ".dbf"))));
                } else assertTrue(Files.readString(file, java.nio.charset.StandardCharsets.ISO_8859_1).contains("EOF"));
            }
        }
    }
    private ArtifactJobRunner.Result run(String target, String variant) throws Exception {
        Path root = ArtifactJobRunner.directory(target + "-" + variant);
        return ArtifactJobRunner.run(new ArtifactJobRunner.Request(target + "-" + variant, ArtifactJobRunner.fixture("formats"), root, List.of(target),
                Map.of("output", root.resolve("output").toString(), "variant", variant), Map.of(), JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(180)));
    }
}
