package ch.so.agi.gretl.packager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Resolves declared Maven coordinates through the Maven CLI. Packaging is an
 * authoring step; the normal script runtime never invokes Maven.
 */
final class MavenArtifactResolver {
    private final Path localRepository;
    private final boolean offline;
    private final String mavenExecutable;

    MavenArtifactResolver() {
        this(Path.of(System.getProperty("user.home"), ".m2", "repository"),
                Boolean.getBoolean("gretl.packager.offline"),
                System.getProperty("gretl.packager.maven", "mvn"));
    }

    MavenArtifactResolver(Path localRepository, boolean offline) {
        this(localRepository, offline, System.getProperty("gretl.packager.maven", "mvn"));
    }

    MavenArtifactResolver(Path localRepository, boolean offline, String mavenExecutable) {
        this.localRepository = localRepository.toAbsolutePath().normalize();
        this.offline = offline;
        this.mavenExecutable = mavenExecutable;
    }

    List<Path> resolve(List<PackageDescriptor.Dependency> dependencies) throws PackageException {
        if (dependencies.isEmpty()) return List.of();
        Path work = null;
        try {
            work = Files.createTempDirectory("gretl-maven-resolve-");
            Path pom = work.resolve("pom.xml");
            Path output = work.resolve("classpath.txt");
            Files.writeString(pom, pom(dependencies), StandardCharsets.UTF_8);
            List<String> command = new ArrayList<>(List.of(
                    mavenExecutable, "-q", "-B", "--no-transfer-progress",
                    "-f", pom.toString(),
                    "-Dmaven.repo.local=" + localRepository,
                    "-Dmdep.outputFile=" + output,
                    "-Dmdep.includeScope=runtime",
                    "dependency:build-classpath"));
            if (offline) command.add(1, "-o");
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true).start();
            String log;
            try (var stream = process.getInputStream()) {
                log = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(180, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new PackageException("PACKAGE_DEPENDENCY_RESOLVE",
                        "Maven dependency resolution timed out");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(output)) {
                throw new PackageException("PACKAGE_DEPENDENCY_RESOLVE",
                        "Maven could not resolve job dependencies: " + tail(log));
            }
            List<Path> paths = new ArrayList<>();
            for (String entry : Files.readString(output, StandardCharsets.UTF_8)
                    .split(java.io.File.pathSeparator)) {
                if (entry.isBlank()) continue;
                Path path = Path.of(entry).toAbsolutePath().normalize();
                if (Files.exists(path)) paths.add(path);
            }
            return List.copyOf(paths);
        } catch (IOException exception) {
            throw new PackageException("PACKAGE_DEPENDENCY_RESOLVE",
                    "Could not start Maven dependency resolution: " + exception.getMessage(),
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PackageException("PACKAGE_DEPENDENCY_RESOLVE",
                    "Interrupted while resolving Maven dependencies", exception);
        } finally {
            if (work != null) {
                try (var walk = Files.walk(work)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                    });
                } catch (IOException ignored) {
                    // Best-effort temporary cleanup.
                }
            }
        }
    }

    private static String pom(List<PackageDescriptor.Dependency> dependencies) {
        StringBuilder xml = new StringBuilder("""
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>ch.so.agi.gretl</groupId>
                  <artifactId>job-dependencies</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <properties><maven.compiler.release>25</maven.compiler.release></properties>
                  <dependencies>
                """);
        for (PackageDescriptor.Dependency dependency : dependencies) {
            xml.append("    <dependency><groupId>").append(dependency.groupId())
                    .append("</groupId><artifactId>").append(dependency.artifactId())
                    .append("</artifactId><version>").append(dependency.version())
                    .append("</version></dependency>\n");
        }
        xml.append("  </dependencies>\n</project>\n");
        return xml.toString();
    }

    private static String tail(String value) {
        String text = value == null ? "" : value.strip();
        return text.length() <= 800 ? text : text.substring(text.length() - 800);
    }
}
