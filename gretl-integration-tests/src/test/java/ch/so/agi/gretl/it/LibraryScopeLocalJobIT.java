package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

/** F4-14/G-04: real external SPI JAR, real launcher and detached regular job package. */
class LibraryScopeLocalJobIT {
    @Test void realRuntimeAdapterKeepsSlf4jAndJulScopesInBothEntryPaths() throws Exception {
        Path base = ArtifactJobRunner.directory("library-provider");
        var external = new ExternalPackageFixture(base); Path provider = external.loggingProvider();
        Path distribution = Path.of("../gretl-dist/target/dist").toAbsolutePath().normalize();
        // The candidate distribution stays byte-identical. Add the external extension to a private copy.
        Path extended = base.resolve("script distribution");
        try (var paths = Files.walk(distribution)) {
            for (Path p : paths.toList()) {
                Path to = extended.resolve(distribution.relativize(p));
                if (Files.isDirectory(p)) Files.createDirectories(to); else Files.createLink(to, p);
            }
        }
        Files.copy(provider, extended.resolve("lib/extensions/logging-provider.jar"));
        for (boolean script : List.of(true, false)) {
            Path root = ArtifactJobRunner.directory("library-scopes-" + (script ? "script" : "jar"));
            Path source = Files.createDirectories(root.resolve("source"));
            Files.createDirectories(root.resolve("cwd")); Files.createDirectories(root.resolve("reports"));
            Files.writeString(source.resolve("job.groovy"), """
                    task('a', ScopeProbe) { label = 'a' }
                    action('nested', dependsOn: 'a') {
                        org.slf4j.LoggerFactory.getLogger('fixture.scope').warn('outer-before')
                        ctx.run(ScopeProbe) { label = 'inner' }
                        try { ctx.run(ScopeProbe) { label = 'caught' } }
                        catch (java.io.IOException expected) { }
                        org.slf4j.LoggerFactory.getLogger('fixture.scope').warn('outer-after')
                    }
                    task('b', ScopeProbe, dependsOn: 'nested') { label = 'b' }
                    cleanup('release') { org.slf4j.LoggerFactory.getLogger('fixture.scope').warn('job-cleanup') }
                    defaults 'b'
                    """);
            SelectedPackageSupport.descriptor(source, List.of(), false, List.of(external.group + ":logging-provider:1.0"));
            Path jar = root.resolve("job.jar");
            var packed = external.pack(source.resolve("job-package.json"), jar, "logging-" + script, List.of(), false);
            assertEquals(0, packed.code(), packed.err()); PackageArtifactAssertions.verify(jar, source.resolve("job-package.json"));
            try (var zip = new java.util.jar.JarFile(jar.toFile())) {
                assertNotNull(zip.getJarEntry("external/logging/ScopeProvider.class"));
                assertNull(zip.getJarEntry("ch/so/agi/gretl/cli/Main.class"));
            }
            Map<String,Object> config = JobRunner.baseConfig(root);
            JobRunner.secret(root, config, "scopeDummy", "provider-dummy-secret-72819");
            config.put("logging", Map.of("libraryLevels", Map.of("fixture.scope", "INFO")));
            Files.writeString(root.resolve("runtime.json"), Json.write(config));
            var request = new ArtifactJobRunner.Request("library-scopes", source, root, List.of(), Map.of(), Map.of(), config, Map.of(), Duration.ofSeconds(45));
            var prepared = new ArtifactJobRunner.Prepared(request, source, jar, hash(jar), root.resolve("reports/report.json"), null);
            ArtifactJobRunner.Result result;
            if (script) {
                var running = ArtifactJobRunner.start(prepared, true, extended.resolve("bin/gretl"));
                try { result = ArtifactJobRunner.finish(running, false); }
                finally { if (running.process().isAlive()) ArtifactJobRunner.stop(running); }
            } else result = SelectedPackageSupport.detached(prepared);
            assertEquals(0, result.code(), result.err());
            var report = (Map<?,?>)Json.parseFile(result.report(), 4L << 20);
            var execution = (Map<?,?>)report.get("execution"); assertEquals("SUCCEEDED", execution.get("status"));
            List<Map<?,?>> records = new ArrayList<>();
            try (var files = Files.walk(root.resolve("reports"))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".events.jsonl")).toList())
                    for (String line : Files.readAllLines(file)) records.add((Map<?,?>)Json.parse(line));
            }
            assertFalse(records.isEmpty());
            for (String label : List.of("a", "inner", "caught", "b")) {
                String task = Set.of("inner", "caught").contains(label) ? "nested" : label;
                var event = message(records, "provider-handler-" + label);
                assertEquals(task, event.get("task_id")); assertEquals("fixture.scope", event.get("operation_id"));
                assertNotNull(event.get("invocation_id"));
                for (String phase : List.of("provider-config-", "provider-jul-", "provider-close-", "provider-jul-close-")) {
                    var other = message(records, phase + label);
                    for (String key : List.of("run_id", "task_id", "invocation_id", "operation_id")) assertEquals(event.get(key), other.get(key));
                }
            }
            assertNotEquals(message(records, "provider-handler-inner").get("invocation_id"), message(records, "provider-handler-caught").get("invocation_id"));
            for (String text : List.of("outer-before", "outer-after")) {
                var event = message(records, text); assertEquals("nested", event.get("task_id")); assertNull(event.get("invocation_id"));
            }
            assertNull(message(records, "job-cleanup").get("task_id"));
            var deferred = message(records, "provider-deferred");
            assertEquals(message(records, "provider-handler-a").get("invocation_id"), deferred.get("invocation_id"));
            assertEquals("a", deferred.get("task_id"));
            assertEquals("2026-10-20T01:02:03.123Z", deferred.get("timestamp"));
            var foreign = records.stream().filter(e -> e.get("message").toString().contains("foreign-")).toList();
            assertEquals(4, foreign.size());
            for (var e : foreign) { assertNull(e.get("task_id")); assertNull(e.get("invocation_id")); }
            assertNull(message(records, "provider-invalid").get("task_id"));
            assertFalse(Json.write(records).contains("foreign-provider-value"));
            assertFalse(Json.write(records).contains("provider-dummy-secret-72819"));
            assertFalse(result.err().contains("provider-dummy-secret-72819"));
            var secret = records.stream().filter(e -> e.get("message").toString().startsWith("provider-secret-")).findFirst().orElseThrow();
            var error = (Map<?,?>)secret.get("error");
            assertEquals(1, ((List<?>)error.get("causes")).size()); assertEquals(1, ((List<?>)error.get("suppressed")).size());
            var terminals = records.stream().filter(e -> "gretl.run.finished".equals(e.get("event_name"))).toList();
            assertEquals(1, terminals.size()); assertEquals(records.getLast(), terminals.getFirst());
            // The complete required report and the sole final event both come from the shared JobRuntime.
            assertEquals("SUCCEEDED", terminals.getFirst().get("outcome"));
            Files.writeString(root.resolve("provider-identity.json"), Json.write(Map.of("coordinate", external.group + ":logging-provider:1.0",
                    "sha256", hash(provider), "scriptDistributionCatalogSha256", hash(distribution.resolve("package-catalog.json")))));
        }
    }
    static Map<?,?> message(List<Map<?,?>> records, String text) {
        var found = records.stream().filter(e -> text.equals(e.get("message"))).toList();
        assertEquals(1, found.size(), text); return found.getFirst();
    }
    static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
