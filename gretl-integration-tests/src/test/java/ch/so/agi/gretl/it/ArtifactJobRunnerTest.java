package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactJobRunnerTest {
    @TempDir Path root;
    @Test void modeParserRejectsExplicitInvalidValues() {
        assertEquals(ArtifactJobRunner.Mode.SCRIPT, ArtifactJobRunner.Mode.parse("SCRIPT"));
        for (String mode : List.of("", " ", "shell", " JAR"))
            assertThrows(IllegalArgumentException.class, () -> ArtifactJobRunner.Mode.parse(mode));
        // Do not mutate a JVM-wide mode selected for a matrix run.
        assertEquals(System.getProperty("gretl.test.executionMode") == null ? ArtifactJobRunner.Mode.SCRIPT
                : ArtifactJobRunner.Mode.parse(System.getProperty("gretl.test.executionMode")), ArtifactJobRunner.Mode.configured());
    }
    @Test void pathsAndInvocationRejectContradictoryStates() {
        for (String path : List.of("../escape", "/host/path", ""))
            assertThrows(IllegalArgumentException.class, () -> ArtifactJobRunner.JobInput.script(path));
        assertThrows(IllegalArgumentException.class, () -> ArtifactJobRunner.JobInput.script(".").resolveAgainst(root));
        assertEquals(root.resolve("a b/ü.groovy"), ArtifactJobRunner.JobInput.script("a b/ü.groovy").resolveAgainst(root));
        assertThrows(IllegalArgumentException.class, () -> new ArtifactJobRunner.Invocation(ArtifactJobRunner.Verb.RUN, false, "mermaid", null));
        assertThrows(IllegalArgumentException.class, () -> ArtifactJobRunner.Invocation.plan(null, root));
    }
    @Test void requestCopiesNestedConfigWithoutStringifyingPathsOrLeakingSecrets() {
        var nested = new LinkedHashMap<String,Object>(); nested.put("path", root); nested.put("plaintext", "private-sentinel");
        var config = new LinkedHashMap<String,Object>(); config.put("nested", nested);
        var request = new ArtifactJobRunner.Request("immutable", root, root, List.of(), Map.of(), Map.of(), config, Map.of(), Duration.ofSeconds(1));
        nested.put("path", Path.of("elsewhere"));
        assertEquals(root, ((Map<?,?>)request.config().get("nested")).get("path"));
        assertThrows(UnsupportedOperationException.class, () -> request.config().clear());
        assertFalse(request.toString().contains("private-sentinel"));
    }
    @Test void inputInventoryDoesNotParseAndTracksEveryResource() throws Exception {
        Files.writeString(root.resolve("settings.gretl"), "{ invalid settings");
        Files.createDirectories(root.resolve("nested")); Files.writeString(root.resolve("nested/data.sql"), "select 1");
        var before = JobInputSnapshot.capture(root);
        Files.writeString(root.resolve("job-package.json"), "{ invalid descriptor");
        assertEquals(before, JobInputSnapshot.capture(root));
        Files.writeString(root.resolve("nested/data.sql"), "select 2");
        assertNotEquals(before, JobInputSnapshot.capture(root));
        assertThrows(java.io.IOException.class, () -> before.verifyUnchanged(root));
        Path external = Files.createTempFile("gretl-symlink-probe", ".txt");
        try {
            Files.createSymbolicLink(root.resolve("link"), external);
            assertThrows(java.io.IOException.class, () -> JobInputSnapshot.capture(root));
        } finally { Files.deleteIfExists(external); }
    }
    @Test void forceTerminationNeverUsesUnboundedWait() throws Exception {
        var process = new Process() {
            boolean forced;
            public java.io.OutputStream getOutputStream() { return java.io.OutputStream.nullOutputStream(); }
            public java.io.InputStream getInputStream() { return java.io.InputStream.nullInputStream(); }
            public java.io.InputStream getErrorStream() { return java.io.InputStream.nullInputStream(); }
            public int waitFor() { throw new AssertionError("Unbounded wait"); }
            public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) { assertTrue(timeout > 0); return forced; }
            public int exitValue() { if (!forced) throw new IllegalThreadStateException(); return 137; }
            public void destroy() { }
            public Process destroyForcibly() { forced = true; return this; }
            public java.util.stream.Stream<ProcessHandle> descendants() { return java.util.stream.Stream.empty(); }
        };
        assertTrue(ArtifactJobRunner.stop(process));
        assertEquals(137, process.exitValue());
    }
    @Test void scriptArgumentsHaveNoShellSplittingOrPackagingBoundary() throws Exception {
        var request = new ArtifactJobRunner.Request("arguments", root, root, List.of("a"), Map.of("value", "ä b;$HOME"),
                Map.of(), Map.of(), Map.of(), Duration.ofSeconds(1), ArtifactJobRunner.JobInput.script("anders ü.groovy"), ArtifactJobRunner.Invocation.run());
        Files.writeString(root.resolve("job-package.json"), "broken");
        var args = ScriptExecutionAdapter.arguments(request, root, root.resolve("report.json"), root.resolve("runtime/bin/gretl"));
        assertEquals(root.resolve("anders ü.groovy").toString(), args.get(2));
        assertTrue(args.contains("value=ä b;$HOME"));
        assertFalse(args.contains("package")); assertFalse(args.contains("-jar"));
        // The deliberately rejecting JAR boundary cannot affect this adapter.
        Files.delete(root.resolve("job-package.json"));
        var project = new ArtifactJobRunner.Request(request.id(), root, root, request.targets(), request.parameters(), request.environment(),
                request.config(), request.endpoints(), request.timeout(), ArtifactJobRunner.JobInput.project("."), request.invocation());
        assertThrows(java.io.IOException.class, () -> JarExecutionAdapter.prepare(project, root, root.resolve("report.json"), null));
    }
}
