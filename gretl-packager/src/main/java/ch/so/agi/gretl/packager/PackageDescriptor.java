package ch.so.agi.gretl.packager;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict package descriptor; all paths stay below the descriptor directory. */
public record PackageDescriptor(
        Path descriptorFile, Path baseDirectory, int schemaVersion,
        String name, String version, String runtimeVersion, String entryScript,
        List<String> sources, List<String> resources, List<Dependency> dependencies,
        List<String> taskModules, boolean standardFunctions) {

    private static final long MAX_DESCRIPTOR_BYTES = 1L << 20;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}");
    private static final Set<String> FIELDS = Set.of(
            "schemaVersion", "name", "version", "runtimeVersion", "entryScript",
            "sources", "resources", "dependencies", "taskModules", "standardFunctions");

    public PackageDescriptor(Path descriptorFile, Path baseDirectory, int schemaVersion,
            String name, String version, String runtimeVersion, String entryScript,
            List<String> sources, List<String> resources, List<Dependency> dependencies) {
        this(descriptorFile, baseDirectory, schemaVersion, name, version, runtimeVersion,
                entryScript, sources, resources, dependencies, List.of(), false);
    }

    public record Dependency(String groupId, String artifactId, String version, String classifier) {
        public Dependency(String groupId, String artifactId, String version) { this(groupId, artifactId, version, ""); }
        public String coordinate() {
            return classifier.isEmpty() ? groupId + ":" + artifactId + ":" + version
                    : groupId + ":" + artifactId + ":jar:" + classifier + ":" + version;
        }
        public String key() { return groupId + ":" + artifactId + ":jar:" + classifier; }
        public String fileName() { return artifactId + "-" + version + (classifier.isEmpty() ? "" : "-" + classifier) + ".jar"; }
    }

    public PackageDescriptor {
        java.util.Objects.requireNonNull(descriptorFile, "descriptorFile");
        java.util.Objects.requireNonNull(baseDirectory, "baseDirectory");
        sources = List.copyOf(sources);
        resources = List.copyOf(resources);
        dependencies = List.copyOf(dependencies);
        taskModules = List.copyOf(taskModules);
    }

    public static PackageDescriptor load(Path descriptor) throws PackageException {
        return load(descriptor, false);
    }

    public static PackageDescriptor load(Path descriptor, boolean allowSnapshots)
            throws PackageException {
        Path file = descriptor.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new PackageException("PACKAGE_DESCRIPTOR_MISSING",
                    PackagerMessages.format("package.descriptorMissing", file));
        }
        Object parsed;
        try {
            parsed = Json.parseFile(file, MAX_DESCRIPTOR_BYTES);
        } catch (IOException | RuntimeException exception) {
            throw new PackageException("PACKAGE_DESCRIPTOR_INVALID",
                    PackagerMessages.format("package.descriptorInvalidJson", exception.getMessage()), exception);
        }
        if (!(parsed instanceof Map<?, ?> raw)) {
            throw new PackageException("PACKAGE_DESCRIPTOR_INVALID",
                    PackagerMessages.get("package.descriptorObject"));
        }
        Map<String, Object> object = stringKeys(raw);
        rejectUnknown(object);
        int schemaVersion = integer(object, "schemaVersion");
        if (schemaVersion != 1 && schemaVersion != 2) {
            throw new PackageException("PACKAGE_DESCRIPTOR_VERSION",
                    PackagerMessages.format("package.descriptorVersion", schemaVersion));
        }
        String name = text(object, "name");
        if (!NAME.matcher(name).matches()) {
            throw new PackageException("PACKAGE_NAME_INVALID",
                    PackagerMessages.format("package.nameInvalid", name));
        }
        String version = text(object, "version");
        String runtimeVersion = text(object, "runtimeVersion");
        if (!VERSION.matcher(version).matches() || !VERSION.matcher(runtimeVersion).matches()) {
            throw new PackageException("PACKAGE_VERSION_INVALID",
                    PackagerMessages.get("package.versionInvalid"));
        }
        String entryScript = text(object, "entryScript");
        List<String> sources = textList(object, "sources");
        List<String> resources = textList(object, "resources");
        List<Dependency> dependencies = dependencies(object, allowSnapshots);
        if (schemaVersion == 1 && (object.containsKey("taskModules") || object.containsKey("standardFunctions")))
            throw new PackageException("PACKAGE_SELECTION_VERSION", "taskModules/standardFunctions require schemaVersion 2");
        List<String> taskModules = textList(object, "taskModules");
        if (!Set.of("io", "database", "interlis", "ilivalidator").containsAll(taskModules))
            throw new PackageException("PACKAGE_MODULE_UNKNOWN", "Unknown taskModules selection: " + taskModules);
        Object functions = object.getOrDefault("standardFunctions", false);
        if (!(functions instanceof Boolean)) throw new PackageException("PACKAGE_FIELD_TYPE", "standardFunctions must be boolean");

        Path base = file.getParent().toAbsolutePath().normalize();
        PackageDescriptor value = new PackageDescriptor(file, base, schemaVersion,
                name, version, runtimeVersion, entryScript, sources, resources, dependencies, taskModules, (Boolean) functions);
        value.validate();
        return value;
    }

    public Path entryPath() {
        return baseDirectory.resolve(entryScript).normalize();
    }

    public List<Path> sourcePaths() {
        return sources.stream().map(value -> baseDirectory.resolve(value).normalize()).toList();
    }

    public List<Path> resourcePaths() {
        return resources.stream().map(value -> baseDirectory.resolve(value).normalize()).toList();
    }

    private void validate() throws PackageException {
        Path entry = safePath(entryScript, "entryScript");
        if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
            throw new PackageException("PACKAGE_ENTRY_MISSING",
                    PackagerMessages.format("package.entryMissing", entryScript));
        }
        if (!entry.getParent().equals(baseDirectory)) {
            throw new PackageException("PACKAGE_ENTRY_LOCATION",
                    PackagerMessages.get("package.entryLocation"));
        }
        if (!entryScript.endsWith(".groovy")) {
            throw new PackageException("PACKAGE_ENTRY_TYPE",
                    PackagerMessages.get("package.entryType"));
        }
        for (String source : sources) {
            Path path = safePath(source, "sources");
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new PackageException("PACKAGE_SOURCE_MISSING",
                        PackagerMessages.format("package.sourceMissing", source));
            }
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && !source.endsWith(".groovy")) {
                throw new PackageException("PACKAGE_SOURCE_TYPE",
                        PackagerMessages.format("package.sourceType", source));
            }
            rejectNestedSymlinks(path, "sources");
        }
        for (String resource : resources) {
            Path path = safePath(resource, "resources");
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new PackageException("PACKAGE_RESOURCE_MISSING",
                        PackagerMessages.format("package.resourceMissing", resource));
            }
            rejectNestedSymlinks(path, "resources");
        }
    }

    private void rejectNestedSymlinks(Path root, String field) throws PackageException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path path : walk.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new PackageException("PACKAGE_PATH_UNSAFE",
                            PackagerMessages.format("package.pathSymlink", field, baseDirectory.relativize(path)));
                }
            }
        } catch (java.io.IOException exception) {
            throw new PackageException("PACKAGE_PATH_UNSAFE",
                    PackagerMessages.format("package.pathCheckFailed", field, root), exception);
        }
    }

    private Path safePath(String raw, String field) throws PackageException {
        Path relative;
        try {
            relative = Path.of(raw);
        } catch (RuntimeException exception) {
            throw new PackageException("PACKAGE_PATH_INVALID",
                    PackagerMessages.format("package.pathInvalid", field, raw), exception);
        }
        if (relative.isAbsolute() || relative.toString().isBlank()
                || relative.normalize().startsWith("..")
                || !relative.normalize().toString().equals(relative.toString())) {
            throw new PackageException("PACKAGE_PATH_INVALID",
                    PackagerMessages.format("package.pathNotNormalized", field, raw));
        }
        Path resolved = baseDirectory.resolve(relative).normalize();
        if (!resolved.startsWith(baseDirectory) || hasSymlink(resolved)) {
            throw new PackageException("PACKAGE_PATH_UNSAFE",
                    PackagerMessages.format("package.pathUnsafe", field, raw));
        }
        return resolved;
    }

    private boolean hasSymlink(Path path) {
        Path current = baseDirectory;
        Path relative = baseDirectory.relativize(path);
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }

    private static void rejectUnknown(Map<String, Object> object) throws PackageException {
        for (String key : object.keySet()) {
            if (!FIELDS.contains(key)) {
                throw new PackageException("PACKAGE_DESCRIPTOR_UNKNOWN_FIELD",
                        PackagerMessages.format("package.unknownField", key));
            }
        }
    }

    private static int integer(Map<String, Object> object, String key) throws PackageException {
        Object value = object.get(key);
        if (!(value instanceof Number number) || number.longValue() != number.doubleValue()
                || number.longValue() < Integer.MIN_VALUE || number.longValue() > Integer.MAX_VALUE) {
            throw new PackageException("PACKAGE_FIELD_TYPE",
                    PackagerMessages.format("package.fieldInteger", key));
        }
        return number.intValue();
    }

    private static String text(Map<String, Object> object, String key) throws PackageException {
        Object value = object.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new PackageException("PACKAGE_FIELD_TYPE",
                    PackagerMessages.format("package.fieldText", key));
        }
        return text;
    }

    private static List<String> textList(Map<String, Object> object, String key)
            throws PackageException {
        Object value = object.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new PackageException("PACKAGE_FIELD_TYPE",
                    PackagerMessages.format("package.fieldList", key));
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Object element : list) {
            if (!(element instanceof String text) || text.isBlank()) {
                throw new PackageException("PACKAGE_FIELD_TYPE",
                        PackagerMessages.format("package.fieldTextList", key));
            }
            if (!seen.add(text)) {
                throw new PackageException("PACKAGE_FIELD_DUPLICATE",
                        PackagerMessages.format("package.fieldDuplicate", key, text));
            }
            result.add(text);
        }
        return result;
    }

    private static List<Dependency> dependencies(Map<String, Object> object,
                                                  boolean allowSnapshots)
            throws PackageException {
        Object value = object.get("dependencies");
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new PackageException("PACKAGE_FIELD_TYPE",
                    PackagerMessages.get("package.dependenciesList"));
        }
        List<Dependency> result = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof String text)) {
                throw new PackageException("PACKAGE_DEPENDENCY_INVALID",
                        PackagerMessages.get("package.dependenciesText"));
            }
            String[] parts = text.split(":", -1);
            if ((parts.length != 3 && parts.length != 5) || !parts[0].matches("[A-Za-z0-9_.-]+")
                    || !parts[1].matches("[A-Za-z0-9_.-]+") || (parts.length == 5 && (!parts[2].equals("jar")
                    || !parts[3].matches("[A-Za-z0-9_.-]+")))) {
                throw new PackageException("PACKAGE_DEPENDENCY_INVALID",
                        PackagerMessages.format("package.dependencyFormat", text));
            }
            String version = parts[parts.length - 1];
            if (!VERSION.matcher(version).matches() || version.startsWith("[") || version.startsWith("(")
                    || version.equals("LATEST") || version.equals("RELEASE")
                    || (!allowSnapshots && version.toUpperCase(java.util.Locale.ROOT)
                    .endsWith("-SNAPSHOT"))
                    || version.contains("*")) {
                throw new PackageException("PACKAGE_DEPENDENCY_VERSION",
                        PackagerMessages.format("package.dependencyVersion", text));
            }
            Dependency dependency = new Dependency(parts[0], parts[1], version, parts.length == 5 ? parts[3] : "");
            if (result.stream().anyMatch(previous -> previous.key().equals(dependency.key())))
                throw new PackageException("PACKAGE_DEPENDENCY_DUPLICATE", "Duplicate/conflicting dependency: " + dependency.coordinate());
            result.add(dependency);
        }
        return result;
    }

    private static Map<String, Object> stringKeys(Map<?, ?> raw) throws PackageException {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new PackageException("PACKAGE_DESCRIPTOR_INVALID",
                        PackagerMessages.get("package.descriptorKeyText"));
            }
            result.put(key, entry.getValue());
        }
        return result;
    }
}
