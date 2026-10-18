package ch.so.agi.gretl.packager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Public authoring entry point used by the CLI and tests. */
public final class JobPackager {
    private final String packagerVersion;

    public JobPackager(String packagerVersion) {
        if (packagerVersion == null || packagerVersion.isBlank()) {
            throw new IllegalArgumentException("packagerVersion must not be blank");
        }
        this.packagerVersion = packagerVersion;
    }

    public JobPackageBuilder.PackageResult packageJob(Path descriptorFile, Path output)
            throws PackageException {
        PackageDescriptor descriptor = PackageDescriptor.load(descriptorFile,
                packagerVersion.endsWith("-SNAPSHOT"));
        if (!packagerVersion.equals(descriptor.runtimeVersion())) {
            throw new PackageException("PACKAGE_RUNTIME_VERSION",
                    "Deskriptor verlangt Runtime " + descriptor.runtimeVersion()
                            + ", Packager ist " + packagerVersion);
        }
        JobCompiler.CompiledJob compiled = new JobCompiler().compile(descriptor);
        return new JobPackageBuilder(currentClasspath(), packagerVersion)
                .build(descriptor, compiled, output);
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
