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
        this(Path.of(System.getProperty("gretl.packager.localRepository", Path.of(System.getProperty("user.home"), ".m2", "repository").toString())),
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
        return resolveArtifacts(dependencies).stream().map(ResolvedArtifact::file).toList();
    }

    List<ResolvedArtifact> resolveArtifacts(List<PackageDescriptor.Dependency> dependencies) throws PackageException {
        if (dependencies.isEmpty()) return List.of();
        Path work = null;
        try {
            work = Files.createTempDirectory("gretl-maven-resolve-");
            Path pom = work.resolve("pom.xml");
            Path output = work.resolve("classpath.txt");
            Path tree = work.resolve("tree.json");
            Path logFile = work.resolve("maven.log");
            Files.writeString(pom, pom(dependencies), StandardCharsets.UTF_8);
            List<String> command = new ArrayList<>(List.of(
                    mavenExecutable, "-q", "-B", "--no-transfer-progress",
                    "-f", pom.toString(),
                    "-Dmaven.repo.local=" + localRepository,
                    "-Dmdep.outputFile=" + output,
                    "-Dmdep.includeScope=runtime", "-Dscope=runtime", "-DoutputType=json", "-DoutputFile=" + tree,
                    "org.apache.maven.plugins:maven-dependency-plugin:3.11.0:build-classpath",
                    "org.apache.maven.plugins:maven-dependency-plugin:3.11.0:tree"));
            if (offline) command.add(1, "-o");
            String settings = System.getProperty("gretl.packager.settings");
            if (settings != null) command.addAll(List.of("-s", settings));
            Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(logFile.toFile()).start();
            try {
                if (!process.waitFor(180, TimeUnit.SECONDS))
                    throw new PackageException("PACKAGE_DEPENDENCY_RESOLVE", "Maven dependency resolution timed out");
            } finally {
                if (process.isAlive()) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); process.waitFor(); }
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(output) || !Files.isRegularFile(tree)) {
                throw new PackageException("PACKAGE_DEPENDENCY_RESOLVE",
                        "Maven could not resolve job dependencies: " + tail(Files.readString(logFile)));
            }
            List<Path> paths = new ArrayList<>();
            for (String entry : Files.readString(output, StandardCharsets.UTF_8)
                    .split(java.io.File.pathSeparator)) {
                if (entry.isBlank()) continue;
                Path path = Path.of(entry).toAbsolutePath().normalize();
                if (Files.exists(path)) paths.add(path);
            }
            List<ResolvedArtifact> artifacts = new ArrayList<>();
            collect((java.util.Map<?, ?>) ch.so.agi.gretl.runtime.logging.Json.parseFile(tree, 16L << 20), paths, artifacts, true);
            if (artifacts.size() != paths.size()) throw new PackageException("PACKAGE_ARTIFACT_IDENTITY", "Maven graph and actual classpath disagree");
            for (var dependency : dependencies) {
                if (artifacts.stream().noneMatch(artifact -> artifact.identity().equals(dependency)))
                    throw new PackageException("PACKAGE_DEPENDENCY_MISSING", "Maven did not resolve the exact declared dependency: " + dependency.coordinate());
            }
            return List.copyOf(artifacts);
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

    private static void collect(java.util.Map<?, ?> node, List<Path> paths, List<ResolvedArtifact> artifacts, boolean root) throws PackageException {
        if (!root) {
            var identity = ResolvedArtifact.identity(node);
            Path suffix = Path.of(identity.groupId().replace('.', '/'), identity.artifactId(), identity.version(), identity.fileName());
            List<Path> matches = paths.stream().filter(path -> path.endsWith(suffix)).toList();
            if (matches.size() != 1) throw new PackageException("PACKAGE_ARTIFACT_IDENTITY", "Cannot bind Maven identity to exactly one input: " + identity.coordinate());
            if (artifacts.stream().noneMatch(artifact -> artifact.identity().equals(identity)))
                artifacts.add(ResolvedArtifact.read(identity, matches.getFirst()));
        }
        if (node.get("children") instanceof List<?> children)
            for (Object child : children) collect((java.util.Map<?, ?>) child, paths, artifacts, false);
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
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
                """);
        String repositories = System.getProperty("gretl.packager.repositories");
        if (repositories != null) {
            xml.append("<repositories>"); int index = 0;
            for (String repository : repositories.split(",")) {
                xml.append("<repository><id>job-").append(index++).append("</id><url>").append(xml(repository))
                        .append("</url><snapshots><enabled>true</enabled></snapshots></repository>");
            }
            xml.append("</repositories>");
        }
        xml.append("<dependencies>");
        for (PackageDescriptor.Dependency dependency : dependencies) {
            xml.append("    <dependency><groupId>").append(dependency.groupId())
                    .append("</groupId><artifactId>").append(dependency.artifactId())
                    .append("</artifactId><version>").append(dependency.version())
                    .append("</version>");
            if (!dependency.classifier().isEmpty()) xml.append("<classifier>").append(dependency.classifier()).append("</classifier>");
            xml.append("</dependency>\n");
        }
        xml.append("  </dependencies>\n</project>\n");
        return xml.toString();
    }

    private static String tail(String value) {
        String text = value == null ? "" : value.strip();
        return text.length() <= 800 ? text : text.substring(text.length() - 800);
    }
}
