package ch.so.agi.gretl.packager;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict package descriptor; all paths stay below the descriptor directory. */
public record PackageDescriptor(
        Path baseDirectory,
        String name, String version, String runtimeVersion, String entryScript,
        List<String> sources, List<String> resources, List<Dependency> dependencies,
        String settingsFile) {

    private static final long MAX_DESCRIPTOR_BYTES = 1L << 20;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}");
    private static final Set<String> FIELDS = Set.of(
            "schemaVersion", "name", "version", "runtimeVersion", "entryScript",
            "sources", "resources", "dependencies", "settingsFile");

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
        java.util.Objects.requireNonNull(baseDirectory, "baseDirectory");
        sources = List.copyOf(sources);
        resources = List.copyOf(resources);
        dependencies = List.copyOf(dependencies);
    }

    public static PackageDescriptor load(Path descriptor) throws PackageException {
        return load(descriptor, false, Locale.GERMAN);
    }

    public static PackageDescriptor load(Path descriptor, boolean allowSnapshots, Locale locale)
            throws PackageException {
        Path file = descriptor.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new PackageException("PACKAGE_DESCRIPTOR_MISSING",
                    Messages.format(locale, "package.descriptorMissing", file));
        }
        Object parsed;
        try {
            parsed = Json.parseFile(file, MAX_DESCRIPTOR_BYTES);
        } catch (IOException | RuntimeException exception) {
            throw new PackageException("PACKAGE_DESCRIPTOR_INVALID",
                    Messages.format(locale, "package.descriptorInvalidJson", exception.getMessage()), exception);
        }
        if (!(parsed instanceof Map<?, ?> raw)) {
            throw new PackageException("PACKAGE_DESCRIPTOR_INVALID",
                    Messages.get(locale, "package.descriptorObject"));
        }
        Map<String, Object> object = stringKeys(locale, raw);
        rejectUnknown(locale, object);
        int schemaVersion = integer(locale, object, "schemaVersion");
        if (schemaVersion != 1) {
            throw new PackageException("PACKAGE_DESCRIPTOR_VERSION",
                    Messages.format(locale, "package.descriptorVersion", schemaVersion));
        }
        String name = text(locale, object, "name");
        if (!NAME.matcher(name).matches()) {
            throw new PackageException("PACKAGE_NAME_INVALID",
                    Messages.format(locale, "package.nameInvalid", name));
        }
        String version = text(locale, object, "version");
        String runtimeVersion = text(locale, object, "runtimeVersion");
        if (!VERSION.matcher(version).matches() || !VERSION.matcher(runtimeVersion).matches()) {
            throw new PackageException("PACKAGE_VERSION_INVALID",
                    Messages.get(locale, "package.versionInvalid"));
        }
        boolean project = object.containsKey("settingsFile");
        if (project == object.containsKey("entryScript")) {
            throw new PackageException("PACKAGE_DESCRIPTOR_SCHEMA", Messages.get(locale, "package.project.schema"));
        }
        String entryScript = project ? null : text(locale, object, "entryScript");
        String settingsFile = project ? text(locale, object, "settingsFile") : null;
        if (project && !"settings.gretl".equals(settingsFile)) {
            throw new PackageException("PACKAGE_SETTINGS_INVALID", Messages.get(locale, "package.project.settings"));
        }
        List<String> sources = textList(locale, object, "sources");
        List<String> resources = textList(locale, object, "resources");
        List<Dependency> dependencies = dependencies(locale, object, allowSnapshots);
        Path base = file.getParent().toAbsolutePath().normalize();
        PackageDescriptor value = new PackageDescriptor(base,
                name, version, runtimeVersion, entryScript, sources, resources, dependencies, settingsFile);
        value.validate(locale);
        return value;
    }

    public boolean projectPackage() { return settingsFile != null; }

    public Path entryPath() {
        if (projectPackage()) throw new IllegalStateException("Projects have no single entryScript");
        return baseDirectory.resolve(entryScript).normalize();
    }

    public List<Path> sourcePaths() {
        return sources.stream().map(value -> baseDirectory.resolve(value).normalize()).toList();
    }

    public List<Path> resourcePaths() {
        return resources.stream().map(value -> baseDirectory.resolve(value).normalize()).toList();
    }

    private void validate(Locale locale) throws PackageException {
        if (projectPackage()) {
            Path settings = safePath(locale, settingsFile, "settingsFile");
            if (!Files.isRegularFile(settings, LinkOption.NOFOLLOW_LINKS)) {
                throw new PackageException("PROJECT_SETTINGS_MISSING", Messages.format(locale, "package.project.settingsMissing", settings));
            }
        } else {
            Path entry = safePath(locale, entryScript, "entryScript");
            if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                throw new PackageException("PACKAGE_ENTRY_MISSING",
                        Messages.format(locale, "package.entryMissing", entryScript));
            }
            if (!entry.getParent().equals(baseDirectory)) {
                throw new PackageException("PACKAGE_ENTRY_LOCATION",
                        Messages.get(locale, "package.entryLocation"));
            }
            if (!entryScript.endsWith(".groovy")) {
                throw new PackageException("PACKAGE_ENTRY_TYPE",
                        Messages.get(locale, "package.entryType"));
            }
        }
        for (String source : sources) {
            Path path = safePath(locale, source, "sources");
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new PackageException("PACKAGE_SOURCE_MISSING",
                        Messages.format(locale, "package.sourceMissing", source));
            }
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && !source.endsWith(".groovy")) {
                throw new PackageException("PACKAGE_SOURCE_TYPE",
                        Messages.format(locale, "package.sourceType", source));
            }
            rejectNestedSymlinks(locale, path, "sources");
        }
        for (String resource : resources) {
            Path path = safePath(locale, resource, "resources");
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new PackageException("PACKAGE_RESOURCE_MISSING",
                        Messages.format(locale, "package.resourceMissing", resource));
            }
            rejectNestedSymlinks(locale, path, "resources");
        }
    }

    private void rejectNestedSymlinks(Locale locale, Path root, String field) throws PackageException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path path : walk.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new PackageException("PACKAGE_PATH_UNSAFE",
                            Messages.format(locale, "package.pathSymlink", field, baseDirectory.relativize(path)));
                }
            }
        } catch (java.io.IOException exception) {
            throw new PackageException("PACKAGE_PATH_UNSAFE",
                    Messages.format(locale, "package.pathCheckFailed", field, root), exception);
        }
    }

    private Path safePath(Locale locale, String raw, String field) throws PackageException {
        Path relative;
        try {
            relative = Path.of(raw);
        } catch (RuntimeException exception) {
            throw new PackageException("PACKAGE_PATH_INVALID",
                    Messages.format(locale, "package.pathInvalid", field, raw), exception);
        }
        if (raw.contains("\\") || raw.contains(":") || raw.chars().anyMatch(Character::isISOControl)
                || relative.isAbsolute() || relative.toString().isBlank()
                || relative.normalize().startsWith("..")
                || !relative.normalize().toString().replace('\\', '/').equals(raw)) {
            throw new PackageException("PACKAGE_PATH_INVALID",
                    Messages.format(locale, "package.pathNotNormalized", field, raw));
        }
        Path resolved = baseDirectory.resolve(relative).normalize();
        if (!resolved.startsWith(baseDirectory) || hasSymlink(resolved)) {
            throw new PackageException("PACKAGE_PATH_UNSAFE",
                    Messages.format(locale, "package.pathUnsafe", field, raw));
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

    private static void rejectUnknown(Locale locale, Map<String, Object> object) throws PackageException {
        for (String key : object.keySet()) {
            if (!FIELDS.contains(key)) {
                throw new PackageException("PACKAGE_DESCRIPTOR_UNKNOWN_FIELD",
                        Messages.format(locale, "package.unknownField", key));
            }
        }
    }

    private static int integer(Locale locale, Map<String, Object> object, String key) throws PackageException {
        Object value = object.get(key);
        if (!(value instanceof Number number) || number.longValue() != number.doubleValue()
                || number.longValue() < Integer.MIN_VALUE || number.longValue() > Integer.MAX_VALUE) {
            throw new PackageException("PACKAGE_FIELD_TYPE",
                    Messages.format(locale, "package.fieldInteger", key));
        }
        return number.intValue();
    }

    private static String text(Locale locale, Map<String, Object> object, String key) throws PackageException {
        Object value = object.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new PackageException("PACKAGE_FIELD_TYPE",
                    Messages.format(locale, "package.fieldText", key));
        }
        return text;
    }

    private static List<String> textList(Locale locale, Map<String, Object> object, String key)
            throws PackageException {
        Object value = object.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new PackageException("PACKAGE_FIELD_TYPE",
                    Messages.format(locale, "package.fieldList", key));
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Object element : list) {
            if (!(element instanceof String text) || text.isBlank()) {
                throw new PackageException("PACKAGE_FIELD_TYPE",
                        Messages.format(locale, "package.fieldTextList", key));
            }
            if (!seen.add(text)) {
                throw new PackageException("PACKAGE_FIELD_DUPLICATE",
                        Messages.format(locale, "package.fieldDuplicate", key, text));
            }
            result.add(text);
        }
        return result;
    }

    private static List<Dependency> dependencies(Locale locale, Map<String, Object> object,
                                                  boolean allowSnapshots)
            throws PackageException {
        Object value = object.get("dependencies");
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new PackageException("PACKAGE_FIELD_TYPE",
                    Messages.get(locale, "package.dependenciesList"));
        }
        List<Dependency> result = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof String text)) {
                throw new PackageException("PACKAGE_DEPENDENCY_INVALID",
                        Messages.get(locale, "package.dependenciesText"));
            }
            String[] parts = text.split(":", -1);
            if ((parts.length != 3 && parts.length != 5) || !parts[0].matches("[A-Za-z0-9_.-]+")
                    || !parts[1].matches("[A-Za-z0-9_.-]+") || (parts.length == 5 && (!parts[2].equals("jar")
                    || !parts[3].matches("[A-Za-z0-9_.-]+")))) {
                throw new PackageException("PACKAGE_DEPENDENCY_INVALID",
                        Messages.format(locale, "package.dependencyFormat", text));
            }
            String version = parts[parts.length - 1];
            if (!VERSION.matcher(version).matches() || version.startsWith("[") || version.startsWith("(")
                    || version.equals("LATEST") || version.equals("RELEASE")
                    || (!allowSnapshots && version.toUpperCase(java.util.Locale.ROOT)
                    .endsWith("-SNAPSHOT"))
                    || version.contains("*")) {
                throw new PackageException("PACKAGE_DEPENDENCY_VERSION",
                        Messages.format(locale, "package.dependencyVersion", text));
            }
            Dependency dependency = new Dependency(parts[0], parts[1], version, parts.length == 5 ? parts[3] : "");
            if (result.stream().anyMatch(previous -> previous.key().equals(dependency.key())))
                throw new PackageException("PACKAGE_DEPENDENCY_DUPLICATE", "Duplicate/conflicting dependency: " + dependency.coordinate());
            result.add(dependency);
        }
        return result;
    }

    private static Map<String, Object> stringKeys(Locale locale, Map<?, ?> raw) throws PackageException {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new PackageException("PACKAGE_DESCRIPTOR_INVALID",
                        Messages.get(locale, "package.descriptorKeyText"));
            }
            result.put(key, entry.getValue());
        }
        return result;
    }
}
