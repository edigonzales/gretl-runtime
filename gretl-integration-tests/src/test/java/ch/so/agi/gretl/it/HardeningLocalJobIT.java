package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;

import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Runs the hardening boundaries through the production launcher and regular job JARs. */
class HardeningLocalJobIT {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void preservesExistingRootsAndProtectsOwnedRunAndDiagnostics(boolean script) throws Exception {
        Path root = ArtifactJobRunner.directory("hardening-permissions-" + script);
        for (String name : List.of("work", "reports", "explicit-reports")) {
            Path directory = Files.createDirectories(root.resolve(name));
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxrwx---"));
        }
        Files.writeString(root.resolve("work/foreign.txt"), "foreign-owned-file");
        var prepared = prepare(root, "permissions");
        prepared = new ArtifactJobRunner.Prepared(prepared.request(), prepared.source(), prepared.jar(),
                prepared.sha256(), root.resolve("explicit-reports/report.json"), prepared.image());
        var result = run(prepared, script);
        assertEquals(0, result.code(), result.err());
        assertFalse(result.timedOut());
        for (String name : List.of("work", "reports", "explicit-reports"))
            assertEquals(PosixFilePermissions.fromString("rwxrwx---"),
                    Files.getPosixFilePermissions(root.resolve(name)), "Existing " + name + " permissions");
        assertEquals("foreign-owned-file", Files.readString(root.resolve("work/foreign.txt")));
        Path runRoot;
        try (var paths = Files.list(root.resolve("work"))) {
            runRoot = paths.filter(Files::isDirectory).findFirst().orElseThrow();
        }
        try (var paths = Files.walk(runRoot)) {
            for (Path path : paths.filter(Files::isDirectory).toList())
                assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(path), path.toString());
        }
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(result.report()));
        try (var paths = Files.list(root.resolve("reports"))) {
            var eventFiles = paths.filter(path -> path.toString().endsWith(".events.jsonl")).toList();
            assertEquals(1, eventFiles.size());
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(eventFiles.getFirst()));
        }
        assertTrue(result.hasValidArtifactIdentity());
        assertTerminal(result, "SUCCEEDED", 0);
    }

    @ParameterizedTest
    @CsvSource({"true,caughtTimeout", "false,caughtTimeout", "true,closeTimeout", "false,closeTimeout"})
    void localBudgetRemainsCancelledAfterCatchOrResourceClose(boolean script, String target) throws Exception {
        Path root = ArtifactJobRunner.directory("hardening-budget-" + target + "-" + script);
        var result = run(prepare(root, target), script);
        assertFalse(result.timedOut(), result.err());
        assertEquals(20, result.code(), result.err());
        assertTrue(Files.isRegularFile(root.resolve("output/closed")), "Resource close must still run");
        assertTrue(Files.isRegularFile(root.resolve("output/cleaned")), "Separate cleanup budget must remain usable");
        if (target.equals("caughtTimeout")) {
            assertEquals("TIMEOUT", Files.readString(root.resolve("output/caught")));
            assertTrue(Files.isRegularFile(root.resolve("output/returned")));
        }
        assertTerminal(result, "CANCELLED", 20);
    }

    @ParameterizedTest
    @CsvSource({"true,blockedTask", "false,blockedTask", "true,originalFailure", "false,originalFailure"})
    void setupFailureStillCleansUpAndReportsTheOriginalCause(boolean script, String target) throws Exception {
        Path root = ArtifactJobRunner.directory("hardening-setup-" + target + "-" + script);
        var result = run(prepare(root, target), script);
        assertFalse(result.timedOut(), result.err());
        assertEquals(10, result.code(), result.err());
        assertTrue(Files.isRegularFile(root.resolve("output/cleaned")), "Cleanup without workspace I/O must execute");
        assertFalse(Files.exists(root.resolve("output/unexpected")));
        String report = Files.readString(result.report());
        if (target.equals("originalFailure")) assertTrue(report.contains("original-hardening-failure"), report);
        else assertTrue(report.contains("task-000004"), report);
        assertTerminal(result, "FAILED", 10);
    }

    @SuppressWarnings("unchecked")
    private static ArtifactJobRunner.Prepared prepare(Path root, String target) throws Exception {
        Map<String, Object> config = JobRunner.baseConfig(root);
        Map<String, Object> runtime = new LinkedHashMap<>((Map<String, Object>) config.get("runtime"));
        runtime.put("keepWork", "always");
        config.put("runtime", runtime);
        var request = new ArtifactJobRunner.Request("hardening-" + target,
                ArtifactJobRunner.fixture("hardening"), root, List.of(target),
                Map.of("output", root.resolve("output")), Map.of(), config, Map.of(), Duration.ofSeconds(45));
        return ArtifactJobRunner.prepare(request, ArtifactJobRunner.Mode.JAR);
    }

    private static ArtifactJobRunner.Result run(ArtifactJobRunner.Prepared prepared, boolean script) throws Exception {
        var running = ArtifactJobRunner.start(prepared, script);
        try {
            return ArtifactJobRunner.finish(running, false);
        } finally {
            if (running.process().isAlive()) ArtifactJobRunner.stop(running);
        }
    }

    private static void assertTerminal(ArtifactJobRunner.Result result, String status, int code) throws Exception {
        Map<?, ?> report = (Map<?, ?>) Json.parseFile(result.report(), 1L << 20);
        Map<?, ?> execution = (Map<?, ?>) report.get("execution");
        assertEquals(status, execution.get("status"));
        assertEquals((long) code, execution.get("exitCode"));
        assertEquals(1, result.err().lines().filter(line -> line.contains("\"event_name\":\"gretl.run.finished\"")).count(), result.err());
        assertEquals(1, result.err().lines().filter(line -> line.contains("\"event_name\":\"gretl.cleanup.finished\"")).count(), result.err());
    }
}
