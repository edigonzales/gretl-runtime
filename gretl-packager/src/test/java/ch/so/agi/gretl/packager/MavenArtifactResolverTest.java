package ch.so.agi.gretl.packager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenArtifactResolverTest {
    @TempDir Path work;

    @Test
    void resolvesDeclaredArtifactFromLocalRepository() throws Exception {
        Path artifact = work.resolve("repo/org/example/demo/1.0/demo-1.0.jar");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, "jar");
        Path maven = fakeMaven("""
                #!/bin/sh
                for arg in "$@"; do
                  case "$arg" in
                    -Dmdep.outputFile=*) out="${arg#-Dmdep.outputFile=}" ;;
                  esac
                done
                printf "%%s" "%s" > "$out"
                """.formatted(artifact));
        List<Path> paths = new MavenArtifactResolver(work.resolve("repo"), true,
                maven.toString()).resolve(List.of(
                        new PackageDescriptor.Dependency("org.example", "demo", "1.0")));
        assertEquals(List.of(artifact), paths);
    }

    @Test
    void reportsUnresolvableDependency() throws Exception {
        Path maven = fakeMaven("#!/bin/sh\nexit 1\n");
        assertThrows(PackageException.class,
                () -> new MavenArtifactResolver(work.resolve("repo"), true,
                        maven.toString()).resolve(List.of(
                                new PackageDescriptor.Dependency("org.example", "missing", "9.9"))));
    }

    private Path fakeMaven(String content) throws Exception {
        Path maven = work.resolve("mvn");
        Files.writeString(maven, content);
        Files.setPosixFilePermissions(maven, PosixFilePermissions.fromString("rwx------"));
        return maven;
    }
}
