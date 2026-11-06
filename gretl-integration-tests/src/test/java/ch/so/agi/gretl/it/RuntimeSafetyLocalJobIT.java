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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.CsvSource;

/** Runs the runtime-safety boundaries through the production launcher and regular job JARs. */
class RuntimeSafetyLocalJobIT {
    @Test
    void preservesExistingRootsAndProtectsOwnedRunAndDiagnostics() throws Exception {
        var mode = ArtifactJobRunner.Mode.configured();
        Path root = ArtifactJobRunner.directory("runtime-safety-permissions-" + mode);
        for (String name : List.of("work", "reports", "explicit-reports")) {
            Path directory = Files.createDirectories(root.resolve(name));
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxrwx---"));
        }
        Files.writeString(root.resolve("work/foreign.txt"), "foreign-owned-file");
        var prepared = prepare(root, "permissions");
        prepared = prepared.withReport(root.resolve("explicit-reports/report.json"));
        var result = run(prepared);
        assertEquals(0, result.code(), result.err());
        assertFalse(result.timedOut());
        for (String name : List.of("work", "reports", "explicit-reports"))
            assertEquals(PosixFilePermissions.fromString("rwxrwx---"),
                    Files.getPosixFilePermissions(root.resolve(name)), "Existing " + name + " permissions");
        assertEquals("foreign-owned-file", Files.readString(root.resolve("work/foreign.txt")));
        var identity=(Map<?,?>)((Map<?,?>)Json.parseFile(result.report(),8L<<20)).get("identity");
        Path runRoot=root.resolve("work").resolve(identity.get("runId").toString());
        assertTrue(Files.isDirectory(runRoot),"The actual owned run workspace");
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
    @CsvSource({"caughtTimeout", "closeTimeout"})
    void localBudgetRemainsCancelledAfterCatchOrResourceClose(String target) throws Exception {
        var mode = ArtifactJobRunner.Mode.configured();
        Path root = ArtifactJobRunner.directory("runtime-safety-budget-" + target + "-" + mode);
        var result = run(prepare(root, target));
        assertFalse(result.timedOut(), result.err());
        assertEquals(20, result.code(), result.err());
        assertTrue(Files.isRegularFile(root.resolve("output/closed")), "Resource close must still run");
        assertTrue(Files.isRegularFile(root.resolve("output/cleaned")), "Separate cleanup budget must remain usable");
        if (target.equals("caughtTimeout")) {
            assertEquals("TIMEOUT", Files.readString(root.resolve("output/caught")));
            assertTrue(Files.isRegularFile(root.resolve("output/returned")));
        }
        assertTerminal(result, "CANCELLED", 20);
        assertCause(result, "TIMEOUT");
    }

    @ParameterizedTest
    @CsvSource({"blockedTask", "originalFailure"})
    void setupFailureStillCleansUpAndReportsTheOriginalCause(String target) throws Exception {
        var mode = ArtifactJobRunner.Mode.configured();
        Path root = ArtifactJobRunner.directory("runtime-safety-setup-" + target + "-" + mode);
        var result = run(prepare(root, target));
        assertFalse(result.timedOut(), result.err());
        assertEquals(10, result.code(), result.err());
        assertTrue(Files.isRegularFile(root.resolve("output/cleaned")), "Cleanup without workspace I/O must execute");
        assertFalse(Files.exists(root.resolve("output/unexpected")));
        String report = Files.readString(result.report());
        if (target.equals("originalFailure")) assertTrue(report.contains("original-runtime-safety-failure"), report);
        else {
            assertTrue(report.contains("task-000004"), report);
            Map<?,?> parsed = (Map<?,?>) Json.parse(report);
            var tasks = (List<?>) ((Map<?,?>) parsed.get("execution")).get("tasks");
            Map<?,?> blocked = tasks.stream().map(row -> (Map<?,?>) row).filter(row -> "blockedTask".equals(row.get("taskId"))).findFirst().orElseThrow();
            assertEquals("task-setup", blocked.get("failurePhase"));
            var terminal = result.err().lines().filter(line -> line.startsWith("{")).map(line -> (Map<?,?>) Json.parse(line))
                    .filter(row -> "gretl.task.failed".equals(row.get("event_name"))).findFirst().orElseThrow();
            assertEquals("task-setup", terminal.get("phase"));
        }
        assertTerminal(result, "FAILED", 10);
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedStartHasNoRunReportOrCleanupEffects() throws Exception {
        var mode = ArtifactJobRunner.Mode.configured();
        Path root = ArtifactJobRunner.directory("runtime-safety-start-" + mode);
        var prepared = prepare(root, "permissions");
        Path blocked = Files.writeString(root.resolve("reports/blocked-root"), "keep");
        Path configFile=root.resolve(mode==ArtifactJobRunner.Mode.IMAGE ? "image-runtime.json" : "runtime.json");
        Map<String,Object> config = (Map<String,Object>) Json.parseFile(configFile, 1L << 20);
        ((Map<String,Object>) config.get("runtime")).put("workRoot",mode==ArtifactJobRunner.Mode.IMAGE ? "/reports/blocked-root" : blocked.toString());
        Files.writeString(configFile, Json.write(config));
        var result = run(prepared);
        assertEquals(2, result.code(), result.err());
        assertFalse(Files.exists(result.report()));
        assertFalse(Files.exists(root.resolve("output/cleaned")));
        assertFalse(result.err().contains("gretl.run.started"));
        assertFalse(result.err().contains("gretl.run.finished"));
        assertEquals("keep", Files.readString(blocked));
    }

    @ParameterizedTest
    @CsvSource({"requestedCancel,REQUESTED", "interruptedCancel,INTERRUPTED"})
    void concreteControlCauseSurvivesTheProductEntry(String target, String reason) throws Exception {
        var mode = ArtifactJobRunner.Mode.configured();
        Path root = ArtifactJobRunner.directory("runtime-safety-cause-" + target + "-" + mode);
        var result = run(prepare(root, target));
        assertEquals(20, result.code(), result.err());
        assertTerminal(result, "CANCELLED", 20);
        assertCause(result, reason);
    }

    private static void assertCause(ArtifactJobRunner.Result result, String reason) throws Exception {
        Map<?,?> report = (Map<?,?>) Json.parseFile(result.report(), 1L << 20);
        Map<?,?> execution = (Map<?,?>) report.get("execution");
        assertEquals(reason, execution.get("cancelReason"));
        for (Object row : (List<?>) execution.get("tasks")) {
            Map<?,?> task = (Map<?,?>) row;
            assertEquals("CANCELLED".equals(task.get("status")) ? reason : null, task.get("cancelReason"));
        }
        for (Map<?,?> event : result.err().lines().filter(line -> line.startsWith("{")).map(line -> (Map<?,?>) Json.parse(line)).toList()) {
            if ("CANCELLED".equals(event.get("outcome")))
                assertEquals(reason, ((Map<?,?>) event.get("attributes")).get("cancel_reason"));
        }
    }

    @SuppressWarnings("unchecked")
    private static ArtifactJobRunner.Prepared prepare(Path root, String target) throws Exception {
        Map<String, Object> config = JobRunner.baseConfig(root);
        Map<String, Object> runtime = new LinkedHashMap<>((Map<String, Object>) config.get("runtime"));
        runtime.put("keepWork", "always");
        config.put("runtime", runtime);
        var request = new ArtifactJobRunner.Request("runtime-safety-" + target,
                ArtifactJobRunner.fixture("runtime-safety"), root, List.of(target),
                Map.of("output", root.resolve("output")), Map.of(), config, Map.of(), Duration.ofSeconds(45));
        return ArtifactJobRunner.prepare(request);
    }

    private static ArtifactJobRunner.Result run(ArtifactJobRunner.Prepared prepared) throws Exception {
        var running = ArtifactJobRunner.start(prepared);
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
        assertEquals(1, JobTestJson.events(result.err(), "gretl.run.finished").count(), result.err());
        assertEquals(1, JobTestJson.events(result.err(), "gretl.cleanup.finished").count(), result.err());
    }
}
