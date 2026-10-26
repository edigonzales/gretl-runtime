package ch.so.agi.gretl.runtime.logging;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.api.*;
import ch.so.agi.gretl.runtime.runner.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class LibraryScopeTest {
    @TempDir Path root;

    @Test void tasksAndNestedCallsUseTheirActualIdentitiesThroughClose() throws Exception {
        var logger = LoggerFactory.getLogger("fixture.scope");
        var op = new OperationType<String,String>("fixture.scope", "ScopeProbe", "probe", String.class, String.class,
                () -> new ConfigBuilder<>() { public String build() { return "ok"; } },
                () -> { logger.warn("factory"); return (request, c) -> {
                    logger.warn("handler-" + request);
                    c.resources().manage(() -> logger.warn("operation-close-" + request));
                    if (request.equals("caught")) throw new IOException("caught-business-failure");
                    return request;
                }; }, List.of(), List.of(), "probe");
        JobDefinitionBuilder b = new JobDefinitionBuilder(root);
        b.register(TaskRegistration.builder("a", Object.class).when(c -> { logger.warn("predicate"); return true; }).body(c -> {
            logger.warn("before");
            c.call(op, "one");
            assertThrows(IOException.class, () -> c.call(op, "caught"));
            logger.warn("after");
            c.resources().manage(() -> logger.warn("task-close"));
            return null;
        }).build());
        b.register(TaskRegistration.builder("b", Object.class).dependsOn("a").body(c -> { logger.warn("second"); return null; }).build());
        b.cleanup("release", c -> logger.warn("cleanup")); b.defaults("b");
        UUID run = UUID.randomUUID(); Path events = root.resolve("events.jsonl");
        var config = LoggingConfig.defaults().withLibraryLevels(Map.of("fixture.scope", LogLevel.INFO));
        try (var pipeline = new LoggingPipeline(config, new LogContext("scope", run, "job", "test", Optional.empty()),
                    new Redactor(new SecretRegistry()), new PrintStream(new ByteArrayOutputStream()), events, Clock.systemUTC());
             var bridge = LogbackBridge.install(pipeline, config)) {
            var d = b.freeze();
            var result = new SequentialJobRunner().run(d, new ExecutionPlanner().plan(d, List.of()),
                    RunInput.builder().startCwd(root).jobRoot(root).workRoot(root.resolve("work"))
                            .runId(run).jobName("job").eventSink(pipeline).build());
            assertEquals(RunStatus.SUCCEEDED, result.status());
            assertEquals(InvocationStatus.FAILED, result.invocations().get(1).status());
        }
        var records = Files.readAllLines(events).stream().map(line -> (Map<?,?>) Json.parse(line)).toList();
        for (String message : List.of("predicate", "before", "after", "task-close")) {
            var event = message(records, message); assertEquals("a", event.get("task_id"));
            assertNull(event.get("invocation_id")); assertNull(event.get("operation_id"));
        }
        assertEquals("b", message(records, "second").get("task_id"));
        for (String request : List.of("one", "caught")) {
            var event = message(records, "handler-" + request);
            assertEquals("a", event.get("task_id")); assertEquals("fixture.scope", event.get("operation_id"));
            assertNotNull(event.get("invocation_id"));
            assertEquals(event.get("invocation_id"), message(records, "operation-close-" + request).get("invocation_id"));
        }
        assertNotEquals(message(records, "handler-one").get("invocation_id"), message(records, "handler-caught").get("invocation_id"));
        assertNull(message(records, "cleanup").get("task_id"));
        assertTrue(records.stream().filter(r -> "gretl.library.log".equals(r.get("event_name")))
                .allMatch(r -> run.toString().equals(r.get("run_id"))));
    }

    @Test void preparationFailureIsCorrelatedAndCatchRestoresTaskScope() throws Exception {
        var logger = LoggerFactory.getLogger("fixture.scope");
        var op = new OperationType<String,String>("fixture.scope", "ScopeProbe", "probe", String.class, String.class,
                () -> new ConfigBuilder<>() { public String build() { return "ok"; } },
                () -> (r, c) -> r, List.of(), List.of(), "probe");
        var b = new JobDefinitionBuilder(root);
        b.register(TaskRegistration.builder("a", Object.class).body(c -> {
            assertThrows(IOException.class, () -> ((OperationCaller)c).invoke(op, (invocation, task) -> {
                logger.warn("configuration");
                invocation.resources().manage(() -> logger.warn("configuration-close"));
                throw new IOException("configuration-failure");
            }));
            logger.warn("after-configuration"); return null;
        }).build()); b.defaults("a");
        UUID id = UUID.randomUUID(); Path file = root.resolve("config.jsonl");
        var config = LoggingConfig.defaults().withLibraryLevels(Map.of("fixture.scope", LogLevel.INFO));
        try (var pipeline = new LoggingPipeline(config, new LogContext("scope", id, "job", "test", Optional.empty()),
                new Redactor(new SecretRegistry()), new PrintStream(new ByteArrayOutputStream()), file, Clock.systemUTC());
             var bridge = LogbackBridge.install(pipeline, config)) {
            var d = b.freeze(); var result = new SequentialJobRunner().run(d, new ExecutionPlanner().plan(d, List.of()),
                    RunInput.builder().startCwd(root).jobRoot(root).workRoot(root.resolve("work")).runId(id)
                            .jobName("job").eventSink(pipeline).build());
            assertEquals(RunStatus.SUCCEEDED, result.status());
        }
        var records = Files.readAllLines(file).stream().map(line -> (Map<?,?>)Json.parse(line)).toList();
        var before = message(records, "configuration");
        assertEquals("a", before.get("task_id")); assertEquals("fixture.scope", before.get("operation_id"));
        assertNotNull(before.get("invocation_id"));
        assertEquals(before.get("invocation_id"), message(records, "configuration-close").get("invocation_id"));
        assertEquals("a", message(records, "after-configuration").get("task_id"));
        assertNull(message(records, "after-configuration").get("invocation_id"));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void failureAndTimeoutReleaseScopesBeforeCleanupAndTheNextRun(boolean timeout) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            boolean first = attempt == 0;
            UUID id = UUID.randomUUID(); Path file = root.resolve("terminal-" + attempt + ".jsonl");
            var logger = LoggerFactory.getLogger("fixture.scope");
            var ticks = new java.util.concurrent.atomic.AtomicLong();
            var b = new JobDefinitionBuilder(root);
            b.register(TaskRegistration.builder(first ? "old" : "fresh", Object.class).timeout(Duration.ofSeconds(1)).body(c -> {
                logger.warn("work");
                c.resources().manage(() -> logger.warn("release"));
                if (first && timeout) ticks.set(Duration.ofSeconds(2).toNanos());
                else if (first) throw new IOException("unhandled");
                return null;
            }).build());
            b.cleanup("release", c -> logger.warn("cleanup")); b.defaults(first ? "old" : "fresh");
            var config = LoggingConfig.defaults().withLibraryLevels(Map.of("fixture.scope", LogLevel.INFO));
            try (var pipeline = new LoggingPipeline(config, new LogContext("scope", id, "job", "test", Optional.empty()),
                    new Redactor(new SecretRegistry()), new PrintStream(new ByteArrayOutputStream()), file, Clock.systemUTC());
                 var bridge = LogbackBridge.install(pipeline, config)) {
                var d = b.freeze(); var result = new SequentialJobRunner().run(d, new ExecutionPlanner().plan(d, List.of()),
                        RunInput.builder().startCwd(root).jobRoot(root).workRoot(root.resolve("work")).runId(id)
                                .jobName("job").monotonicClock(ticks::get).eventSink(pipeline).build());
                assertEquals(first ? (timeout ? RunStatus.CANCELLED : RunStatus.FAILED) : RunStatus.SUCCEEDED, result.status());
                assertNull(org.slf4j.MDC.get("gretl.task_id")); assertNull(org.slf4j.MDC.get("gretl.invocation_id"));
                logger.warn("after-run");
            }
            var records = Files.readAllLines(file).stream().map(line -> (Map<?,?>)Json.parse(line)).toList();
            assertEquals(first ? "old" : "fresh", message(records, "release").get("task_id"));
            for (String text : List.of("cleanup", "after-run")) {
                var e = message(records, text); assertEquals(id.toString(), e.get("run_id"));
                assertNull(e.get("task_id")); assertNull(e.get("invocation_id"));
            }
        }
    }

    static Map<?,?> message(List<? extends Map<?,?>> records, String message) {
        var found = records.stream().filter(r -> message.equals(r.get("message"))).toList();
        assertEquals(1, found.size(), "message=" + message); return found.getFirst();
    }
}
