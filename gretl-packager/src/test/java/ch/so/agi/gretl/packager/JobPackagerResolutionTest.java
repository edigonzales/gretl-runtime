package ch.so.agi.gretl.packager;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JobPackagerResolutionTest {
    @TempDir Path work;

    @Test
    void packagesResolvedDependencyThatIsNotOnTheRuntimeClasspath() throws Exception {
        Path artifact = work.resolve("repo/org/example/demo/1.0/demo-1.0.jar");
        Files.createDirectories(artifact.getParent());
        try (OutputStream output = Files.newOutputStream(artifact);
             JarOutputStream jar = new JarOutputStream(output)) {
            jar.putNextEntry(new JarEntry("demo/Marker.class"));
            jar.write(new byte[] {1, 2, 3});
            jar.closeEntry();
        }
        Path maven = work.resolve("mvn");
        Files.writeString(maven, """
                #!/bin/sh
                for arg in "$@"; do
                  case "$arg" in
                    -Dmdep.outputFile=*) out="${arg#-Dmdep.outputFile=}" ;;
                  esac
                done
                printf "%%s" "%s" > "$out"
                """.formatted(artifact));
        Files.setPosixFilePermissions(maven, PosixFilePermissions.fromString("rwx------"));
        String previous = System.getProperty("gretl.packager.maven");
        System.setProperty("gretl.packager.maven", maven.toString());
        try {
            Path testClasses = Path.of("target/test-classes")
                    .resolve("ch/so/agi/gretl/job/JobJarMain.class");
            Files.createDirectories(testClasses.getParent());
            Files.writeString(testClasses, "main");
            Files.writeString(work.resolve("job.groovy"),
                    "action('run') { }\ndefaults 'run'\n");
            Path descriptor = work.resolve("job-package.json");
            Files.writeString(descriptor, """
                    {"schemaVersion":1,"name":"resolver-demo","version":"1",
                     "runtimeVersion":"0.1.0-SNAPSHOT","entryScript":"job.groovy",
                     "sources":[],"resources":[],
                     "dependencies":["org.example:demo:1.0"]}
                    """);
            Path output = work.resolve("job.jar");
            new JobPackager("0.1.0-SNAPSHOT").packageJob(descriptor, output);
            try (var jar = new java.util.jar.JarFile(output.toFile())) {
                assertNotNull(jar.getEntry("demo/Marker.class"));
            }
        } finally {
            if (previous == null) System.clearProperty("gretl.packager.maven");
            else System.setProperty("gretl.packager.maven", previous);
        }
    }
}
