package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageExecutionAdapterTest {
    @TempDir Path temporary;
    static final String ID = "sha256:" + "a".repeat(64);
    final ImageExecutionAdapter.Settings settings = new ImageExecutionAdapter.Settings(
            "registry.example/gretl:qualified", "/product/bin/gretl", "gretl-test-network", currentUser(), "services:host-gateway");
    static String currentUser() {
        var user = new com.sun.security.auth.module.UnixSystem();
        return user.getUid() + ":" + user.getGid();
    }
    static final class RecordingEngine implements ImageExecutionAdapter.Engine {
        final List<List<String>> calls = new ArrayList<>();
        boolean failed;
        public String inspect(List<String> args) throws Exception {
            calls.add(List.copyOf(args));
            if (failed) throw new java.io.IOException("missing prerequisite");
            return args.getFirst().equals("image") ? ID : "present";
        }
        public Process start(List<String> args, Path directory, Map<String, String> env) {
            fail("Contract test must never start GRETL"); return null;
        }
        public void control(List<String> args) { calls.add(List.copyOf(args)); }
    }
    private ArtifactJobRunner.Request request() throws Exception {
        Path root = temporary.resolve("scenario with spaces"); Files.createDirectories(root);
        Path source = root.resolve("source"); Files.createDirectories(source);
        Files.writeString(source.resolve("job.groovy"), "defaults 'target'");
        var config = JobRunner.baseConfig(root);
        JobRunner.secret(root, config, "password", "secret must not be in command");
        @SuppressWarnings("unchecked") var secrets = (Map<String, Object>) config.get("secrets");
        secrets.put("envPassword", Map.of("environment", "SECRET_ENV"));
        JobRunner.taskConfig(config, "db", Map.of("jdbcUrl", "jdbc:postgresql://localhost:4567/db?currentSchema=test", "user", "a", "passwordSecret", "password"));
        JobRunner.taskConfig(config, "paths", Map.of("localFile", source.resolve("data.txt"), "serverPath", "/home/gretl/remote"));
        return new ArtifactJobRunner.Request("contract", source, root, List.of("first", "second"),
                Map.of("output", root.resolve("output/a b.txt"), "count", "12"),
                Map.of("SECRET_ENV", "/do not log this", "GRETL_TERMINATION_MESSAGE_PATH", root.resolve("output/termination.json")),
                config, Map.of("postgres", new ArtifactJobRunner.Endpoint("jdbc:postgresql://localhost:4567/db", "jdbc:postgresql://postgres:5432/db")), Duration.ofSeconds(1));
    }
    @Test void immutableImageAndCompleteNonStartingCommand() throws Exception {
        var request = request(); var engine = new RecordingEngine();
        var launch = ImageExecutionAdapter.prepare(request, request.jobDirectory(), settings, engine);
        var args = launch.arguments();
        assertEquals(ID, launch.imageId());
        assertEquals(List.of("info", "--format", "{{.ServerVersion}}"), engine.calls.getFirst());
        assertTrue(engine.calls.stream().anyMatch(c -> c.contains(settings.image())));
        assertTrue(engine.calls.stream().anyMatch(c -> c.getFirst().equals("network")));
        assertTrue(args.containsAll(List.of("--rm", "--init", "--sig-proxy=true", currentUser(), "gretl-test-network", "/run", "/product/bin/gretl", ID, "/job/job.groovy", "first", "second", "output=/outputs/a b.txt", "count=12", "/reports/report.json")));
        assertTrue(args.stream().anyMatch(a -> a.contains("source with spaces") || a.contains("scenario with spaces/source,target=/job,readonly")));
        assertTrue(args.stream().anyMatch(a -> a.endsWith("target=/secrets/password,readonly")));
        assertTrue(args.stream().anyMatch(a -> a.endsWith("target=/config/runtime.json,readonly")));
        for (String target : List.of("/outputs", "/work", "/reports", "/run")) {
            assertTrue(args.stream().anyMatch(a -> a.endsWith("target=" + target)), target);
        }
        assertEquals("/work/home", launch.environment().get("HOME"));
        assertEquals("/work/tmp", launch.environment().get("TMPDIR"));
        assertTrue(launch.environment().get("JAVA_TOOL_OPTIONS").contains("-Duser.home=/work/home -Djava.io.tmpdir=/work/tmp"));
        assertTrue(Files.isDirectory(request.directory().resolve("work/home")));
        assertTrue(Files.isDirectory(request.directory().resolve("work/tmp")));
        assertEquals("/outputs/termination.json", launch.environment().get("GRETL_TERMINATION_MESSAGE_PATH"));
        assertEquals("/do not log this", launch.environment().get("SECRET_ENV"));
        String serialized = args.toString();
        assertFalse(serialized.contains("do not log this")); assertFalse(serialized.contains("secret must not"));
        String config = Files.readString(request.directory().resolve("image-runtime.json"));
        assertTrue(config.contains("jdbc:postgresql://postgres:5432/db?currentSchema=test"), config);
        assertTrue(config.contains("/secrets/password")); assertFalse(config.contains("localhost"));
        assertTrue(config.contains("/job/data.txt")); assertTrue(config.contains("/home/gretl/remote"));
        assertTrue(ch.so.agi.gretl.runtime.logging.Json.write(ArtifactJobRunner.jsonValue(request.config()))
                .contains(request.jobDirectory().resolve("data.txt").toString()));
        launch.cancel(); launch.remove();
        assertTrue(engine.calls.contains(List.of("stop", "--time", "35", launch.name())));
        assertTrue(engine.calls.contains(List.of("rm", "--force", launch.name())));
    }
    @Test void finishKeepsImageExitAndTimeoutCancelsAndReapsAtTheBoundary() throws Exception {
        for (boolean timeout : List.of(false, true)) {
            var request = request();
            @SuppressWarnings("unchecked") var secrets = (Map<String, Object>) request.config().get("secrets");
            secrets.put("plain", Map.of("plaintext", "evidence-plaintext-sentinel"));
            Files.writeString(request.directory().resolve("job.stdout.log"), "image output evidence-plaintext-sentinel");
            Files.writeString(request.directory().resolve("job.stderr.log"), "image diagnostic");
            Files.createDirectories(request.directory().resolve("reports"));
            Path report = request.directory().resolve("reports/report.json"); Files.writeString(report, "{\"status\":\"FAILED\"}");
            var process = new Process() {
                boolean alive = timeout;
                public java.io.OutputStream getOutputStream() { return java.io.OutputStream.nullOutputStream(); }
                public java.io.InputStream getInputStream() { return java.io.InputStream.nullInputStream(); }
                public java.io.InputStream getErrorStream() { return java.io.InputStream.nullInputStream(); }
                public int waitFor() { alive = false; return exitValue(); }
                public boolean waitFor(long time, java.util.concurrent.TimeUnit unit) { return !alive; }
                public int exitValue() { if (alive) throw new IllegalThreadStateException(); return timeout ? 20 : 42; }
                public void destroy() { alive = false; }
                public boolean isAlive() { return alive; }
            };
            var calls = new ArrayList<List<String>>();
            var engine = new ImageExecutionAdapter.Engine() {
                public String inspect(List<String> args) { calls.add(args); return "container"; }
                public Process start(List<String> args, Path directory, Map<String, String> environment) { calls.add(args); return process; }
                public void control(List<String> args) { calls.add(args); if (args.getFirst().equals("stop")) process.destroy(); }
            };
            var launch = new ImageExecutionAdapter.Launch(engine, "fake-container", ID, List.of("run", ID), Map.of(), Map.of());
            var prepared = new ArtifactJobRunner.Prepared(request, request.jobDirectory(), null, ID, report, launch);
            var result = ArtifactJobRunner.finish(ArtifactJobRunner.start(prepared, false), false);
            assertEquals(timeout ? 20 : 42, result.code()); assertEquals(timeout, result.timedOut());
            assertEquals(ID, result.artifactIdentity()); assertEquals("IMAGE", result.mode()); assertTrue(result.hasValidArtifactIdentity()); assertEquals(report, result.report()); assertEquals("image output [REDACTED]", result.out());
            assertEquals(result.out(), Files.readString(request.directory().resolve("job.stdout.log")));
            assertFalse(process.isAlive()); assertTrue(calls.contains(List.of("rm", "--force", "fake-container")));
            assertEquals(timeout, calls.stream().anyMatch(c -> c.getFirst().equals("stop")));
            assertTrue(Files.readString(request.directory().resolve("execution.json")).contains("\"mode\":\"IMAGE\""));
        }
    }

    @Test void missingPrerequisiteFailsWithoutStartingOrFallback() throws Exception {
        var request = request(); var engine = new RecordingEngine(); engine.failed = true;
        assertThrows(java.io.IOException.class, () -> ImageExecutionAdapter.prepare(request, request.jobDirectory(), settings, engine));
        assertEquals(1, engine.calls.size());
    }
    @Test void ftpServerPathKeepsItsMeaningWhileLocalPathsAndEndpointAreTranslated() throws Exception {
        var base = request();
        var parameters = new java.util.LinkedHashMap<String, Object>(base.parameters());
        parameters.put("remoteDir", "/home/gretl/lease with spaces");
        parameters.put("server", "127.0.0.1:2121");
        var endpoints = new java.util.LinkedHashMap<>(base.endpoints());
        endpoints.put("ftp", new ArtifactJobRunner.Endpoint("127.0.0.1:2121", "ftp:21"));
        var request = new ArtifactJobRunner.Request("ftp-contract", base.jobDirectory(), base.directory(),
                List.of("ftp-upload"), parameters, base.environment(), base.config(), endpoints, base.timeout());
        var engine = new RecordingEngine();
        var args = ImageExecutionAdapter.prepare(request, request.jobDirectory(), settings, engine).arguments();
        assertTrue(args.contains("remoteDir=/home/gretl/lease with spaces"));
        assertTrue(args.contains("output=/outputs/a b.txt"));
        assertTrue(args.contains("server=ftp:21"));
        assertEquals("/home/gretl/lease with spaces", request.parameters().get("remoteDir"));
        assertFalse(engine.calls.stream().anyMatch(c -> c.getFirst().equals("run")));
    }
    @Test void opaqueConfigAndEnvironmentIdentityRemainUnchanged() throws Exception {
        var base = request();
        Map<String, Object> runtime = new java.util.LinkedHashMap<>();
        ((Map<?, ?>) base.config().get("runtime")).forEach((key, value) -> runtime.put(key.toString(), value));
        String opaque = "/ci/review/42";
        runtime.put("correlationId", opaque); runtime.put("jobName", "/ci/job");
        base.config().put("runtime", runtime);
        var environment = new java.util.LinkedHashMap<String, Object>(base.environment());
        environment.put("GRETL_CORRELATION_ID", opaque); environment.put("GRETL_JOB_REVISION", "/refs/review/42");
        var request = new ArtifactJobRunner.Request(base.id(), base.jobDirectory(), base.directory(), base.targets(),
                base.parameters(), environment, base.config(), base.endpoints(), base.timeout());
        var launch = ImageExecutionAdapter.prepare(request, request.jobDirectory(), settings, new RecordingEngine());
        assertEquals(opaque, ((Map<?, ?>) launch.config().get("runtime")).get("correlationId"));
        assertEquals("/ci/job", ((Map<?, ?>) launch.config().get("runtime")).get("jobName"));
        assertEquals(opaque, launch.environment().get("GRETL_CORRELATION_ID"));
        assertEquals("/refs/review/42", launch.environment().get("GRETL_JOB_REVISION"));
        assertEquals("/work", ((Map<?, ?>) launch.config().get("runtime")).get("workRoot"));
    }
    @Test void explicitlyLocalParameterAndEnvironmentPathsMustBeMounted() throws Exception {
        var base = request();
        for (boolean parameter : List.of(true, false)) {
            var parameters = new java.util.LinkedHashMap<String, Object>(base.parameters());
            var environment = new java.util.LinkedHashMap<String, Object>(base.environment());
            if (parameter) parameters.put("input", Path.of("/unmounted/data"));
            else environment.put("LOCAL_INPUT", Path.of("/unmounted/data"));
            var request = new ArtifactJobRunner.Request(base.id(), base.jobDirectory(), base.directory(), base.targets(),
                    parameters, environment, base.config(), base.endpoints(), base.timeout());
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> ImageExecutionAdapter.prepare(request, request.jobDirectory(), settings, new RecordingEngine()));
            assertTrue(exception.getMessage().contains("Unmapped IMAGE path"));
        }
    }
    @Test void plaintextSecretsAreOpaqueAndNeverBecomeCommandArguments() throws Exception {
        var request = request();
        Map<String, Object> secrets = new java.util.LinkedHashMap<>();
        ((Map<?, ?>) request.config().get("secrets"))
                .forEach((key, value) -> secrets.put(key.toString(), value));
        List<String> values = List.of("", "  ${UNCHANGED}\r\n", "/unmounted/secret",
                request.jobDirectory().resolve("private.txt").toString(),
                "jdbc:postgresql://localhost:4567/db", "http://127.0.0.1:4321/token",
                "127.0.0.1:2121");
        for (int i = 0; i < values.size(); i++) {
            secrets.put("plain" + i, Map.of("plaintext", values.get(i)));
        }
        request.config().put("secrets", secrets);
        var engine = new RecordingEngine();
        var launch = ImageExecutionAdapter.prepare(request, request.jobDirectory(), settings, engine);
        var transported = (Map<?, ?>) launch.config().get("secrets");
        var saved = (Map<?, ?>) ((Map<?, ?>) ch.so.agi.gretl.runtime.logging.Json.parseFile(
                request.directory().resolve("image-runtime.json"), 1L << 20)).get("secrets");
        for (int i = 0; i < values.size(); i++) {
            String alias = "plain" + i;
            assertEquals(Map.of("plaintext", values.get(i)), transported.get(alias));
            assertEquals(transported.get(alias), saved.get(alias));
        }
        assertFalse(launch.arguments().toString().contains("private.txt"));
        assertFalse(launch.arguments().toString().contains("127.0.0.1:4321"));
        assertFalse(launch.environment().containsValue("/unmounted/secret"));
        assertFalse(engine.calls.stream().anyMatch(c -> c.getFirst().equals("run")));
    }

    @Test void missingDeclaredEnvironmentSecretFailsBeforeContainerStart() throws Exception {
        var request = request();
        @SuppressWarnings("unchecked") var secrets = (Map<String, Object>) request.config().get("secrets");
        secrets.put("missingEnvironment", Map.of("environment", "GRETL_IMAGE_MISSING_SECRET_25F0019"));
        var exception = assertThrows(java.io.IOException.class, () -> ImageExecutionAdapter.prepare(request, request.jobDirectory(), settings, new RecordingEngine()));
        assertTrue(exception.getMessage().contains("Missing IMAGE secret environment"));
    }

    @Test void explicitImageNetworkLauncherAndNonRootUserAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new ImageExecutionAdapter.Settings(null, "/bin/gretl", "net", "1:1", ""));
        assertThrows(IllegalArgumentException.class, () -> new ImageExecutionAdapter.Settings("x", "gretl", "net", "1:1", ""));
        assertThrows(IllegalArgumentException.class, () -> new ImageExecutionAdapter.Settings("x", "/bin/gretl", "host", "1:1", ""));
        assertThrows(IllegalArgumentException.class, () -> new ImageExecutionAdapter.Settings("x", "/bin/gretl", "net", "0:0", ""));
    }
    @Test void untranslatedPathsAndHostOnlyEndpointsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ImageExecutionAdapter.translate("/unmounted/data", Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ImageExecutionAdapter.translate("http://127.0.0.1:4321/data", Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ImageExecutionAdapter.translate("127.0.0.1:2121", Map.of(), Map.of()));
        assertEquals("/outputs2/file", ImageExecutionAdapter.translate("/host2/file", Map.of(Path.of("/host"), "/outputs", Path.of("/host2"), "/outputs2"), Map.of()));
    }
}
