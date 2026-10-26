package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DuckDbLocalJobIT {
    @Test void originalSqlParquetAndGeoPackageImportPlusIndependentExportRead() throws Exception {
        Path root = ArtifactJobRunner.directory("original-duckdb");
        var result = ArtifactJobRunner.run(new ArtifactJobRunner.Request("original-duckdb", ArtifactJobRunner.fixture("duckdb"), root, List.of(),
                Map.of("output", root.resolve("output")), Map.of(), JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(120)));
        assertEquals(0, result.code(), result.err());
        try (var db = DriverManager.getConnection("jdbc:duckdb:" + root.resolve("output/foo.duckdb")); var query = db.createStatement()) {
            try (var rows = query.executeQuery("select count(*),sum(i) from tbl")) { assertTrue(rows.next()); assertEquals(3, rows.getInt(1)); assertEquals(6, rows.getInt(2)); }
            for (String table : List.of("bewilligte_ews", "abbaustelle")) {
                try (var rows = query.executeQuery("select count(*) from " + table)) { assertTrue(rows.next()); assertTrue(rows.getInt(1) > 0); }
            }
            String parquet = root.resolve("output/tbl.parquet").toString().replace("'", "''");
            try (var rows = query.executeQuery("select i from read_parquet('" + parquet + "') order by i")) {
                for (int i = 1; i <= 3; i++) { assertTrue(rows.next()); assertEquals(i, rows.getInt(1)); } assertFalse(rows.next());
            }
        }
    }
}
