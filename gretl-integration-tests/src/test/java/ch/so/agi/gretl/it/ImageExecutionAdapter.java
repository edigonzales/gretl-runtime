package ch.so.agi.gretl.it;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Test transport only. No GRETL image is started by its contract tests. */
final class ImageExecutionAdapter {
    record Settings(String image, String launcher, String network, String user, String extraHost) {
        Settings {
            if (image == null || image.isBlank()) throw new IllegalArgumentException("IMAGE requires gretl.test.image; no fallback");
            if (launcher == null || !launcher.startsWith("/")) throw new IllegalArgumentException("IMAGE launcher must be absolute");
            if (network == null || !network.matches("[A-Za-z0-9_.-]+") || List.of("host", "none", "bridge").contains(network))
                throw new IllegalArgumentException("IMAGE requires an explicit service network");
            if (user == null || !user.matches("[1-9][0-9]*:[1-9][0-9]*"))
                throw new IllegalArgumentException("IMAGE requires non-root numeric uid:gid with access to mounted files");
        }
        static Settings configured() {
            return new Settings(System.getProperty("gretl.test.image"),
                    System.getProperty("gretl.test.imageLauncher", "/opt/gretl/bin/gretl"),
                    System.getProperty("gretl.test.imageNetwork"), System.getProperty("gretl.test.imageUser"),
                    System.getProperty("gretl.test.imageExtraHost", ""));
        }
    }
    interface Engine {
        String inspect(List<String> arguments) throws Exception;
        Process start(List<String> arguments, Path directory, Map<String, String> environment) throws IOException;
        void control(List<String> arguments) throws Exception;
    }
    static final class Docker implements Engine {
        public String inspect(List<String> args) throws Exception {
            Process p = new ProcessBuilder(command(args)).redirectErrorStream(true).start();
            // Inspect output is bounded by Docker's selected format; collect after a bounded wait.
            if (!p.waitFor(args.getFirst().equals("stop") ? 45 : 20, TimeUnit.SECONDS)) { p.destroyForcibly(); p.waitFor(); throw new IOException("Docker inspection timed out"); }
            String text = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (p.exitValue() != 0) throw new IOException("IMAGE prerequisite failed: " + args + ": " + text);
            return text;
        }
        public Process start(List<String> args, Path directory, Map<String, String> environment) throws IOException {
            return ArtifactJobRunner.process(command(args), directory, environment, "job").start();
        }
        public void control(List<String> args) throws Exception {
            try { inspect(args); }
            catch (IOException exception) {
                // --rm may finish between the signal request and Docker's stop.
                if (!List.of("stop", "rm").contains(args.getFirst()) || !exception.getMessage().contains("No such container")) throw exception;
            }
        }
        private static List<String> command(List<String> args) {
            var command = new ArrayList<String>(); command.add("docker"); command.addAll(args); return command;
        }
    }
    record Launch(Engine engine, String name, String imageId, List<String> arguments,
                  Map<String, String> environment, Map<String, Object> config) {
        Process start(Path directory) throws IOException { return engine.start(arguments, directory, environment); }
        void cancel() throws Exception { engine.control(List.of("stop", "--time", "35", name)); }
        void remove() throws Exception {
            if (!engine.inspect(List.of("container", "ls", "--all", "--quiet", "--filter", "name=^/" + name + "$")).isBlank())
                engine.control(List.of("rm", "--force", name));
        }
    }
    static Launch prepare(ArtifactJobRunner.Request request, Path source, Settings settings, Engine engine) throws Exception {
        int uid = ((Number) Files.getAttribute(request.directory(), "unix:uid")).intValue();
        if (!settings.user().startsWith(uid + ":")) throw new IOException("IMAGE uid must own the private scenario directory (" + uid + ")");
        engine.inspect(List.of("info", "--format", "{{.ServerVersion}}"));
        String imageId = engine.inspect(List.of("image", "inspect", "--format", "{{.Id}}", settings.image()));
        if (!imageId.matches("sha256:[0-9a-f]{64}")) throw new IOException("IMAGE inspect returned no immutable image identity");
        engine.inspect(List.of("network", "inspect", "--format", "{{.Name}}", settings.network()));
        String name = "gretl-job-" + UUID.randomUUID();
        var args = new ArrayList<>(List.of("run", "--rm", "--init", "--sig-proxy=true", "--name", name,
                "--user", settings.user(), "--network", settings.network(), "--workdir", "/run",
                "--entrypoint", settings.launcher()));
        if (!settings.extraHost().isBlank()) args.addAll(List.of("--add-host", settings.extraHost()));
        Map<Path, String> paths = new LinkedHashMap<>();
        mount(args, paths, source, "/job", true);
        mount(args, paths, request.directory().resolve("output"), "/outputs", false);
        mount(args, paths, request.directory().resolve("work"), "/work", false);
        mount(args, paths, request.directory().resolve("reports"), "/reports", false);
        mount(args, paths, request.directory().resolve("cwd"), "/run", false);
        Map<String, String> secretEnvironment = new LinkedHashMap<>();
        if (request.config().get("secrets") instanceof Map<?, ?> secrets) {
            for (var entry : secrets.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> environmentSecret && environmentSecret.get("environment") instanceof String key) {
                    Object supplied = request.environment().get(key);
                    String value = supplied == null ? System.getenv(key) : supplied.toString();
                    if (value == null) throw new IOException("Missing IMAGE secret environment: " + key);
                    secretEnvironment.put(key, value);
                }
                if (entry.getValue() instanceof Map<?, ?> secret && secret.get("file") instanceof String file) {
                    if (!entry.getKey().toString().matches("[A-Za-z][A-Za-z0-9_.-]*")) throw new IOException("Invalid IMAGE secret alias");
                    Path host = request.directory().resolve(file).toAbsolutePath().normalize();
                    if (!Files.isRegularFile(host)) throw new IOException("Missing IMAGE secret file: " + entry.getKey());
                    mount(args, paths, host, "/secrets/" + entry.getKey(), true);
                }
            }
        }
        Map<String, Object> config = new LinkedHashMap<>();
        request.config().forEach((key, value) -> config.put(key,
                key.equals("runtime") || key.equals("secrets")
                        ? value : translateValue(value, paths, request.endpoints())));
        // Runtime identity is opaque text, even if it starts with a slash or resembles an endpoint.
        if (request.config().get("runtime") instanceof Map<?, ?> runtime) {
            Map<String, Object> translated = new LinkedHashMap<>();
            runtime.forEach((key, value) -> translated.put(key.toString(), ArtifactJobRunner.jsonValue(value)));
            for (String field : List.of("workRoot", "reportRoot")) {
                if (runtime.get(field) != null) {
                    Path host = request.directory().resolve(runtime.get(field).toString()).normalize();
                    translated.put(field, translate(host.toString(), paths, request.endpoints()));
                }
            }
            config.put("runtime", translated);
        }
        // A relative secret source is relative to the host config, never to /run.
        if (config.get("secrets") instanceof Map<?, ?> secrets) {
            Map<String, Object> translated = new LinkedHashMap<>();
            for (var entry : secrets.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> secret && secret.containsKey("file")) value = Map.of("file", "/secrets/" + entry.getKey());
                translated.put(entry.getKey().toString(), value);
            }
            config.put("secrets", translated);
        }
        Path configFile = request.directory().resolve("image-runtime.json");
        Files.writeString(configFile, Json.write(config));
        mount(args, paths, configFile, "/config/runtime.json", true);
        Files.createDirectories(request.directory().resolve("work/home"));
        Files.createDirectories(request.directory().resolve("work/tmp"));
        Map<String, String> environment = new LinkedHashMap<>();
        request.environment().forEach((key, value) -> {
            if (secretEnvironment.containsKey(key)) { environment.put(key, secretEnvironment.get(key)); return; }
            boolean opaque = List.of("GRETL_JOB_NAME", "GRETL_CORRELATION_ID", "GRETL_JOB_REVISION", "GRETL_DEPLOYMENT_ENVIRONMENT").contains(key);
            String translated = opaque ? value.toString() : String.valueOf(translateValue(value, paths, request.endpoints()));
            if (key.equals("GRETL_TERMINATION_MESSAGE_PATH")) translated = translate(value.toString(), paths, request.endpoints());
            environment.put(key, translated);
        });
        environment.putAll(secretEnvironment);
        environment.put("HOME", "/work/home"); environment.put("TMPDIR", "/work/tmp");
        environment.put("JAVA_TOOL_OPTIONS", "-Duser.home=/work/home -Djava.io.tmpdir=/work/tmp "
                + environment.getOrDefault("JAVA_TOOL_OPTIONS", ""));
        environment.put("GRETL_LANGUAGE", "de"); environment.put("TZ", "UTC"); environment.put("LANG", "C.UTF-8");
        environment.keySet().stream().sorted().forEach(key -> args.addAll(List.of("--env", key)));
        args.addAll(List.of(imageId, "run", request.scriptInput().equals(".") ? "/job" : "/job/job.groovy", "--config", "/config/runtime.json",
                "--report", "/reports/report.json", "--log-format", "json"));
        for (String target : request.targets()) args.addAll(List.of("--target", target));
        request.parameters().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                args.addAll(List.of("--param", e.getKey() + "=" + translateValue(e.getValue(), paths, request.endpoints()))));
        return new Launch(engine, name, imageId, List.copyOf(args), Map.copyOf(environment), config);
    }
    private static void mount(List<String> args, Map<Path, String> paths, Path host, String image, boolean readOnly) throws IOException {
        host = host.toAbsolutePath().normalize();
        if (!Files.exists(host)) Files.createDirectories(host);
        if (host.toString().contains(",")) throw new IOException("Docker --mount source contains comma: " + host);
        paths.put(host, image);
        args.addAll(List.of("--mount", "type=bind,source=" + host + ",target=" + image + (readOnly ? ",readonly" : "")));
    }
    private static Map<String, Object> translateMap(Map<?, ?> input, Map<Path, String> paths,
                                                   Map<String, ArtifactJobRunner.Endpoint> endpoints) {
        Map<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, value) -> result.put(key.toString(), translateValue(value, paths, endpoints)));
        return result;
    }
    private static Object translateValue(Object value, Map<Path, String> paths, Map<String, ArtifactJobRunner.Endpoint> endpoints) {
        if (value instanceof Map<?, ?> map) return translateMap(map, paths, endpoints);
        if (value instanceof List<?> list) return list.stream().map(v -> translateValue(v, paths, endpoints)).toList();
        if (value instanceof Path path) return translate(path.toString(), paths, endpoints);
        return value instanceof String text ? translateEndpoint(text, endpoints) : value;
    }
    static String translate(String value, Map<Path, String> paths, Map<String, ArtifactJobRunner.Endpoint> endpoints) {
        if (value.startsWith("/")) {
            Path path = Path.of(value).normalize();
            return paths.entrySet().stream().filter(e -> path.startsWith(e.getKey()))
                    .max(java.util.Comparator.comparingInt(e -> e.getKey().getNameCount()))
                    .map(e -> e.getValue() + (path.equals(e.getKey()) ? "" : "/" + e.getKey().relativize(path)))
                    .orElseThrow(() -> new IllegalArgumentException("Unmapped IMAGE path: " + value));
        }
        return translateEndpoint(value, endpoints);
    }
    private static String translateEndpoint(String value, Map<String, ArtifactJobRunner.Endpoint> endpoints) {
        for (var endpoint : endpoints.values()) {
            if (value.equals(endpoint.host()) || value.startsWith(endpoint.host() + "/") || value.startsWith(endpoint.host() + "?")) {
                if (endpoint.image() == null || endpoint.image().isBlank()
                        || endpoint.image().matches("(?i).*(localhost|127\\.0\\.0\\.1).*")) throw new IllegalArgumentException("Missing or host-only IMAGE endpoint mapping");
                return endpoint.image() + value.substring(endpoint.host().length());
            }
        }
        if (value.matches("(?i)(localhost|127\\.0\\.0\\.1)(:[0-9]+)?([/?].*)?"))
            throw new IllegalArgumentException("Unmapped host-only IMAGE endpoint: " + value);
        if (value.matches("(?i)(jdbc:|https?://|ftp://).*(localhost|127\\.0\\.0\\.1).*"))
            throw new IllegalArgumentException("Unmapped host-only IMAGE endpoint: " + value);
        return value;
    }
}
