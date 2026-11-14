package ch.so.agi.gretl.it;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Small process records used by the JUnit assertions and runtime tooling canary. */
final class JobExecutionEvidence {
    static void recordImageInput(ArtifactJobRunner.Prepared prepared) throws IOException {
        if (prepared.mode() == ArtifactJobRunner.Mode.IMAGE)
            Files.writeString(prepared.request().directory().resolve("input-manifest.json"),
                    Json.write(prepared.inputSnapshot().inventory()));
    }

    static void completed(ArtifactJobRunner.Prepared prepared, ArtifactJobRunner.Result result) throws IOException {
        var row = new LinkedHashMap<String, Object>();
        row.put("mode", result.mode());
        row.put("executionKind", prepared.mode() == ArtifactJobRunner.Mode.JAR ? "PRECOMPILED_JOB" : "SCRIPT");
        var dist = prepared.launch().distribution();
        row.put("exitCode", result.code());
        row.put("timedOut", result.timedOut());
        row.put("process", Map.of("state", state(result), "exitCode", result.code()));
        write(prepared.request().directory(), row);
    }

    static void failure(ArtifactJobRunner.Request request, ArtifactJobRunner.Mode mode, String state, Exception cause) {
        try {
            write(request.directory(), Map.of("mode", mode.name(), "process", Map.of("state", state)));
        } catch (IOException diagnostics) {
            cause.addSuppressed(diagnostics);
        }
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
