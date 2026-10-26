package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.runtime.logging.Json;
import ch.so.agi.gretl.runtime.runner.JobOutcome;
import ch.so.agi.gretl.runtime.runner.TaskOutcome;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Optional small Kubernetes termination message; never a stack or secret dump. */
public final class TerminationMessage {
    private static final int MAX_BYTES = 3072;

    private TerminationMessage() {
    }

    public static void write(Path path, JobOutcome outcome) throws IOException {
        Path target = path.toAbsolutePath().normalize();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("runId", outcome.runId().toString());
        message.put("status", outcome.status().name());
        message.put("exitCode", outcome.exitCode());
        outcome.primaryFailure().flatMap(failure -> outcome.tasks().stream()
                        .filter(task -> task.status() == ch.so.agi.gretl.runtime.runner.TaskStatus.FAILED)
                        .map(TaskOutcome::taskId).findFirst())
                .ifPresent(taskId -> message.put("taskId", taskId));
        outcome.primaryFailure().ifPresent(failure -> message.put("errorId", failure.id()));
        String json = Json.write(message);
        byte[] bytes = (json + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            message.remove("errorId");
            json = Json.write(message);
            bytes = (json + "\n").getBytes(StandardCharsets.UTF_8);
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = Files.createTempFile(parent, ".termination-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
