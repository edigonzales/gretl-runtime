package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;

/** Build/runtime identity used by --version and the run report. */
public final class RuntimeInfo {
    public static final String VERSION = "0.1.0-SNAPSHOT";
    public static final String PACKAGE_IDENTITY = "gretl-runtime-script";

    private RuntimeInfo() {
    }

    public static String inventoryHash(OperationRegistry registry) {
        String canonical = registry.all().stream()
                .map(operation -> operation.id() + "|" + operation.symbol() + "|"
                        + operation.requestType().getName() + "|"
                        + operation.resultType().getName())
                .sorted()
                .collect(Collectors.joining("\n")) + "\n" + VERSION;
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
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
