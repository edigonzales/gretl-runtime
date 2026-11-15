package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

/** All tests here start the candidate launcher with external uncompiled sources. */
class ScriptProcessLocalJobIT {
    private static ArtifactJobRunner.Request request(Path fixture, Path root, String name, ArtifactJobRunner.Invocation invocation) throws Exception {
        var environment=new LinkedHashMap<String,Object>();
        environment.put("GRETL_JAVA_OPTS","-Dgretl.packager.maven=/deliberate-failing-packager");
        Path tools=Files.createDirectory(root.resolve("runtime-bin"));
        for (String tool : List.of("dirname", "basename", "uname", "xargs", "sed", "tr", "ls")) {
            Files.createSymbolicLink(tools.resolve(tool), Path.of("/usr/bin", tool));
        }
        environment.put("PATH",tools.toString());
        return new ArtifactJobRunner.Request("source-contract",fixture,root,List.of(),Map.of("output",root.resolve("output/value.txt")),environment,
                JobRunner.baseConfig(root),Map.of(),Duration.ofSeconds(45),ArtifactJobRunner.JobInput.script(name),invocation);
    }
    private static void assertNoRunWorkspace(Path root) throws Exception {
        assertFalse(Files.exists(root.resolve("work")));
    }
    private static String job(String value) {
        return "parameter('output', type: 'path', required: true, description: 'External output')\naction('write') { System.getenv('PATH').split(java.io.File.pathSeparator).each { assert !new File(it, 'mvn').exists(); assert !new File(it, 'gradle').exists() }; java.nio.file.Files.writeString(parameter('output'), java.nio.file.Files.readString(file('payload.txt')) + '" + value + "') }; defaults 'write'\n";
    }
    @Test void runsWithoutDescriptorThenWithBrokenDescriptorAndEditedSource() throws Exception {
        Path fixture = Files.createTempDirectory("gretl-external-ü ");
        try {
            String name = "externer Job ü.groovy";
            Files.writeString(fixture.resolve(name), job("first-ä"));
            Files.writeString(fixture.resolve("payload.txt"), "resource-λ|");
            for (int i=0; i<3; i++) {
                if (i==1) {
                    Files.writeString(fixture.resolve("job-package.json"), "{ deliberately invalid");
                    Path packageRoot = ArtifactJobRunner.directory("rejecting-package-boundary");
                    var rejected = assertThrows(Exception.class, () -> ArtifactJobRunner.prepare(
                            request(fixture, packageRoot, name, ArtifactJobRunner.Invocation.run()), ArtifactJobRunner.Mode.JAR));
                    assertTrue(rejected.getMessage().contains("JSON"), rejected.toString());
                }
                if (i==2) Files.writeString(fixture.resolve(name), job("edited-ö"));
                Path root = ArtifactJobRunner.directory("external-edit-" + i);
                var prepared = ArtifactJobRunner.prepare(request(fixture, root, name, ArtifactJobRunner.Invocation.run()));
                assertNull(prepared.jar()); assertNull(prepared.image());
                var running = ArtifactJobRunner.start(prepared);
                ArtifactJobRunner.Result result;
                try { result = ArtifactJobRunner.finish(running, false); }
                finally { if (running.process().isAlive()) ArtifactJobRunner.stop(running); }
                assertEquals(0, result.code(), result.err()); assertFalse(result.timedOut());
                assertEquals(i==2 ? "resource-λ|edited-ö" : "resource-λ|first-ä", Files.readString(root.resolve("output/value.txt")));
                assertFalse(Files.exists(root.resolve("job.jar"))); assertFalse(Files.exists(root.resolve("package.stdout.log")));
                var proof = (Map<?,?>)Json.parseFile(root.resolve("execution.json"), 1L<<20);
                assertEquals("SCRIPT", proof.get("executionKind"));
                assertEquals("EXITED", ((Map<?,?>)proof.get("process")).get("state"));
                assertEquals(0L, ((Map<?,?>)proof.get("process")).get("exitCode"));
            }
        } finally { SelectedPackageSupport.delete(fixture); }
    }
    @Test void launchesTheUnpackedZipOutsideTheCheckout() throws Exception {
        Path external = Files.createTempDirectory("gretl-unpacked-");
        try {
            Path archive = Path.of(System.getProperty("gretl.test.distributionArchive",
                    "../gretl-dist/target/gretl-runtime-6.0.0-SNAPSHOT-bin.zip"));
            try (var zip = new java.util.zip.ZipInputStream(Files.newInputStream(archive))) {
                for (java.util.zip.ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                    Path target = external.resolve(entry.getName()).normalize();
                    if (!target.startsWith(external)) throw new java.io.IOException("Zip traversal");
                    if (entry.isDirectory()) Files.createDirectories(target);
                    else { Files.createDirectories(target.getParent()); Files.copy(zip, target); }
                }
            }
            Path distributionRoot;
            try (var children = Files.list(external)) { distributionRoot = children.findFirst().orElseThrow(); }
            assertTrue(distributionRoot.resolve("bin/gretl").toFile().setExecutable(true, true));
            var distribution = CandidateDistribution.at(distributionRoot);
            Path fixture = Files.createDirectory(external.resolve("new input"));
            Files.writeString(fixture.resolve("payload.txt"), "unpacked|");
            Files.writeString(fixture.resolve("job.groovy"), job("external"));
            Path root = ArtifactJobRunner.directory("unpacked-script");
            var request = request(fixture, root, "job.groovy", ArtifactJobRunner.Invocation.run());
            var canonical = ArtifactJobRunner.prepare(request, ArtifactJobRunner.Mode.SCRIPT);
            var prepared = new ArtifactJobRunner.Prepared(request, canonical.source(), canonical.report(), canonical.inputSnapshot(),
                    ScriptExecutionAdapter.prepare(request, canonical.source(), canonical.report(), distribution));
            var running = ArtifactJobRunner.start(prepared);
            ArtifactJobRunner.Result result;
            try { result = ArtifactJobRunner.finish(running, false); }
            finally { if (running.process().isAlive()) ArtifactJobRunner.stop(running); }
            assertEquals(0, result.code(), result.err());
            assertEquals("unpacked|external", Files.readString(root.resolve("output/value.txt")));
        } finally { SelectedPackageSupport.delete(external); }
    }
    @Test void controlsPerformNoTaskWorkAndLargeStreamsDoNotDeadlock() throws Exception {
        Path fixture = Files.createTempDirectory("gretl-controls-");
        try {
            Files.writeString(fixture.resolve("job.groovy"), job("must-not-run"));
            for (var invocation : List.of(ArtifactJobRunner.Invocation.check(), ArtifactJobRunner.Invocation.plan(),
                    ArtifactJobRunner.Invocation.plan("mermaid", null), ArtifactJobRunner.Invocation.jobHelp())) {
                Path root = ArtifactJobRunner.directory("source-controls");
                var result = ArtifactJobRunner.run(request(fixture, root, "job.groovy", invocation));
                assertEquals(0, result.code(), result.err()); assertFalse(result.timedOut());
                assertFalse(Files.exists(root.resolve("output/value.txt"))); assertFalse(Files.exists(result.report()));
                assertNoRunWorkspace(root);
                if (invocation.graphFormat() != null) assertTrue(result.out().contains("flowchart"), result.out());
            }
            Files.writeString(fixture.resolve("job.groovy"), "parameter('output', type: 'path', description: 'Unused external output'); action('noise') { for (int i=0;i<5000;i++) { System.out.println('O' * 100); System.err.println('E' * 100) } }; defaults 'noise'");
            Path root = ArtifactJobRunner.directory("source-large-streams");
            var result = ArtifactJobRunner.run(request(fixture, root, "job.groovy", ArtifactJobRunner.Invocation.run()));
            assertEquals(0, result.code(), result.err()); assertFalse(result.timedOut());
            assertEquals(5000, result.out().lines().filter(line -> line.equals("O".repeat(100))).count());
            assertEquals(5000, result.err().lines().filter(line -> line.equals("E".repeat(100))).count());
        } finally { SelectedPackageSupport.delete(fixture); }
    }
    @Test void earlySyntaxMissingFileAndInvalidSettingsReachTheProduct() throws Exception {
        for (String variant : List.of("syntax", "missing", "settings")) {
            Path fixture = Files.createTempDirectory("gretl-invalid-");
            try {
                Files.writeString(fixture.resolve("bad.groovy"), "action( { invalid syntax");
                Files.writeString(fixture.resolve("settings.gretl"), "System.exit(0)");
                Path root = ArtifactJobRunner.directory("source-invalid-" + variant);
                var base = request(fixture, root, variant.equals("missing") ? "missing.groovy" : "bad.groovy", ArtifactJobRunner.Invocation.run());
                var input = variant.equals("settings") ? ArtifactJobRunner.JobInput.project(".") : base.input();
                var request = new ArtifactJobRunner.Request(base.id(), fixture, root, base.targets(), base.parameters(), base.environment(),
                        base.config(), base.endpoints(), base.timeout(), input, base.invocation());
                var result = ArtifactJobRunner.run(request);
                assertEquals(2, result.code(), result.err()); assertFalse(result.timedOut());
                assertFalse(result.err().contains("ClassNotFoundException"), result.err());
                assertTrue(result.err().contains(variant.equals("settings") ? "settings.gretl" : variant.equals("syntax") ? "bad.groovy" : "missing.groovy"), result.err());
                if (variant.equals("syntax")) assertTrue(result.err().contains("bad.groovy: 1:"), result.err());
                assertFalse(Files.exists(result.report()));
                assertFalse(Files.exists(root.resolve("package.stdout.log")));
            } finally { SelectedPackageSupport.delete(fixture); }
        }
    }
    @Test void launchFailureIsRecordedSeparatelyAndSourceChangesAreRejected() throws Exception {
        Path fixture = Files.createTempDirectory("gretl-launch-");
        try {
            Files.writeString(fixture.resolve("job.groovy"), job("never"));
            Path root = ArtifactJobRunner.directory("source-launch-failure");
            var prepared = ArtifactJobRunner.prepare(request(fixture, root, "job.groovy", ArtifactJobRunner.Invocation.run()));
            Files.delete(root.resolve("cwd"));
            assertThrows(java.io.IOException.class, () -> ArtifactJobRunner.start(prepared));
            var proof = (Map<?,?>)Json.parseFile(root.resolve("execution.json"),1L<<20);
            assertEquals("LAUNCH_FAILED", ((Map<?,?>)proof.get("process")).get("state"));
            assertFalse(Files.exists(prepared.report()));
            Files.createDirectory(root.resolve("cwd"));
            Files.writeString(prepared.source().resolve("job.groovy"), job("changed"));
            assertThrows(java.io.IOException.class, () -> ArtifactJobRunner.start(prepared));
            assertFalse(Files.exists(root.resolve("output/value.txt")));
        } finally { SelectedPackageSupport.delete(fixture); }
    }
}
