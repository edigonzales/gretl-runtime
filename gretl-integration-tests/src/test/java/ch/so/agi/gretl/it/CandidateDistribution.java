package ch.so.agi.gretl.it;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Verifies actual reactor distribution bytes, including overrides used by negative probes. */
final class CandidateDistribution {
    private static CandidateDistribution candidate;
    private final Path root;
    private final String sourceRevision;
    private final boolean sourceDirty;
    private final String manifestSha256;
    private CandidateDistribution(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        rejectSymlinks();
        var catalog = (Map<?, ?>) Json.parseFile(this.root.resolve("package-catalog.json"), 32L << 20);
        sourceRevision = (String) catalog.get("sourceRevision");
        sourceDirty = !Boolean.FALSE.equals(catalog.get("sourceDirty"));
        if (sourceRevision == null || !sourceRevision.matches("[0-9a-f]{40}")) throw new IOException("Invalid distribution source revision");
        manifestSha256 = JobInputSnapshot.hash(this.root.resolve("SHA256SUMS"));
        verifyFiles();
    }
    static synchronized CandidateDistribution resolve() throws IOException {
        if (candidate == null) {
            var reactor = at(Path.of(System.getProperty("gretl.test.reactorDistribution", "../gretl-dist/target/dist")));
            String override = System.getProperty("gretl.test.distribution");
            var selected = override == null ? reactor : at(Path.of(override));
            if (!selected.sourceRevision.equals(reactor.sourceRevision)
                    || !selected.manifestSha256.equals(reactor.manifestSha256))
                throw new IOException("Distribution override differs from the reactor candidate");
            candidate = selected;
        }
        String expected = System.getProperty("gretl.test.candidateSha");
        if (expected != null) {
            candidate.requireRevision(expected);
            if (candidate.sourceDirty) throw new IOException("Candidate distribution is dirty");
        }
        return candidate;
    }
    static CandidateDistribution at(Path root) throws IOException { return new CandidateDistribution(root); }
    Path root() { return root; }
    Path launcher() { return root.resolve("bin/gretl"); }
    String sourceRevision() { return sourceRevision; }
    boolean sourceDirty() { return sourceDirty; }
    String manifestSha256() { return manifestSha256; }
    void requireRevision(String expected) throws IOException {
        if (!sourceRevision.equals(expected)) throw new IOException("Wrong candidate distribution revision");
    }
    private void rejectSymlinks() throws IOException {
        try (var walk = Files.walk(root)) {
            if (walk.anyMatch(Files::isSymbolicLink)) throw new IOException("Distribution symlink");
        }
    }
    void verifyFiles() throws IOException {
        rejectSymlinks();
        if (!manifestSha256.equals(JobInputSnapshot.hash(root.resolve("SHA256SUMS")))) throw new IOException("Distribution manifest changed");
        Set<String> listed = new HashSet<>();
        for (String line : Files.readAllLines(root.resolve("SHA256SUMS"))) {
            String[] parts = line.split("  ", 2);
            if (parts.length != 2 || !parts[0].matches("[0-9a-f]{64}") || !listed.add(parts[1])) throw new IOException("Invalid distribution manifest");
            Path file = root.resolve(parts[1]).normalize();
            if (!file.startsWith(root) || Files.isSymbolicLink(file) || !Files.isRegularFile(file)
                    || !JobInputSnapshot.hash(file).equals(parts[0])) throw new IOException("Distribution byte mismatch: " + parts[1]);
        }
        try (var walk = Files.walk(root)) {
            for (Path file : walk.toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("Distribution symlink");
                String name = root.relativize(file).toString().replace('\\', '/');
                if (Files.isRegularFile(file) && !name.equals("SHA256SUMS") && !listed.contains(name))
                    throw new IOException("Uncatalogued distribution file: " + name);
            }
        }
        if (!Files.isExecutable(launcher())) throw new IOException("Candidate launcher is not executable");
    }
    @Override public String toString() { return "CandidateDistribution[" + sourceRevision + "]"; }
}
