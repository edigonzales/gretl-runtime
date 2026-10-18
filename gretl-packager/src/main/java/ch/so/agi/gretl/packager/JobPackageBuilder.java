package ch.so.agi.gretl.packager;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Small, testable shaded-JAR assembler for the first J6 package profile. It
 * uses only the deliberately supplied runtime classpath, never a network
 * resolver, and refuses silent class/resource collisions.
 */
public final class JobPackageBuilder {
    public static final String MAIN_CLASS = "ch.so.agi.gretl.job.JobJarMain";
    private static final long MAX_ENTRY_BYTES = 64L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 1024L * 1024 * 1024;
    private static final Set<String> MERGED_METADATA = Set.of(
            "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE",
            "META-INF/NOTICE.txt", "META-INF/DEPENDENCIES");

    private final List<Path> classpath;
    private final String packagerVersion;
    private final Map<String, Entry> entries = new TreeMap<>();
    private final Map<String, Set<String>> services = new TreeMap<>();
    private final Map<String, Map<String, String>> groovyModules = new TreeMap<>();
    private final List<IncludedArtifact> includedArtifacts = new ArrayList<>();

    public JobPackageBuilder(List<Path> classpath, String packagerVersion) {
        this.classpath = classpath.stream().map(path -> path.toAbsolutePath().normalize())
                .distinct().toList();
        this.packagerVersion = packagerVersion;
    }

    public PackageResult build(PackageDescriptor descriptor,
                               JobCompiler.CompiledJob compiled,
                               Path output) throws PackageException {
        if (classpath.isEmpty()) {
            throw new PackageException("PACKAGE_CLASSPATH_EMPTY",
                    "Kein Runtime-Klassenpfad fuer das Jobpaket vorhanden");
        }
        for (Path path : classpath) {
            if (!Files.exists(path)) {
                continue;
            }
            if (Files.isDirectory(path)) {
                addDirectory(path);
            } else if (path.getFileName().toString().endsWith(".jar")) {
                addJar(path);
            }
        }
        if (!entries.containsKey(MAIN_CLASS.replace('.', '/') + ".class")) {
            throw new PackageException("PACKAGE_JOB_RUNTIME_MISSING",
                    "gretl-job-runtime fehlt auf dem Packager-Klassenpfad");
        }
        validateDeclaredDependencies(descriptor);
        addCompiled(compiled);
        addResources(descriptor);
        addMetadata(descriptor, compiled);
        addServices();
        addGroovyModules();
        return write(output, descriptor, compiled);
    }

    private void addJar(Path path) throws PackageException {
        try (JarFile jar = new JarFile(path.toFile())) {
            var entriesInJar = jar.entries();
            while (entriesInJar.hasMoreElements()) {
                JarEntry entry = entriesInJar.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] bytes;
                try (InputStream input = jar.getInputStream(entry)) {
                    bytes = readBounded(input, entry.getName());
                }
                addEntry(entry.getName(), bytes, path.getFileName().toString());
            }
            includedArtifacts.add(new IncludedArtifact(path.getFileName().toString(),
                    sha256(path), "jar"));
        } catch (IOException exception) {
            throw new PackageException("PACKAGE_CLASSPATH_READ",
                    "JAR konnte nicht gelesen werden: " + path.getFileName(), exception);
        }
    }

    private void addDirectory(Path root) throws PackageException {
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .sorted(Comparator.comparing(Path::toString)).toList();
            for (Path file : files) {
                String name = root.relativize(file).toString().replace('\\', '/');
                addEntry(name, Files.readAllBytes(file), root.getFileName().toString());
            }
            includedArtifacts.add(new IncludedArtifact(root.getFileName().toString(),
                    hashDirectory(root), "classes"));
        } catch (IOException exception) {
            throw new PackageException("PACKAGE_CLASSPATH_READ",
                    "Klassenverzeichnis konnte nicht gelesen werden: " + root.getFileName(),
                    exception);
        }
    }

    private void addEntry(String name, byte[] bytes, String origin) throws PackageException {
        String normalized = normalizeEntry(name);
        if (normalized == null || isIgnored(normalized)) {
            return;
        }
        if (normalized.startsWith("META-INF/services/")) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            Set<String> lines = services.computeIfAbsent(normalized,
                    key -> new TreeSet<>());
            for (String line : text.split("\\R")) {
                String cleaned = line.strip();
                if (!cleaned.isEmpty() && !cleaned.startsWith("#")) {
                    lines.add(cleaned);
                }
            }
            return;
        }
        if (normalized.startsWith("META-INF/groovy/")) {
            Properties properties = new Properties();
            try {
                properties.load(new ByteArrayInputStream(bytes));
            } catch (IOException exception) {
                throw new PackageException("PACKAGE_GROOVY_METADATA",
                        "Groovy-Extension-Metadaten sind ungueltig: " + origin, exception);
            }
            Map<String, String> values = groovyModules.computeIfAbsent(normalized,
                    key -> new TreeMap<>());
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                String previous = values.putIfAbsent(key, value);
                if (previous != null && !previous.equals(value)) {
                    throw new PackageException("PACKAGE_RESOURCE_CONFLICT",
                            "Groovy-Extension-Metadaten kollidieren: " + normalized
                                    + " (" + origin + ")");
                }
            }
            return;
        }
        Entry previous = entries.get(normalized);
        if (previous == null) {
            entries.put(normalized, new Entry(bytes, origin));
            return;
        }
        if (java.util.Arrays.equals(previous.bytes(), bytes)) {
            return;
        }
        if (normalized.endsWith(".class")) {
            throw new PackageException("PACKAGE_CLASS_CONFLICT",
                    "Klasse " + normalized + " kollidiert zwischen "
                            + previous.origin() + " und " + origin);
        }
        if (MERGED_METADATA.contains(normalized)) {
            ByteArrayOutputStream merged = new ByteArrayOutputStream();
            merged.writeBytes(previous.bytes());
            merged.writeBytes(("\n\n--- " + origin + " ---\n").getBytes(StandardCharsets.UTF_8));
            merged.writeBytes(bytes);
            entries.put(normalized, new Entry(merged.toByteArray(), previous.origin()));
            return;
        }
        throw new PackageException("PACKAGE_RESOURCE_CONFLICT",
                "Ressource " + normalized + " kollidiert zwischen "
                        + previous.origin() + " und " + origin);
    }

    private void addCompiled(JobCompiler.CompiledJob compiled) throws PackageException {
        for (Map.Entry<String, byte[]> entry : compiled.classes().entrySet()) {
            addEntry(entry.getKey(), entry.getValue(), "compiled job");
        }
        for (JobCompiler.SourceFile source : compiled.sources()) {
            addEntry("META-INF/gretl/sources/" + source.relativePath(),
                    source.text().getBytes(StandardCharsets.UTF_8), "job source");
        }
    }

    private void addResources(PackageDescriptor descriptor) throws PackageException {
        for (Path resource : descriptor.resourcePaths()) {
            if (Files.isDirectory(resource)) {
                try (Stream<Path> walk = Files.walk(resource)) {
                    List<Path> files = walk.filter(Files::isRegularFile)
                            .filter(path -> !Files.isSymbolicLink(path))
                            .sorted(Comparator.comparing(Path::toString)).toList();
                    for (Path file : files) {
                        addResource(descriptor, file);
                    }
                } catch (IOException exception) {
                    throw new PackageException("PACKAGE_RESOURCE_READ",
                            "Ressourcen konnten nicht gelesen werden: " + resource, exception);
                }
            } else {
                addResource(descriptor, resource);
            }
        }
    }

    private void addResource(PackageDescriptor descriptor, Path file) throws PackageException {
        String relative = descriptor.baseDirectory().relativize(file.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
        try {
            addEntry("META-INF/gretl/job-resources/" + relative,
                    Files.readAllBytes(file), "job resource");
        } catch (IOException exception) {
            throw new PackageException("PACKAGE_RESOURCE_READ",
                    "Ressource konnte nicht gelesen werden: " + relative, exception);
        }
    }

    private void addMetadata(PackageDescriptor descriptor, JobCompiler.CompiledJob compiled)
            throws PackageException {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("schemaVersion", 1);
        job.put("name", descriptor.name());
        job.put("version", descriptor.version());
        job.put("runtimeVersion", descriptor.runtimeVersion());
        job.put("javaFeatureVersion", 25);
        job.put("entryClass", compiled.entryClass());
        job.put("entryScript", descriptor.entryScript());
        job.put("resourceRoot", "META-INF/gretl/job-resources/");
        job.put("resourceFiles", resourceNames(descriptor));
        job.put("sourceFiles", compiled.sources().stream()
                .map(JobCompiler.SourceFile::relativePath).toList());
        job.put("scriptSha256", compiled.scriptSha256());
        job.put("dependencyInventory", "META-INF/gretl/dependencies.json");
        job.put("packagerVersion", packagerVersion);
        addMetadataEntry("META-INF/gretl/job.json", Json.write(job));

        Map<String, Object> dependencies = new LinkedHashMap<>();
        dependencies.put("schemaVersion", 1);
        dependencies.put("declared", descriptor.dependencies().stream()
                .map(PackageDescriptor.Dependency::coordinate).toList());
        List<Map<String, String>> included = new ArrayList<>();
        for (IncludedArtifact artifact : includedArtifacts) {
            Map<String, String> value = new LinkedHashMap<>();
            value.put("name", artifact.name());
            value.put("kind", artifact.kind());
            value.put("sha256", artifact.sha256());
            included.add(value);
        }
        dependencies.put("included", included);
        addMetadataEntry("META-INF/gretl/dependencies.json", Json.write(dependencies));
    }

    private List<String> resourceNames(PackageDescriptor descriptor) throws PackageException {
        List<String> result = new ArrayList<>();
        for (Path resource : descriptor.resourcePaths()) {
            if (Files.isDirectory(resource)) {
                try (Stream<Path> walk = Files.walk(resource)) {
                    walk.filter(Files::isRegularFile)
                            .filter(path -> !Files.isSymbolicLink(path))
                            .map(path -> descriptor.baseDirectory()
                                    .relativize(path.toAbsolutePath().normalize())
                                    .toString().replace('\\', '/'))
                            .sorted().forEach(result::add);
                } catch (IOException exception) {
                    throw new PackageException("PACKAGE_RESOURCE_READ",
                            "Ressourcenindex konnte nicht erstellt werden", exception);
                }
            } else {
                result.add(descriptor.baseDirectory()
                        .relativize(resource.toAbsolutePath().normalize())
                        .toString().replace('\\', '/'));
            }
        }
        return result.stream().distinct().sorted().toList();
    }

    private void addMetadataEntry(String name, String value) throws PackageException {
        addEntry(name, value.getBytes(StandardCharsets.UTF_8), "package metadata");
    }

    private void addServices() throws PackageException {
        for (Map.Entry<String, Set<String>> entry : services.entrySet()) {
            String value = String.join("\n", entry.getValue()) + "\n";
            putMetadataEntry(entry.getKey(), value.getBytes(StandardCharsets.UTF_8),
                    "service merge");
        }
    }

    private void addGroovyModules() throws PackageException {
        for (Map.Entry<String, Map<String, String>> entry : groovyModules.entrySet()) {
            StringBuilder value = new StringBuilder();
            entry.getValue().forEach((key, item) ->
                    value.append(key).append('=').append(item).append('\n'));
            putMetadataEntry(entry.getKey(),
                    value.toString().getBytes(StandardCharsets.ISO_8859_1), "groovy merge");
        }
    }

    private void putMetadataEntry(String name, byte[] bytes, String origin) throws PackageException {
        Entry previous = entries.get(name);
        if (previous != null) {
            throw new PackageException("PACKAGE_RESOURCE_CONFLICT",
                    "Metadatenressource kollidiert: " + name + " (" + previous.origin() + ")");
        }
        entries.put(name, new Entry(bytes, origin));
    }

    private void validateDeclaredDependencies(PackageDescriptor descriptor) throws PackageException {
        for (PackageDescriptor.Dependency dependency : descriptor.dependencies()) {
            boolean found = includedArtifacts.stream().anyMatch(artifact ->
                    artifact.name().equals(dependency.artifactId() + ".jar")
                            || artifact.name().startsWith(dependency.artifactId() + "-"));
            if (!found) {
                throw new PackageException("PACKAGE_DEPENDENCY_MISSING",
                        "Dependency " + dependency.coordinate()
                                + " ist nicht auf dem bereitgestellten Klassenpfad enthalten");
            }
        }
    }

    private PackageResult write(Path output, PackageDescriptor descriptor,
                                JobCompiler.CompiledJob compiled) throws PackageException {
        Path absolute = output.toAbsolutePath().normalize();
        try {
            Path parent = absolute.getParent();
            if (parent == null) {
                throw new PackageException("PACKAGE_OUTPUT_INVALID",
                        "Ausgabepfad hat kein Elternverzeichnis");
            }
            Files.createDirectories(parent);
            Path temporary = parent.resolve(absolute.getFileName() + ".tmp-" + UUID.randomUUID());
            Manifest manifest = manifest(descriptor);
            try (OutputStream file = Files.newOutputStream(temporary);
                 JarOutputStream jar = new JarOutputStream(file, manifest)) {
                long total = 0;
                for (Map.Entry<String, Entry> entry : entries.entrySet()) {
                    byte[] bytes = entry.getValue().bytes();
                    total += bytes.length;
                    if (total > MAX_TOTAL_BYTES) {
                        throw new PackageException("PACKAGE_TOO_LARGE",
                                "Jobpaket ueberschreitet das Groessenlimit");
                    }
                    JarEntry jarEntry = new JarEntry(entry.getKey());
                    jarEntry.setTime(0L);
                    jar.putNextEntry(jarEntry);
                    jar.write(bytes);
                    jar.closeEntry();
                }
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                Files.deleteIfExists(temporary);
                throw exception;
            }
        } catch (IOException exception) {
            throw new PackageException("PACKAGE_WRITE_FAILED",
                    "Jobpaket konnte nicht geschrieben werden: " + output, exception);
        }
        return new PackageResult(absolute, descriptor.name(), descriptor.version(),
                compiled.entryClass(), compiled.classCount(),
                resourceNamesUnchecked(descriptor).size());
    }

    private List<String> resourceNamesUnchecked(PackageDescriptor descriptor) {
        try {
            return resourceNames(descriptor);
        } catch (PackageException exception) {
            return List.of();
        }
    }

    private Manifest manifest(PackageDescriptor descriptor) {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.MAIN_CLASS, MAIN_CLASS);
        attributes.putValue("Implementation-Title", descriptor.name());
        attributes.putValue("Implementation-Version", descriptor.version());
        if (entries.keySet().stream().anyMatch(name -> name.startsWith("META-INF/versions/"))) {
            attributes.putValue("Multi-Release", "true");
        }
        return manifest;
    }

    private static String normalizeEntry(String name) throws PackageException {
        String value = name.replace('\\', '/');
        if (value.startsWith("/") || value.contains("../") || value.equals("..")) {
            throw new PackageException("PACKAGE_ENTRY_UNSAFE",
                    "Unsicherer JAR-Eintrag: " + name);
        }
        return value;
    }

    private static boolean isIgnored(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return name.equals("META-INF/MANIFEST.MF")
                || name.equals("META-INF/INDEX.LIST")
                || name.equals("module-info.class")
                || upper.endsWith("/MODULE-INFO.CLASS")
                || upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA");
    }

    private static byte[] readBounded(InputStream input, String name) throws IOException,
            PackageException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
            if (out.size() > MAX_ENTRY_BYTES) {
                throw new PackageException("PACKAGE_ENTRY_TOO_LARGE",
                        "JAR-Eintrag ist zu gross: " + name);
            }
        }
        return out.toByteArray();
    }

    private static String sha256(Path file) throws PackageException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(file)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new PackageException("PACKAGE_HASH_FAILED",
                    "SHA-256 konnte nicht berechnet werden: " + file.getFileName(), exception);
        }
    }

    private static String hashDirectory(Path root) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                    digest.update(root.relativize(file).toString()
                            .replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                    digest.update(Files.readAllBytes(file));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record Entry(byte[] bytes, String origin) {
    }

    private record IncludedArtifact(String name, String sha256, String kind) {
    }

    public record PackageResult(Path output, String name, String version,
                                String entryClass, int classCount, int resourceCount) {
    }
}
