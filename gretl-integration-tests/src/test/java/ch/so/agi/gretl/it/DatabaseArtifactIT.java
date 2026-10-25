package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

class DatabaseArtifactIT {
    @Test void realDatabaseChainAndCancellationRollback() throws Exception {
        try (var lease = PostgresFixture.lease()) {
            Path root = ArtifactJobRunner.directory("database-chain");
            var request = request(root, lease, List.of());
            var prepared = ArtifactJobRunner.prepare(request);
            var result = ArtifactJobRunner.finish(ArtifactJobRunner.start(prepared, false), false);
            assertEquals(0, result.code(), result.err());
            try (var db = connection(lease); var statement = db.createStatement();
                 var rows = statement.executeQuery("select id, name from colors_copy order by id")) {
                assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); assertEquals("red", rows.getString(2));
                assertTrue(rows.next()); assertEquals(2, rows.getInt(1)); assertEquals("Grün", rows.getString(2));
                assertFalse(rows.next());
            }
            // A fresh job process and work copy, using the same scenario and database lease.
            Path cancelRoot = ArtifactJobRunner.directory("database-cancel");
            var running = ArtifactJobRunner.start(ArtifactJobRunner.prepare(request(cancelRoot, lease, List.of("never"))), false);
            try {
                awaitQuery(lease, running.process());
                running.process().destroy();
                var cancelled = ArtifactJobRunner.finish(running, true);
                assertFalse(cancelled.timedOut());
                String report = Files.readString(cancelled.report());
                assertTrue(report.contains("\"status\":\"CANCELLED\""), report);
                assertTrue(report.contains("\"exitCode\":20"), report);
                assertFalse(report.contains("DB2DB_EXECUTION"), report);
                assertTrue(report.contains("NOT_RUN"), report);
                try (var db = connection(lease); var statement = db.createStatement();
                     var rows = statement.executeQuery("select string_agg(name, ',' order by id) from colors_copy")) {
                    assertTrue(rows.next()); assertEquals("red,Grün", rows.getString(1));
                }
                try (var db = connection(lease); var statement = db.createStatement();
                     var rows = statement.executeQuery("select count(*) from pg_stat_activity where pid<>pg_backend_pid() "
                             + "and state='idle in transaction' and query like '%cancellation-checkpoint%'")) {
                    assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
                }
            } finally {
                if (running.process().isAlive()) ArtifactJobRunner.stop(running.process());
            }
        }
    }

    @Test void geometryFederationRejectsUnsupportedPostgisTypeExplicitly() throws Exception {
        try (var lease = PostgresFixture.lease()) {
            try (var db = connection(lease); var statement = db.createStatement()) {
                statement.execute("create table geo_source(id int, geom geometry(Point,2056))");
                statement.execute("insert into geo_source values(1, ST_SetSRID(ST_MakePoint(2600000,1200000),2056))");
            }
            Path root = ArtifactJobRunner.directory("postgis-geometry-contract");
            var result = ArtifactJobRunner.run(request(root, lease, List.of("geometry")));
            assertEquals(10, result.code(), result.err());
            String report = Files.readString(result.report());
            assertTrue(report.contains("DUCKDB_GEOMETRY_UNSUPPORTED"), report);
            assertTrue(report.contains("geometry/geography sources are not supported"), report);
            try (var db = connection(lease); var statement = db.createStatement();
                 var rows = statement.executeQuery("select ST_AsText(geom), ST_SRID(geom) from geo_source")) {
                assertTrue(rows.next()); assertEquals("POINT(2600000 1200000)", rows.getString(1)); assertEquals(2056, rows.getInt(2));
            }
        }
    }

    private ArtifactJobRunner.Request request(Path root, PostgresFixture.Lease lease, List<String> targets) throws Exception {
        var config = JobRunner.baseConfig(root);
        JobRunner.secret(root, config, "dbPassword", lease.password());
        JobRunner.taskConfig(config, "db", Map.of("jdbcUrl", lease.jdbcUrl(), "user", lease.user(), "passwordSecret", "dbPassword"));
        return new ArtifactJobRunner.Request("database", ArtifactJobRunner.fixture("database"), root, targets,
                Map.of("schema", lease.schema()), Map.of(), config,
                Map.of("postgres", new ArtifactJobRunner.Endpoint(lease.jdbcUrl(), "postgres:5432")), Duration.ofSeconds(90));
    }
    private static Connection connection(PostgresFixture.Lease lease) throws Exception {
        return DriverManager.getConnection(lease.jdbcUrl(), lease.user(), lease.password());
    }
    private static void awaitQuery(PostgresFixture.Lease lease, Process process) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        try (var db = connection(lease); var statement = db.prepareStatement(
                "select count(*) from pg_stat_activity where pid<>pg_backend_pid() and state='active' "
                        + "and query like '%cancellation-checkpoint%' and query not like '%pg_stat_activity%'")) {
            while (System.nanoTime() < deadline) {
                assertTrue(process.isAlive(), "Job ended before database checkpoint");
                try (var rows = statement.executeQuery()) { rows.next(); if (rows.getInt(1) > 0) return; }
                LockSupport.parkNanos(1_000_000L);
            }
        }
        fail("No active Db2Db query checkpoint");
    }
}
