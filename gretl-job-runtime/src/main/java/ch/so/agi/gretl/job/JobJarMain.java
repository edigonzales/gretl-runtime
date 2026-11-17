package ch.so.agi.gretl.job;

import ch.so.agi.gretl.job.runtime.JobSource;
import ch.so.agi.gretl.job.runtime.JobOptions;
import ch.so.agi.gretl.job.runtime.StartException;

import ch.so.agi.gretl.job.runtime.JobRuntime;
import ch.so.agi.gretl.i18n.LocaleSelection;
import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.job.runtime.RuntimeInfo;
import ch.so.agi.gretl.runtime.groovy.DefinitionEnvironment;
import ch.so.agi.gretl.runtime.groovy.GroovyJobLoader;
import ch.so.agi.gretl.runtime.groovy.UserMessageSink;
import ch.so.agi.gretl.runtime.groovy.fragment.CompiledScriptBundle;
import ch.so.agi.gretl.runtime.logging.Json;
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
        Locale locale = LocaleSelection.resolve(JobOptions.requestedLanguage(args),
                System.getenv("GRETL_LANGUAGE"), null, Locale.getDefault());
        JobMetadata metadata;
        try {
            metadata = JobMetadata.load(JobJarMain.class.getClassLoader(), locale);
        } catch (JobJarException exception) {
            startupError(exception, args, locale);
            System.exit(2);
            return;
        }
        if (!RuntimeInfo.VERSION.equals(metadata.runtimeVersion())) {
            startupError(new JobJarException("JOB_METADATA_RUNTIME",
                    Messages.format(locale, "job.error.runtime",
                            metadata.runtimeVersion(), RuntimeInfo.VERSION)), args, locale);
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
        JobSource source = new PackagedJobSource(metadata, definitionRoot, JobJarMain.class.getClassLoader());
        JobRuntime runtime = new JobRuntime(System.out, System.err, System.getenv(),
                definitionRoot, JobJarMain.class.getClassLoader());
        System.exit(runtime.executePackaged(source, args));
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
                Path target = root.resolve(file).normalize();
                if (!target.startsWith(root)) {
                    throw new JobJarException("JOB_RESOURCE_PATH",
                            Messages.format(locale, "job.error.resourceEscapes", file));
                }
                Files.createDirectories(target.getParent(), permissions);
                try (InputStream input = classLoader
                        .getResourceAsStream(RESOURCE_ROOT + file)) {
                    if (input == null) {
                        throw new JobJarException("JOB_RESOURCE_MISSING",
                                Messages.format(locale, "job.error.resourceMissing",
                                        file));
                    }
                    Files.copy(input, target);
                }
            }
            return root;
        } catch (JobJarException exception) {
            throw new IOException(exception.code + ": " + exception.getMessage(), exception);
        }
    }

    private static void startupError(JobJarException failure, String[] args, Locale locale) {
        StartException.report(new StartException(2, failure.getMessage(), failure, failure.code, null, null, null),
                System.err, Instant.now(), java.util.UUID.randomUUID().toString(), locale, JobOptions.jsonRequested(args));
    }

    record JobMetadata(String name, String version, String runtimeVersion,
                       List<String> resourceFiles, CompiledScriptBundle bundle) {

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
                if (schema != 1) {
                    throw new JobJarException("JOB_METADATA_VERSION",
                            Messages.format(locale, "job.error.metadataVersion", schema));
                }
                if (!value.keySet().equals(java.util.Set.of("schemaVersion", "name", "version",
                        "runtimeVersion", "bundle", "resourceFiles"))) {
                    throw new IllegalArgumentException("Invalid job metadata fields");
                }
                List<String> resources = uniqueStrings(value, "resourceFiles", locale);
                resources.forEach(path -> validateResourcePath(path, locale));
                return new JobMetadata(text(value, "name", locale), text(value, "version", locale),
                        text(value, "runtimeVersion", locale), resources, CompiledScriptBundle.parse(value.get("bundle")));
            } catch (IllegalArgumentException exception) {
                throw new JobJarException("JOB_METADATA_INVALID", Messages.format(locale, "job.error.metadataInvalid", exception.getMessage()), exception);
            } catch (IOException exception) {
                throw new JobJarException("JOB_METADATA_READ",
                        Messages.get(locale, "job.error.metadataRead"), exception);
            }
        }

        private static List<String> uniqueStrings(Map<String, Object> value, String key, Locale locale) throws JobJarException {
            List<String> list = strings(value, key, locale);
            if (new java.util.HashSet<>(list).size() != list.size()) throw new JobJarException("JOB_METADATA_INVALID", Messages.format(locale, "job.project.duplicate", key));
            return list;
        }

        private static void validateResourcePath(String value, Locale locale) {
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
            if (!(item instanceof Number number) || number.doubleValue() != number.intValue()) {
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

    static final class PackagedJobSource implements JobSource {
        final JobMetadata metadata;
        final Path jobRoot;
        final ClassLoader classLoader;

        PackagedJobSource(JobMetadata metadata, Path jobRoot, ClassLoader classLoader) {
            this.metadata = metadata;
            this.jobRoot = jobRoot;
            this.classLoader = classLoader;
        }

        @Override
        public Path displayFile() {
            var bundle = metadata.bundle();
            return Path.of(bundle.projectMode() ? bundle.settingsId() : bundle.entries().getFirst().sourceId());
        }

        @Override
        public Path jobRoot() {
            return jobRoot;
        }

        @Override
        public Path prepareRun(Path runRoot, Locale locale) throws IOException {
            return materialize(metadata, locale, runRoot, classLoader);
        }

        @Override
        public String jobName() {
            return metadata.name();
        }

        @Override
        public String sha256(Locale locale) {
            return metadata.bundle().sha256();
        }

        @Override
        public JobDefinition load(OperationRegistry registry, Path startCwd,
                                  UserMessageSink sink, Locale locale) {
            DefinitionEnvironment environment = new DefinitionEnvironment(
                    registry, jobRoot, startCwd, sink, locale);
            return new GroovyJobLoader().loadCompiledBundle(metadata.bundle(), environment, classLoader);
        }

        @Override
        public boolean projectMode() {
            return metadata.bundle().projectMode();
        }
    }

    static final class JobJarException extends Exception {
        private static final long serialVersionUID = 1L;
        private final String code;

        JobJarException(String code, String message) {
            super(message);
            this.code = code;
        }

        JobJarException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }
    }
}
