package ch.so.agi.gretl.runtime.runner;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.api.CancelReason;
import ch.so.agi.gretl.runtime.api.TaskCancelledException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WorkspacePreparationTest {
    @TempDir Path root;

    @ParameterizedTest @EnumSource(KeepWork.class)
    void resourcesAndWorkFollowTheSameRetentionAfterCleanup(KeepWork mode) throws Exception {
        for (String ending : new String[]{"success", "no-data", "failure", "cancel", "cleanup-failure"}) {
            UUID id = UUID.randomUUID();
            AtomicReference<Path> output = new AtomicReference<>();
            Path external = root.resolve(id + ".txt");
            boolean[] cleaned = {false};
            var definition = RunnerTestSupport.definition(root, builder -> {
                builder.register(RunnerTestSupport.task("producer").body(context -> {
                    assertEquals("resource", Files.readString(context.file("input.txt")));
                    output.set(context.workPath("sub/data.txt"));
                    Files.writeString(output.get(), "work");
                    Files.writeString(external, "permanent");
                    switch (ending) {
                        case "no-data" -> context.finishJob("empty");
                        case "failure" -> throw new IOException("task failed");
                        case "cancel" -> throw new TaskCancelledException(CancelReason.REQUESTED);
                    }
                    return output.get();
                }).build());
                builder.cleanup("read-before-retention", context -> {
                    assertEquals("work", Files.readString(output.get()));
                    assertEquals("resource", Files.readString(context.file("input.txt")));
                    cleaned[0] = true;
                    if (ending.equals("cleanup-failure")) throw new IOException("cleanup failed");
                });
                builder.defaults("producer");
            });
            var input = RunnerTestSupport.input(root, new FakeMonotonicClock(), new RecordingEventSink())
                    .runId(id).keepWork(mode).workspacePreparation(run -> {
                        Path resources = Files.createDirectory(run.resolve("job-resources"));
                        Files.writeString(resources.resolve("input.txt"), "resource");
                        return resources;
                    }).build();
            var outcome = new SequentialJobRunner().run(definition, RunnerTestSupport.plan(definition), input);
            RunStatus expected = switch (ending) {
                case "no-data" -> RunStatus.NO_DATA;
                case "failure", "cleanup-failure" -> RunStatus.FAILED;
                case "cancel" -> RunStatus.CANCELLED;
                default -> RunStatus.SUCCEEDED;
            };
            assertEquals(expected, outcome.status(), ending);
            boolean retained = mode == KeepWork.ALWAYS || mode == KeepWork.ON_FAILURE
                    && (expected == RunStatus.FAILED || expected == RunStatus.CANCELLED);
            assertTrue(cleaned[0], ending);
            assertEquals(retained, outcome.workRetained());
            assertEquals(retained, Files.exists(output.get()));
            assertEquals(retained, Files.exists(input.workRoot().resolve(id.toString())));
            assertEquals("permanent", Files.readString(external));
            assertTrue(Files.isDirectory(input.workRoot()));
        }
    }

    @Test void failedPreparationRemovesPartialOwnedRunWithoutTaskOrCleanup() throws Exception {
        var sink = new RecordingEventSink();
        var definition = RunnerTestSupport.definition(root, builder -> {
            builder.register(RunnerTestSupport.task("never").body(ctx -> fail("task ran")).build());
            builder.cleanup("never", ctx -> fail("cleanup ran"));
            builder.defaults("never");
        });
        var input = RunnerTestSupport.input(root, new FakeMonotonicClock(), sink)
                .keepWork(KeepWork.ALWAYS).workspacePreparation(run -> {
                    Files.writeString(run.resolve("partial"), "partial");
                    throw new IOException("missing packaged resource");
                }).build();
        Path sentinel = Files.writeString(root.resolve("external"), "safe");
        var failure = assertThrows(RunStartException.class, () -> new SequentialJobRunner()
                .run(definition, RunnerTestSupport.plan(definition), input));
        assertTrue(failure.getMessage().contains("missing packaged resource"));
        assertFalse(Files.exists(input.workRoot().resolve(input.runId().toString())));
        assertTrue(sink.events().isEmpty());
        assertEquals("safe", Files.readString(sentinel));
    }

    @Test void failedSetupReportsCleanupFailureWithoutFollowingReplacedRoot() throws Exception {
        Path external = Files.createDirectory(root.resolve("outside"));
        Files.writeString(external.resolve("sentinel"), "safe");
        var sink = new RecordingEventSink();
        var definition = RunnerTestSupport.definition(root, builder -> {
            builder.register(RunnerTestSupport.task("never").body(ctx -> fail("task ran")).build());
            builder.defaults("never");
        });
        var input = RunnerTestSupport.input(root, new FakeMonotonicClock(), sink)
                .workspacePreparation(run -> {
                    Files.delete(run);
                    Files.createSymbolicLink(run, external);
                    throw new IOException("setup failure");
                }).build();
        var failure = assertThrows(RunStartException.class, () -> new SequentialJobRunner()
                .run(definition, RunnerTestSupport.plan(definition), input));
        assertEquals(1, failure.getCause().getSuppressed().length);
        assertEquals(1, sink.count("gretl.workspace.retention_failed"));
        assertEquals(0, sink.count("gretl.run.started"));
        assertEquals("safe", Files.readString(external.resolve("sentinel")));
    }

    @Test void sameNamesStaySeparateAndProducerResultSurvivesItsScope() throws Exception {
        AtomicReference<Path> first = new AtomicReference<>();
        var definition = RunnerTestSupport.definition(root, builder -> {
            var producer = builder.register(RunnerTestSupport.task("first").body(ctx -> {
                first.set(ctx.workPath("data.txt"));
                assertEquals(first.get(), ctx.workPath("data.txt"));
                Files.writeString(first.get(), "first");
                return first.get();
            }).build());
            builder.register(RunnerTestSupport.task("second").dependsOn("first").body(ctx -> {
                Path own = ctx.workPath("data.txt");
                assertNotEquals(first.get(), own);
                assertEquals("first", Files.readString((Path) ctx.result(producer)));
                Files.writeString(own, "second");
                return own;
            }).build());
            builder.defaults("second");
        });
        var result = new SequentialJobRunner().run(definition, RunnerTestSupport.plan(definition),
                RunnerTestSupport.input(root, new FakeMonotonicClock(), new RecordingEventSink()).build());
        assertEquals(RunStatus.SUCCEEDED, result.status());
        assertFalse(Files.exists(first.get()));
    }
}
