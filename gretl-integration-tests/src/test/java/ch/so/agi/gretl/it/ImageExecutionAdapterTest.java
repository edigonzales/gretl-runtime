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
        try {
            return Files.getAttribute(Path.of("."), "unix:uid") + ":" + Files.getAttribute(Path.of("."), "unix:gid");
        } catch (Exception exception) { throw new IllegalStateException(exception); }
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
        return new ArtifactJobRunner.Request("contract", source, root, List.of("first", "second"),
                Map.of("output", root.resolve("output/a b.txt").toString(), "count", "12"),
                Map.of("SECRET_ENV", "/do not log this", "GRETL_TERMINATION_MESSAGE_PATH", root.resolve("output/termination.json").toString()),
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
        launch.cancel(); launch.remove();
        assertTrue(engine.calls.contains(List.of("stop", "--time", "35", launch.name())));
        assertTrue(engine.calls.contains(List.of("rm", "--force", launch.name())));
    }
    @Test void finishKeepsImageExitAndTimeoutCancelsAndReapsAtTheBoundary() throws Exception {
        for (boolean timeout : List.of(false, true)) {
            var request = request();
            Files.writeString(request.directory().resolve("job.stdout.log"), "image output");
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
            assertEquals(ID, result.artifactIdentity()); assertEquals("IMAGE", result.mode()); assertTrue(result.hasValidArtifactIdentity()); assertEquals(report, result.report()); assertEquals("image output", result.out());
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
