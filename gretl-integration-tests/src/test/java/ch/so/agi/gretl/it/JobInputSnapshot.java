package ch.so.agi.gretl.it;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Byte inventory, deliberately independent of Groovy, settings and descriptor parsers. */
record JobInputSnapshot(String treeSha256, List<JobInputSnapshot.InputFile> files) {
    record InputFile(String relativePath, long size, String sha256) {}
    JobInputSnapshot { files = List.copyOf(files); }
    static JobInputSnapshot capture(Path root) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
            throw new IOException("Input root must be a real directory");
        var files = new ArrayList<InputFile>();
        try (var walk = Files.walk(root)) {
            for (Path file : walk.sorted().toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("Symlink input: " + root.relativize(file));
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                String name = root.relativize(file).toString().replace('\\', '/');
                // Package metadata is separate from the common fachlicher input identity.
                if (name.equals("job-package.json")) continue;
                files.add(new InputFile(name, Files.size(file), hash(file)));
            }
        }
        files.sort(Comparator.comparing(InputFile::relativePath));
        var canonical = files.stream().map(f -> List.of(f.relativePath(), f.size(), f.sha256())).toList();
        return new JobInputSnapshot(hash(Json.write(List.of("gretl-input-v1", canonical))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), files);
    }
    void verifyUnchanged(Path root) throws IOException {
        if (!equals(capture(root))) throw new IOException("Input changed during execution");
    }
    Map<String, Object> inventory() {
        return Map.of("schemaVersion", 1, "treeSha256", treeSha256, "files", files.stream()
                .map(f -> Map.of("path", f.relativePath(), "size", f.size(), "sha256", f.sha256())).toList());
    }
    static String hash(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            MessageDigest digest = digest();
            byte[] buffer = new byte[65536];
            for (int n; (n = input.read(buffer)) >= 0;) digest.update(buffer, 0, n);
            return HexFormat.of().formatHex(digest.digest());
        }
    }
    static String hash(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
