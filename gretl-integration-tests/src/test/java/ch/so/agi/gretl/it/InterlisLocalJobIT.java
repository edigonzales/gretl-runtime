package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InterlisLocalJobIT {
    @Test void narrowValidatorOriginalSingleFileAndFileSetAndInvalidData() throws Exception {
        var valid = run(List.of("ili-report", "ili-second"), "single"); assertEquals(0, valid.code(), valid.err());
        String diagnostic = Files.readString(valid.directory().resolve("output/validation.txt")); assertTrue(diagnostic.contains("validation done"), diagnostic);
        String report = Files.readString(valid.report()); assertTrue(report.contains("\"taskId\":\"ili-second\",\"status\":\"SUCCEEDED\""), report);
        var invalid = run(List.of("ili"), "fail"); assertEquals(10, invalid.code(), invalid.err());
        report = Files.readString(invalid.report()); assertTrue(report.contains("VALIDATION_FAILED"), report); assertTrue(report.contains("IliValidatorTaskException"), report);
    }
    @Test void originalNgkAdditionalModelAndBusinessFailureThroughExistingValidationConfig() throws Exception {
        var result = run(List.of("ngk"), "single"); assertEquals(10, result.code(), result.err());
        String report = Files.readString(result.report()); assertTrue(report.contains("VALIDATION_FAILED"), report);
        StringBuilder diagnostics = new StringBuilder();
        try (var files = Files.walk(result.directory().resolve("work"))) {
            for (Path path : files.filter(p -> p.getFileName().toString().equals("validation-report.txt")).toList()) diagnostics.append(Files.readString(path));
        }
        String log = diagnostics.toString(); assertTrue(log.contains("additional model SO_AFU_Naturgefahren_Validierung_20240515"), log);
        assertTrue(log.contains("tid 701051de-6f2f-476d-81fb-43b8885ae7fc: Die Kennung des Auftrags im XTF muss dem Dateinamen des XTF entsprechen"), log);
        assertFalse(log.contains("assume unknown external objects"), log);
    }

    @ParameterizedTest @ValueSource(strings = {"single", "set"})
    void originalGeoPackageImportNamesAndFileSetDatasets(String variant) throws Exception {
        var result = run(List.of("gpkg"), variant); assertEquals(0, result.code(), result.err());
        try (var db = DriverManager.getConnection("jdbc:sqlite:" + result.directory().resolve("output/result.gpkg")); var query = db.createStatement()) {
            if (variant.equals("single")) {
                var names = new HashSet<String>();
                try (var rows = query.executeQuery("select aname from nachfuehrungsgeometer")) { while (rows.next()) names.add(rows.getString(1)); }
                assertEquals(java.util.Set.of("Kägi", "Cantaluppi", "Schor", "Meile", "Weber"), names);
            } else {
                try (var rows = query.executeQuery("select count(*) from boflaechen")) { assertTrue(rows.next()); assertEquals(4, rows.getInt(1)); }
                var datasets = new HashSet<String>();
                try (var rows = query.executeQuery("select datasetname from t_ili2db_dataset")) { while (rows.next()) datasets.add(rows.getString(1)); }
                assertEquals(java.util.Set.of("DatasetA", "DatasetB"), datasets);
            }
        }
    }
    @Test void originalDuckDbSchemaImportDataAndStructuredTransferExport() throws Exception {
        var result = run(List.of("duck-export"), "single"); assertEquals(0, result.code(), result.err());
        try (var db = DriverManager.getConnection("jdbc:duckdb:" + result.directory().resolve("output/result.duckdb")); var query = db.createStatement()) {
            try (var rows = query.executeQuery("select content from t_ili2db_model")) { assertTrue(rows.next()); assertTrue(rows.getString(1).contains("INTERLIS 2.2;")); assertFalse(rows.next()); }
            try (var rows = query.executeQuery("select column_name from information_schema.columns where table_name='vollzugsgegenstand' and column_name='mutationsnummer'")) { assertTrue(rows.next()); assertEquals("mutationsnummer", rows.getString(1)); assertFalse(rows.next()); }
            try (var rows = query.executeQuery("select astatus from vollzugsgegenstand")) { assertTrue(rows.next()); assertEquals("Eintrag", rows.getString(1)); assertFalse(rows.next()); }
        }
        assertTransfer(result.directory().resolve("output/export.xtf"));
    }
    static void assertTransfer(Path file) throws Exception {
        var reader = new ch.interlis.iom_j.xtf.XtfReader(file.toFile());
        try {
            assertInstanceOf(ch.interlis.iox.StartTransferEvent.class, reader.read()); assertInstanceOf(ch.interlis.iox.StartBasketEvent.class, reader.read());
            ch.interlis.iox.IoxEvent event = reader.read(); int objects = 0;
            while (event instanceof ch.interlis.iox.ObjectEvent) { objects++; event = reader.read(); }
            assertTrue(objects > 0); assertInstanceOf(ch.interlis.iox.EndBasketEvent.class, event); assertInstanceOf(ch.interlis.iox.EndTransferEvent.class, reader.read());
        } finally { reader.close(); }
    }
    private ArtifactJobRunner.Result run(List<String> targets, String variant) throws Exception {
        Path root = ArtifactJobRunner.directory("ili-" + targets.getFirst() + "-" + variant);
        return ArtifactJobRunner.run(new ArtifactJobRunner.Request("ili-" + targets.getFirst() + "-" + variant, ArtifactJobRunner.fixture("ili"), root, targets,
                Map.of("output", root.resolve("output").toString(), "variant", variant), Map.of(), JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(120)));
    }
}
