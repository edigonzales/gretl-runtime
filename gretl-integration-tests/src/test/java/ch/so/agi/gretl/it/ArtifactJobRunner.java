package ch.so.agi.gretl.it;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Artifact boundary: regular distribution packager, then a fresh java -jar process.
 * Capture uses OS file redirection: no pipe can deadlock or remain undrained.
 * Evidence survives JUnit temp cleanup under target/job-evidence.
 */
final class ArtifactJobRunner {
    enum Mode { JAR, IMAGE }
    private static final Map<String, Path> BUILT_FIXTURES = new LinkedHashMap<>();
    record Endpoint(String host, String image) {}
    record Request(String id, Path jobDirectory, Path directory, List<String> targets,
                   Map<String, ?> parameters, Map<String, ?> environment,
                   Map<String, Object> config, Map<String, Endpoint> endpoints,
                   Duration timeout) {
        Request {
            targets = List.copyOf(targets);
            parameters = Map.copyOf(parameters);
            environment = Map.copyOf(environment);
            endpoints = Map.copyOf(endpoints);
            for (var values : List.of(parameters, environment)) values.forEach((key, value) -> {
                if (!(value instanceof String || value instanceof Path))
                    throw new IllegalArgumentException("Request value must be text or a local Path: " + key);
            });
            if (!id.matches("[A-Za-z0-9._-]+") || timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("Invalid scenario identity or timeout");
            }
        }
    }
    record Prepared(Request request, Path source, Path jar, String sha256, Path report, ImageExecutionAdapter.Launch image) {}
    record Result(int code, String out, String err, Path report, Path directory,
                  Duration duration, boolean timedOut, boolean aborted, String artifactIdentity, String mode) {
        boolean hasValidArtifactIdentity() {
            return artifactIdentity.matches(mode.equals("IMAGE") ? "sha256:[0-9a-f]{64}" : "[0-9a-f]{64}");
        }
    }
    record Running(Prepared prepared, Process process, Path out, Path err, Instant started, boolean script) {}

    static Path directory(String id) throws IOException {
        Path root = Path.of("target", "job-evidence", id + "-" + UUID.randomUUID())
                .toAbsolutePath().normalize();
        Files.createDirectories(root);
        privateDirectory(root);
        Files.createDirectories(root.resolve("output"));
        return root;
    }

    static Path fixture(String id) throws Exception {
        return Path.of(ArtifactJobRunner.class.getResource("/jobs/" + id).toURI());
    }

    static Prepared prepare(Request request) throws Exception {
        Mode mode = Mode.valueOf(System.getProperty("gretl.test.executionMode", "JAR"));
        Path source = request.directory().resolve("source");
        if (Files.exists(source)) throw new IOException("Scenario work copy already exists: " + source);
        try (var paths = Files.walk(request.jobDirectory())) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) throw new IOException("Symlink fixture: " + path);
                Path target = source.resolve(request.jobDirectory().relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else Files.copy(path, target);
            }
        }
        Files.createDirectories(request.directory().resolve("cwd"));
        Files.createDirectories(request.directory().resolve("reports"));
        Files.writeString(request.directory().resolve("runtime.json"), Json.write(jsonValue(request.config())));
        Path report = request.directory().resolve("reports/report.json");
        if (mode == Mode.IMAGE) {
            var image = ImageExecutionAdapter.prepare(request, source, ImageExecutionAdapter.Settings.configured(), new ImageExecutionAdapter.Docker());
            return new Prepared(request, source, null, image.imageId(), report, image);
        }
        List<String> resources;
        try (var paths = Files.list(source)) {
            resources = paths.filter(p -> !p.getFileName().toString().equals("job.groovy")
                            && !p.getFileName().toString().equals("job-package.json"))
                    .map(p -> p.getFileName().toString()).sorted().toList();
        }
        Path descriptor = source.resolve("job-package.json");
        if (!Files.exists(descriptor)) {
            Files.writeString(descriptor, Json.write(Map.of(
                    "schemaVersion", 1, "name", "fixture", "version", "1",
                    "runtimeVersion", "0.1.0-SNAPSHOT", "entryScript", "job.groovy",
                    "sources", List.of(), "resources", resources, "dependencies", List.of())));
        }
        Path distribution = Path.of("..", "gretl-dist", "target", "dist").toAbsolutePath().normalize();
        Path launcher = distribution.resolve("bin/gretl");
        if (!Files.isRegularFile(launcher)) throw new IOException("Candidate distribution missing: " + launcher);
        Path jar = request.directory().resolve("job.jar");
        List<String> command = List.of(launcher.toString(), "package", descriptor.toString(),
                "--output", jar.toString());
        MessageDigest fixtureDigest = MessageDigest.getInstance("SHA-256");
        try (var paths = Files.walk(source)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                fixtureDigest.update(source.relativize(file).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                fixtureDigest.update(Files.readAllBytes(file));
            }
        }
        // Per test JVM only: reuse a regular packager build of byte-identical fixtures.
        // Every execution still gets its own job.jar, process, parameters and filesystem.
        String key = HexFormat.of().formatHex(fixtureDigest.digest());
        Path built = BUILT_FIXTURES.get(key);
        if (built != null) {
            Files.createLink(jar, built);
            Files.writeString(request.directory().resolve("package-reuse.txt"), built.toString());
        } else {
            Process packaging = process(command, request.directory(), request.environment(), "package").start();
            try {
                if (!packaging.waitFor(120, TimeUnit.SECONDS)) throw new IOException("Packaging timed out: " + request.id());
            } finally { if (packaging.isAlive()) stop(packaging); }
            if (packaging.exitValue() != 0 || !Files.isRegularFile(jar)) {
                throw new IOException("Packaging failed (not a job result): "
                        + Files.readString(request.directory().resolve("package.stderr.log")));
            }
            BUILT_FIXTURES.put(key, jar);
        }
        String hash;
        try (var input = Files.newInputStream(jar)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
            hash = HexFormat.of().formatHex(digest.digest());
        }
        Files.writeString(request.directory().resolve("runtime.json"), Json.write(jsonValue(request.config())));
        return new Prepared(request, source, jar, hash, report, null);
    }

    static Running start(Prepared prepared, boolean script) throws IOException {
        if (!script && prepared.image() != null) {
            Files.writeString(prepared.request().directory().resolve("command.json"), Json.write(prepared.image().arguments()));
            return new Running(prepared, prepared.image().start(prepared.request().directory()),
                    prepared.request().directory().resolve("job.stdout.log"), prepared.request().directory().resolve("job.stderr.log"), Instant.now(), false);
        }
        List<String> command = new ArrayList<>();
        if (script) {
            command.add(Path.of("..", "gretl-dist", "target", "dist", "bin", "gretl")
                    .toAbsolutePath().normalize().toString());
            command.add("run");
            command.add(prepared.source().resolve("job.groovy").toString());
        } else {
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            command.add("-Dfile.encoding=UTF-8");
            command.add("-Duser.timezone=UTC");
            command.add("-Duser.language=de");
            command.add("-jar");
            command.add(prepared.jar().toString());
        }
        command.addAll(List.of("--config", prepared.request().directory().resolve("runtime.json").toString(),
                "--report", prepared.report().toString(), "--log-format", "json"));
        for (String target : prepared.request().targets()) command.addAll(List.of("--target", target));
        prepared.request().parameters().forEach((key, value) -> command.addAll(List.of("--param", key + "=" + value)));
        Files.writeString(prepared.request().directory().resolve("command.json"), Json.write(command));
        Instant started = Instant.now();
        ProcessBuilder builder = process(command, prepared.request().directory(), prepared.request().environment(), "job");
        builder.directory(prepared.request().directory().resolve("cwd").toFile());
        Process process = builder.start();
        return new Running(prepared, process, prepared.request().directory().resolve("job.stdout.log"),
                prepared.request().directory().resolve("job.stderr.log"), started, script);
    }

    static Result run(Request request) throws Exception {
        Running running = start(prepare(request), false);
        try {
            return finish(running, false);
        } finally {
            if (running.process().isAlive()) stop(running);
        }
    }

    static Result finish(Running running, boolean aborted) throws Exception {
        Process process = running.process();
        boolean timedOut = !process.waitFor(running.prepared().request().timeout().toMillis(), TimeUnit.MILLISECONDS);
        if (timedOut) stop(running);
        if (!running.script() && running.prepared().image() != null) running.prepared().image().remove();
        // waitFor/stop has reaped the process; redirected streams are now complete.
        Request request = running.prepared().request();
        var registry = new ch.so.agi.gretl.runtime.runner.SecretRegistry();
        if (request.config().get("secrets") instanceof Map<?, ?> secrets) {
            for (Object value : secrets.values()) {
                if (value instanceof Map<?, ?> secret) {
                    String clear = null;
                    if (secret.get("file") instanceof String file) {
                        clear = Files.readString(request.directory().resolve(file));
                        if (clear.endsWith("\r\n")) clear = clear.substring(0, clear.length() - 2);
                        else if (clear.endsWith("\n")) clear = clear.substring(0, clear.length() - 1);
                    } else if (secret.get("environment") instanceof String key) {
                        Object environmentValue = request.environment().get(key);
                        clear = environmentValue == null ? System.getenv(key) : environmentValue.toString();
                    }
                    if (clear != null) registry.register(clear);
                }
            }
        }
        var redactor = new ch.so.agi.gretl.runtime.logging.Redactor(registry);
        String out = redactor.redact(Files.readString(running.out()));
        String err = redactor.redact(Files.readString(running.err()));
        Files.writeString(running.out(), out);
        Files.writeString(running.err(), err);
        String identity = running.script()
                ? HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                        Files.readAllBytes(running.prepared().source().resolve("job.groovy"))))
                : running.prepared().sha256();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("scenario", request.id());
        evidence.put("mode", running.script() ? "SCRIPT" : running.prepared().image() == null ? "JAR" : "IMAGE");
        evidence.put("sha256", identity);
        evidence.put("exitCode", process.exitValue());
        evidence.put("timedOut", timedOut);
        evidence.put("aborted", aborted);
        evidence.put("durationMillis", Duration.between(running.started(), Instant.now()).toMillis());
        evidence.put("endpoints", request.endpoints().entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().host())));
        evidence.put("report", running.prepared().report().toString());
        Files.writeString(request.directory().resolve("execution.json"), Json.write(evidence));
        return new Result(process.exitValue(), out, err, running.prepared().report(), request.directory(),
                Duration.between(running.started(), Instant.now()), timedOut, aborted, identity, evidence.get("mode").toString());
    }

    static void cancel(Running running) throws Exception {
        if (!running.script() && running.prepared().image() != null) running.prepared().image().cancel();
        else running.process().destroy();
    }

    static void stop(Running running) throws Exception {
        try { cancel(running); }
        finally {
            stop(running.process());
            if (!running.script() && running.prepared().image() != null) running.prepared().image().remove();
        }
    }

    static void stop(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(35, TimeUnit.SECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor();
        }
    }

    static ProcessBuilder process(List<String> command, Path directory,
                                           Map<String, ?> environment, String prefix) {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        for (String key : List.of("CLASSPATH", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_OPTS")) {
            builder.environment().remove(key);
        }
        for (String key : List.of("GRETL_JOB_NAME", "GRETL_CORRELATION_ID", "GRETL_JOB_REVISION",
                "GRETL_TERMINATION_MESSAGE_PATH", "GRETL_DEPLOYMENT_ENVIRONMENT", "GRETL_LANGUAGE")) {
            builder.environment().remove(key);
        }
        builder.environment().put("GRETL_LANGUAGE", "de");
        environment.forEach((key, value) -> builder.environment().put(key, value.toString()));
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        builder.environment().put("TZ", "UTC");
        builder.environment().put("LANG", "C.UTF-8");
        return builder.redirectOutput(directory.resolve(prefix + ".stdout.log").toFile())
                .redirectError(directory.resolve(prefix + ".stderr.log").toFile());
    }

    /** Keep Path as a type in the request; serialize it only at the JAR/config boundary. */
    static Object jsonValue(Object value) {
        if (value instanceof Path path) return path.toString();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, entry) -> result.put(key.toString(), jsonValue(entry)));
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(ArtifactJobRunner::jsonValue).toList();
        return value;
    }

    private static void privateDirectory(Path directory) throws IOException {
        try { Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------")); }
        catch (UnsupportedOperationException ignored) { }
    }
}
