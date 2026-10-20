package ch.so.agi.gretl.internal.sql;

public record DatabaseSpec(String jdbcUrl, String username, String password) {

    public DatabaseSpec {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl must not be null or blank");
        }
    }
}
