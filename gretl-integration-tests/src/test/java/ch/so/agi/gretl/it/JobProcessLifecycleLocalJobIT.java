package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Product process boundaries shared by the configured mode, with fixed independent expectations. */
class JobProcessLifecycleLocalJobIT {
    @Test void bothLargeStreamsAreComplete() throws Exception {
        var request = request("large-streams", "action('noise') { for (int i=0;i<5000;i++) { System.out.println('O' * 100); System.err.println('E' * 100) } }; defaults 'noise'", Map.of());
        var result = ArtifactJobRunner.run(request);
        assertEquals(0, result.code(), result.err()); assertFalse(result.timedOut());
        assertEquals(5000, result.out().lines().filter(line -> line.equals("O".repeat(100))).count());
        assertEquals(5000, result.err().lines().filter(line -> line.equals("E".repeat(100))).count());
    }
    @Test void requiredReportWriteFailureCannotBecomeSuccess() throws Exception {
        var request = request("report-write-failure", "action('write') { java.nio.file.Files.writeString(parameter('output'), 'task-completed') }; defaults 'write'", Map.of());
        var prepared = ArtifactJobRunner.prepare(request);
        Path blocked = Files.writeString(request.directory().resolve("reports/blocked-report"), "preserve-this-file");
        var result = ArtifactJobRunner.finish(ArtifactJobRunner.start(prepared.withReport(blocked.resolve("report.json"))), false);
        assertEquals(2, result.code(), result.err()); assertFalse(result.timedOut());
        assertEquals("preserve-this-file", Files.readString(blocked));
        assertFalse(Files.exists(result.report()));
        assertTrue(result.err().contains("report parent"), result.err());
        assertFalse(Files.exists(request.directory().resolve("output/result.txt")));
    }
    @Test void relativePathParameterUsesForeignCwdAndHostOverridesDoNotLeak() throws Exception {
        var request = request("relative-parameter", "action('write') { assert System.getProperty('foreign.marker') == null; assert System.getenv('CLASSPATH') == null; java.nio.file.Files.writeString(parameter('output'), 'cwd-ü') }; defaults 'write'", Map.of());
        request = new ArtifactJobRunner.Request(request.id(), request.jobDirectory(), request.directory(), List.of(), Map.of("output", "relative-ü.txt"), Map.of(), request.config(), Map.of(), request.timeout());
        var result = ArtifactJobRunner.run(request);
        assertEquals(0, result.code(), result.err()); assertFalse(result.timedOut());
        assertEquals("cwd-ü", Files.readString(request.directory().resolve("cwd/relative-ü.txt")));
        assertFalse(Files.exists(request.directory().resolve("source/relative-ü.txt")));
    }
    @Test void relativeConfigAndSecretPathsUseTheConfigDirectory() throws Exception {
        var original = request("relative-config-secret", "action('write') { def value = secret('probe').reveal(); assert value == 'relative-secret-sentinel'; log().info('probe ' + value); java.nio.file.Files.writeString(workPath('marker.txt'), 'owned'); java.nio.file.Files.writeString(parameter('output'), 'resolved') }; defaults 'write'", Map.of());
        var config = JobRunner.baseConfig(original.directory());
        config.put("runtime", Map.of("workRoot", "relative-work", "reportRoot", "reports", "keepWork", "always"));
        JobRunner.secret(original.directory(), config, "probe", "relative-secret-sentinel");
        var request = new ArtifactJobRunner.Request(original.id(),original.jobDirectory(),original.directory(),original.targets(),original.parameters(),original.environment(),config,original.endpoints(),original.timeout());
        var result = ArtifactJobRunner.run(request);
        assertEquals(0,result.code(),result.err()); assertFalse(result.timedOut());
        assertEquals("resolved",Files.readString(request.directory().resolve("output/result.txt")));
        assertTrue(Files.isDirectory(request.directory().resolve("relative-work")));
        assertFalse(Files.exists(request.directory().resolve("cwd/relative-work")));
        assertTrue(result.err().contains("probe [REDACTED]"),result.err());
        assertFalse(result.err().contains("relative-secret-sentinel"));
        assertFalse(Files.readString(result.report()).contains("relative-secret-sentinel"));
    }
    private ArtifactJobRunner.Request request(String id, String body, Map<String,String> environment) throws Exception {
        Path root = ArtifactJobRunner.directory(id), fixture = Files.createDirectories(root.resolve("fixture"));
        Files.writeString(fixture.resolve("job.groovy"), "parameter('output', type: 'path', required: true, description: 'External output')\n" + body);
        return new ArtifactJobRunner.Request(id, fixture, root, List.of(), Map.of("output", root.resolve("output/result.txt")), environment, JobRunner.baseConfig(root), Map.of(), Duration.ofSeconds(45));
    }
}
