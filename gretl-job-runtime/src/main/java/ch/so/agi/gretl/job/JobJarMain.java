package ch.so.agi.gretl.job;

import ch.so.agi.gretl.cli.GretlCli;
import ch.so.agi.gretl.cli.RuntimeInfo;
import ch.so.agi.gretl.runtime.groovy.DefinitionEnvironment;
import ch.so.agi.gretl.runtime.groovy.GroovyJobLoader;
import ch.so.agi.gretl.runtime.groovy.UserMessageSink;
import ch.so.agi.gretl.runtime.logging.Json;
import ch.so.agi.gretl.runtime.logging.TextFormatter;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Entry point of a precompiled GRETL job JAR. */
public final class JobJarMain {
    public static final String METADATA_RESOURCE = "META-INF/gretl/job.json";
    public static final String RESOURCE_ROOT = "META-INF/gretl/job-resources/";

    private JobJarMain() {
    }

    public static void main(String[] args) {
        JobMetadata metadata;
        try {
            metadata = JobMetadata.load(JobJarMain.class.getClassLoader());
        } catch (JobJarException exception) {
            startupError(exception);
            System.exit(2);
            return;
        }
        if (metadata.javaFeatureVersion() != 25) {
            startupError(new JobJarException("JOB_METADATA_RUNTIME",
                    "Jobpaket benoetigt Java 25"));
            System.exit(2);
            return;
        }
        if (!RuntimeInfo.VERSION.equals(metadata.runtimeVersion())) {
            startupError(new JobJarException("JOB_METADATA_RUNTIME",
                    "Jobpaket wurde fuer Runtime " + metadata.runtimeVersion()
                            + " gebaut, vorhanden ist " + RuntimeInfo.VERSION));
            System.exit(2);
            return;
        }
        if (args.length == 1 && (args[0].equals("--version") || args[0].equals("-V"))) {
            System.out.println(metadata.name() + " " + metadata.version()
                    + " (runtime " + metadata.runtimeVersion() + ")");
            return;
        }
        Path jobRoot = null;
        int exit = 2;
        try {
            jobRoot = materialize(metadata);
            PackagedJobSource source = new PackagedJobSource(metadata, jobRoot,
                    JobJarMain.class.getClassLoader());
            GretlCli cli = new GretlCli(System.out, System.err, System.getenv(),
                    Path.of("").toAbsolutePath().normalize(),
                    JobJarMain.class.getClassLoader());
            exit = cli.runPackaged(source, args);
        } catch (JobJarException exception) {
            startupError(exception);
        } finally {
            if (jobRoot != null) {
                deleteQuietly(jobRoot);
            }
        }
        System.exit(exit);
    }

    private static Path materialize(JobMetadata metadata) throws JobJarException {
        try {
            Path root = Files.createTempDirectory("gretl-job-");
            for (String file : metadata.resourceFiles()) {
                String normalized = safeRelative(file);
                Path target = root.resolve(normalized).normalize();
                if (!target.startsWith(root)) {
                    throw new JobJarException("JOB_RESOURCE_PATH",
                            "Ressourcenpfad verlaesst den Jobroot: " + file);
                }
                Files.createDirectories(target.getParent());
                try (InputStream input = JobJarMain.class.getClassLoader()
                        .getResourceAsStream(RESOURCE_ROOT + normalized)) {
                    if (input == null) {
                        throw new JobJarException("JOB_RESOURCE_MISSING",
                                "Paketressource fehlt: " + normalized);
                    }
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return root;
        } catch (IOException exception) {
            throw new JobJarException("JOB_RESOURCE_IO",
                    "Paketressourcen konnten nicht materialisiert werden", exception);
        }
    }

    private static String safeRelative(String value) throws JobJarException {
        if (value == null || value.isBlank() || value.startsWith("/")
                || value.contains("..") || value.contains("\\")) {
            throw new JobJarException("JOB_RESOURCE_PATH",
                    "Ungueltiger Paketressourcenpfad: " + value);
        }
        return value;
    }

    private static void startupError(JobJarException exception) {
        System.err.println(new TextFormatter().formatProblem(Instant.now(),
                "JOB_JAR_STARTUP", null, exception.getMessage(), Map.of()));
    }

    private static void deleteQuietly(Path root) {
        try {
            if (!Files.exists(root)) {
                return;
            }
            try (var walk = Files.walk(root)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        } catch (IOException ignored) {
        }
    }

    public record JobMetadata(
            int schemaVersion, String name, String version, String runtimeVersion,
            int javaFeatureVersion, String entryClass, String entryScript,
            String resourceRoot, List<String> resourceFiles,
            List<String> sourceFiles, String scriptSha256) {

        public JobMetadata {
            resourceFiles = List.copyOf(resourceFiles);
            sourceFiles = List.copyOf(sourceFiles);
        }

        static JobMetadata load(ClassLoader loader) throws JobJarException {
            try (InputStream input = loader.getResourceAsStream(METADATA_RESOURCE)) {
                if (input == null) {
                    throw new JobJarException("JOB_METADATA_MISSING",
                            "Paketmetadaten fehlen: " + METADATA_RESOURCE);
                }
                Object parsed = Json.parse(new String(input.readAllBytes(),
                        StandardCharsets.UTF_8));
                if (!(parsed instanceof Map<?, ?> raw)) {
                    throw new JobJarException("JOB_METADATA_INVALID",
                            "Paketmetadaten muessen ein JSON-Objekt sein");
                }
                Map<String, Object> value = new java.util.LinkedHashMap<>();
                raw.forEach((key, item) -> value.put(String.valueOf(key), item));
                int schema = integer(value, "schemaVersion");
                if (schema != 1) {
                    throw new JobJarException("JOB_METADATA_VERSION",
                            "Unbekannte Paket-schemaVersion: " + schema);
                }
                return new JobMetadata(schema, text(value, "name"),
                        text(value, "version"), text(value, "runtimeVersion"),
                        integer(value, "javaFeatureVersion"),
                        text(value, "entryClass"), text(value, "entryScript"),
                        text(value, "resourceRoot"), strings(value, "resourceFiles"),
                        strings(value, "sourceFiles"), text(value, "scriptSha256"));
            } catch (IOException exception) {
                throw new JobJarException("JOB_METADATA_READ",
                        "Paketmetadaten konnten nicht gelesen werden", exception);
            }
        }

        private static String text(Map<String, Object> value, String key)
                throws JobJarException {
            Object item = value.get(key);
            if (!(item instanceof String text) || text.isBlank()) {
                throw new JobJarException("JOB_METADATA_INVALID",
                        "Paketmetadatenfeld fehlt: " + key);
            }
            return text;
        }

        private static int integer(Map<String, Object> value, String key)
                throws JobJarException {
            Object item = value.get(key);
            if (!(item instanceof Number number)) {
                throw new JobJarException("JOB_METADATA_INVALID",
                        "Paketmetadatenfeld muss eine Zahl sein: " + key);
            }
            return number.intValue();
        }

        private static List<String> strings(Map<String, Object> value, String key)
                throws JobJarException {
            Object item = value.get(key);
            if (!(item instanceof List<?> list)) {
                throw new JobJarException("JOB_METADATA_INVALID",
                        "Paketmetadatenfeld muss eine Liste sein: " + key);
            }
            List<String> result = new ArrayList<>();
            for (Object element : list) {
                if (!(element instanceof String text)) {
                    throw new JobJarException("JOB_METADATA_INVALID",
                            "Paketmetadatenliste enthaelt keinen Text: " + key);
                }
                result.add(text);
            }
            return result;
        }
    }

    static final class PackagedJobSource implements ch.so.agi.gretl.cli.JobSource {
        private final JobMetadata metadata;
        private final Path jobRoot;
        private final ClassLoader classLoader;

        PackagedJobSource(JobMetadata metadata, Path jobRoot, ClassLoader classLoader) {
            this.metadata = metadata;
            this.jobRoot = jobRoot;
            this.classLoader = classLoader;
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
                    registry, jobRoot, startCwd, sink);
            return new GroovyJobLoader().loadCompiled(metadata.entryClass(),
                    displayFile(), environment, classLoader);
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
