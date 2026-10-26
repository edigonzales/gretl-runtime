package ch.so.agi.gretl.it;

import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Packaging-only qualification. Its test object is explicitly a JAR, independently of the common IMAGE job suite. */
final class SelectedPackageSupport {
    private SelectedPackageSupport() { }
    static void copy(Path source, Path target) throws Exception {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else Files.copy(path, destination);
            }
        }
    }
    static Path fixture(Path root, String job, Map<String, String> resources, List<String> modules, boolean functions) throws Exception {
        Path source = root.resolve("package input with spaces");
        copy(ArtifactJobRunner.fixture(job), source);
        for (var resource : resources.entrySet()) copy(ArtifactJobRunner.fixture(resource.getKey()), source.resolve(resource.getValue()));
        descriptor(source, modules, functions, List.of());
        return source;
    }
    static void descriptor(Path source, List<String> modules, boolean functions, List<String> dependencies) throws Exception {
        List<String> resources;
        try (var files = Files.list(source)) { resources = files.filter(p -> !Set.of("job.groovy", "job-package.json").contains(p.getFileName().toString()))
                .map(p -> p.getFileName().toString()).sorted().toList(); }
        Files.writeString(source.resolve("job-package.json"), Json.write(Map.of("schemaVersion", 2, "name", "selected-package", "version", "1",
                "runtimeVersion", "0.1.0-SNAPSHOT", "entryScript", "job.groovy", "resources", resources,
                "taskModules", modules, "standardFunctions", functions, "dependencies", dependencies)));
    }
    static ArtifactJobRunner.Result detached(ArtifactJobRunner.Request request) throws Exception {
        return detached(ArtifactJobRunner.prepare(request, ArtifactJobRunner.Mode.JAR));
    }
    @SuppressWarnings("unchecked")
    static ArtifactJobRunner.Result detached(ArtifactJobRunner.Prepared prepared) throws Exception {
        var original = prepared.request();
        Path foreign = Files.createTempDirectory("gretl-selected-package with spaces-");
        try {
            for (String directory : List.of("cwd", "output", "work", "reports", "home", "tmp")) Files.createDirectories(foreign.resolve(directory));
            Files.copy(prepared.jar(), foreign.resolve("job.jar"));
            delete(prepared.source());
            Map<String, Object> config = (Map<String, Object>) Json.parse(Json.write(ArtifactJobRunner.jsonValue(original.config())));
            Map<String, Object> runtime = new LinkedHashMap<>((Map<String, Object>) config.getOrDefault("runtime", Map.of()));
            runtime.put("workRoot", foreign.resolve("work").toString()); runtime.put("reportRoot", foreign.resolve("reports").toString()); config.put("runtime", runtime);
            if (config.get("secrets") instanceof Map<?, ?> secrets) {
                for (var entry : secrets.entrySet()) {
                    Map<String, Object> secret = (Map<String, Object>) entry.getValue();
                    if (secret.get("file") instanceof String file) {
                        String name = "secret-" + entry.getKey() + ".txt";
                        Files.copy(original.directory().resolve(file), foreign.resolve(name)); secret.put("file", name);
                    }
                }
            }
            Map<String, Object> parameters = new LinkedHashMap<>();
            original.parameters().forEach((key, value) -> parameters.put(key, relocate(value, original.directory(), foreign)));
            Map<String, Object> environment = new LinkedHashMap<>(original.environment());
            environment.put("PATH", "/gretl-no-build-tools"); environment.put("HOME", foreign.resolve("home"));
            environment.put("JAVA_TOOL_OPTIONS", "-Duser.home=\"" + foreign.resolve("home") + "\" -Djava.io.tmpdir=\"" + foreign.resolve("tmp") + "\""
                    + " " + environment.getOrDefault("JAVA_TOOL_OPTIONS", ""));
            Files.writeString(foreign.resolve("runtime.json"), Json.write(config));
            var request = new ArtifactJobRunner.Request(original.id(), original.jobDirectory(), foreign, original.targets(), parameters,
                    environment, config, original.endpoints(), original.timeout());
            var isolated = new ArtifactJobRunner.Prepared(request, prepared.source(), foreign.resolve("job.jar"), prepared.sha256(), foreign.resolve("reports/report.json"), null);
            var running = ArtifactJobRunner.start(isolated, false);
            ArtifactJobRunner.Result result;
            try { result = ArtifactJobRunner.finish(running, false); }
            finally { if (running.process().isAlive()) ArtifactJobRunner.stop(running); }
            for (String directory : List.of("reports", "output")) {
                delete(original.directory().resolve(directory)); copy(foreign.resolve(directory), original.directory().resolve(directory));
            }
            for (String file : List.of("execution.json", "command.json", "job.stdout.log", "job.stderr.log"))
                Files.copy(foreign.resolve(file), original.directory().resolve(file), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(original.directory().resolve("isolation.json"), Json.write(Map.of("foreignCwd", foreign.toString(),
                    "sourceDeleted", !Files.exists(prepared.source()), "path", environment.get("PATH"), "emptyMavenCache", !Files.exists(foreign.resolve("home/.m2")))));
            return new ArtifactJobRunner.Result(result.code(), result.out(), result.err(), original.directory().resolve("reports/report.json"),
                    original.directory(), result.duration(), result.timedOut(), result.aborted(), result.artifactIdentity(), "JAR");
        } finally { delete(foreign); }
    }
    private static Object relocate(Object value, Path original, Path foreign) {
        return value instanceof Path path && path.startsWith(original) ? foreign.resolve(original.relativize(path)) : value;
    }
    static void delete(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
    }
}
