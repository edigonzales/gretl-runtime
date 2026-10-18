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
        List<String> sources, List<String> resources, List<Dependency> dependencies) {

    private static final long MAX_DESCRIPTOR_BYTES = 1L << 20;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}");
    private static final Set<String> FIELDS = Set.of(
            "schemaVersion", "name", "version", "runtimeVersion", "entryScript",
            "sources", "resources", "dependencies");

    public record Dependency(String groupId, String artifactId, String version) {
        public String coordinate() {
            return groupId + ":" + artifactId + ":" + version;
        }
    }

    public PackageDescriptor {
        java.util.Objects.requireNonNull(descriptorFile, "descriptorFile");
        java.util.Objects.requireNonNull(baseDirectory, "baseDirectory");
        sources = List.copyOf(sources);
        resources = List.copyOf(resources);
        dependencies = List.copyOf(dependencies);
    }

    public static PackageDescriptor load(Path descriptor) throws PackageException {
        return load(descriptor, false);
    }

    public static PackageDescriptor load(Path descriptor, boolean allowSnapshots)
            throws PackageException {
        Path file = descriptor.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new PackageException("PACKAGE_DESCRIPTOR_MISSING",
                    "Paketdeskriptor nicht gefunden: " + file);
        }
        Object parsed;
        try {
            parsed = Json.parseFile(file, MAX_DESCRIPTOR_BYTES);
        } catch (IOException | RuntimeException exception) {
            throw new PackageException("PACKAGE_DESCRIPTOR_INVALID",
                    "Paketdeskriptor ist kein gueltiges JSON: " + exception.getMessage(), exception);
        }
        if (!(parsed instanceof Map<?, ?> raw)) {
            throw new PackageException("PACKAGE_DESCRIPTOR_INVALID",
                    "Paketdeskriptor muss ein JSON-Objekt sein");
        }
        Map<String, Object> object = stringKeys(raw);
        rejectUnknown(object);
        int schemaVersion = integer(object, "schemaVersion");
        if (schemaVersion != 1) {
            throw new PackageException("PACKAGE_DESCRIPTOR_VERSION",
                    "Unbekannte schemaVersion: " + schemaVersion);
        }
        String name = text(object, "name");
        if (!NAME.matcher(name).matches()) {
            throw new PackageException("PACKAGE_NAME_INVALID",
                    "name enthaelt unerlaubte Zeichen: " + name);
        }
        String version = text(object, "version");
        String runtimeVersion = text(object, "runtimeVersion");
        if (!VERSION.matcher(version).matches() || !VERSION.matcher(runtimeVersion).matches()) {
            throw new PackageException("PACKAGE_VERSION_INVALID",
                    "version und runtimeVersion muessen feste, nichtleere Versionen sein");
        }
        String entryScript = text(object, "entryScript");
        List<String> sources = textList(object, "sources");
        List<String> resources = textList(object, "resources");
        List<Dependency> dependencies = dependencies(object, allowSnapshots);

        Path base = file.getParent().toAbsolutePath().normalize();
        PackageDescriptor value = new PackageDescriptor(file, base, schemaVersion,
                name, version, runtimeVersion, entryScript, sources, resources, dependencies);
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
                    "entryScript ist keine regulaere Datei: " + entryScript);
        }
        if (!entry.getParent().equals(baseDirectory)) {
            throw new PackageException("PACKAGE_ENTRY_LOCATION",
                    "entryScript muss unmittelbar im Deskriptorverzeichnis liegen");
        }
        if (!entryScript.endsWith(".groovy")) {
            throw new PackageException("PACKAGE_ENTRY_TYPE",
                    "entryScript muss eine .groovy-Datei sein");
        }
        for (String source : sources) {
            Path path = safePath(source, "sources");
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new PackageException("PACKAGE_SOURCE_MISSING",
                        "Quelle nicht gefunden: " + source);
            }
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && !source.endsWith(".groovy")) {
                throw new PackageException("PACKAGE_SOURCE_TYPE",
                        "Quelldatei muss .groovy sein: " + source);
            }
            rejectNestedSymlinks(path, "sources");
        }
        for (String resource : resources) {
            Path path = safePath(resource, "resources");
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new PackageException("PACKAGE_RESOURCE_MISSING",
                        "Ressource nicht gefunden: " + resource);
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
                            field + " enthaelt einen Symlink: "
                                    + baseDirectory.relativize(path));
                }
            }
        } catch (java.io.IOException exception) {
            throw new PackageException("PACKAGE_PATH_UNSAFE",
                    field + " konnte nicht geprueft werden: " + root, exception);
        }
    }

    private Path safePath(String raw, String field) throws PackageException {
        Path relative;
        try {
            relative = Path.of(raw);
        } catch (RuntimeException exception) {
            throw new PackageException("PACKAGE_PATH_INVALID",
                    field + " ist kein gueltiger Pfad: " + raw, exception);
        }
        if (relative.isAbsolute() || relative.toString().isBlank()
                || relative.normalize().startsWith("..")
                || !relative.normalize().toString().equals(relative.toString())) {
            throw new PackageException("PACKAGE_PATH_INVALID",
                    field + " muss ein normalisierter relativer Pfad ohne '..' sein: " + raw);
        }
        Path resolved = baseDirectory.resolve(relative).normalize();
        if (!resolved.startsWith(baseDirectory) || hasSymlink(resolved)) {
            throw new PackageException("PACKAGE_PATH_UNSAFE",
                    field + " darf das Deskriptorverzeichnis nicht verlassen oder ein Symlink sein: "
                            + raw);
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
                        "Unbekanntes Deskriptorfeld: " + key);
            }
        }
    }

    private static int integer(Map<String, Object> object, String key) throws PackageException {
        Object value = object.get(key);
        if (!(value instanceof Number number) || number.longValue() != number.doubleValue()) {
            throw new PackageException("PACKAGE_FIELD_TYPE",
                    key + " muss eine Ganzzahl sein");
        }
        return number.intValue();
    }

    private static String text(Map<String, Object> object, String key) throws PackageException {
        Object value = object.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new PackageException("PACKAGE_FIELD_TYPE",
                    key + " muss ein nichtleerer Text sein");
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
                    key + " muss eine Liste sein");
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Object element : list) {
            if (!(element instanceof String text) || text.isBlank()) {
                throw new PackageException("PACKAGE_FIELD_TYPE",
                        key + " darf nur nichtleere Texte enthalten");
            }
            if (!seen.add(text)) {
                throw new PackageException("PACKAGE_FIELD_DUPLICATE",
                        key + " enthaelt " + text + " doppelt");
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
                    "dependencies muss eine Liste sein");
        }
        List<Dependency> result = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof String text)) {
                throw new PackageException("PACKAGE_DEPENDENCY_INVALID",
                        "dependencies darf nur Textkoordinaten enthalten");
            }
            String[] parts = text.split(":", -1);
            if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank()
                    || parts[2].isBlank()) {
                throw new PackageException("PACKAGE_DEPENDENCY_INVALID",
                        "Dependency muss group:artifact:version sein: " + text);
            }
            String version = parts[2];
            if (version.startsWith("[") || version.startsWith("(")
                    || version.equals("LATEST") || version.equals("RELEASE")
                    || (!allowSnapshots && version.toUpperCase(java.util.Locale.ROOT)
                    .endsWith("-SNAPSHOT"))
                    || version.contains("*")) {
                throw new PackageException("PACKAGE_DEPENDENCY_VERSION",
                        "Dependency benoetigt eine feste Version: " + text);
            }
            result.add(new Dependency(parts[0], parts[1], version));
        }
        return result;
    }

    private static Map<String, Object> stringKeys(Map<?, ?> raw) throws PackageException {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new PackageException("PACKAGE_DESCRIPTOR_INVALID",
                        "Deskriptorschluessel muessen Text sein");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }
}
