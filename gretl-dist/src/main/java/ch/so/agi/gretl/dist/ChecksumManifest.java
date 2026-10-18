package ch.so.agi.gretl.dist;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Build-time SHA-256 manifest for a staged distribution directory. */
public final class ChecksumManifest {
    private ChecksumManifest() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: ChecksumManifest <dist-dir>");
        }
        write(Path.of(args[0]));
    }

    public static void write(Path root) throws IOException {
        Path base = root.toAbsolutePath().normalize();
        Path manifest = base.resolve("SHA256SUMS");
        List<Path> files;
        try (var walk = Files.walk(base)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(manifest))
                    .sorted(Comparator.comparing(path ->
                            base.relativize(path).toString().replace('\\', '/')))
                    .toList();
        }
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(manifest,
                StandardCharsets.UTF_8))) {
            for (Path file : files) {
                writer.println(sha256(file) + "  "
                        + base.relativize(file).toString().replace('\\', '/'));
            }
        }
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
