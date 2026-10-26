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
                ArtifactJobRunner.cancel(running);
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
                if (running.process().isAlive()) ArtifactJobRunner.stop(running);
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

    @Test void positiveFederationAndOriginalScalarAndListParameterResults() throws Exception {
        try (var lease = PostgresFixture.lease(); var db = connection(lease); var statement = db.createStatement()) {
            statement.execute("create table source1(id int primary key,name text)");
            statement.execute("create table source2(id int primary key,name text)");
            statement.execute("insert into source1 values(1,'1a'),(2,'1b')");
            statement.execute("insert into source2 values(3,'2a'),(4,'2b'),(5,'2c')");
            statement.execute("create table parameter_copy(id int primary key,name text)");
            statement.execute("create table parameter_values(title text)");
            for (String variant : List.of("scalar", "list")) {
                statement.execute("truncate parameter_copy,parameter_values");
                for (String target : List.of("parameter-sql", "parameter-copy")) {
                    Path root = ArtifactJobRunner.directory(target + "-" + variant);
                    var base = request(root, lease, List.of(target));
                    var result = ArtifactJobRunner.run(new ArtifactJobRunner.Request(target + "-" + variant, base.jobDirectory(), root, base.targets(),
                            Map.of("schema", lease.schema(), "variant", variant), base.environment(), base.config(), base.endpoints(), base.timeout()));
                    assertEquals(0, result.code(), result.err());
                }
                try (var rows = statement.executeQuery("select count(*) from parameter_copy")) { assertTrue(rows.next()); assertEquals(variant.equals("scalar") ? 2 : 5, rows.getInt(1)); }
                try (var rows = statement.executeQuery("select string_agg(title,',' order by title) from parameter_values")) { assertTrue(rows.next()); assertEquals(variant.equals("scalar") ? "ele1" : "ele1,ele2", rows.getString(1)); }
            }
            Path root = ArtifactJobRunner.directory("positive-federation");
            var result = ArtifactJobRunner.run(request(root, lease, List.of("federation")));
            assertEquals(0, result.code(), result.err());
            try (var rows = statement.executeQuery("select id,name from federated order by id")) {
                assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); assertEquals("1a", rows.getString(2));
                assertTrue(rows.next()); assertEquals(2, rows.getInt(1)); assertEquals("1b", rows.getString(2)); assertFalse(rows.next());
            }
        }
    }

    @Test void originalTypedFetchSizeChainRelativeSqlAndDestinationDeletion() throws Exception {
        try (var lease = PostgresFixture.lease(); var db = connection(lease); var statement = db.createStatement()) {
            String columns = "(t_id int, aint int, adec decimal(7,1), atext varchar(40), aenum varchar(120), adate date, atimestamp timestamp, aboolean boolean, geom_so geometry(Point,2056))";
            statement.execute("create table source_data" + columns); statement.execute("create table target_data" + columns);
            statement.execute("insert into source_data values(1,2,3.4,'abc',null,'2013-10-21','2015-02-16T08:35:45.000',true,ST_GeomFromText('POINT(2638000 1175250)',2056)),(2,33,44.4,'asdf',null,'2017-12-21','2015-03-16T11:35:45.000',true,ST_GeomFromText('POINT(2648000 1185250)',2056))");
            statement.execute("insert into target_data(t_id) values(9)");
            Path root = ArtifactJobRunner.directory("typed-db2db-chain");
            var result = ArtifactJobRunner.run(request(root, lease, List.of("typed-back"))); assertEquals(0, result.code(), result.err());
            for (String table : List.of("source_data", "target_data")) {
                try (var rows = statement.executeQuery("select t_id,aint,adec,atext,adate,atimestamp,aboolean,ST_AsText(geom_so),ST_SRID(geom_so) from " + table + " order by t_id")) {
                    assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); assertEquals(2, rows.getInt(2)); assertEquals(new java.math.BigDecimal("3.4"), rows.getBigDecimal(3)); assertEquals("abc", rows.getString(4));
                    assertEquals(java.sql.Date.valueOf("2013-10-21"), rows.getDate(5)); assertEquals(java.sql.Timestamp.valueOf("2015-02-16 08:35:45"), rows.getTimestamp(6)); assertTrue(rows.getBoolean(7));
                    assertEquals("POINT(2638000 1175250)", rows.getString(8)); assertEquals(2056, rows.getInt(9));
                    assertTrue(rows.next()); assertEquals(2, rows.getInt(1)); assertEquals(33, rows.getInt(2)); assertEquals(new java.math.BigDecimal("44.4"), rows.getBigDecimal(3)); assertEquals("asdf", rows.getString(4));
                    assertEquals(java.sql.Date.valueOf("2017-12-21"), rows.getDate(5)); assertEquals(java.sql.Timestamp.valueOf("2015-03-16 11:35:45"), rows.getTimestamp(6)); assertTrue(rows.getBoolean(7));
                    assertEquals("POINT(2648000 1185250)", rows.getString(8)); assertEquals(2056, rows.getInt(9)); assertFalse(rows.next());
                }
            }
        }
    }

    private ArtifactJobRunner.Request request(Path root, PostgresFixture.Lease lease, List<String> targets) throws Exception {
        var config = JobRunner.baseConfig(root);
        JobRunner.secret(root, config, "dbPassword", lease.password());
        JobRunner.taskConfig(config, "db", Map.of("jdbcUrl", lease.jdbcUrl(), "user", lease.user(), "passwordSecret", "dbPassword"));
        return new ArtifactJobRunner.Request("database", ArtifactJobRunner.fixture("database"), root, targets,
                Map.of("schema", lease.schema()), Map.of(), config,
                Map.of("postgres", new ArtifactJobRunner.Endpoint(lease.jdbcUrl(), lease.imageJdbcUrl())), Duration.ofSeconds(90));
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
