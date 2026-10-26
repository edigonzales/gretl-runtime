package ch.so.agi.gretl.job.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.groovy.UserMessageSink;
import ch.so.agi.gretl.runtime.logging.Json;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.JobDefinitionBuilder;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import ch.so.agi.gretl.runtime.runner.TaskRegistration;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SetupCompletionTest {
    @TempDir
    Path tempDir;

    @Test
    void knownBrokenWorkRootRemainsAStartErrorWithoutTaskOrCleanupWork() throws Exception {
        Path blocked = Files.writeString(tempDir.resolve("blocked-work"), "untouched");
        Path report = tempDir.resolve("report.json");
        AtomicInteger taskCalls = new AtomicInteger();
        AtomicInteger cleanups = new AtomicInteger();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int exit = runtime(errors).executePackaged(source(taskCalls, cleanups), new String[] {
                "--work-dir", blocked.toString(), "--report", report.toString(),
                "--log-format", "json"});

        assertEquals(2, exit);
        assertEquals(0, taskCalls.get());
        assertEquals(0, cleanups.get());
        assertEquals("untouched", Files.readString(blocked));
        assertFalse(Files.exists(report));
        String logs = errors.toString(StandardCharsets.UTF_8);
        assertFalse(logs.contains("gretl.run.started"), logs);
    }

    @Test
    void brokenExplicitReportParentDoesNotPreventTaskAndCleanupCompletion() throws Exception {
        Path blocked = Files.createDirectory(tempDir.resolve("blocked-report"));
        AtomicInteger taskCalls = new AtomicInteger();
        AtomicInteger cleanups = new AtomicInteger();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        Path termination = tempDir.resolve("termination.json");
        int exit = runtime(errors, Map.of("GRETL_TERMINATION_MESSAGE_PATH", termination.toString()))
                .executePackaged(source(taskCalls, cleanups, blocked), new String[] {
                "--report", blocked.resolve("report.json").toString(),
                "--log-format", "json"});

        assertEquals(10, exit);
        assertEquals(1, taskCalls.get());
        assertEquals(1, cleanups.get());
        assertEquals("untouched", Files.readString(blocked));
        String logs = errors.toString(StandardCharsets.UTF_8);
        assertTrue(logs.contains("REPORT_WRITE_FAILED"), logs);
        assertEquals(1, logs.lines().filter(line -> line.contains("gretl.run.finished")).count(), logs);
        String terminal = logs.lines().filter(line -> line.contains("gretl.run.finished"))
                .findFirst().orElseThrow();
        assertTrue(terminal.contains("\"outcome\":\"FAILED\""), terminal);
        assertTrue(terminal.contains("\"exit_code\":\"10\""), terminal);
        assertTrue(logs.indexOf("gretl.report.write_failed") < logs.indexOf("gretl.run.finished"), logs);
        Map<?, ?> summary = (Map<?, ?>) Json.parseFile(termination, 4096);
        assertEquals("FAILED", summary.get("status"));
        assertEquals(10L, summary.get("exitCode"));
    }

    private JobRuntime runtime(ByteArrayOutputStream errors) {
        return runtime(errors, Map.of());
    }

    private JobRuntime runtime(ByteArrayOutputStream errors, Map<String, String> environment) {
        return new JobRuntime(new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(errors), environment, tempDir, getClass().getClassLoader());
    }

    private JobSource source(AtomicInteger taskCalls, AtomicInteger cleanups) {
        return source(taskCalls, cleanups, null);
    }

    private JobSource source(AtomicInteger taskCalls, AtomicInteger cleanups, Path sabotageReportParent) {
        return new JobSource() {
            @Override public Path displayFile() { return tempDir.resolve("fixture.groovy"); }
            @Override public Path jobRoot() { return tempDir; }
            @Override public String jobName() { return "setup-fixture"; }
            @Override public String sha256() { return "a".repeat(64); }
            @Override
            public JobDefinition load(OperationRegistry registry, Path startCwd, UserMessageSink sink) {
                JobDefinitionBuilder builder = new JobDefinitionBuilder(tempDir);
                builder.register(TaskRegistration.builder("work", Object.class).body(context -> {
                    taskCalls.incrementAndGet();
                    if (sabotageReportParent != null) {
                        Files.delete(sabotageReportParent);
                        Files.writeString(sabotageReportParent, "untouched");
                    }
                    return null;
                }).build());
                builder.cleanup("cleanup", context -> {
                    cleanups.incrementAndGet();
                    context.log().info("cleanup completed");
                });
                builder.defaults("work");
                return builder.freeze();
            }
        };
    }
}
