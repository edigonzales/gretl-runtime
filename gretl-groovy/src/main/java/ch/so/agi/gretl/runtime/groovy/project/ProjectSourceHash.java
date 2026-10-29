package ch.so.agi.gretl.runtime.groovy.project;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Same framed definition-byte identity for source projects and their compiled packages. */
public final class ProjectSourceHash {
    private ProjectSourceHash() { }
    public static String sha256(GretlProject project) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update("gretl-project-source-v1\0".getBytes(StandardCharsets.UTF_8));
            hashFile(digest, project, project.settingsFile());
            for (var entry : project.entries()) hashFile(digest, project, entry.scriptFile());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private static void hashFile(MessageDigest digest, GretlProject project, Path file) throws IOException {
        byte[] path = StreamSupport.stream(project.root().relativize(file).spliterator(), false)
                .map(Path::toString).collect(Collectors.joining("/")).getBytes(StandardCharsets.UTF_8);
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readAllBytes();
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(path.length).array()); digest.update(path);
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array()); digest.update(bytes);
        }
    }
}
