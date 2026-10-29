package ch.so.agi.gretl.runtime.runner;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunnerSourceMappingTest {
    @TempDir Path root;

    @Test void sourceMetadataIsFrozenAndOldConstructorStaysEmpty() {
        var builder = new JobDefinitionBuilder(root);
        var aliases = new LinkedHashMap<>(Map.of("Compiled.groovy", root.resolve("import/job.groovy")));
        builder.sourceFiles(aliases);
        aliases.clear();
        var definition = builder.freeze();
        assertEquals(root.resolve("import/job.groovy"), definition.sourceFiles().get("Compiled.groovy"));
        assertThrows(UnsupportedOperationException.class, () -> definition.sourceFiles().clear());
        assertThrows(DefinitionException.class, () -> builder.sourceFiles(Map.of()));
        assertTrue(new JobDefinition(definition.definitionId(), definition.tasks(), definition.parameters(),
                definition.defaultTargets(), definition.cleanups(), definition.jobRoot()).sourceFiles().isEmpty());
    }

    @Test void caughtEarlyLogIsMappedAndSuccessfulRunDoesNotLeakAliasesToNextRun() {
        var raw = failure("caught", 12);
        for (boolean mapped : List.of(true, false)) {
            var builder = new JobDefinitionBuilder(root);
            if (mapped) builder.sourceFiles(Map.of("Compiled.groovy", root.resolve("import/job.groovy")));
            builder.register(RunnerTestSupport.task("action").body(context -> {
                try { throw raw; }
                catch (IOException actual) {
                    assertSame(raw, actual);
                    context.log().error("caught", actual);
                }
                return null;
            }).build());
            builder.defaults("action");
            var definition = builder.freeze();
            var sink = new RecordingEventSink();
            var outcome = new SequentialJobRunner().run(definition, RunnerTestSupport.plan(definition),
                    RunnerTestSupport.input(root, new FakeMonotonicClock(), sink).build());
            assertEquals(RunStatus.SUCCEEDED, outcome.status());
            var source = sink.first("gretl.task.message").failure().orElseThrow().source().orElseThrow();
            assertEquals(mapped ? root.resolve("import/job.groovy").toString() : "Compiled.groovy", source.file());
            assertEquals(12, source.line());
        }
        assertEquals("Compiled.groovy", FailureInfo.from(raw).source().orElseThrow().file());
    }

    @Test void taskResourceAndCleanupFailuresAreMappedAfterTheBodyReturns() {
        var resourceError = failure("resource", 21);
        var cleanupError = failure("cleanup", 24);
        var phases = new ArrayList<String>();
        var definition = RunnerTestSupport.definition(root, builder -> {
            builder.sourceFiles(Map.of("Compiled.groovy", root.resolve("import/job.groovy")));
            builder.register(RunnerTestSupport.task("action").body(context -> {
                context.resources().manage(() -> { phases.add("resource"); throw resourceError; });
                phases.add("body");
                return null;
            }).build());
            builder.cleanup("cleanup", context -> { phases.add("cleanup"); throw cleanupError; });
            builder.defaults("action");
        });
        var sink = new RecordingEventSink();
        var outcome = new SequentialJobRunner().run(definition, RunnerTestSupport.plan(definition),
                RunnerTestSupport.input(root, new FakeMonotonicClock(), sink).build());
        assertEquals(List.of("body", "resource", "cleanup"), phases);
        assertEquals(RunStatus.FAILED, outcome.status());
        var resource = outcome.primaryFailure().orElseThrow();
        assertEquals(IOException.class.getName(), resource.type());
        assertEquals(root.resolve("import/job.groovy").toString(), resource.source().orElseThrow().file());
        assertEquals(21, resource.source().orElseThrow().line());
        var cleanup = sink.events().stream().flatMap(event -> event.failure().stream())
                .filter(info -> info.message().equals("cleanup")).findFirst().orElseThrow();
        assertEquals(root.resolve("import/job.groovy").toString(), cleanup.source().orElseThrow().file());
        assertEquals(24, cleanup.source().orElseThrow().line());
        assertEquals("Compiled.groovy", resourceError.getStackTrace()[0].getFileName());
    }

    private IOException failure(String message, int line) {
        var failure = new IOException(message);
        failure.setStackTrace(new StackTraceElement[] { new StackTraceElement("Script", "run", "Compiled.groovy", line) });
        return failure;
    }
}
