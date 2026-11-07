package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.CsvSource;

class CancellationLocalJobIT {
    @ParameterizedTest
    @CsvSource({"false", "true"})
    void cooperativeCancelAndSigtermFinishOnce(boolean sigterm) throws Exception {
        Path root = ArtifactJobRunner.directory("cancel-" + sigterm);
        try (var watch = FileSystems.getDefault().newWatchService()) {
            root.resolve("output").register(watch, StandardWatchEventKinds.ENTRY_CREATE);
            var request = new ArtifactJobRunner.Request("cancel", ArtifactJobRunner.fixture("cancellation"),
                    root, List.of(), Map.of("signals", root.resolve("output")), Map.of(),
                    JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(45));
            var running = ArtifactJobRunner.start(ArtifactJobRunner.prepare(request));
            try {
                await(root.resolve("output/ready"), running.process(), watch);
                if (sigterm) ArtifactJobRunner.cancel(running);
                else Files.writeString(root.resolve("output/cancel"), "cancel");
                var result = ArtifactJobRunner.finish(running, true);
                assertFalse(result.timedOut());
                if (sigterm) assertTrue(result.code() == 143 || result.code() == 20, result.err());
                else assertEquals(20, result.code(), result.err());
                assertTrue(Files.exists(root.resolve("output/cleaned")));
                assertFalse(Files.exists(root.resolve("output/unexpected")));
                var execution = JobTestJson.execution(JobTestJson.read(result.report()));
                assertEquals("CANCELLED", execution.get("status"));
                assertEquals(20L, execution.get("exitCode"));
                assertEquals(1, JobTestJson.events(result.err(), "gretl.run.finished").count());
                assertEquals(1, JobTestJson.events(result.err(), "gretl.cleanup.finished").count());
            } finally {
                if (running.process().isAlive()) ArtifactJobRunner.stop(running);
            }
        }
    }

    @Test
    void originalFailureWinsOverSigtermDuringCleanup() throws Exception {
        Path root = ArtifactJobRunner.directory("failed-shutdown");
        try (var watch = FileSystems.getDefault().newWatchService()) {
            root.resolve("output").register(watch, StandardWatchEventKinds.ENTRY_CREATE);
            var request = new ArtifactJobRunner.Request("failed-shutdown", ArtifactJobRunner.fixture("cancellation"),
                    root, List.of(), Map.of("signals", root.resolve("output"), "fail", "true"), Map.of(),
                    JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(45));
            var running = ArtifactJobRunner.start(ArtifactJobRunner.prepare(request));
            try {
                await(root.resolve("output/ready"), running.process(), watch);
                ArtifactJobRunner.cancel(running);
                Files.writeString(root.resolve("output/release"), "release");
                var result = ArtifactJobRunner.finish(running, true);
                var report = JobTestJson.read(result.report());
                var execution = JobTestJson.execution(report);
                assertEquals("FAILED", execution.get("status"));
                assertEquals(10L, execution.get("exitCode"));
                assertEquals("original-business-failure", JobTestJson.primaryError(report).get("message"));
            } finally {
                Files.writeString(root.resolve("output/release"), "release");
                if (running.process().isAlive()) ArtifactJobRunner.stop(running);
            }
        }
    }

    @Test
    void runnerTimeoutReapsTheProcessAndPreservesCooperativeReport() throws Exception {
        Path root = ArtifactJobRunner.directory("runner-timeout");
        try (var watch = FileSystems.getDefault().newWatchService()) {
            root.resolve("output").register(watch, StandardWatchEventKinds.ENTRY_CREATE);
            var request = new ArtifactJobRunner.Request("runner-timeout", ArtifactJobRunner.fixture("cancellation"),
                    root, List.of(), Map.of("signals", root.resolve("output")), Map.of(),
                    JobRunner.baseConfig(root), Map.of(), Duration.ofMillis(10));
            var running = ArtifactJobRunner.start(ArtifactJobRunner.prepare(request));
            try {
                await(root.resolve("output/ready"), running.process(), watch);
                var result = ArtifactJobRunner.finish(running, false);
                assertTrue(result.timedOut());
                assertFalse(running.process().isAlive());
                assertEquals("CANCELLED", JobTestJson.execution(JobTestJson.read(result.report())).get("status"));
                assertTrue(Files.exists(root.resolve("output/cleaned")));
            } finally {
                if (running.process().isAlive()) ArtifactJobRunner.stop(running);
            }
        }
    }

    private static void await(Path ready, Process process, WatchService watch) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!Files.exists(ready)) {
            assertTrue(process.isAlive(), "Job ended before synchronization point");
            long left = deadline - System.nanoTime();
            assertTrue(left > 0, "No synchronization point");
            WatchKey key = watch.poll(Math.min(left, TimeUnit.SECONDS.toNanos(1)), TimeUnit.NANOSECONDS);
            if (key != null) { key.pollEvents(); key.reset(); }
        }
    }
}
