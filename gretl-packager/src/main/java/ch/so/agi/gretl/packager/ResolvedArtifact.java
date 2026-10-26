package ch.so.agi.gretl.packager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Maven identity and the exact input bytes of one ordinary library JAR. */
public record ResolvedArtifact(PackageDescriptor.Dependency identity, Path file, String sha256) {
    public ResolvedArtifact {
        file = file.toAbsolutePath().normalize();
    }
    public static PackageDescriptor.Dependency identity(Map<?, ?> node) throws PackageException {
        if (!"jar".equals(node.get("type"))) throw new PackageException("PACKAGE_ARTIFACT_TYPE", "Only JAR program artifacts are supported: " + node);
        return new PackageDescriptor.Dependency(text(node, "groupId"), text(node, "artifactId"), text(node, "version"),
                String.valueOf(node.get("classifier") == null ? "" : node.get("classifier")));
    }
    private static String text(Map<?, ?> node, String key) throws PackageException {
        if (!(node.get(key) instanceof String text) || text.isBlank())
            throw new PackageException("PACKAGE_ARTIFACT_IDENTITY", "Missing Maven identity field: " + key);
        return text;
    }
    public static ResolvedArtifact read(PackageDescriptor.Dependency identity, Path file) throws PackageException {
        return new ResolvedArtifact(identity, file, hash(file));
    }
    public void verify() throws PackageException {
        if (!sha256.equals(hash(file))) throw new PackageException("PACKAGE_ARTIFACT_CHECKSUM", "Artifact bytes differ from the qualified catalog: " + identity.coordinate());
    }
    public static String hash(Path file) throws PackageException {
        try (var input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | java.security.NoSuchAlgorithmException exception) {
            throw new PackageException("PACKAGE_ARTIFACT_READ", "Cannot read artifact: " + file, exception);
        }
    }
}
