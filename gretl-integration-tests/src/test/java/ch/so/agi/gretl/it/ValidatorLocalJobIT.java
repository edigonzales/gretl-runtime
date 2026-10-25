package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ch.so.agi.gretl.runtime.logging.Json;

class ValidatorLocalJobIT {
    @ParameterizedTest
    @CsvSource({"csv,CsvValidator,path", "csv,CsvValidator,string", "gpkg,GpkgValidator,path",
            "gpkg,GpkgValidator,string", "json,JsonValidatorOk,path", "json,JsonValidatorOk,string",
            "shp,ShpValidator,path", "shp,ShpValidator,string"})
    void validatesThroughPublicDsl(String target, String variant, String repository) throws Exception {
        Path root = ArtifactJobRunner.directory("valid-" + target + "-" + repository);
        var result = validators(root, target, Map.of("variant", variant, "repository", repository));
        assertEquals(0, result.code(), result.err());
        assertFalse(result.timedOut());
        assertTrue(Files.readString(root.resolve("output/validation.log")).contains("validation done"));
        assertTrue(Files.readString(result.report()).contains("SUCCEEDED"));
        assertEquals(64, result.artifactIdentity().length());
    }

    @Test void invalidCsvNeedsBothBusinessDiagnostics() throws Exception {
        Path root = ArtifactJobRunner.directory("invalid-csv");
        var result = validators(root, "csv", Map.of("variant", "CsvValidatorFail"));
        assertEquals(10, result.code(), result.err());
        String log = Files.readString(root.resolve("output/validation.log"));
        assertTrue(log.contains("value <x> is not a number"), log);
        assertTrue(log.contains("value gruen is not a member of the enumeration"), log);
        assertTrue(Files.readString(result.report()).contains("InterlisTaskException"));
    }

    @Test void wrongGpkgTableIsAValidationFailure() throws Exception {
        Path root = ArtifactJobRunner.directory("wrong-gpkg-table");
        var result = validators(root, "gpkg", Map.of("variant", "GpkgValidator", "table", "absent_table"));
        assertEquals(10, result.code(), result.err());
        assertTrue(Files.readString(result.report()).contains("InterlisTaskException"));
        assertTrue(Files.readString(root.resolve("output/validation.log")).contains("absent_table"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"gpkg-missing-table", "csv-unsupported-table"})
    void tableFieldContractRejectsMissingAndUnsupportedSettings(String target) throws Exception {
        Path root = ArtifactJobRunner.directory(target);
        var result = validators(root, target, Map.of());
        assertEquals(10, result.code(), result.err());
        String report = Files.readString(result.report());
        assertTrue(report.contains("tableName"), report);
        assertTrue(report.contains(target.equals("gpkg-missing-table") ? "MISSING_REQUIRED_FIELD" : "UNKNOWN_CONFIGURATION_FIELD"), report);
        assertFalse(Files.exists(root.resolve("output/validation.log")));
    }

    @Test void rejectsInvalidRepositoryBeforeEngine() throws Exception {
        Path root = ArtifactJobRunner.directory("invalid-model-repository");
        var result = validators(root, "csv", Map.of("invalidRepository", "true"));
        assertEquals(10, result.code(), result.err());
        String report = Files.readString(result.report());
        assertTrue(report.contains("modelDirectories"));
        assertTrue(report.contains("Path or non-blank repository string"), report);
        assertFalse(Files.exists(root.resolve("output/validation.log")));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"2056", "21781"})
    void geoPackageContainsDslSelectedSridAndImportedData(String srs) throws Exception {
        Path root = ArtifactJobRunner.directory("gpkg-srid");
        Path output = root.resolve("output/result.gpkg");
        var result = ArtifactJobRunner.run(new ArtifactJobRunner.Request("gpkg-srid",
                ArtifactJobRunner.fixture("gpkg-import"), root, List.of(),
                Map.of("output", output.toString(), "srs", srs), Map.of(),
                JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(90)));
        assertEquals(0, result.code(), result.err());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + output);
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("select distinct srs_id from gpkg_geometry_columns")) {
                assertTrue(rows.next());
                assertEquals(Integer.parseInt(srs), rows.getInt(1));
                assertFalse(rows.next());
            }
            try (var rows = statement.executeQuery("select count(*) from boflaechen")) {
                assertTrue(rows.next());
                assertTrue(rows.getInt(1) > 0);
            }
        }
    }

    @Test void archiveJobPreservesOriginalXmlBytes() throws Exception {
        Path root = ArtifactJobRunner.directory("original-gzip");
        Path output = root.resolve("output/planregister.xml");
        var result = ArtifactJobRunner.run(new ArtifactJobRunner.Request("original-gzip",
                ArtifactJobRunner.fixture("archive"), root, List.of(), Map.of("output", output.toString()),
                Map.of(), JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(60)));
        assertEquals(0, result.code(), result.err());
        assertArrayEquals(Files.readAllBytes(ArtifactJobRunner.fixture("archive").resolve("planregister.xml")),
                Files.readAllBytes(output));
        // The assertion must reject an absent or incorrect expected output.
        assertThrows(AssertionError.class, () -> assertArrayEquals(new byte[] {0}, Files.readAllBytes(output)));
    }

    @Test void publishedGeoPackageExampleImportsTransferData() throws Exception {
        Path root = ArtifactJobRunner.directory("gpkg-help-example");
        Path fixture = root.resolve("example");
        Files.createDirectories(fixture.resolve("models"));
        Path original = ArtifactJobRunner.fixture("gpkg-import");
        Files.copy(original.resolve("Beispiel2a.xtf"), fixture.resolve("data.xtf"));
        Files.copy(original.resolve("models/Beispiel2.ili"), fixture.resolve("models/Beispiel2.ili"));
        var operation = ch.so.agi.gretl.runtime.runner.OperationRegistry.load(getClass().getClassLoader())
                .requireSymbol("Ili2gpkgImport");
        // This is the exact public example, including parameter declaration and default target.
        Files.writeString(fixture.resolve("job.groovy"), operation.example());
        Path output = root.resolve("output/example.gpkg");
        var result = ArtifactJobRunner.run(new ArtifactJobRunner.Request("gpkg-help-example",
                fixture, root, List.of(), Map.of("output", output.toString()), Map.of(),
                JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(90)));
        assertEquals(0, result.code(), result.err());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + output);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("select count(*) from boflaechen")) {
            assertTrue(rows.next());
            assertTrue(rows.getInt(1) > 0);
        }
    }

    private ArtifactJobRunner.Result validators(Path root, String target, Map<String, String> parameters) throws Exception {
        var params = new java.util.LinkedHashMap<>(parameters);
        params.put("log", root.resolve("output/validation.log").toString());
        return ArtifactJobRunner.run(new ArtifactJobRunner.Request(root.getFileName().toString().substring(0, root.getFileName().toString().length() - 37),
                ArtifactJobRunner.fixture("validators"), root, List.of(target), params, Map.of(),
                JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(60)));
    }
}
