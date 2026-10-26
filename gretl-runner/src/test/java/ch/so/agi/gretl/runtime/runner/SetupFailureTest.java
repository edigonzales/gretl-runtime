package ch.so.agi.gretl.runtime.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Deterministic setup failures: a regular file occupies a required directory. */
class SetupFailureTest {
    @TempDir
    Path tempDir;

    @Test
    void failedRunWorkspaceStillRunsNonFileCleanupsAndFinishes() throws Exception {
        Path workRoot = Files.writeString(tempDir.resolve("blocked-work"), "untouched");
        List<String> cleaned = new ArrayList<>();
        AtomicInteger taskCalls = new AtomicInteger();
        JobDefinition definition = RunnerTestSupport.definition(tempDir, builder -> {
            builder.register(RunnerTestSupport.task("never").body(context -> {
                taskCalls.incrementAndGet();
                return null;
            }).build());
            builder.cleanup("first", context -> cleaned.add("first"));
            builder.cleanup("second", context -> {
                context.log().info("cleanup after workspace failure");
                cleaned.add("second");
            });
            builder.defaults("never");
        });
        RecordingEventSink sink = new RecordingEventSink();
        JobOutcome outcome = new SequentialJobRunner().run(definition,
                RunnerTestSupport.plan(definition),
                RunnerTestSupport.input(tempDir, new FakeMonotonicClock(), sink)
                        .workRoot(workRoot).build());

        assertEquals(RunStatus.FAILED, outcome.status());
        assertEquals(10, outcome.exitCode());
        assertEquals(List.of("second", "first"), cleaned);
        assertEquals(0, taskCalls.get());
        assertEquals(TaskStatus.NOT_RUN, outcome.task("never").orElseThrow().status());
        assertTrue(outcome.primaryFailure().orElseThrow().message().contains("blocked-work"));
        assertEquals("untouched", Files.readString(workRoot));
        assertFalse(outcome.workRetained());
        assertEquals(1, sink.count("gretl.run.started"));
        assertEquals(1, sink.count("gretl.run.finished"));
        assertEquals(2, sink.count("gretl.cleanup.finished"));
    }

    @Test
    void taskWorkspaceFailureIsTerminalAndPreservesSetupCauseBeforeCleanupErrors() {
        UUID runId = UUID.randomUUID();
        Path runRoot = tempDir.resolve("work").resolve(runId.toString());
        List<String> cleaned = new ArrayList<>();
        JobDefinition definition = RunnerTestSupport.definition(tempDir, builder -> {
            builder.register(RunnerTestSupport.task("prepare").body(context -> {
                Files.writeString(runRoot.resolve("task-000001"), "blocking task directory");
                return null;
            }).build());
            builder.register(RunnerTestSupport.task("blocked").body(context -> {
                throw new AssertionError("task body must not start");
            }).build());
            builder.register(RunnerTestSupport.task("later").body(context -> {
                throw new AssertionError("later task must not start");
            }).build());
            builder.cleanup("survivor", context -> cleaned.add("survivor"));
            builder.cleanup("failing", context -> {
                throw new IllegalStateException("secondary cleanup failure");
            });
            builder.defaults("prepare", "blocked", "later");
        });
        RecordingEventSink sink = new RecordingEventSink();
        JobOutcome outcome = new SequentialJobRunner().run(definition,
                RunnerTestSupport.plan(definition),
                RunnerTestSupport.input(tempDir, new FakeMonotonicClock(), sink)
                        .runId(runId).build());

        assertEquals(RunStatus.FAILED, outcome.status());
        assertEquals(TaskStatus.SUCCEEDED, outcome.task("prepare").orElseThrow().status());
        assertEquals(TaskStatus.FAILED, outcome.task("blocked").orElseThrow().status());
        assertEquals(TaskStatus.NOT_RUN, outcome.task("later").orElseThrow().status());
        assertTrue(outcome.primaryFailure().orElseThrow().message().contains("task-000001"));
        assertEquals("secondary cleanup failure", outcome.secondaryFailures().get(0).message());
        assertEquals(List.of("survivor"), cleaned);
        assertEquals(1, sink.count("gretl.run.finished"));
    }

    @Test
    void cleanupWithoutFilesDoesNotOpenItsWorkspaceAndFileCleanupFailureIsSecondary() {
        UUID runId = UUID.randomUUID();
        Path runRoot = tempDir.resolve("work").resolve(runId.toString());
        List<String> cleaned = new ArrayList<>();
        JobDefinition definition = RunnerTestSupport.definition(tempDir, builder -> {
            builder.register(RunnerTestSupport.task("failing").body(context -> {
                Files.writeString(runRoot.resolve("cleanup-000"), "non-file cleanup blocker");
                Files.writeString(runRoot.resolve("cleanup-001"), "file cleanup blocker");
                throw new IllegalArgumentException("original task failure");
            }).build());
            builder.cleanup("no-files", context -> cleaned.add("no-files"));
            builder.cleanup("needs-files", context -> Files.writeString(
                    context.workPath("result.txt"), "never written"));
            builder.defaults("failing");
        });
        RecordingEventSink sink = new RecordingEventSink();
        JobOutcome outcome = new SequentialJobRunner().run(definition,
                RunnerTestSupport.plan(definition),
                RunnerTestSupport.input(tempDir, new FakeMonotonicClock(), sink)
                        .runId(runId).build());

        assertEquals(RunStatus.FAILED, outcome.status());
        assertEquals("original task failure", outcome.primaryFailure().orElseThrow().message());
        assertEquals(List.of("no-files"), cleaned);
        assertEquals(1, outcome.secondaryFailures().size());
        assertTrue(outcome.secondaryFailures().get(0).message().contains("cleanup-001"));
        assertEquals(1, sink.count("gretl.cleanup.finished"));
        assertEquals(1, sink.count("gretl.cleanup.failed"));
        assertEquals(1, sink.count("gretl.run.finished"));
    }
}
