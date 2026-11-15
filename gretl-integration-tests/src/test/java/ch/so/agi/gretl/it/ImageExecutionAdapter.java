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
    record Settings(String image, String launcher, String network, String user, String extraHost, Path metadata) {
        Settings(String image, String launcher, String network, String user, String extraHost) { this(image,launcher,network,user,extraHost,null); }
        Settings {
            if (image == null || image.isBlank()) throw new IllegalArgumentException("IMAGE requires gretl.test.image; no fallback");
            if (launcher == null || !launcher.startsWith("/")) throw new IllegalArgumentException("IMAGE launcher must be absolute");
            if (network == null || !network.matches("[A-Za-z0-9_.-]+") || List.of("host", "none", "bridge").contains(network))
                throw new IllegalArgumentException("IMAGE requires an explicit service network");
            if (user == null || !user.matches("[1-9][0-9]*:(0|[1-9][0-9]*)"))
                throw new IllegalArgumentException("IMAGE requires non-root numeric uid:gid with access to mounted files");
        }
        static Settings configured() throws Exception {
            String image = System.getProperty("gretl.test.image");
            String id = new Docker().inspect(List.of("image", "inspect", "--format", "{{.Id}}", image));
            return new Settings(id, "/opt/gretl/bin/gretl",
                    System.getProperty("gretl.test.imageNetwork"), System.getProperty("gretl.test.imageUser"),
                    System.getProperty("gretl.test.imageExtraHost", "services:host-gateway"));
        }
    }

    interface Engine {
        String inspect(List<String> arguments) throws Exception;
        Process start(List<String> arguments, Path directory, Map<String, String> environment) throws IOException;
        void control(List<String> arguments) throws Exception;
    }
    static final class Docker implements Engine {
        public String inspect(List<String> args) throws Exception {
            Path output = Files.createTempFile("gretl-docker-inspect-", ".log");
            try {
                Process p = new ProcessBuilder(command(args)).redirectErrorStream(true).redirectOutput(output.toFile()).start();
                if (!p.waitFor(args.getFirst().equals("stop") ? 45 : 20, TimeUnit.SECONDS)) {
                    p.destroyForcibly(); if (!p.waitFor(5,TimeUnit.SECONDS)) throw new IOException("Docker survived force termination");
                    throw new IOException("Docker inspection timed out");
                }
                if (Files.size(output) > (1L << 20)) throw new IOException("Oversized Docker metadata");
                String text = Files.readString(output).trim();
                if (p.exitValue() != 0) throw new IOException("IMAGE prerequisite failed: " + args + ": " + text);
                return text;
            } finally { Files.deleteIfExists(output); }
        }

        public Process start(List<String> args, Path directory, Map<String, String> environment) throws IOException {
            var transfer = new LinkedHashMap<>(environment);
            // Docker itself retains its host HOME/TMPDIR; the container receives the fixed values in argv.
            transfer.remove("HOME"); transfer.remove("TMPDIR");
            return ArtifactJobRunner.process(command(args), directory, transfer, "job").start();
        }
        public void control(List<String> args) throws Exception {
            try { inspect(args); }
            catch (IOException exception) {
                // The container may finish between the signal request and Docker's stop.
                if (!List.of("stop", "rm", "kill").contains(args.getFirst()) || !(exception.getMessage().contains("No such container") || args.getFirst().equals("kill") && exception.getMessage().contains("is not running"))) throw exception;
            }
        }
        private static List<String> command(List<String> args) {
            var command = new ArrayList<String>(); command.add("docker"); command.addAll(args); return command;
        }
    }
    record Launch(Engine engine, String name, String imageId, List<String> arguments,
                  Map<String, String> environment, Map<String, Object> config, Map<Path,String> paths, Path proofFile, java.util.concurrent.atomic.AtomicBoolean cancellation) {
        Launch(Engine engine,String name,String imageId,List<String> arguments,Map<String,String> environment,Map<String,Object> config) { this(engine,name,imageId,arguments,environment,config,Map.of(),null,new java.util.concurrent.atomic.AtomicBoolean()); }
        Launch { arguments = List.copyOf(arguments); environment = Map.copyOf(environment); config = Map.copyOf(config); paths=Map.copyOf(paths); }
        @Override public String toString() { return "ImageLaunch[" + name + ", " + imageId + "]"; }
        Process start(Path directory) throws IOException { return engine.start(arguments, directory, environment); }
        void cancel() throws Exception { cancellation.set(true); engine.control(List.of("kill","--signal=TERM",name)); }
        void cancel(int seconds) throws Exception {
            if (seconds < 1 || seconds > 35) throw new IllegalArgumentException("IMAGE stop grace must be 1..35 seconds");
            cancellation.set(true); engine.control(List.of("stop","--time",Integer.toString(seconds),name));
        }
        Launch withReport(Path report, ArtifactJobRunner.Request request) throws IOException {
            var changed = new ArrayList<>(arguments);
            var mappings = new LinkedHashMap<>(paths);
            String target;
            try { target = translate(report.toString(),mappings,request.endpoints()); }
            catch (IllegalArgumentException unmapped) {
                Path parent = report.toAbsolutePath().normalize().getParent();
                if (parent.equals(request.directory()) || !parent.startsWith(request.directory()) || !Files.isDirectory(parent)) throw unmapped;
                var mounts = new ArrayList<String>();
                mount(mounts,mappings,parent,"/explicit-reports",false);
                changed.addAll(changed.indexOf(imageId),mounts);
                target = translate(report.toString(),mappings,request.endpoints());
            }
            int option = changed.indexOf("--report");
            if (option < 0) throw new IllegalArgumentException("This IMAGE invocation has no run report");
            changed.set(option+1,target);
            return new Launch(engine,name,imageId,changed,environment,config,mappings,proofFile,cancellation);
        }
        boolean completed() throws Exception {
            int option = arguments.indexOf("--cidfile");
            if (option < 0) return false; // RecordingEngine component only.
            Path cidFile = Path.of(arguments.get(option+1));
            if (!Files.isRegularFile(cidFile)) return false; // Failed docker run; no invented container proof.
            String cid = Files.readString(cidFile).trim();
            if (!cid.matches("[a-f0-9]{64}")) throw new IOException("Invalid IMAGE container ID");
            String format = """
                    {"id":{{json .Id}},"image":{{json .Image}},"user":{{json .Config.User}},"readOnlyRoot":{{json .HostConfig.ReadonlyRootfs}},"running":{{json .State.Running}},"exitCode":{{json .State.ExitCode}},"oomKilled":{{json .State.OOMKilled}},"startedAt":{{json .State.StartedAt}},"finishedAt":{{json .State.FinishedAt}}}
                    """.trim();
            var container = (Map<?,?>) Json.parse(engine.inspect(List.of("container","inspect","--format",format,cid)));
            if (!imageId.equals(container.get("image")) || !Boolean.FALSE.equals(container.get("running"))) throw new IOException("IMAGE container did not finish using the selected image");
            if (proofFile != null) {
                var proof = new LinkedHashMap<String,Object>();
                ((Map<?,?>)Json.parseFile(proofFile,8L<<20)).forEach((k,v)->proof.put(k.toString(),v));
                proof.put("container",container); proof.put("name",name); proof.put("removed",false);
                Files.writeString(proofFile,Json.write(proof));
            }
            return cancellation.get() && Long.valueOf(137).equals(container.get("exitCode"));
        }
        void remove() throws Exception {
            if (!engine.inspect(List.of("container", "ls", "--all", "--quiet", "--filter", "name=^/" + name + "$")).isBlank())
                engine.control(List.of("rm", "--force", name));
            if (proofFile != null && Files.isRegularFile(proofFile)) {
                if (!engine.inspect(List.of("container","ls","--all","--quiet","--filter","name=^/"+name+"$")).isBlank()) throw new IOException("Owned IMAGE container survived removal");
                var proof = new LinkedHashMap<String,Object>();
                ((Map<?,?>)Json.parseFile(proofFile,8L<<20)).forEach((k,v)->proof.put(k.toString(),v));
                proof.put("removed",true); Files.writeString(proofFile,Json.write(proof));
            }
        }
    }
    static Launch prepare(ArtifactJobRunner.Request request, Path source, Settings settings, Engine engine) throws Exception {
        int uid = ((Number) Files.getAttribute(request.directory(), "unix:uid")).intValue();
        if (!settings.user().startsWith(uid + ":")) throw new IOException("IMAGE uid must own the private scenario directory (" + uid + ")");
        engine.inspect(List.of("info", "--format", "{{.ServerVersion}}"));
        String imageId = engine.inspect(List.of("image", "inspect", "--format", "{{.Id}}", settings.image()));
        if (!imageId.matches("sha256:[0-9a-f]{64}")) throw new IOException("IMAGE inspect returned no immutable image identity");
        engine.inspect(List.of("network", "inspect", "--format", "{{.Name}}", settings.network()));
        String entrypoint = engine.inspect(List.of("image", "inspect", "--format", "{{json .Config.Entrypoint}}", imageId));
        if (!Json.write(List.of(settings.launcher())).equals(entrypoint)) throw new IOException("IMAGE default entrypoint differs from the verified launcher");
        Path proofFile = null;
        if (engine instanceof Docker) {
            proofFile = request.directory().resolve("image-proof.json");
            Files.writeString(proofFile, Json.write(Map.of("id", imageId)));
        }
        String name = "gretl-job-" + UUID.randomUUID();
        var args = new ArrayList<>(List.of("run", "--pull=never", "--cap-drop", "ALL", "--security-opt", "no-new-privileges", "--init", "--read-only", "--tmpfs", "/tmp:rw,exec,nosuid,size=256m", "--sig-proxy=true", "--name", name,
                "--user", settings.user(), "--network", settings.network(), "--workdir", "/run",
                "--label", "gretl.verification.run=" + System.getProperty("gretl.test.runId", "development"),
                "--cidfile", request.directory().resolve("container.id").toString()));
        if (!settings.extraHost().isBlank()) args.addAll(List.of("--add-host", settings.extraHost()));
        Map<Path, String> paths = new LinkedHashMap<>();
        mount(args, paths, source, "/job", true);
        mount(args, paths, request.directory().resolve("output"), "/outputs", false);
        Path work = runtimePath(request,"workRoot","work"), reports = runtimePath(request,"reportRoot","reports");
        mount(args, paths, work, "/work", false);
        mount(args, paths, reports, "/reports", false);
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
        Files.createDirectories(work.resolve("home"),java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
        Files.createDirectories(work.resolve("tmp"),java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
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
        environment.put("GRETL_JAVA_OPTS", "-Dfile.encoding=UTF-8 -Duser.timezone=UTC -Duser.language=de "
                + environment.getOrDefault("GRETL_JAVA_OPTS", ""));
        environment.put("GRETL_LANGUAGE", "de"); environment.put("TZ", "UTC"); environment.put("LANG", "C.UTF-8");
        environment.keySet().stream().sorted().forEach(key -> args.addAll(List.of("--env", List.of("HOME","TMPDIR").contains(key) ? key+"="+environment.get(key) : key)));
        args.addAll(List.of(imageId, request.invocation().verb().name().toLowerCase(java.util.Locale.ROOT), request.input().resolveAgainst(Path.of("/job")).toString(), "--config", "/config/runtime.json"));
        if (request.invocation().help()) args.add("--help");
        if (request.invocation().graphFormat() != null) args.addAll(List.of("--graph",request.invocation().graphFormat()));
        if (request.invocation().graphOutput() != null) args.addAll(List.of("--output",translate(request.invocation().graphOutput().toString(),paths,request.endpoints())));
        if (request.invocation().verb() == ArtifactJobRunner.Verb.RUN && !request.invocation().help()) args.addAll(List.of("--report","/reports/report.json","--log-format","json"));
        for (String target : request.targets()) args.addAll(List.of("--target", target));
        request.parameters().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                args.addAll(List.of("--param", e.getKey() + "=" + translateValue(e.getValue(), paths, request.endpoints()))));
        return new Launch(engine, name, imageId, List.copyOf(args), Map.copyOf(environment), config, paths, proofFile, new java.util.concurrent.atomic.AtomicBoolean());
    }
    private static Path runtimePath(ArtifactJobRunner.Request request, String key, String fallback) {
        Object value = request.config().get("runtime") instanceof Map<?,?> runtime ? runtime.get(key) : null;
        return request.directory().resolve(value == null ? fallback : value.toString()).toAbsolutePath().normalize();
    }
    private static void mount(List<String> args, Map<Path, String> paths, Path host, String image, boolean readOnly) throws IOException {
        host = host.toAbsolutePath().normalize();
        if (!Files.exists(host)) {
            if (readOnly) throw new IOException("Missing read-only IMAGE mount: " + host);
            Files.createDirectories(host);
        }
        if (Files.isSymbolicLink(host)) throw new IOException("Symlink IMAGE mount: " + host);
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
