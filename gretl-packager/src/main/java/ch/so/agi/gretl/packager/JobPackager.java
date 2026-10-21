package ch.so.agi.gretl.packager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
        JobCompiler.CompiledJob compiled = new JobCompiler().compile(descriptor);
        List<Path> classpath = new ArrayList<>(currentClasspath());
        List<PackageDescriptor.Dependency> missing = descriptor.dependencies().stream()
                .filter(dependency -> !isOnClasspath(dependency, classpath))
                .toList();
        classpath.addAll(new MavenArtifactResolver().resolve(missing));
        return new JobPackageBuilder(classpath, packagerVersion)
                .build(descriptor, compiled, output);
    }

    private static boolean isOnClasspath(PackageDescriptor.Dependency dependency,
                                         List<Path> classpath) {
        String name = dependency.artifactId() + "-" + dependency.version();
        for (Path path : classpath) {
            String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
            if (fileName.equals(dependency.artifactId() + ".jar")
                    || fileName.startsWith(name)
                    || (fileName.endsWith(".jar")
                    && fileName.contains(dependency.artifactId()))) {
                return true;
            }
        }
        return false;
    }

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
