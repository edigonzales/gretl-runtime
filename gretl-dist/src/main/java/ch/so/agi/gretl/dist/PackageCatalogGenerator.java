package ch.so.agi.gretl.dist;

import ch.so.agi.gretl.packager.PackageDescriptor.Dependency;
import ch.so.agi.gretl.packager.ResolvedArtifact;
import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Turns Maven's resolved identities and verbose edges into a checksummed release selection. */
public final class PackageCatalogGenerator {
    private PackageCatalogGenerator() { }
    public static void main(String[] args) throws Exception {
        Path dist = Path.of(args[0]).toAbsolutePath().normalize();
        Map<?, ?> resolved = (Map<?, ?>) Json.parseFile(Path.of(args[1]), 32L << 20);
        Map<?, ?> verbose = (Map<?, ?>) Json.parseFile(Path.of(args[2]), 32L << 20);
        String version = args[3];
        Map<String, Dependency> identities = new TreeMap<>();
        collectIdentities(resolved, identities, true);
        Map<String, Set<String>> edges = new TreeMap<>();
        identities.values().forEach(identity -> edges.put(identity.coordinate(), new java.util.TreeSet<>()));
        collectEdges(verbose, identities, edges, true);
        List<Path> jars;
        try (var files = Files.walk(dist.resolve("lib"))) { jars = files.filter(p -> p.toString().endsWith(".jar")).toList(); }
        Map<Path, String> occupied = new LinkedHashMap<>();
        List<Map<String, Object>> artifacts = new ArrayList<>();
        for (Dependency identity : identities.values()) {
            List<Path> matches = jars.stream().filter(p -> p.getFileName().toString().equals(identity.fileName())).toList();
            if (matches.size() != 1) throw new IllegalStateException("Maven identity has no unique distribution input: " + identity.coordinate() + " " + matches);
            Path file = matches.getFirst();
            if (occupied.putIfAbsent(file, identity.coordinate()) != null) throw new IllegalStateException("Two Maven identities share one distribution file: " + file);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("groupId", identity.groupId()); row.put("artifactId", identity.artifactId()); row.put("type", "jar");
            row.put("version", identity.version()); row.put("classifier", identity.classifier());
            row.put("file", dist.relativize(file).toString().replace('\\', '/')); row.put("sha256", ResolvedArtifact.hash(file));
            row.put("dependencies", List.copyOf(edges.get(identity.coordinate()))); artifacts.add(row);
        }
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("schemaVersion", 1); catalog.put("runtimeVersion", version);
        catalog.put("mediation", "Maven resolved runtime graph of this standard distribution");
        catalog.put("runtime", "ch.so.agi.gretl:gretl-job-runtime:" + version);
        catalog.put("taskModules", Map.of("io", "ch.so.agi.gretl:gretl-io-tasks:" + version,
                "database", "ch.so.agi.gretl:gretl-database-tasks:" + version,
                "interlis", "ch.so.agi.gretl:gretl-interlis-tasks:" + version,
                "ilivalidator", "ch.so.agi.gretl:gretl-ilivalidator:" + version));
        List<String> functions = new ArrayList<>();
        for (Object value : (List<?>) resolved.get("children")) {
            Dependency identity = ResolvedArtifact.identity((Map<?, ?>) value);
            if (identity.groupId().equals("io.github.sogis") || identity.groupId().equals("ch.geowerkstatt.ilivalidator.extensions.functions")
                    || identity.classifier().equals("standard-functions")) functions.add(identity.coordinate());
        }
        if (functions.size() != 5) throw new IllegalStateException("Expected the qualified four function libraries and selection resource");
        catalog.put("standardFunctions", functions); catalog.put("artifacts", artifacts);
        String revision = args.length > 4 ? args[4] : "";
        if (revision.isBlank()) {
            Process git = new ProcessBuilder("git", "rev-parse", "HEAD").redirectErrorStream(true).start();
            if (!git.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) { git.destroyForcibly(); git.waitFor(); throw new IllegalStateException("git revision timed out"); }
            revision = new String(git.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
            if (git.exitValue() != 0) throw new IllegalStateException("Set gretl.sourceRevision for a source archive build");
        }
        if (!revision.matches("[0-9a-f]{40}")) throw new IllegalStateException("Source revision must be an exact Git SHA");
        boolean dirty;
        Path status = Files.createTempFile("gretl-source-status-", ".txt");
        try {
            Process git = new ProcessBuilder("git", "status", "--porcelain", "--untracked-files=normal")
                    .redirectErrorStream(true).redirectOutput(status.toFile()).start();
            if (!git.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) { git.destroyForcibly(); git.waitFor(); throw new IllegalStateException("git status timed out"); }
            dirty = git.exitValue() != 0 || !Files.readString(status).isBlank();
        } finally { Files.deleteIfExists(status); }
        catalog.put("sourceRevision", revision); catalog.put("sourceDirty", dirty);
        Files.writeString(dist.resolve("package-catalog.json"), Json.write(catalog));
        @SuppressWarnings("unchecked") Map<String, Object> inventory = (Map<String, Object>) Json.parseFile(dist.resolve("inventory.json"), 1L << 20);
        inventory.put("sourceRevision", revision); inventory.put("sourceDirty", dirty); inventory.put("packageCatalog", "package-catalog.json");
        Files.writeString(dist.resolve("inventory.json"), Json.write(inventory));
    }
    private static void collectIdentities(Map<?, ?> node, Map<String, Dependency> identities, boolean root) throws Exception {
        if (!root) {
            Dependency identity = ResolvedArtifact.identity(node);
            Dependency previous = identities.putIfAbsent(identity.key(), identity);
            if (previous != null && !previous.equals(identity)) throw new IllegalStateException("Unmediated Maven versions: " + identity.key());
        }
        if (node.get("children") instanceof List<?> children)
            for (Object child : children) collectIdentities((Map<?, ?>) child, identities, false);
    }
    private static void collectEdges(Map<?, ?> node, Map<String, Dependency> identities, Map<String, Set<String>> edges, boolean root) throws Exception {
        Dependency identity = root ? null : ResolvedArtifact.identity(node);
        Dependency selected = root ? null : identities.get(identity.key());
        if (node.get("children") instanceof List<?> children) {
            for (Object value : children) {
                Map<?, ?> child = (Map<?, ?>) value;
                Dependency requested = ResolvedArtifact.identity(child);
                Dependency actual = identities.get(requested.key());
                if (actual == null) throw new IllegalStateException("Maven edge outside resolved graph: " + requested.coordinate());
                if (!root && identity.equals(selected)) edges.get(selected.coordinate()).add(actual.coordinate());
                collectEdges(child, identities, edges, false);
            }
        }
    }
}
