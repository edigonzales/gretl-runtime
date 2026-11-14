package ch.so.agi.gretl.packager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Public authoring entry point used by the CLI and tests. */
public final class JobPackager {
    private final String packagerVersion;
    private final Locale locale;

    public JobPackager(String packagerVersion) {
        this(packagerVersion, Locale.GERMAN);
    }

    public JobPackager(String packagerVersion, Locale locale) {
        if (packagerVersion == null || packagerVersion.isBlank()) {
            throw new IllegalArgumentException("packagerVersion must not be blank");
        }
        this.packagerVersion = packagerVersion;
        this.locale = java.util.Objects.requireNonNull(locale, "locale");
    }

    public JobPackageBuilder.PackageResult packageJob(Path descriptorFile, Path output)
            throws PackageException {
        PackagerMessages.use(locale);
        PackageDescriptor descriptor = PackageDescriptor.load(descriptorFile,
                packagerVersion.endsWith("-SNAPSHOT"));
        if (!packagerVersion.equals(descriptor.runtimeVersion())) {
            throw new PackageException("PACKAGE_RUNTIME_VERSION",
                    PackagerMessages.format("package.runtimeVersion",
                            descriptor.runtimeVersion(), packagerVersion));
        }
        var identities = new java.util.LinkedHashMap<Path, PackageDescriptor.Dependency>();
        List<Path> standard = runtimeJars();
        List<Path> classpath = new ArrayList<>(standard);
        List<PackageDescriptor.Dependency> external = new ArrayList<>();
        for (var dependency : descriptor.dependencies()) {
            Path installed = installedArtifact(standard, dependency);
            if (installed == null || !dependency.groupId().equals("ch.so.agi.gretl")) external.add(dependency);
            else identities.put(installed, dependency);
        }
        for (ResolvedArtifact artifact : new MavenArtifactResolver().resolveArtifacts(external)) {
            Path installed = installedArtifact(standard, artifact.identity());
            if (installed == null) {
                classpath.add(artifact.file());
                identities.put(artifact.file(), artifact.identity());
            } else {
                if (!ResolvedArtifact.hash(installed).equals(artifact.sha256())) {
                    throw dependencyConflict(artifact.identity());
                }
                identities.put(installed, artifact.identity());
            }
        }
        var builder = new JobPackageBuilder(classpath, packagerVersion, identities);
        if (descriptor.projectPackage()) {
            var project = new ch.so.agi.gretl.runtime.groovy.project.GretlProjectParser(locale).parse(descriptor.baseDirectory());
            return builder.buildProject(descriptor, new ProjectCompiler().compile(project, descriptor, classpath), output);
        }
        return builder.build(descriptor, new JobCompiler().compile(descriptor, classpath), output);
    }

    private static Path installedArtifact(List<Path> standard, PackageDescriptor.Dependency dependency)
            throws PackageException {
        for (Path path : standard) {
            if (path.getFileName().toString().equals(dependency.fileName())) return path;
        }
        if (dependency.groupId().equals("ch.so.agi.gretl") && dependency.classifier().isEmpty()
                && standard.stream().anyMatch(path -> path.getFileName().toString()
                        .matches(java.util.regex.Pattern.quote(dependency.artifactId()) + "-[0-9].*\\.jar"))) {
            throw dependencyConflict(dependency);
        }
        return null;
    }

    private static PackageException dependencyConflict(PackageDescriptor.Dependency dependency) {
        return new PackageException("PACKAGE_DEPENDENCY_CONFLICT",
                "Dependency conflicts with the installed runtime: " + dependency.coordinate());
    }

    static List<Path> runtimeJars() throws PackageException {
        try {
            Path packager = Path.of(JobPackager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path lib = packager.getParent();
            if (!Files.isRegularFile(packager) || !"lib".equals(lib.getFileName().toString())) {
                throw new PackageException("PACKAGE_RUNTIME_MISSING", "Use gretl from the installed distribution");
            }
            try (var files = Files.list(lib)) {
                return files.filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> path.getFileName().toString().endsWith(".jar"))
                        .filter(path -> !path.equals(packager))
                        .filter(path -> !path.getFileName().toString().startsWith("gretl-cli-"))
                        .filter(path -> !path.getFileName().toString().startsWith("file-size-extension-"))
                        .sorted().toList();
            }
        } catch (java.io.IOException | java.net.URISyntaxException exception) {
            throw new PackageException("PACKAGE_RUNTIME_MISSING", "Cannot read the installed distribution", exception);
        }
    }

    /** Diagnostic utility only; never used to select a package. */
    public static List<Path> currentClasspath() {
        String value = System.getProperty("java.class.path", "");
        List<Path> result = new ArrayList<>();
        for (String part : value.split(File.pathSeparator)) {
            if (!part.isBlank()) {
                Path path = Path.of(part).toAbsolutePath().normalize();
                if (Files.exists(path)) {
                    result.add(path);
                }
            }
        }
        return List.copyOf(result);
    }
}
