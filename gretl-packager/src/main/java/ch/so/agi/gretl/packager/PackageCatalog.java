package ch.so.agi.gretl.packager;

import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A release's Maven-resolved graph; never discovers input JARs from java.class.path. */
final class PackageCatalog {
    private final Map<?, ?> catalog;
    private final Map<String, ResolvedArtifact> artifacts = new LinkedHashMap<>();
    private final Map<String, List<String>> edges = new LinkedHashMap<>();

    PackageCatalog(Path catalogFile, String version) throws PackageException {
        try {
            if (!(Json.parseFile(catalogFile, 16L << 20) instanceof Map<?, ?> parsed)
                    || !Long.valueOf(1).equals(parsed.get("schemaVersion")) || !version.equals(parsed.get("runtimeVersion")))
                throw new PackageException("PACKAGE_CATALOG_VERSION", "No matching package catalog for runtime " + version);
            catalog = parsed;
            Path root = catalogFile.toAbsolutePath().normalize().getParent();
            for (Object entry : (List<?>) catalog.get("artifacts")) {
                Map<?, ?> row = (Map<?, ?>) entry;
                var identity = ResolvedArtifact.identity(row);
                Path file = root.resolve((String) row.get("file")).normalize();
                if (!file.startsWith(root) || !Files.isRegularFile(file) || !file.toRealPath().startsWith(root.toRealPath()))
                    throw new PackageException("PACKAGE_CATALOG_PATH", "Unsafe/missing catalog input: " + identity.coordinate());
                var artifact = new ResolvedArtifact(identity, file, (String) row.get("sha256"));
                if (artifacts.putIfAbsent(identity.coordinate(), artifact) != null)
                    throw new PackageException("PACKAGE_CATALOG_DUPLICATE", "Duplicate catalog identity: " + identity.coordinate());
                edges.put(identity.coordinate(), ((List<?>) row.get("dependencies")).stream().map(String.class::cast).toList());
            }
        } catch (PackageException exception) { throw exception; }
        catch (Exception exception) { throw new PackageException("PACKAGE_CATALOG_INVALID", "Cannot load package catalog: " + catalogFile, exception); }
    }

    List<ResolvedArtifact> select(PackageDescriptor descriptor) throws PackageException {
        Set<String> roots = new LinkedHashSet<>(); roots.add((String) catalog.get("runtime"));
        Map<?, ?> modules = (Map<?, ?>) catalog.get("taskModules");
        List<?> functions = (List<?>) catalog.get("standardFunctions");
        if (descriptor.schemaVersion() == 1) {
            modules.values().forEach(value -> roots.add((String) value));
            functions.forEach(value -> roots.add((String) value));
        } else {
            descriptor.taskModules().forEach(module -> roots.add((String) modules.get(module)));
            if (descriptor.standardFunctions()) functions.forEach(value -> roots.add((String) value));
        }
        List<PackageDescriptor.Dependency> external = new ArrayList<>();
        for (var dependency : descriptor.dependencies()) {
            if (artifacts.containsKey(dependency.coordinate())) roots.add(dependency.coordinate());
            else external.add(dependency);
        }
        Set<String> selected = new LinkedHashSet<>();
        for (String root : roots) visit(root, selected);
        Map<String, ResolvedArtifact> byKey = new LinkedHashMap<>();
        for (String coordinate : selected) add(byKey, artifacts.get(coordinate));
        for (var dependency : external) {
            ResolvedArtifact qualified = byKey.get(dependency.key());
            if (qualified != null && !qualified.identity().equals(dependency))
                throw new PackageException("PACKAGE_DEPENDENCY_CONFLICT", "Declared version conflicts with qualified runtime: "
                        + dependency.coordinate() + " / " + qualified.identity().coordinate());
        }
        for (ResolvedArtifact artifact : new MavenArtifactResolver().resolveArtifacts(external)) add(byKey, artifact);
        for (var dependency : descriptor.dependencies()) {
            ResolvedArtifact actual = byKey.get(dependency.key());
            if (actual == null || !actual.identity().equals(dependency))
                throw new PackageException("PACKAGE_DEPENDENCY_MISSING", "Declared artifact was not resolved exactly: " + dependency.coordinate());
        }
        var result = byKey.values().stream().sorted(java.util.Comparator.comparing(a -> a.identity().coordinate())).toList();
        for (ResolvedArtifact artifact : result) artifact.verify();
        return result;
    }
    private static void add(Map<String, ResolvedArtifact> selected, ResolvedArtifact artifact) throws PackageException {
        ResolvedArtifact previous = selected.putIfAbsent(artifact.identity().key(), artifact);
        if (previous != null && (!previous.identity().equals(artifact.identity()) || !previous.sha256().equals(artifact.sha256())))
            throw new PackageException("PACKAGE_DEPENDENCY_CONFLICT", "Conflicting artifact identity/version/bytes: " + previous.identity().coordinate() + " / " + artifact.identity().coordinate());
    }
    private void visit(String coordinate, Set<String> selected) throws PackageException {
        if (!artifacts.containsKey(coordinate)) throw new PackageException("PACKAGE_CATALOG_MISSING", "Catalog has no artifact: " + coordinate);
        if (!selected.add(coordinate)) return;
        for (String child : edges.get(coordinate)) visit(child, selected);
    }
}
