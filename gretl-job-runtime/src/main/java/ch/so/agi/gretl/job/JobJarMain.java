package ch.so.agi.gretl.job;

import ch.so.agi.gretl.job.runtime.JobSource;

import ch.so.agi.gretl.job.runtime.JobRuntime;
import ch.so.agi.gretl.i18n.LocaleSelection;
import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.job.runtime.RuntimeInfo;
import ch.so.agi.gretl.runtime.groovy.DefinitionEnvironment;
import ch.so.agi.gretl.runtime.groovy.GroovyJobLoader;
import ch.so.agi.gretl.runtime.groovy.UserMessageSink;
import ch.so.agi.gretl.runtime.groovy.project.CompiledProjectScript;
import ch.so.agi.gretl.runtime.groovy.project.ProjectPath;
import ch.so.agi.gretl.runtime.groovy.project.ProjectSourceNames;
import ch.so.agi.gretl.runtime.logging.Json;
import ch.so.agi.gretl.runtime.logging.TextFormatter;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Entry point of a precompiled GRETL job JAR. */
public final class JobJarMain {
    public static final String METADATA_RESOURCE = "META-INF/gretl/job.json";
    public static final String RESOURCE_ROOT = "META-INF/gretl/job-resources/";

    private JobJarMain() {
    }

    public static void main(String[] args) {
        Locale locale = LocaleSelection.resolve(requestedLanguage(args),
                System.getenv("GRETL_LANGUAGE"), null, Locale.getDefault());
        JobMetadata metadata;
        try {
            metadata = JobMetadata.load(JobJarMain.class.getClassLoader(), locale);
        } catch (JobJarException exception) {
            startupError(exception, locale);
            System.exit(2);
            return;
        }
        if (metadata.javaFeatureVersion() != 25) {
            startupError(new JobJarException("JOB_METADATA_RUNTIME",
                    Messages.get(locale, "job.error.java")), locale);
            System.exit(2);
            return;
        }
        if (!RuntimeInfo.VERSION.equals(metadata.runtimeVersion())) {
            startupError(new JobJarException("JOB_METADATA_RUNTIME",
                    Messages.format(locale, "job.error.runtime",
                            metadata.runtimeVersion(), RuntimeInfo.VERSION)), locale);
            System.exit(2);
            return;
        }
        if (args.length == 1 && (args[0].equals("--version") || args[0].equals("-V"))) {
            System.out.println(metadata.name() + " " + metadata.version()
                    + " (runtime " + metadata.runtimeVersion() + ")");
            return;
        }
        // Registration needs a logical root; the runner prepares packaged resources.
        Path definitionRoot = Path.of("").toAbsolutePath().normalize();
        JobSource source = metadata.schemaVersion() == 2
                ? new PackagedProjectJobSource(metadata, definitionRoot, JobJarMain.class.getClassLoader(), locale)
                : new PackagedJobSource(metadata, definitionRoot, JobJarMain.class.getClassLoader(), locale);
        JobRuntime runtime = new JobRuntime(System.out, System.err, System.getenv(),
                definitionRoot, JobJarMain.class.getClassLoader());
        System.exit(runtime.executePackaged(source, args));
    }

    private static String requestedLanguage(String[] args) {
        for (int index = 0; index < args.length - 1; index++) {
            if (args[index].equals("--lang")) {
                return args[index + 1];
            }
        }
        return null;
    }

    private static Path materialize(JobMetadata metadata, Locale locale, Path runRoot,
                                    ClassLoader classLoader) throws IOException {
        try {
            FileAttribute<?>[] permissions = Files.getFileStore(runRoot)
                    .supportsFileAttributeView("posix")
                    ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rwx------"))}
                    : new FileAttribute<?>[0];
            Path root = Files.createDirectory(runRoot.resolve("job-resources"), permissions);
            for (String file : metadata.resourceFiles()) {
                String normalized;
                if (metadata.schemaVersion() == 2) {
                    try { JobMetadata.projectRelative(file, locale); }
                    catch (IllegalArgumentException failure) { throw new JobJarException("JOB_RESOURCE_PATH", failure.getMessage(), failure); }
                    normalized = file;
                } else normalized = safeRelative(file, locale);
                Path target = root.resolve(normalized).normalize();
                if (!target.startsWith(root)) {
                    throw new JobJarException("JOB_RESOURCE_PATH",
                            Messages.format(locale, "job.error.resourceEscapes", file));
                }
                Files.createDirectories(target.getParent(), permissions);
                try (InputStream input = classLoader
                        .getResourceAsStream(RESOURCE_ROOT + normalized)) {
                    if (input == null) {
                        throw new JobJarException("JOB_RESOURCE_MISSING",
                                Messages.format(locale, "job.error.resourceMissing",
                                        normalized));
                    }
                    Files.copy(input, target);
                }
            }
            return root;
        } catch (JobJarException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
    }

    private static String safeRelative(String value, Locale locale) throws JobJarException {
        if (value == null || value.isBlank() || value.startsWith("/")
                || value.contains("..") || value.contains("\\")) {
            throw new JobJarException("JOB_RESOURCE_PATH",
                    Messages.format(locale, "job.error.resourcePath", value));
        }
        return value;
    }

    private static void startupError(JobJarException exception, Locale locale) {
        System.err.println(new TextFormatter(locale).formatProblem(Instant.now(),
                "JOB_JAR_STARTUP", null, exception.getMessage(), Map.of()));
    }

    public record JobMetadata(
            int schemaVersion, String name, String version, String runtimeVersion,
            int javaFeatureVersion, String entryClass, String entryScript,
            String resourceRoot, List<String> resourceFiles,
            List<String> sourceFiles, String scriptSha256,
            List<CompiledProjectScript> projectEntries, List<String> defaultTargets) {

        public JobMetadata(int schemaVersion, String name, String version, String runtimeVersion,
                           int javaFeatureVersion, String entryClass, String entryScript, String resourceRoot,
                           List<String> resourceFiles, List<String> sourceFiles, String scriptSha256) {
            this(schemaVersion, name, version, runtimeVersion, javaFeatureVersion, entryClass, entryScript,
                    resourceRoot, resourceFiles, sourceFiles, scriptSha256, List.of(), List.of());
        }

        public JobMetadata {
            resourceFiles = List.copyOf(resourceFiles);
            sourceFiles = List.copyOf(sourceFiles);
            projectEntries = List.copyOf(projectEntries); defaultTargets = List.copyOf(defaultTargets);
        }

        static JobMetadata load(ClassLoader loader, Locale locale) throws JobJarException {
            try (InputStream input = loader.getResourceAsStream(METADATA_RESOURCE)) {
                if (input == null) {
                    throw new JobJarException("JOB_METADATA_MISSING",
                            Messages.format(locale, "job.error.metadataMissing",
                                    METADATA_RESOURCE));
                }
                Object parsed = Json.parse(new String(input.readAllBytes(),
                        StandardCharsets.UTF_8));
                if (!(parsed instanceof Map<?, ?> raw)) {
                    throw new JobJarException("JOB_METADATA_INVALID",
                            Messages.get(locale, "job.error.metadataObject"));
                }
                Map<String, Object> value = new java.util.LinkedHashMap<>();
                raw.forEach((key, item) -> value.put(String.valueOf(key), item));
                int schema = integer(value, "schemaVersion", locale);
                if (schema == 2) return projectMetadata(value, locale);
                if (schema != 1) {
                    throw new JobJarException("JOB_METADATA_VERSION",
                            Messages.format(locale, "job.error.metadataVersion", schema));
                }
                return new JobMetadata(schema, text(value, "name", locale),
                        text(value, "version", locale), text(value, "runtimeVersion", locale),
                        integer(value, "javaFeatureVersion", locale),
                        text(value, "entryClass", locale), text(value, "entryScript", locale),
                        text(value, "resourceRoot", locale),
                        strings(value, "resourceFiles", locale),
                        strings(value, "sourceFiles", locale),
                        text(value, "scriptSha256", locale));
            } catch (IllegalArgumentException exception) {
                throw new JobJarException("JOB_METADATA_INVALID", Messages.format(locale, "job.project.syntax", exception.getMessage()), exception);
            } catch (IOException exception) {
                throw new JobJarException("JOB_METADATA_READ",
                        Messages.get(locale, "job.error.metadataRead"), exception);
            }
        }

        private static JobMetadata projectMetadata(Map<String, Object> value, Locale locale) throws JobJarException {
            try {
                var fields = java.util.Set.of("schemaVersion", "name", "version", "runtimeVersion", "javaFeatureVersion",
                        "project", "settingsFile", "defaultTargets", "entries", "resourceRoot", "resourceFiles", "sourceFiles",
                        "projectSha256", "dependencyInventory", "packagerVersion");
                if (!(value.get("schemaVersion") instanceof Number schema) || schema.doubleValue() != 2
                        || !fields.containsAll(value.keySet()) || !Boolean.TRUE.equals(value.get("project"))
                        || !"settings.gretl".equals(text(value, "settingsFile", locale))
                        || !RESOURCE_ROOT.equals(text(value, "resourceRoot", locale))
                        || !"META-INF/gretl/dependencies.json".equals(text(value, "dependencyInventory", locale)))
                    throw new IllegalArgumentException(Messages.get(locale, "job.project.fields"));
                String hash = text(value, "projectSha256", locale);
                if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(Messages.get(locale, "job.project.hash"));
                List<String> resources = uniqueStrings(value, "resourceFiles", locale);
                List<String> sources = uniqueStrings(value, "sourceFiles", locale);
                for (String path : resources) projectRelative(path, locale);
                for (String path : sources) projectRelative(path, locale);
                if (!sources.contains("settings.gretl")) throw new IllegalArgumentException(Messages.get(locale, "job.project.settingsSource"));
                List<String> defaults = uniqueStrings(value, "defaultTargets", locale);
                for (String target : defaults) if (ProjectPath.parseQualified(target).isRoot())
                    throw new IllegalArgumentException(Messages.get(locale, "job.project.default"));
                if (!(value.get("entries") instanceof List<?> entries) || entries.isEmpty())
                    throw new IllegalArgumentException(Messages.get(locale, "job.project.entries"));
                var scripts = new ArrayList<CompiledProjectScript>();
                var paths = new java.util.HashSet<ProjectPath>(); var classes = new java.util.HashSet<String>();
                for (Object item : entries) {
                    if (!(item instanceof Map<?, ?> entry) || !entry.keySet().equals(java.util.Set.of("projectPath", "entryScript", "entryClass")))
                        throw new IllegalArgumentException(Messages.get(locale, "job.project.entryFields"));
                    if (!(entry.get("projectPath") instanceof String path) || !(entry.get("entryClass") instanceof String type)
                            || !(entry.get("entryScript") instanceof String script)) throw new IllegalArgumentException(Messages.get(locale, "job.project.entryTypes"));
                    ProjectPath project = ProjectPath.parseQualified(path);
                    projectRelative(script, locale);
                    if (!paths.add(project) || !classes.add(type) || !type.matches("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(?:\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*")
                            || !sources.contains(script) || (project.isRoot() && !scripts.isEmpty()))
                        throw new IllegalArgumentException(Messages.format(locale, "job.project.entryInvalid", path));
                    scripts.add(new CompiledProjectScript(project, type, script, ProjectSourceNames.virtualGroovyName(project)));
                }
                if (value.containsKey("packagerVersion")) text(value, "packagerVersion", locale);
                Object feature = value.get("javaFeatureVersion");
                if (!(feature instanceof Number number) || number.doubleValue() != number.intValue())
                    throw new IllegalArgumentException(Messages.get(locale, "job.project.javaInteger"));
                return new JobMetadata(2, text(value, "name", locale), text(value, "version", locale),
                        text(value, "runtimeVersion", locale), integer(value, "javaFeatureVersion", locale),
                        null, "settings.gretl", RESOURCE_ROOT, resources, sources, hash, scripts, defaults);
            } catch (IllegalArgumentException failure) {
                throw new JobJarException("JOB_METADATA_INVALID", failure.getMessage(), failure);
            }
        }

        private static List<String> uniqueStrings(Map<String, Object> value, String key, Locale locale) throws JobJarException {
            List<String> list = strings(value, key, locale);
            if (new java.util.HashSet<>(list).size() != list.size()) throw new JobJarException("JOB_METADATA_INVALID", Messages.format(locale, "job.project.duplicate", key));
            return list;
        }

        private static void projectRelative(String value, Locale locale) {
            if (value.isBlank() || value.contains("\\") || value.contains(":") || value.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException(Messages.format(locale, "job.project.path", value));
            Path path = Path.of(value);
            if (path.isAbsolute() || path.startsWith("..") || !path.normalize().toString().replace('\\', '/').equals(value)
                    || java.util.Arrays.asList(value.split("/", -1)).contains(""))
                throw new IllegalArgumentException(Messages.format(locale, "job.project.path", value));
        }

        private static String text(Map<String, Object> value, String key, Locale locale)
                throws JobJarException {
            Object item = value.get(key);
            if (!(item instanceof String text) || text.isBlank()) {
                throw new JobJarException("JOB_METADATA_INVALID",
                        Messages.format(locale, "job.error.metadataFieldMissing", key));
            }
            return text;
        }

        private static int integer(Map<String, Object> value, String key, Locale locale)
                throws JobJarException {
            Object item = value.get(key);
            if (!(item instanceof Number number)) {
                throw new JobJarException("JOB_METADATA_INVALID",
                        Messages.format(locale, "job.error.metadataFieldNumber", key));
            }
            return number.intValue();
        }

        private static List<String> strings(Map<String, Object> value, String key,
                                            Locale locale)
                throws JobJarException {
            Object item = value.get(key);
            if (!(item instanceof List<?> list)) {
                throw new JobJarException("JOB_METADATA_INVALID",
                        Messages.format(locale, "job.error.metadataFieldList", key));
            }
            List<String> result = new ArrayList<>();
            for (Object element : list) {
                if (!(element instanceof String text)) {
                    throw new JobJarException("JOB_METADATA_INVALID",
                            Messages.format(locale, "job.error.metadataListText", key));
                }
                result.add(text);
            }
            return result;
        }
    }

    static class PackagedJobSource implements ch.so.agi.gretl.job.runtime.JobSource {
        final JobMetadata metadata;
        final Path jobRoot;
        final ClassLoader classLoader;
        final Locale locale;

        PackagedJobSource(JobMetadata metadata, Path jobRoot, ClassLoader classLoader,
                          Locale locale) {
            this.metadata = metadata;
            this.jobRoot = jobRoot;
            this.classLoader = classLoader;
            this.locale = locale;
        }

        @Override
        public Path displayFile() {
            return Path.of(metadata.entryScript());
        }

        @Override
        public Path jobRoot() {
            return jobRoot;
        }

        @Override
        public Path prepareRun(Path runRoot) throws IOException {
            return materialize(metadata, locale, runRoot, classLoader);
        }

        @Override
        public String jobName() {
            return metadata.name();
        }

        @Override
        public String sha256() {
            return metadata.scriptSha256();
        }

        @Override
        public JobDefinition load(OperationRegistry registry, Path startCwd,
                                  UserMessageSink sink) {
            DefinitionEnvironment environment = new DefinitionEnvironment(
                    registry, jobRoot, startCwd, sink, locale);
            return new GroovyJobLoader().loadCompiled(metadata.entryClass(),
                    displayFile(), environment, classLoader);
        }
    }

    static final class PackagedProjectJobSource extends PackagedJobSource {
        PackagedProjectJobSource(JobMetadata metadata, Path root, ClassLoader loader, Locale locale) {
            super(metadata, root, loader, locale);
        }
        @Override public boolean projectMode() { return true; }
        @Override public JobDefinition load(OperationRegistry registry, Path startCwd, UserMessageSink sink) {
            return new GroovyJobLoader().loadCompiledProject(metadata.projectEntries(), metadata.defaultTargets(),
                    new DefinitionEnvironment(registry, jobRoot, startCwd, sink, locale), classLoader);
        }
    }

    static final class JobJarException extends Exception {
        private static final long serialVersionUID = 1L;
        private final String code;

        JobJarException(String code, String message) {
            super(code + ": " + message);
            this.code = code;
        }

        JobJarException(String code, String message, Throwable cause) {
            super(code + ": " + message, cause);
            this.code = code;
        }
    }
}
