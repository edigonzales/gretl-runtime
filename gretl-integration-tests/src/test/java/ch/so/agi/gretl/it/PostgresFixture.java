package ch.so.agi.gretl.it;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Shared real PostgreSQL/PostGIS container with isolated schema leases. */
final class PostgresFixture {
    static final String IMAGE =
            "postgis/postgis:16-3.4@sha256:44126d872ac91993766c341e369c539e8196614321765d36a6f1bab0419a5fa5";
    private static PostgreSQLContainer<?> container;

    private PostgresFixture() {
    }

    static synchronized PostgreSQLContainer<?> container() {
        if (container == null) {
            PostgreSQLContainer<?> candidate = new PostgreSQLContainer<>(
                    DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("gretl")
                    .withUsername("gretl_user")
                    .withPassword("gretl_password");
            candidate.start();
            try (Connection connection = DriverManager.getConnection(
                    candidate.getJdbcUrl(), candidate.getUsername(),
                    candidate.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.execute("create extension if not exists postgis");
            } catch (SQLException exception) {
                candidate.stop();
                throw new IllegalStateException("Could not initialize PostGIS", exception);
            }
            container = candidate;
        }
        return container;
    }

    static Lease lease() {
        PostgreSQLContainer<?> running = container();
        String schema = "gretl_" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 16);
        try (Connection connection = DriverManager.getConnection(
                running.getJdbcUrl(), running.getUsername(), running.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("create schema \"" + schema + "\"");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create Postgres lease schema", exception);
        }
        return new Lease(running, schema);
    }

    static final class Lease implements AutoCloseable {
        private final PostgreSQLContainer<?> container;
        private final String schema;
        private boolean closed;

        private Lease(PostgreSQLContainer<?> container, String schema) {
            this.container = container;
            this.schema = schema;
        }

        String schema() {
            return schema;
        }

        String jdbcUrl() {
            String separator = container.getJdbcUrl().contains("?") ? "&" : "?";
            return container.getJdbcUrl() + separator + "currentSchema=" + schema + ",public";
        }

        String user() {
            return container.getUsername();
        }

        String password() {
            return container.getPassword();
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            try (Connection connection = DriverManager.getConnection(
                    container.getJdbcUrl(), container.getUsername(),
                    container.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.execute("drop schema if exists \"" + schema + "\" cascade");
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Could not clean Postgres lease schema " + schema, exception);
            }
        }
    }
}
