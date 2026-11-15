package ch.so.agi.gretl.it;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** The only common-runner adapter that packages the concrete job. */
final class JarExecutionAdapter {
    static ArtifactJobRunner.JarLaunch prepare(ArtifactJobRunner.Request request, Path source,
                                             CandidateDistribution distribution) throws Exception {
        if (request.invocation().verb() == ArtifactJobRunner.Verb.CHECK)
            throw new IllegalArgumentException("JAR has no CHECK command");
        Path descriptor = source.resolve("job-package.json");
        if (!Files.exists(descriptor)) {
            if (request.input().kind() == ArtifactJobRunner.InputKind.PROJECT_DIRECTORY) throw new IOException("Project fixture requires a schema-3 descriptor");
            List<String> resources;
            try (var paths = Files.list(source)) {
                resources = paths.map(p -> p.getFileName().toString())
                        .filter(name -> !name.equals(request.input().relativePath()))
                        .sorted().toList();
            }
            Files.writeString(descriptor, Json.write(Map.of(
                    "schemaVersion", 1, "name", "fixture", "version", "1",
                    "runtimeVersion", "6.0.0-SNAPSHOT", "entryScript", request.input().relativePath(),
                    "sources", List.of(), "resources", resources, "dependencies", List.of())));
        }
        var metadata = (Map<?, ?>) Json.parseFile(descriptor, 1L << 20);
        if (request.input().kind() == ArtifactJobRunner.InputKind.SCRIPT_FILE
                && !request.input().relativePath().equals(metadata.get("entryScript")))
            throw new IOException("Requested JAR script does not match package descriptor");
        if (request.input().kind() == ArtifactJobRunner.InputKind.PROJECT_DIRECTORY
                && (!request.input().relativePath().equals(".") || !Long.valueOf(3).equals(metadata.get("schemaVersion"))))
            throw new IOException("Requested JAR project does not match schema-3 package root");
        Path launcher = distribution.launcher();
        if (!Files.isRegularFile(launcher)) throw new IOException("Candidate distribution missing: " + launcher);
        Path jar = request.directory().resolve("job.jar");
        List<String> command = List.of(launcher.toString(), "package", descriptor.toString(),
                "--output", jar.toString());
        Process packaging = ArtifactJobRunner.process(command, request.directory(), request.environment(), "package").start();
        try {
            if (!packaging.waitFor(120, TimeUnit.SECONDS)) throw new IOException("Packaging timed out: " + request.id());
        } finally {
            if (packaging.isAlive()) ArtifactJobRunner.stop(packaging);
        }
        if (packaging.exitValue() != 0 || !Files.isRegularFile(jar)) {
            throw new IOException("Packaging failed: " + Files.readString(request.directory().resolve("package.stderr.log")));
        }
        return new ArtifactJobRunner.JarLaunch(jar);
    }

}
