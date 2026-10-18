package ch.so.agi.gretl.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChecksumManifestTest {
    @TempDir
    Path tempDir;

    @Test
    void writesSortedHashesWithRelativePaths() throws Exception {
        Files.writeString(tempDir.resolve("b.txt"), "b");
        Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub/a.txt"), "a");
        ChecksumManifest.write(tempDir);

        List<String> lines = Files.readAllLines(tempDir.resolve("SHA256SUMS"));
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).endsWith("  b.txt"));
        assertTrue(lines.get(1).endsWith("  sub/a.txt"));
        String hash = lines.get(0).substring(0, 64);
        assertEquals(ChecksumManifest.sha256(tempDir.resolve("b.txt")), hash);
        assertFalse(lines.stream().anyMatch(line -> line.contains(tempDir.toString())));
    }
}
