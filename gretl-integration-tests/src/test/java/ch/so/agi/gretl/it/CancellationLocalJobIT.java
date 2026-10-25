package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CancellationLocalJobIT {
    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void cooperativeCancelAndSigtermFinishOnce(boolean script, boolean sigterm) throws Exception {
        Path root = ArtifactJobRunner.directory("cancel-" + script + "-" + sigterm);
        try (var watch = FileSystems.getDefault().newWatchService()) {
            root.resolve("output").register(watch, StandardWatchEventKinds.ENTRY_CREATE);
            var request = new ArtifactJobRunner.Request("cancel", ArtifactJobRunner.fixture("cancellation"),
                    root, List.of(), Map.of("signals", root.resolve("output").toString()), Map.of(),
                    JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(45));
            var running = ArtifactJobRunner.start(ArtifactJobRunner.prepare(request), script);
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
                String report = Files.readString(result.report());
                assertTrue(report.contains("\"status\":\"CANCELLED\""), report);
                assertTrue(report.contains("\"exitCode\":20"), report);
                assertEquals(1, result.err().lines().filter(s -> s.contains("\"event_name\":\"gretl.run.finished\"")).count());
                assertEquals(1, result.err().lines().filter(s -> s.contains("\"event_name\":\"gretl.cleanup.finished\"")).count());
            } finally {
                if (running.process().isAlive()) ArtifactJobRunner.stop(running);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void originalFailureWinsOverSigtermDuringCleanup(boolean script) throws Exception {
        Path root = ArtifactJobRunner.directory("failed-shutdown-" + script);
        try (var watch = FileSystems.getDefault().newWatchService()) {
            root.resolve("output").register(watch, StandardWatchEventKinds.ENTRY_CREATE);
            var request = new ArtifactJobRunner.Request("failed-shutdown", ArtifactJobRunner.fixture("cancellation"),
                    root, List.of(), Map.of("signals", root.resolve("output").toString(), "fail", "true"), Map.of(),
                    JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(45));
            var running = ArtifactJobRunner.start(ArtifactJobRunner.prepare(request), script);
            try {
                await(root.resolve("output/ready"), running.process(), watch);
                ArtifactJobRunner.cancel(running);
                Files.writeString(root.resolve("output/release"), "release");
                var result = ArtifactJobRunner.finish(running, true);
                String report = Files.readString(result.report());
                assertTrue(report.contains("\"status\":\"FAILED\""), report);
                assertTrue(report.contains("\"exitCode\":10"), report);
                assertTrue(report.contains("original-business-failure"));
            } finally {
                Files.writeString(root.resolve("output/release"), "release");
                if (running.process().isAlive()) ArtifactJobRunner.stop(running);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void runnerTimeoutReapsTheProcessAndPreservesCooperativeReport(boolean script) throws Exception {
        Path root = ArtifactJobRunner.directory("runner-timeout-" + script);
        try (var watch = FileSystems.getDefault().newWatchService()) {
            root.resolve("output").register(watch, StandardWatchEventKinds.ENTRY_CREATE);
            var request = new ArtifactJobRunner.Request("runner-timeout", ArtifactJobRunner.fixture("cancellation"),
                    root, List.of(), Map.of("signals", root.resolve("output").toString()), Map.of(),
                    JobRunner.baseConfig(root), Map.of(), Duration.ofMillis(10));
            var running = ArtifactJobRunner.start(ArtifactJobRunner.prepare(request), script);
            try {
                await(root.resolve("output/ready"), running.process(), watch);
                var result = ArtifactJobRunner.finish(running, false);
                assertTrue(result.timedOut());
                assertFalse(running.process().isAlive());
                assertTrue(Files.readString(result.report()).contains("\"status\":\"CANCELLED\""));
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
