package ch.so.agi.gretl.job.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Build/runtime identity used by --version and the run report. */
public final class RuntimeInfo {
    public static final String VERSION = "6.0.0-SNAPSHOT";
    public static final String PACKAGE_IDENTITY = "gretl-runtime-script";

    private RuntimeInfo() {
    }

    public static String scriptHash(Path script) {
        try {
            return sha256(Files.readAllBytes(script));
        } catch (java.io.IOException exception) {
            throw new StartException(2, "Job file could not be read: " + script,
                    exception);
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
