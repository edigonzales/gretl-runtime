package ch.so.agi.gretl.it;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Process facts only. Fachliche success still requires the independent JUnit assertions. */
final class JobExecutionEvidence {
    private static final boolean LEGACY_MATRIX = Boolean.getBoolean("gretl.test.legacyMatrix");

    static boolean legacyMatrix() { return LEGACY_MATRIX; }

    static void started(ArtifactJobRunner.Prepared prepared, Instant at) throws IOException {
        if (LEGACY_MATRIX || prepared.mode() == ArtifactJobRunner.Mode.IMAGE)
            Files.writeString(prepared.request().directory().resolve("input-manifest.json"), Json.write(prepared.inputSnapshot().inventory()));
        if (!LEGACY_MATRIX) return;
        var row = base(prepared);
        row.put("process", Map.of("state", "STARTED", "startedAt", at.toString()));
        write(prepared.request().directory(), row);
    }
    static void completed(ArtifactJobRunner.Running running, ArtifactJobRunner.Result result) throws IOException {
        var prepared = running.prepared();
        var row = base(prepared);
        row.put("sha256", result.artifactIdentity());
        row.put("exitCode", result.code()); row.put("timedOut", result.timedOut()); row.put("aborted", result.aborted());
        if (!LEGACY_MATRIX) {
            row.put("process", Map.of("state", state(result), "exitCode", result.code()));
            write(prepared.request().directory(), row);
            return;
        }
        row.put("durationMillis", result.duration().toMillis());
        if (Files.isRegularFile(result.report())) {
            var report = (Map<?, ?>) Json.parseFile(result.report(), 16L << 20);
            if (report.get("identity") instanceof Map<?, ?> identity)
                row.put("reportIdentity", Map.of("runId", identity.get("runId"),
                        "commandId", identity.get("commandId"), "sha256", JobInputSnapshot.hash(result.report())));
        }
        row.put("process", Map.of("state", state(result), "exitCode", result.code(),
                "timedOut", result.timedOut(), "cancellationRequested", result.aborted(), "forciblyKilled", result.forciblyKilled(),
                "startedAt", running.started().toString(), "finishedAt", Instant.now().toString(), "pid", running.process().pid()));
        write(prepared.request().directory(), row);
    }
    private static Map<String, Object> base(ArtifactJobRunner.Prepared prepared) {
        var request = prepared.request();
        var row = new LinkedHashMap<String, Object>();
        row.put("mode", prepared.mode().name());
        row.put("executionKind", prepared.mode() == ArtifactJobRunner.Mode.JAR ? "PRECOMPILED_JOB" : "SCRIPT");
        var dist = prepared.launch().distribution();
        if (dist != null) row.put("sourceRevision", dist.sourceRevision());
        if (!LEGACY_MATRIX) return row;
        row.putAll(JobTestContext.identity(request.directory()));
        row.put("schemaVersion", 2); row.put("scenario", request.id());
        row.put("suiteRunId", System.getProperty("gretl.test.runId", "development"));
        row.put("input", Map.of("kind", request.input().kind().name(), "relativePath", request.input().relativePath(),
                "treeSha256", prepared.inputSnapshot().treeSha256(), "manifest", "input-manifest.json"));
        if (dist != null) {
            row.put("sourceDirty", dist.sourceDirty());
            row.put("runtime", Map.of("distributionManifestSha256", dist.manifestSha256(), "root", dist.root().toString(),
                    "launcherRelativePath", "bin/gretl"));
        }
        if (prepared.jar() != null) row.put("jobJar", Map.of("path", prepared.jar().toString(), "sha256", prepared.sha256()));
        if (prepared.image() != null) {
            var image = new LinkedHashMap<String,Object>(); image.put("id",prepared.image().imageId());
            if (prepared.image().proofFile() != null) {
                image.put("proof","image-proof.json");
                try {
                    var proof = (Map<?,?>)Json.parseFile(prepared.image().proofFile(),8L<<20);
                    image.put("os",proof.get("os")); image.put("architecture",proof.get("architecture"));
                } catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
            }
            row.put("image",image);
        }
        row.put("invocation", Map.of("verb", request.invocation().verb().name(), "help", request.invocation().help()));
        row.put("command", "command.json"); row.put("report", prepared.report().toString());
        row.put("endpoints", request.endpoints().entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().host())));
        return row;
    }
    static void failure(ArtifactJobRunner.Request request, ArtifactJobRunner.Mode mode, String state, Exception cause) throws IOException {
        if (!LEGACY_MATRIX) {
            write(request.directory(), Map.of("mode", mode.name(), "process", Map.of("state", state)));
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>(JobTestContext.identity(request.directory()));
        row.putAll(Map.of("schemaVersion", 2, "scenario", request.id(), "mode", mode.name(),
                "suiteRunId", System.getProperty("gretl.test.runId", "development"),
                "process", Map.of("state", state, "causeType", cause.getClass().getName())));
        write(request.directory(), row);
    }
    private static String state(ArtifactJobRunner.Result result) {
        if (result.forciblyKilled()) return "FORCED_TERMINATION";
        if (result.timedOut()) return "TIMED_OUT";
        return result.aborted() ? "CANCELLED" : "EXITED";
    }

    private static void write(Path directory, Map<String, Object> row) throws IOException {
        Path temporary = Files.createTempFile(directory, "execution-", ".tmp");
        try {
            Files.writeString(temporary, Json.write(row));
            Files.move(temporary, directory.resolve("execution.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
    }
}
