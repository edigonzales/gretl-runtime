package ch.so.agi.gretl.runtime.logging;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.api.*;
import ch.so.agi.gretl.runtime.runner.*;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.*;

class LibrarySnapshotTest {
    @TempDir Path root;
    @AfterEach void clearTestMdc() { MDC.clear(); }

    @Test void nestingRestoresOnlyReservedKeysAndKeepsForeignChanges() throws Exception {
        for (String key : MdcExecutionScope.KEYS) MDC.put(key, "previous-" + key);
        Map<String,String> previous = MDC.getCopyOfContextMap(); MDC.put("foreign", "owner-before");
        try (var f = new Fixture()) {
            try (var run = f.scope(null, null)) {
                assertNull(MDC.get(MdcExecutionScope.TASK));
                try (var task = f.scope("a", null)) {
                    try (var invocation = f.scope("a", "i1")) {
                        assertEquals("i1", MDC.get(MdcExecutionScope.INVOCATION));
                        MDC.put("foreign", "owner-during"); f.logger.warn("inside");
                    }
                    assertNull(MDC.get(MdcExecutionScope.INVOCATION));
                    assertNull(MDC.get(MdcExecutionScope.OPERATION));
                    f.logger.warn("outside");
                }
                assertNull(MDC.get(MdcExecutionScope.TASK));
            }
            for (var entry : previous.entrySet()) assertEquals(entry.getValue(), MDC.get(entry.getKey()));
            assertEquals("owner-during", MDC.get("foreign"));
            assertFalse(Files.readString(f.file).contains("owner-during"));
        }
    }

    @Test void deferredEventKeepsOriginSnapshotAndTimestampDuringAnotherTask() throws Exception {
        try (var f = new Fixture()) {
            LoggingEvent event;
            Instant origin = Instant.parse("2026-10-20T01:02:03.123Z");
            try (var a = f.scope("a", "i1")) {
                event = new LoggingEvent(getClass().getName(), f.logger, Level.WARN, "deferred", null, null);
                event.setInstant(origin);
                event.prepareForDeferredProcessing();
            }
            try (var b = f.scope("b", "i2")) {
                f.logger.callAppenders(event); f.logger.warn("current");
            }
            var old = f.message("deferred");
            assertEquals("a", old.get("task_id")); assertEquals("i1", old.get("invocation_id"));
            assertEquals(origin.toString(), old.get("timestamp"));
            assertEquals("b", f.message("current").get("task_id"));
        }
    }

    @Test void invalidOrForeignEventSnapshotsFallBackToRunOnly() throws Exception {
        try (var f = new Fixture()) {
            var valid = Map.of(MdcExecutionScope.RUN, f.id.toString(), MdcExecutionScope.JOB, "job", MdcExecutionScope.TASK, "a");
            List<Map<String,String>> invalid = new ArrayList<>(); invalid.add(Map.of());
            for (String run : List.of("", "broken", UUID.randomUUID().toString())) {
                var m = new HashMap<>(valid); m.put(MdcExecutionScope.RUN, run); invalid.add(m);
            }
            var missingJob = new HashMap<>(valid); missingJob.remove(MdcExecutionScope.JOB); invalid.add(missingJob);
            var emptyTask = new HashMap<>(valid); emptyTask.put(MdcExecutionScope.TASK, ""); invalid.add(emptyTask);
            var orphan = new HashMap<>(valid); orphan.remove(MdcExecutionScope.TASK);
            orphan.put(MdcExecutionScope.INVOCATION, "i1"); orphan.put(MdcExecutionScope.OPERATION, "fixture.scope"); invalid.add(orphan);
            int index = 0;
            try (var b = f.scope("b", "i2")) {
                for (Map<String,String> m : invalid) {
                    var e = new LoggingEvent(getClass().getName(), f.logger, Level.WARN, "invalid-" + index++, null, null);
                    e.setMDCPropertyMap(Map.copyOf(m)); f.logger.callAppenders(e);
                }
            }
            for (var e : f.records()) {
                assertEquals(f.id.toString(), e.get("run_id")); assertNull(e.get("task_id"));
                assertNull(e.get("invocation_id")); assertNull(e.get("operation_id"));
            }
        }
    }

    @Test void newAndPoolThreadsDoNotInheritOrGuessTheActiveTask() throws Exception {
        try (var f = new Fixture(); var pool = Executors.newSingleThreadExecutor()) {
            CountDownLatch ready = new CountDownLatch(2), release = new CountDownLatch(1), done = new CountDownLatch(2);
            Runnable work = () -> {
                ready.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    f.logger.warn("foreign-" + Thread.currentThread().getName());
                    java.util.logging.Logger.getLogger("fixture.scope").warning("jul-foreign-" + Thread.currentThread().getName());
                } catch (InterruptedException e) { throw new AssertionError(e); }
                finally { done.countDown(); }
            };
            Thread child; Future<?> pooled;
            try (var a = f.scope("a", "i1")) {
                child = Thread.ofPlatform().name("child").start(work); pooled = pool.submit(work);
                assertTrue(ready.await(5, TimeUnit.SECONDS));
            }
            try (var b = f.scope("b", "i2")) {
                release.countDown(); assertTrue(done.await(5, TimeUnit.SECONDS)); child.join(); pooled.get();
                f.logger.warn("b-owned");
            }
            var foreign = f.records().stream().filter(e -> e.get("message").toString().contains("foreign-")).toList();
            assertEquals(4, foreign.size());
            for (var e : foreign) { assertNull(e.get("task_id")); assertNull(e.get("invocation_id")); }
            assertEquals("b", f.message("b-owned").get("task_id"));
        }
    }

    @Test void relayedJulRecordCannotBorrowConsumersTask() throws Exception {
        try (var f = new Fixture(); var executor = Executors.newSingleThreadExecutor()) {
            java.util.logging.LogRecord record;
            try (var a = f.scope("a", "i1")) {
                record = new java.util.logging.LogRecord(java.util.logging.Level.WARNING, "relayed-jul");
                record.setLoggerName("fixture.scope"); record.setLongThreadID(Thread.currentThread().threadId());
            }
            executor.submit(() -> {
                try (var b = f.scope("b", "i2")) {
                    java.util.logging.Logger.getLogger("fixture.scope").log(record);
                    f.logger.warn("consumer-owned");
                }
            }).get();
            assertNull(f.message("relayed-jul").get("task_id"));
            assertNull(f.message("relayed-jul").get("invocation_id"));
            assertEquals("b", f.message("consumer-owned").get("task_id"));
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void sameThreadRelayedJulRecordCannotBorrowLaterScope(boolean nestedReturn) throws Exception {
        try (var f = new Fixture(); var parent = f.scope("a", null)) {
            java.util.logging.LogRecord record;
            try (var invocation = f.scope("a", "i1")) {
                record = new java.util.logging.LogRecord(java.util.logging.Level.WARNING, "same-thread-relay");
                record.setLoggerName("fixture.scope"); record.setLongThreadID(Thread.currentThread().threadId());
                // The guard must use creation ordering, not an assumed wall-clock relationship.
                record.setInstant(Instant.now().plusSeconds(86400));
            }
            if (nestedReturn) {
                java.util.logging.Logger.getLogger("fixture.scope").log(record);
                java.util.logging.Logger.getLogger("fixture.scope").warning("fresh-jul");
            } else {
                try (var b = f.scope("b", "i2")) {
                    java.util.logging.Logger.getLogger("fixture.scope").log(record);
                    java.util.logging.Logger.getLogger("fixture.scope").warning("fresh-jul");
                }
            }
            var relayed = f.message("same-thread-relay"); assertNull(relayed.get("task_id"));
            assertNull(relayed.get("invocation_id"));
            assertEquals(nestedReturn ? "a" : "b", f.message("fresh-jul").get("task_id"));
        }
    }

    @Test void julHasTheSameInvocationExactlyOnce() throws Exception {
        try (var f = new Fixture()) {
            try (var scope = f.scope("a", "i1")) {
                java.util.logging.Logger.getLogger("fixture.scope").warning("jul-owned");
            }
            var event = f.message("jul-owned"); assertEquals("a", event.get("task_id"));
            assertEquals("i1", event.get("invocation_id")); assertEquals("fixture.scope", event.get("operation_id"));
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void libraryFailuresKeepTheirGraphAndMaskSecretsInTextAndJson(boolean json) throws Exception {
        try (var f = new Fixture(json)) {
            String secret = "dummy-scope-secret-72819"; f.secrets.register(secret);
            var error = new IOException("outer-" + secret, new IllegalStateException("cause-" + secret));
            error.addSuppressed(new IOException("suppressed-" + secret));
            for (Throwable part : List.of(error, error.getCause(), error.getSuppressed()[0]))
                part.setStackTrace(new StackTraceElement[]{new StackTraceElement("Fixture", "probe", "Fixture.java", 12)});
            try (var scope = f.scope("a", "i1")) { f.logger.warn("message-" + secret, error); }
            String data = Files.readString(f.file); assertFalse(data.contains(secret));
            assertFalse(f.console.toString().contains(secret));
            var e = f.records().getFirst(); var graph = (Map<?,?>) e.get("error");
            assertEquals("java.io.IOException", graph.get("type"));
            assertEquals(1, ((List<?>)graph.get("causes")).size());
            assertEquals(1, ((List<?>)graph.get("suppressed")).size()); assertEquals("i1", e.get("invocation_id"));
        }
    }

    @Test void wrongThreadCloseDegradesWithoutClearingThatThreadsMdcAndOwnerCanStillRestore() throws Exception {
        MDC.put(MdcExecutionScope.TASK, "previous");
        try (var f = new Fixture()) {
            var scope = f.scope("a", "i1");
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> {
                    MDC.put(MdcExecutionScope.TASK, "foreign-owner"); scope.close();
                    assertEquals("foreign-owner", MDC.get(MdcExecutionScope.TASK)); MDC.clear();
                }).get();
            }
            assertEquals("a", MDC.get(MdcExecutionScope.TASK)); scope.close(); scope.close();
            assertEquals("previous", MDC.get(MdcExecutionScope.TASK)); assertTrue(f.pipeline.state().degraded());
            try (var later = f.scope("b", "i2")) { f.logger.warn("unreliable"); }
            assertNull(f.message("unreliable").get("task_id"));
            assertEquals(1, f.records().stream().filter(e -> "gretl.logging.degraded".equals(e.get("event_name"))).count());
        }
    }

    final class Fixture implements AutoCloseable {
        final UUID id = UUID.randomUUID(); final Path file = root.resolve(id + ".jsonl");
        final SecretRegistry secrets = new SecretRegistry(); final ByteArrayOutputStream console = new ByteArrayOutputStream();
        final LoggingPipeline pipeline; final LogbackBridge bridge;
        final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger("fixture.scope");
        Fixture() throws Exception { this(false); }
        Fixture(boolean json) throws Exception {
            var config = LoggingConfig.defaults().withLibraryLevels(Map.of("fixture.scope", LogLevel.INFO))
                    .withOverrides(json ? LoggingConfig.Format.JSON : LoggingConfig.Format.TEXT, null);
            pipeline = new LoggingPipeline(config, new LogContext("snapshot", id, "job", "test", Optional.empty()),
                    new Redactor(secrets), new PrintStream(console), file, Clock.systemUTC());
            bridge = LogbackBridge.install(pipeline, config);
        }
        RunEventSink.Scope scope(String task, String invocation) {
            return pipeline.openScope(new ExecutionIdentity(id, "job", Optional.ofNullable(task), Optional.ofNullable(invocation)),
                    invocation == null ? Optional.empty() : Optional.of("fixture.scope"));
        }
        List<Map<?,?>> records() throws Exception {
            List<Map<?,?>> result = new ArrayList<>();
            for (String line : Files.readAllLines(file)) result.add((Map<?,?>)Json.parse(line)); return result;
        }
        Map<?,?> message(String text) throws Exception { return LibraryScopeTest.message(records(), text); }
        public void close() { bridge.close(); pipeline.close(); }
    }
}
