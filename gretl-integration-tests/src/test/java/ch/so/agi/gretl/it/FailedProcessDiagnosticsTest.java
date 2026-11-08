package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

class FailedProcessDiagnosticsTest {
    @Test void stagesOnlyFailedTestsAndKeepsAllTheirRegisteredProcesses() throws Exception {
        String property = "gretl.test.runId";
        String previous = System.getProperty(property);
        String runId = "fixture-" + UUID.randomUUID();
        Path staging = Path.of("target", "failed-process-diagnostics", runId);
        System.setProperty(property, runId);
        try {
            var summary = new SummaryGeneratingListener();
            var selection = LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectClass(FailedProcessDiagnosticsFixture.class))
                    .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                    .build();
            LauncherFactory.create().execute(selection, summary);
            assertEquals(1, summary.getSummary().getTestsSucceededCount());
            assertEquals(3, summary.getSummary().getTestsFailedCount());
            try (var files = Files.walk(staging)) {
                StringBuilder metadata = new StringBuilder();
                StringBuilder logs = new StringBuilder();
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    if (file.getFileName().toString().equals("case.json")) metadata.append(Files.readString(file));
                    else logs.append(Files.readString(file));
                }
                for (String method : List.of("afterExitZero", "setupFailure", "teardownFailure")) {
                    assertTrue(metadata.toString().contains("#" + method), metadata.toString());
                }
                assertFalse(metadata.toString().contains("#expectedNegativeProcess"));
                for (String marker : List.of("first fixture output", "second fixture output",
                        "setup fixture output", "teardown fixture output", "fixture stderr", "[REDACTED]")) {
                    assertTrue(logs.toString().contains(marker), logs.toString());
                }
                assertFalse(logs.toString().contains("negative fixture output"));
                assertFalse(logs.toString().contains(FailedProcessDiagnosticsFixture.CANARY));
            }
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
            if (Files.exists(staging)) {
                try (var files = Files.walk(staging)) {
                    for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
                }
            }
        }
    }
}

/** Deliberate failures are selected by the launcher test, outside ordinary Maven discovery. */
class FailedProcessDiagnosticsFixture {
    static final String CANARY = "fixture-opaque-31a68f38";
    @TempDir Path root;

    @BeforeEach void setup(TestInfo test) throws Exception {
        if (test.getTestMethod().orElseThrow().getName().equals("setupFailure")) {
            assertEquals(0, run("setup", 0));
            throw new AssertionError("fixture setup failed");
        }
    }

    @AfterEach void teardown(TestInfo test) {
        if (test.getTestMethod().orElseThrow().getName().equals("teardownFailure")) {
            throw new AssertionError("fixture teardown failed");
        }
    }

    @Test void afterExitZero() throws Exception {
        assertEquals(0, run("first", 0));
        assertEquals(0, run("second", 0));
        throw new AssertionError("fixture assertion after product exit zero");
    }

    @Test void expectedNegativeProcess() throws Exception {
        assertEquals(19, run("negative", 19));
    }

    @Test void setupFailure() {
        throw new AssertionError("test body must not run after setup failed");
    }

    @Test void teardownFailure() throws Exception {
        assertEquals(0, run("teardown", 0));
    }

    private int run(String marker, int exit) throws Exception {
        Path source = Files.createDirectory(root.resolve(marker + "-source"));
        Path workspace = Files.createDirectory(root.resolve(marker + "-work"));
        Files.writeString(source.resolve("job.groovy"), "// synthetic fixture");
        var request = new ArtifactJobRunner.Request(marker, source, workspace,
                List.of(), Map.of(), Map.of(),
                Map.of("secrets", Map.of("fixture", Map.of("plaintext", CANARY))),
                Map.of(), Duration.ofSeconds(10));
        var engine = new ImageExecutionAdapter.Engine() {
            @Override public String inspect(List<String> arguments) { return "fixture"; }
            @Override public void control(List<String> arguments) { }
            @Override public Process start(List<String> arguments, Path directory,
                                           Map<String, String> environment) throws IOException {
                return ArtifactJobRunner.process(List.of("sh", "-c",
                        "printf '%s fixture output %s\\n' \"$1\" \"$2\"; "
                                + "printf '%s\\n' 'fixture stderr' >&2; exit \"$3\"",
                        "fixture", marker, CANARY, Integer.toString(exit)), directory, environment, "job").start();
            }
        };
        var launch = new ImageExecutionAdapter.Launch(engine, "fixture", "sha256:" + "0".repeat(64),
                List.of(), Map.of(), Map.of());
        var prepared = new ArtifactJobRunner.Prepared(request, source, workspace.resolve("report.json"),
                JobInputSnapshot.capture(source), new ArtifactJobRunner.ImageLaunch(null, launch));
        Process process = ArtifactJobRunner.start(prepared).process();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            return process.exitValue();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }
}
