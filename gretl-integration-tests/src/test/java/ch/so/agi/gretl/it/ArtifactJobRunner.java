package ch.so.agi.gretl.it;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Process boundary: actual distribution SCRIPT by default, optional JAR or IMAGE.
 * Capture uses OS file redirection: no pipe can deadlock or remain undrained.
 * Workspaces retain raw process files. FailedProcessDiagnostics stages bounded excerpts.
 */
final class ArtifactJobRunner {
    enum Mode { SCRIPT, IMAGE, JAR }
    enum InputKind { SCRIPT_FILE, PROJECT_DIRECTORY }
    record JobInput(InputKind kind, String relativePath) {
        JobInput {
            java.util.Objects.requireNonNull(kind);
            Path path = Path.of(relativePath);
            if (relativePath.isBlank() || path.isAbsolute() || path.normalize().startsWith(".."))
                throw new IllegalArgumentException("Input must stay within the staged root");
            relativePath = path.normalize().toString();
            if (relativePath.isEmpty()) relativePath = ".";
        }
        static JobInput script(String file) { return new JobInput(InputKind.SCRIPT_FILE, file); }
        static JobInput project(String directory) { return new JobInput(InputKind.PROJECT_DIRECTORY, directory); }
        Path resolveAgainst(Path source) {
            Path selected = source.resolve(relativePath).toAbsolutePath().normalize();
            if (Files.exists(selected, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    && (kind == InputKind.SCRIPT_FILE ? !Files.isRegularFile(selected, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    : !Files.isDirectory(selected, java.nio.file.LinkOption.NOFOLLOW_LINKS)))
                throw new IllegalArgumentException("Selected input type does not match " + kind);
            // Missing selections intentionally reach the real CLI.
            return selected;
        }
    }
    enum Verb { RUN, CHECK, PLAN }
    record Invocation(Verb verb, boolean help, String graphFormat, Path graphOutput) {
        Invocation {
            java.util.Objects.requireNonNull(verb);
            if ((graphFormat != null || graphOutput != null) && (verb != Verb.PLAN || help)
                    || graphOutput != null && graphFormat == null
                    || graphFormat != null && !List.of("mermaid", "plantuml").contains(graphFormat))
                throw new IllegalArgumentException("Invalid graph invocation");
            if (help && verb != Verb.RUN) throw new IllegalArgumentException("Job help uses RUN");
        }
        static Invocation run() { return new Invocation(Verb.RUN, false, null, null); }
        static Invocation check() { return new Invocation(Verb.CHECK, false, null, null); }
        static Invocation plan() { return plan(null, null); }
        static Invocation plan(String format, Path output) { return new Invocation(Verb.PLAN, false, format, output); }
        static Invocation jobHelp() { return new Invocation(Verb.RUN, true, null, null); }
    }
    record Endpoint(String host, String image) {}
    record Request(String id, Path jobDirectory, Path directory, List<String> targets,
                   Map<String, ?> parameters, Map<String, ?> environment,
                   Map<String, Object> config, Map<String, Endpoint> endpoints,
                   Duration timeout, JobInput input, Invocation invocation) {
        Request(String id, Path jobDirectory, Path directory, List<String> targets,
                Map<String, ?> parameters, Map<String, ?> environment, Map<String, Object> config,
                Map<String, Endpoint> endpoints, Duration timeout) {
            this(id, jobDirectory, directory, targets, parameters, environment, config, endpoints, timeout,
                    JobInput.script("job.groovy"), Invocation.run());
        }
        Request {
            targets = List.copyOf(targets);
            parameters = Map.copyOf(parameters);
            environment = Map.copyOf(environment);
            config = copyConfig(config);
            endpoints = Map.copyOf(endpoints);
            java.util.Objects.requireNonNull(input);
            java.util.Objects.requireNonNull(invocation);
            for (var values : List.of(parameters, environment)) values.forEach((key, value) -> {
                if (!(value instanceof String || value instanceof Path))
                    throw new IllegalArgumentException("Request value must be text or a local Path: " + key);
            });
            if (!id.matches("[A-Za-z0-9._-]+") || timeout.isNegative() || timeout.isZero())
                throw new IllegalArgumentException("Invalid scenario identity or timeout");
        }
        @Override public String toString() { return "Request[" + id + ", " + input.kind() + "]"; }
    }
    sealed interface JobLaunch permits ScriptLaunch, JarLaunch, ImageLaunch {
        Mode mode();
        CandidateDistribution distribution();
    }
    record ScriptLaunch(CandidateDistribution distribution, List<String> arguments) implements JobLaunch {
        ScriptLaunch { java.util.Objects.requireNonNull(distribution); arguments = List.copyOf(arguments); }
        public Mode mode() { return Mode.SCRIPT; }
    }
    record JarLaunch(CandidateDistribution distribution, Path jar, String sha256) implements JobLaunch {
        JarLaunch {
            java.util.Objects.requireNonNull(distribution);
            jar = jar.toAbsolutePath().normalize();
            if (!sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid JAR identity");
        }
        public Mode mode() { return Mode.JAR; }
    }
    record ImageLaunch(CandidateDistribution distribution, ImageExecutionAdapter.Launch image) implements JobLaunch {
        public Mode mode() { return Mode.IMAGE; }
    }
    record Prepared(Request request, Path source, Path report, JobInputSnapshot inputSnapshot, JobLaunch launch) {
        Prepared { java.util.Objects.requireNonNull(launch); java.util.Objects.requireNonNull(inputSnapshot); }
        Mode mode() { return launch.mode(); }
        Path jar() { return launch instanceof JarLaunch jar ? jar.jar() : null; }
        String sha256() { return launch instanceof JarLaunch jar ? jar.sha256()
                : launch instanceof ImageLaunch image ? image.image().imageId() : inputSnapshot.treeSha256(); }
        ImageExecutionAdapter.Launch image() { return launch instanceof ImageLaunch image ? image.image() : null; }
        Prepared withReport(Path newReport) throws IOException {
            JobLaunch changed = launch instanceof ScriptLaunch script
                    ? ScriptExecutionAdapter.prepare(request, source, newReport, script.distribution())
                    : launch instanceof ImageLaunch image ? new ImageLaunch(image.distribution(),image.image().withReport(newReport,request)) : launch;
            return new Prepared(request, source, newReport, inputSnapshot, changed);
        }
    }
    static Prepared existingJar(Request request, Path source, Path jar, Path report,
                                 JobInputSnapshot snapshot) throws IOException {
        return new Prepared(request, source, report, snapshot,
                new JarLaunch(CandidateDistribution.resolve(), jar, JobInputSnapshot.hash(jar)));
    }
    record Result(int code, String out, String err, Path report, Path directory,
                  Duration duration, boolean timedOut, boolean aborted, String artifactIdentity, String mode, boolean forciblyKilled) {
        boolean hasValidArtifactIdentity() {
            return artifactIdentity.matches(mode.equals("IMAGE") ? "sha256:[0-9a-f]{64}" : "[0-9a-f]{64}");
        }
    }
    record Running(Prepared prepared, Process process, Path out, Path err, long startedNanos) {}

    static Path directory(String id) throws IOException {
        return directory(id, Mode.SCRIPT);
    }

    static Path directory(String id, Mode mode) throws IOException {
        Path base = Path.of("target", "job-evidence");
        if (mode == Mode.IMAGE) {
            String workspace = System.getProperty("gretl.test.imageWorkspace");
            if (workspace == null || !Files.isDirectory(Path.of(workspace)))
                throw new IOException("IMAGE requires the private workspace created by python3.12 scripts/verify-runtime.py");
            base = Path.of(workspace);
        }
        Path root = base.resolve(id + "-" + UUID.randomUUID()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        privateDirectory(root);
        Files.createDirectories(root.resolve("output"));
        return root;
    }

    static Path fixture(String id) throws Exception {
        return Path.of(ArtifactJobRunner.class.getResource("/jobs/" + id).toURI());
    }

    static Prepared prepare(Request request) throws Exception {
        return prepare(request, Mode.SCRIPT);
    }

    /** Explicit JAR selection is used only by tests of the packager artifact itself. */
    static Prepared prepare(Request request, Mode mode) throws Exception {
        FailedProcessDiagnostics.register(request, mode);
        try { return prepareInWorkspace(request, mode); }
        catch (Exception e) {
            JobExecutionEvidence.failure(request, mode, "PREPARE_FAILED", e);
            throw e;
        }
    }

    private static Prepared prepareInWorkspace(Request request, Mode mode) throws Exception {
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
        request = stagedRequest(request, source);
        Files.createDirectories(request.directory().resolve("cwd"));
        Files.createDirectories(request.directory().resolve("reports"));
        Files.writeString(request.directory().resolve("runtime.json"), Json.write(jsonValue(request.config())));
        Path report = request.directory().resolve("reports/report.json");
        CandidateDistribution distribution = CandidateDistribution.resolve();
        JobInputSnapshot snapshot = JobInputSnapshot.capture(source);
        request.input().resolveAgainst(source);
        JobLaunch launch = switch (mode) {
            case SCRIPT -> ScriptExecutionAdapter.prepare(request, source, report, distribution);
            case JAR -> JarExecutionAdapter.prepare(request, source, report, distribution);
            case IMAGE -> new ImageLaunch(distribution, ImageExecutionAdapter.prepare(request, source,
                    ImageExecutionAdapter.Settings.configured(), new ImageExecutionAdapter.Docker()));
        };
        return new Prepared(request, source, report, snapshot, launch);
    }

    static Running start(Prepared prepared) throws IOException {
        Request request = prepared.request();
        FailedProcessDiagnostics.register(request, prepared.mode());
        try {
            if (prepared.launch().distribution() != null) prepared.launch().distribution().verifyFiles();
            if (prepared.mode() != Mode.JAR) prepared.inputSnapshot().verifyUnchanged(prepared.source());
            else if (!JobInputSnapshot.hash(prepared.jar()).equals(prepared.sha256())) throw new IOException("Prepared JAR changed");
            List<String> command;
            if (prepared.launch() instanceof ScriptLaunch script) {
                command = script.arguments();
                if (!command.equals(ScriptExecutionAdapter.arguments(request, prepared.source(), prepared.report(), script.distribution().launcher())))
                    throw new IOException("SCRIPT launch does not match its prepared input/invocation");
            }
            else if (prepared.image() != null) { command = new ArrayList<>(List.of("docker")); command.addAll(prepared.image().arguments()); }
            else {
                command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-Dfile.encoding=UTF-8", "-Duser.timezone=UTC", "-Duser.language=de", "-jar", prepared.jar().toString()));
                ScriptExecutionAdapter.appendOptions(command, request, prepared.report(), true);
            }
            Files.writeString(request.directory().resolve("command.json"), Json.write(command));
            long nanos = System.nanoTime();
            JobExecutionEvidence.recordImageInput(prepared);
            Process process;
            if (prepared.image() != null) process = prepared.image().start(request.directory());
            else {
                ProcessBuilder builder = process(command, request.directory(), request.environment(), "job");
                builder.directory(request.directory().resolve("cwd").toFile());
                process = builder.start();
            }
            return new Running(prepared, process, request.directory().resolve("job.stdout.log"),
                    request.directory().resolve("job.stderr.log"), nanos);
        } catch (IOException e) {
            JobExecutionEvidence.failure(request, prepared.mode(), "LAUNCH_FAILED", e);
            if (prepared.image() != null) try { prepared.image().remove(); } catch (Exception cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }

    static Result run(Request request) throws Exception {
        return run(request, Mode.SCRIPT);
    }

    static Result run(Request request, Mode mode) throws Exception {
        Running running = start(prepare(request, mode));
        try {
            return finish(running, false);
        } finally {
            if (running.process().isAlive()) stop(running);
        }
    }

    static Result finish(Running running, boolean aborted) throws Exception {
        Process process = running.process();
        long remaining = running.prepared().request().timeout().toNanos() - (System.nanoTime() - running.startedNanos());
        boolean timedOut = !process.waitFor(Math.max(1, remaining), TimeUnit.NANOSECONDS);
        boolean forced = false;
        if (timedOut) {
            if (running.prepared().image() != null) forced = stopImage(running);
            else { cancel(running); forced = stop(process); }
        }
        if (running.prepared().image() != null) {
            try { forced |= running.prepared().image().completed(); }
            finally { running.prepared().image().remove(); }
        }
        // waitFor/stop has reaped the process; redirected streams are now complete.
        Request request = running.prepared().request();
        var redactor = redactor(request);
        String out = redactor.redact(Files.readString(running.out()));
        String err = redactor.redact(Files.readString(running.err()));
        Files.writeString(running.out(), out);
        Files.writeString(running.err(), err);
        if (running.prepared().mode() != Mode.JAR) running.prepared().inputSnapshot().verifyUnchanged(running.prepared().source());
        String identity = running.prepared().sha256();
        Result result = new Result(process.exitValue(), out, err, running.prepared().report(), request.directory(),
                Duration.ofNanos(System.nanoTime() - running.startedNanos()), timedOut, aborted, identity,
                running.prepared().mode().name(), forced);
        ScriptOperationCoverage.record(request.invocation(), result);
        JobExecutionEvidence.completed(running.prepared(), result);
        return result;
    }

    static ch.so.agi.gretl.runtime.logging.Redactor redactor(Request request) throws IOException {
        var registry = new ch.so.agi.gretl.runtime.runner.SecretRegistry();
        if (request.config().get("secrets") instanceof Map<?, ?> secrets) {
            for (Object value : secrets.values()) {
                if (value instanceof Map<?, ?> secret) {
                    String clear = null;
                    if (secret.get("file") instanceof String file) {
                        try (var input = Files.newInputStream(request.directory().resolve(file))) {
                            byte[] bytes = input.readNBytes(64 * 1024 + 1);
                            if (bytes.length > 64 * 1024) throw new IOException("Oversized test secret file");
                            clear = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                        }
                        if (clear.endsWith("\r\n")) clear = clear.substring(0, clear.length() - 2);
                        else if (clear.endsWith("\n")) clear = clear.substring(0, clear.length() - 1);
                    } else if (secret.get("plaintext") instanceof String plaintext) {
                        clear = plaintext;
                    } else if (secret.get("environment") instanceof String key) {
                        Object environmentValue = request.environment().get(key);
                        clear = environmentValue == null ? System.getenv(key) : environmentValue.toString();
                    }
                    if (clear != null) registry.register(clear);
                }
            }
        }
        return new ch.so.agi.gretl.runtime.logging.Redactor(registry);
    }

    static void cancel(Running running) throws Exception {
        if (running.prepared().image() != null) running.prepared().image().cancel();
        else running.process().destroy();
    }

    static void stop(Running running) throws Exception {
        if (running.prepared().image() != null) {
            try { stopImage(running); }
            finally { running.prepared().image().remove(); }
        } else {
            try { cancel(running); }
            finally { stop(running.process()); }
        }
    }

    private static boolean stopImage(Running running) throws Exception {
        // Bound the actual container lifetime before reaping its client process.
        running.prepared().image().cancel(35);
        Process process=running.process();
        if (process.waitFor(5,TimeUnit.SECONDS)) return false;
        process.destroyForcibly();
        if (!process.waitFor(5,TimeUnit.SECONDS)) throw new IllegalStateException("Docker client survived force termination");
        return true;
    }

    static boolean stop(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(35, TimeUnit.SECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            if (!process.waitFor(5, TimeUnit.SECONDS)) throw new IllegalStateException("Process survived force termination");
            return true;
        }
        return false;
    }

    static ProcessBuilder process(List<String> command, Path directory,
                                           Map<String, ?> environment, String prefix) {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        for (String key : List.of("CLASSPATH", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_OPTS", "GRETL_JAVA_OPTS")) {
            builder.environment().remove(key);
        }
        for (String key : List.of("GRETL_JOB_NAME", "GRETL_CORRELATION_ID", "GRETL_JOB_REVISION",
                "GRETL_TERMINATION_MESSAGE_PATH", "GRETL_DEPLOYMENT_ENVIRONMENT", "GRETL_LANGUAGE")) {
            builder.environment().remove(key);
        }
        builder.environment().put("GRETL_LANGUAGE", "de");
        builder.environment().put("GRETL_JAVA_OPTS", "-Dfile.encoding=UTF-8 -Duser.timezone=UTC -Duser.language=de");
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

    private static Request stagedRequest(Request request, Path source) {
        return new Request(request.id(), request.jobDirectory(), request.directory(), request.targets(),
                rebaseMap(request.parameters(), request.jobDirectory(), source),
                rebaseMap(request.environment(), request.jobDirectory(), source),
                rebaseMap(request.config(), request.jobDirectory(), source), request.endpoints(), request.timeout(), request.input(), request.invocation());
    }
    private static Map<String, Object> rebaseMap(Map<String, ?> map, Path original, Path staged) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(key, rebaseValue(value, original.toAbsolutePath().normalize(), staged)));
        return copy;
    }
    private static Object rebaseValue(Object value, Path original, Path staged) {
        if (value instanceof Path path && path.isAbsolute() && path.normalize().startsWith(original))
            return staged.resolve(original.relativize(path.normalize()));
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entry) -> copy.put(key.toString(), rebaseValue(entry, original, staged)));
            return copy;
        }
        if (value instanceof List<?> list) return list.stream().map(entry -> rebaseValue(entry, original, staged)).toList();
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyConfig(Map<String, Object> config) {
        return (Map<String, Object>) immutableValue(config);
    }
    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entry) -> copy.put(key.toString(), immutableValue(entry)));
            return java.util.Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) return list.stream().map(ArtifactJobRunner::immutableValue).toList();
        return value;
    }

    private static void privateDirectory(Path directory) throws IOException {
        try { Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------")); }
        catch (UnsupportedOperationException ignored) { }
    }
}
