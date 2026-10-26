package ch.so.agi.gretl.tasks.io.http;

import java.net.URI;
import java.nio.file.Path;

/** Completed HTTP download. */
public record HttpDownloadResult(Path file, URI finalUri, int status, long bytes, String sha256) {
    public HttpDownloadResult {
        java.util.Objects.requireNonNull(file, "file");
        java.util.Objects.requireNonNull(finalUri, "finalUri");
        if (status < 200 || status > 299) {
            throw new IllegalArgumentException("status must be 2xx");
        }
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes must not be negative");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be lowercase hex");
        }
    }
}
