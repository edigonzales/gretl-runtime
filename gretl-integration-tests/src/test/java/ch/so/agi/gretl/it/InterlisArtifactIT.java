package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InterlisArtifactIT {
    @Test void sevenOperationsWithIndependentStateChecksFileSetExportReplacementAndDeletion() throws Exception {
        try (var lease = PostgresFixture.lease(); var db = connection(lease); var statement = db.createStatement()) {
            success(run(lease, "schema", Map.of())); success(run(lease, "import", Map.of()));
            assertEquals(4, count(db, "select count(*) from boflaechen")); assertEquals(Set.of("DatasetA", "DatasetB"), datasets(db));
            success(run(lease, "replace", Map.of())); assertEquals(4, count(db, "select count(*) from boflaechen"));
            success(run(lease, "update", Map.of())); assertEquals(Set.of("DatasetA", "DatasetB"), datasets(db));
            assertEquals(4, count(db, "select count(*) from boflaechen"));
            assertEquals(2, count(db, "select count(*) from t_ili2db_basket"));
            var exported = run(lease, "export", Map.of()); success(exported);
            InterlisLocalJobIT.assertTransfer(exported.directory().resolve("output/DatasetA.xtf"));
            InterlisLocalJobIT.assertTransfer(exported.directory().resolve("output/DatasetB.xtf"));
            for (String selection : List.of("single", "multiple", "global")) {
                var valid = run(lease, "validate", Map.of("validationSelection", selection)); success(valid);
                assertTrue(Files.readString(valid.directory().resolve("output/validation.log")).contains("Info: ...validate done"));
            }
            success(run(lease, "delete", Map.of("deleteSelection", "single"))); assertEquals(Set.of("DatasetB"), datasets(db)); assertEquals(2, count(db, "select count(*) from boflaechen"));
            success(run(lease, "replace", Map.of())); assertEquals(Set.of("DatasetA", "DatasetB"), datasets(db));
            success(run(lease, "delete", Map.of())); assertTrue(datasets(db).isEmpty()); assertEquals(0, count(db, "select count(*) from boflaechen"));
        }
    }
    @Test void originalDatasetSubstringAndRepositoryDataIdResolveThroughLocalRepository() throws Exception {
        try (var lease = PostgresFixture.lease(); var db = connection(lease)) {
            success(run(lease, "schema", Map.of())); success(run(lease, "import", Map.of("datasetSubstring", "true")));
            assertEquals(Set.of("A_Da", "B_Da"), datasets(db)); assertEquals(4, count(db, "select count(*) from boflaechen"));
        }
        try (var lease = PostgresFixture.lease(); var db = connection(lease)) {
            var params = Map.of("fixture", "Ili2pgReplaceIlidataFile", "model", "SO_AGI_MOpublic_20190424");
            success(run(lease, "schema", params)); success(run(lease, "replace-repository", params));
            assertEquals(Set.of("2549"), datasets(db)); assertEquals(1, count(db, "select count(*) from strassenname_pos"));
        }
    }

    @Test void originalLocalReplacementKeepsTwoObjectsAndExplicitDatasetName() throws Exception {
        try (var lease = PostgresFixture.lease(); var db = connection(lease)) {
            var params = Map.of("fixture", "Ili2pgReplaceFile", "datasetA", "A_Dataset");
            success(run(lease, "schema", params)); success(run(lease, "replace", params));
            assertEquals(2, count(db, "select count(*) from boflaechen")); assertEquals(Set.of("A_Dataset"), datasets(db));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"standard", "options", "meta-file", "meta-repository"})
    void originalSchemaModelOptionsAndMetaConfiguration(String variant) throws Exception {
        try (var lease = PostgresFixture.lease(); var db = connection(lease)) {
            Map<String, String> params = switch (variant) {
                case "standard" -> Map.of("fixture", "Ili2pgImportSchema", "model", "GB2AV");
                case "options" -> Map.of("fixture", "Ili2pgImportSchema_Options", "model", "SO_AFU_ABBAUSTELLEN_20210630", "options", "true");
                case "meta-file" -> Map.of("fixture", "Ili2pgImportSchema_MetaConfigFile", "model", "SimpleTable", "meta", "simple_table_ini_20240502.ini");
                default -> Map.of("fixture", "Ili2pgImportSchema_MetaConfigIliData", "model", "SimpleTable", "meta", "ilidata:metaconfig_simple_table_ini_20240502");
            };
            success(run(lease, "schema", params));
            try (var statement = db.createStatement()) {
                if (variant.equals("standard")) {
                    try (var rows = statement.executeQuery("select content from t_ili2db_model")) { assertTrue(rows.next()); assertTrue(rows.getString(1).contains("INTERLIS 2.2;")); assertFalse(rows.next()); }
                    assertEquals(1, count(db, "select count(*) from information_schema.columns where table_schema='" + lease.schema() + "' and table_name='vollzugsgegenstand' and column_name='mutationsnummer'"));
                } else if (variant.equals("options")) {
                    for (var expected : Map.of("gemeinde_bfs", "text", "geometrie", "character varying").entrySet()) {
                        try (var rows = statement.executeQuery("select data_type from information_schema.columns where table_schema='" + lease.schema() + "' and table_name='abbaustelle' and column_name='" + expected.getKey() + "'")) {
                            assertTrue(rows.next()); assertEquals(expected.getValue(), rows.getString(1)); assertFalse(rows.next());
                        }
                    }
                } else {
                    try (var rows = statement.executeQuery("select setting from t_ili2db_settings where tag ilike 'ch.ehi.ili2db.metaConfigFileName'")) {
                        assertTrue(rows.next()); assertTrue(rows.getString(1).contains(variant.equals("meta-file") ? "simple_table_ini_20240502.ini" : "ilidata:metaconfig_simple_table_ini_20240502"));
                    }
                }
            }
        }
    }
    @ParameterizedTest @ValueSource(strings = {"Ili2pgValidate", "Ili2pgValidateFail", "Ili2pgValidateSingleDataset", "Ili2pgValidateMultipleDatasets", "Ili2pgValidateDatasetFail"})
    void originalValidInvalidAndDatasetScopedValidation(String fixture) throws Exception {
        boolean protection = fixture.contains("Dataset"); boolean failed = fixture.contains("Fail");
        var params = new LinkedHashMap<String, String>(); params.put("fixture", fixture);
        params.put("model", protection ? "SO_AFU_Schutzbauten_20231212" : "SO_AGI_AV_GB_Administrative_Einteilungen_20180613");
        params.put("first", protection ? "ch.so.agi.testeinzelobjekt_valid1.xtf" : failed ? "ch.so.agi.av_gb_admin_einteilung_edit_fail.xtf" : "ch.so.agi.av_gb_admin_einteilung_edit_ok.xtf");
        params.put("second", protection ? "ch.so.agi.testeinzelobjekt_valid2.xtf" : "");
        params.put("disableValidation", failed ? "true" : "false");
        params.put("validationSelection", fixture.endsWith("SingleDataset") ? "single" : fixture.endsWith("MultipleDatasets") ? "multiple" : "global");
        try (var lease = PostgresFixture.lease()) {
            success(run(lease, "schema", params)); success(run(lease, "import", params));
            var validated = run(lease, "validate", params);
            assertEquals(failed ? 10 : 0, validated.code(), validated.err());
            String log = Files.readString(validated.directory().resolve("output/validation.log"));
            assertTrue(log.contains(failed ? "Error: ...validate failed" : "Info: ...validate done"), log);
            if (failed) { String report = Files.readString(validated.report()); assertTrue(report.contains("ILI2DB"), report); assertTrue(report.contains("Ili2dbException"), report); }
        }
    }
    private ArtifactJobRunner.Result run(PostgresFixture.Lease lease, String target, Map<String, String> extra) throws Exception {
        Path root = ArtifactJobRunner.directory("ili2pg-" + target); var config = JobRunner.baseConfig(root); JobRunner.secret(root, config, "dbPassword", lease.password());
        JobRunner.taskConfig(config, "db", Map.of("jdbcUrl", lease.jdbcUrl(), "user", lease.user(), "passwordSecret", "dbPassword"));
        var params = new LinkedHashMap<>(extra); params.put("schema", lease.schema()); params.put("output", root.resolve("output").toString());
        return ArtifactJobRunner.run(new ArtifactJobRunner.Request("ili2pg-" + target, ArtifactJobRunner.fixture("ili2pg"), root, List.of(target), params, Map.of(), config,
                Map.of("postgres", new ArtifactJobRunner.Endpoint(lease.jdbcUrl(), lease.imageJdbcUrl())), Duration.ofSeconds(120)));
    }
    private static void success(ArtifactJobRunner.Result result) { assertEquals(0, result.code(), result.err()); }
    private static Connection connection(PostgresFixture.Lease lease) throws Exception { return DriverManager.getConnection(lease.jdbcUrl(), lease.user(), lease.password()); }
    private static int count(Connection db, String sql) throws Exception { try (var query = db.createStatement(); var rows = query.executeQuery(sql)) { assertTrue(rows.next()); return rows.getInt(1); } }
    private static Set<String> datasets(Connection db) throws Exception {
        var result = new java.util.HashSet<String>(); try (var query = db.createStatement(); var rows = query.executeQuery("select datasetname from t_ili2db_dataset")) { while (rows.next()) result.add(rows.getString(1)); } return result;
    }
}
