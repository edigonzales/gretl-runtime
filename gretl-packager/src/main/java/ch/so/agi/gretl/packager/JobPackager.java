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
        Path catalog = catalogFile();
        List<ResolvedArtifact> selected = new PackageCatalog(catalog, descriptor.runtimeVersion()).select(descriptor);
        List<Path> classpath = selected.stream().map(ResolvedArtifact::file).toList();
        JobCompiler.CompiledJob compiled = new JobCompiler().compile(descriptor, classpath);
        java.util.Map<Path, PackageDescriptor.Dependency> identities = new java.util.LinkedHashMap<>();
        selected.forEach(artifact -> identities.put(artifact.file(), artifact.identity()));
        return new JobPackageBuilder(classpath, packagerVersion, identities)
                .build(descriptor, compiled, output);
    }

    static Path catalogFile() throws PackageException {
        String configured = System.getProperty("gretl.packager.catalog");
        if (configured != null) return Path.of(configured);
        try {
            Path jar = Path.of(JobPackager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path catalog = jar.getParent().getParent().getParent().resolve("package-catalog.json");
            if (Files.isRegularFile(catalog)) return catalog;
        } catch (Exception ignored) { }
        throw new PackageException("PACKAGE_CATALOG_MISSING", "Use the candidate distribution or set gretl.packager.catalog explicitly");
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
