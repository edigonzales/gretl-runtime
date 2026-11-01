package ch.so.agi.gretl.it;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Process facts only. Fachliche success still requires the independent JUnit assertions. */
final class JobExecutionEvidence {
    static void started(ArtifactJobRunner.Prepared prepared, Instant at) throws IOException {
        Files.writeString(prepared.request().directory().resolve("input-manifest.json"), Json.write(prepared.inputSnapshot().inventory()));
        var row = base(prepared);
        row.put("process", Map.of("state", "STARTED", "startedAt", at.toString()));
        write(prepared.request().directory(), row);
    }
    static void completed(ArtifactJobRunner.Running running, ArtifactJobRunner.Result result) throws IOException {
        var prepared = running.prepared();
        var row = base(prepared);
        row.put("sha256", result.artifactIdentity());
        row.put("exitCode", result.code()); row.put("timedOut", result.timedOut()); row.put("aborted", result.aborted());
        row.put("durationMillis", result.duration().toMillis());
        if (Files.isRegularFile(result.report())) {
            var report = (Map<?, ?>) Json.parseFile(result.report(), 16L << 20);
            if (report.get("identity") instanceof Map<?, ?> identity)
                row.put("reportIdentity", Map.of("runId", identity.get("runId"),
                        "commandId", identity.get("commandId"), "sha256", JobInputSnapshot.hash(result.report())));
        }
        row.put("process", Map.of("state", result.forciblyKilled() ? "FORCED_TERMINATION" : result.timedOut() ? "TIMED_OUT"
                        : result.aborted() ? "CANCELLED" : "EXITED", "exitCode", result.code(),
                "timedOut", result.timedOut(), "cancellationRequested", result.aborted(), "forciblyKilled", result.forciblyKilled(),
                "startedAt", running.started().toString(), "finishedAt", Instant.now().toString(), "pid", running.process().pid()));
        write(prepared.request().directory(), row);
    }
    private static Map<String, Object> base(ArtifactJobRunner.Prepared prepared) {
        var request = prepared.request();
        var row = new LinkedHashMap<String, Object>();
        row.putAll(JobTestContext.identity(request.directory()));
        row.put("schemaVersion", 2); row.put("scenario", request.id()); row.put("mode", prepared.mode().name());
        row.put("suiteRunId", System.getProperty("gretl.test.runId", "development"));
        row.put("executionKind", prepared.mode() == ArtifactJobRunner.Mode.JAR ? "PRECOMPILED_JOB" : "SCRIPT");
        row.put("input", Map.of("kind", request.input().kind().name(), "relativePath", request.input().relativePath(),
                "treeSha256", prepared.inputSnapshot().treeSha256(), "manifest", "input-manifest.json"));
        var dist = prepared.launch().distribution();
        if (dist != null) {
            row.put("sourceRevision", dist.sourceRevision()); row.put("sourceDirty", dist.sourceDirty());
            row.put("runtime", Map.of("distributionManifestSha256", dist.manifestSha256(), "root", dist.root().toString(),
                    "launcherRelativePath", "bin/gretl"));
        }
        if (prepared.jar() != null) row.put("jobJar", Map.of("path", prepared.jar().toString(), "sha256", prepared.sha256()));
        if (prepared.image() != null) row.put("image", Map.of("id", prepared.image().imageId()));
        row.put("invocation", Map.of("verb", request.invocation().verb().name(), "help", request.invocation().help()));
        row.put("command", "command.json"); row.put("report", prepared.report().toString());
        row.put("endpoints", request.endpoints().entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().host())));
        return row;
    }
    static void failure(ArtifactJobRunner.Request request, ArtifactJobRunner.Mode mode, String state, Exception cause) throws IOException {
        Map<String, Object> row = new LinkedHashMap<>(JobTestContext.identity(request.directory()));
        row.putAll(Map.of("schemaVersion", 2, "scenario", request.id(), "mode", mode.name(),
                "suiteRunId", System.getProperty("gretl.test.runId", "development"),
                "process", Map.of("state", state, "causeType", cause.getClass().getName())));
        write(request.directory(), row);
    }
    private static void write(Path directory, Map<String, Object> row) throws IOException {
        Path temporary = Files.createTempFile(directory, "execution-", ".tmp");
        try {
            Files.writeString(temporary, Json.write(row));
            Files.move(temporary, directory.resolve("execution.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
    }
}
