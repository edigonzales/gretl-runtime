package ch.so.agi.gretl.packager;

import ch.so.agi.gretl.i18n.Messages;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import ch.so.agi.gretl.runtime.groovy.fragment.CompiledScriptBundle;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private static final ObjectWriter METADATA_JSON = new JsonMapper().writer();
    private static final long MAX_ENTRY_BYTES = 256L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 1024L * 1024 * 1024;
    private static final Set<String> MERGED_METADATA = Set.of(
            "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE",
            "META-INF/NOTICE.txt", "META-INF/DEPENDENCIES",
            "META-INF/sisu/javax.inject.Named");

    private final List<Path> classpath;
    private final Locale locale;
    private final Map<Path, PackageDescriptor.Dependency> identities;
    private final Map<String, Entry> entries = new TreeMap<>();
    private final Map<String, Set<String>> services = new TreeMap<>();
    private final Map<String, Map<String, String>> groovyModules = new TreeMap<>();
    private final Set<PackageDescriptor.Dependency> includedDependencies = new java.util.HashSet<>();
    private final Set<String> resourceFiles = new TreeSet<>();

    public JobPackageBuilder(List<Path> classpath, Map<Path, PackageDescriptor.Dependency> identities, Locale locale) {
        this.locale = locale;
        this.identities = Map.copyOf(identities);
        this.classpath = classpath.stream().map(path -> path.toAbsolutePath().normalize())
                .distinct().toList();
    }

    public PackageResult build(PackageDescriptor descriptor, BundleCompiler.Result compiled, Path output)
            throws PackageException {
        entries.clear();
        services.clear();
        groovyModules.clear();
        includedDependencies.clear();
        resourceFiles.clear();
        if (classpath.isEmpty()) {
            throw new PackageException("PACKAGE_CLASSPATH_EMPTY",
                    Messages.get(locale, "package.classpathEmpty"));
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
                    Messages.get(locale, "package.jobRuntimeMissing"));
        }
        validateDeclaredDependencies(descriptor);
        addCompiled(compiled.classes(), compiled.sources());
        addResources(descriptor);
        addMetadata(descriptor, compiled.bundle());
        addServices();
        addGroovyModules();
        String entryId = compiled.bundle().entries().getFirst().sourceId();
        String entryClass = compiled.bundle().sources().stream().filter(source -> source.sourceId().equals(entryId))
                .findFirst().orElseThrow().className();
        return write(output, descriptor, entryClass, compiled.classes().size());
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
                addEntry(entry.getName(), bytes, identities.containsKey(path) ? identities.get(path).coordinate() : path.getFileName().toString());
            }
            if (identities.containsKey(path)) includedDependencies.add(identities.get(path));
        } catch (IOException exception) {
            throw new PackageException("PACKAGE_CLASSPATH_READ",
                    Messages.format(locale, "package.jarRead", path.getFileName()), exception);
        }
    }

    private void addDirectory(Path root) throws PackageException {
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .sorted(Comparator.comparing(Path::toString)).toList();
            for (Path file : files) {
                String name = root.relativize(file).toString().replace('\\', '/');
                addEntry(name, Files.readAllBytes(file), identities.containsKey(root) ? identities.get(root).coordinate() : root.getFileName().toString());
            }
            if (identities.containsKey(root)) includedDependencies.add(identities.get(root));
        } catch (IOException exception) {
            throw new PackageException("PACKAGE_CLASSPATH_READ",
                    Messages.format(locale, "package.classpathRead", root.getFileName()),
                    exception);
        }
    }

    private void addEntry(String name, byte[] bytes, String origin) throws PackageException {
        String normalized = normalizeEntry(name);
        if (!normalized.startsWith("META-INF/gretl/job-resources/") && isIgnored(normalized)) {
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
                        Messages.format(locale, "package.groovyMetadata", origin), exception);
            }
            Map<String, String> values = groovyModules.computeIfAbsent(normalized,
                    key -> new TreeMap<>());
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                String previous = values.putIfAbsent(key, value);
                if (previous != null && !previous.equals(value)) {
                    throw new PackageException("PACKAGE_RESOURCE_CONFLICT",
                            Messages.format(locale, "package.groovyMetadataConflict", normalized, origin));
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
            // av2geobau embeds a private copy of the generated INTERLIS model
            // classes. Prefer the canonical iox-ili copy in the runtime image.
            if (normalized.startsWith("ch/interlis/models/") && canonicalModelPair(previous.origin(), origin)) {
                if (isIoxIli(origin)) entries.put(normalized, new Entry(bytes, origin));
                return;
            }
            throw new PackageException("PACKAGE_CLASS_CONFLICT",
                    Messages.format(locale, "package.classConflict", normalized, previous.origin(), origin));
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
                Messages.format(locale, "package.resourceConflict", normalized, previous.origin(), origin));
    }

    private static boolean canonicalModelPair(String first, String second) {
        return (isIoxIli(first) && isAv2geobau(second))
                || (isIoxIli(second) && isAv2geobau(first));
    }

    private static boolean isIoxIli(String origin) {
        return origin.startsWith("ch.interlis:iox-ili:") || origin.matches("iox-ili-[0-9].*\\.jar");
    }

    private static boolean isAv2geobau(String origin) {
        return origin.startsWith("ch.interlis:av2geobau:") || origin.matches("av2geobau-[0-9].*\\.jar");
    }

    private void addCompiled(Map<String, byte[]> classes, List<BundleCompiler.SourceFile> sources) throws PackageException {
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            addEntry(entry.getKey(), entry.getValue(), "compiled job");
        }
        for (BundleCompiler.SourceFile source : sources) {
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
                            Messages.format(locale, "package.resourcesRead", resource), exception);
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
            resourceFiles.add(relative);
        } catch (IOException exception) {
            throw new PackageException("PACKAGE_RESOURCE_READ",
                    Messages.format(locale, "package.resourceRead", relative), exception);
        }
    }

    private void addMetadata(PackageDescriptor descriptor, CompiledScriptBundle bundle) throws PackageException {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("schemaVersion", 1);
        job.put("name", descriptor.name());
        job.put("version", descriptor.version());
        job.put("runtimeVersion", descriptor.runtimeVersion());
        job.put("bundle", bundle.toMetadata());
        job.put("resourceFiles", List.copyOf(resourceFiles));
        String name = "META-INF/gretl/job.json";
        try {
            byte[] bytes = METADATA_JSON.writeValueAsBytes(job);
            if (bytes.length > CompiledScriptBundle.MAX_METADATA_BYTES) {
                throw new PackageException("PACKAGE_ENTRY_TOO_LARGE",
                        Messages.format(locale, "package.entryTooLarge", name));
            }
            addEntry(name, bytes, "package metadata");
        } catch (JsonProcessingException exception) {
            throw new PackageException("PACKAGE_WRITE_FAILED", "Could not encode " + name, exception);
        }
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
                    Messages.format(locale, "package.metadataConflict", name, previous.origin()));
        }
        entries.put(name, new Entry(bytes, origin));
    }

    private void validateDeclaredDependencies(PackageDescriptor descriptor) throws PackageException {
        for (PackageDescriptor.Dependency dependency : descriptor.dependencies()) {
            if (!includedDependencies.contains(dependency)) {
                throw new PackageException("PACKAGE_DEPENDENCY_MISSING",
                        Messages.format(locale, "package.dependencyMissing", dependency.coordinate()));
            }
        }
    }

    private PackageResult write(Path output, PackageDescriptor descriptor,
                                String entryClass, int classCount) throws PackageException {
        Path absolute = output.toAbsolutePath().normalize();
        try {
            Path parent = absolute.getParent();
            if (parent == null) {
                throw new PackageException("PACKAGE_OUTPUT_INVALID",
                        Messages.get(locale, "package.outputParent"));
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
                                Messages.get(locale, "package.tooLarge"));
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
                    Messages.format(locale, "package.writeFailed", output), exception);
        }
        return new PackageResult(absolute, descriptor.name(), descriptor.version(),
                entryClass, classCount,
                resourceFiles.size());
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

    private String normalizeEntry(String name) throws PackageException {
        String value = name.replace('\\', '/');
        if (value.startsWith("/") || value.contains("../") || value.equals("..")) {
            throw new PackageException("PACKAGE_ENTRY_UNSAFE",
                    Messages.format(locale, "package.unsafeEntry", name));
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

    private byte[] readBounded(InputStream input, String name) throws IOException,
            PackageException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
            if (out.size() > MAX_ENTRY_BYTES) {
                throw new PackageException("PACKAGE_ENTRY_TOO_LARGE",
                        Messages.format(locale, "package.entryTooLarge", name));
            }
        }
        return out.toByteArray();
    }

    private record Entry(byte[] bytes, String origin) {
    }

    public record PackageResult(Path output, String name, String version,
                                String entryClass, int classCount, int resourceCount) {
    }
}
