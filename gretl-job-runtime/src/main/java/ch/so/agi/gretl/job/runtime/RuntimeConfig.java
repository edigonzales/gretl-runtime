package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.i18n.LocaleSelection;
import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.logging.Json;
import ch.so.agi.gretl.runtime.logging.LoggingConfig;
import ch.so.agi.gretl.runtime.runner.KeepWork;
import ch.so.agi.gretl.runtime.runner.SecretRegistry;
import ch.so.agi.gretl.runtime.runner.SecretResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Strict schema-1 runtime configuration and secret source resolution. */
public final class RuntimeConfig {
    private static final long MAX_CONFIG_BYTES = 16L * 1024 * 1024;
    private static final long MAX_SECRET_BYTES = 64 * 1024;
    private static final Pattern PARAMETER_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]*");
    private static final ThreadLocal<Locale> ERROR_LOCALE =
            ThreadLocal.withInitial(() -> Locale.GERMAN);

    private static String msg(String key, Object... arguments) {
        return Messages.format(ERROR_LOCALE.get(), key, arguments);
    }

    public record SecretSource(String environment, Path file, String plaintext) {
        public SecretSource(String environment, Path file) {
            this(environment, file, null);
        }

        public SecretSource {
            int sources = (environment != null ? 1 : 0) + (file != null ? 1 : 0)
                    + (plaintext != null ? 1 : 0);
            if (sources != 1) {
                throw new IllegalArgumentException(
                        "secret source requires exactly one of environment, file or plaintext");
            }
        }

        @Override
        public String toString() {
            return "SecretSource[environment=" + environment + ", file=" + file
                    + ", plaintext=" + (plaintext == null ? "null" : "[REDACTED]") + "]";
        }
    }

    private final Optional<Path> file;
    private final Path configDirectory;
    private final Path workRoot;
    private final Path reportRoot;
    private final KeepWork keepWork;
    private final Duration timeout;
    private final Optional<String> jobName;
    private final Optional<String> correlationId;
    private final Optional<String> language;
    private final LoggingConfig logging;
    private final Map<String, String> params;
    private final Map<String, SecretSource> secrets;
    private final Map<String, Map<String, Object>> taskConfig;

    private RuntimeConfig(Optional<Path> file, Path configDirectory, Path workRoot,
                          Path reportRoot, KeepWork keepWork, Duration timeout,
                          Optional<String> jobName, Optional<String> correlationId,
                          Optional<String> language, LoggingConfig logging, Map<String, String> params,
                          Map<String, SecretSource> secrets,
                          Map<String, Map<String, Object>> taskConfig) {
        this.file = file;
        this.configDirectory = configDirectory;
        this.workRoot = workRoot;
        this.reportRoot = reportRoot;
        this.keepWork = keepWork;
        this.timeout = timeout;
        this.jobName = jobName;
        this.correlationId = correlationId;
        this.language = language;
        this.logging = logging;
        this.params = Map.copyOf(params);
        this.secrets = Map.copyOf(secrets);
        this.taskConfig = Map.copyOf(taskConfig);
    }

    public static RuntimeConfig defaults(Path startCwd) {
        Path cwd = startCwd.toAbsolutePath().normalize();
        return new RuntimeConfig(Optional.empty(), cwd,
                Path.of(System.getProperty("java.io.tmpdir"), "gretl-work"),
                cwd.resolve("gretl-reports"), KeepWork.ON_FAILURE, null,
                Optional.empty(), Optional.empty(), Optional.empty(), LoggingConfig.defaults(),
                Map.of(), Map.of(), Map.of());
    }

    public static RuntimeConfig load(Path configFile, Path startCwd) {
        return load(configFile, startCwd, Locale.GERMAN);
    }

    public static RuntimeConfig load(Path configFile, Path startCwd, Locale locale) {
        ERROR_LOCALE.set(locale);
        Path absolute = resolve(startCwd, configFile.toString());
        if (!Files.isRegularFile(absolute)) {
            throw new StartException(2, Messages.format(locale,
                    "cli.error.runtimeConfig.notFound", absolute));
        }
        Map<String, Object> root;
        try {
            root = object(Json.parseFile(absolute, MAX_CONFIG_BYTES), "Runtimekonfiguration");
        } catch (IOException | IllegalArgumentException exception) {
            throw new StartException(2, Messages.format(locale,
                    "cli.error.runtimeConfig.invalid", safeMessage(exception)), exception);
        }
        rejectUnknown(root, List.of("schemaVersion", "language", "runtime", "logging",
                "params", "secrets", "taskConfig"), "Runtimekonfiguration");
        if (!(root.get("schemaVersion") instanceof Long version) || version != 1) {
            throw new StartException(2, Messages.format(locale,
                    "cli.error.runtimeConfig.schemaVersion"));
        }
        Path configDir = absolute.getParent();
        Map<String, Object> runtime = optionalObject(root, "runtime");
        rejectUnknown(runtime, List.of("workRoot", "reportRoot", "keepWork", "timeout",
                "jobName", "correlationId"), "runtime");
        Path workRoot = optionalPath(runtime.get("workRoot"), configDir,
                "runtime.workRoot", Path.of(System.getProperty("java.io.tmpdir"), "gretl-work"));
        Path reportRoot = optionalPath(runtime.get("reportRoot"), configDir,
                "runtime.reportRoot", startCwd.toAbsolutePath().normalize().resolve("gretl-reports"));
        KeepWork keepWork = keepWork(optionalText(runtime.get("keepWork"), "runtime.keepWork",
                "on-failure"));
        Duration timeout = optionalDuration(runtime.get("timeout"), "runtime.timeout");
        Optional<String> jobName = optionalBoundedText(runtime.get("jobName"), "runtime.jobName");
        Optional<String> correlationId = optionalBoundedText(runtime.get("correlationId"),
                "runtime.correlationId");
        Optional<String> language = language(root.get("language"));
        LoggingConfig logging = logging(optionalObject(root, "logging"));
        Map<String, String> params = params(optionalObject(root, "params"));
        Map<String, SecretSource> secrets = secrets(optionalObject(root, "secrets"), configDir);
        Map<String, Map<String, Object>> taskConfig = taskConfig(
                optionalObject(root, "taskConfig"));
        return new RuntimeConfig(Optional.of(absolute), configDir, workRoot, reportRoot,
                keepWork, timeout, jobName, correlationId, language, logging, params,
                secrets, taskConfig);
    }

    public RuntimeConfig withOverrides(Path workRootOverride, Path reportRootOverride,
                                       KeepWork keepWorkOverride, Duration timeoutOverride,
                                       LoggingConfig loggingOverride,
                                       Optional<String> jobNameOverride) {
        return new RuntimeConfig(file, configDirectory,
                workRootOverride == null ? workRoot : workRootOverride,
                reportRootOverride == null ? reportRoot : reportRootOverride,
                keepWorkOverride == null ? keepWork : keepWorkOverride,
                timeoutOverride == null ? timeout : timeoutOverride,
                jobNameOverride == null ? jobName : jobNameOverride,
                correlationId, language, loggingOverride == null ? logging : loggingOverride,
                params, secrets, taskConfig);
    }

    public Optional<String> language() {
        return language;
    }

    public RuntimeConfig withCorrelationId(Optional<String> value) {
        return new RuntimeConfig(file, configDirectory, workRoot, reportRoot,
                keepWork, timeout, jobName, value, language, logging, params, secrets, taskConfig);
    }

    public SecretResolver secretResolver(Map<String, String> environment,
                                         SecretRegistry registry) {
        Map<String, String> cache = new LinkedHashMap<>();
        return alias -> {
            if (alias == null || alias.isBlank()) {
                throw new IllegalArgumentException(msg("cli.error.config.secretAliasBlank"));
            }
            SecretSource source = secrets.get(alias);
            if (source == null) {
                throw new IllegalArgumentException(msg("cli.error.config.secretAliasUnknown", alias));
            }
            String value = cache.get(alias);
            if (value == null) {
                value = source.plaintext() != null ? source.plaintext()
                        : source.environment() != null ? environment.get(source.environment())
                        : readSecretFile(source.file(), alias);
                if (value == null) {
                    throw new IllegalArgumentException(msg("cli.error.config.secretSourceMissing", alias,
                            source.environment() != null
                            ? "environment=" + source.environment() : "file=" + source.file()));
                }
                cache.put(alias, value);
            }
            registry.register(value);
            return value;
        };
    }

    private static LoggingConfig logging(Map<String, Object> logging) {
        rejectUnknown(logging, List.of("format", "level", "libraryLevels", "eventFile",
                "maxRecordBytes", "maxEventFileBytes", "progressInterval",
                "heartbeatInterval"), "logging");
        LoggingConfig defaults = LoggingConfig.defaults();
        LoggingConfig.Format format = logging.containsKey("format")
                ? format(requiredText(logging.get("format"), "logging.format"))
                : defaults.format();
        LogLevel level = logging.containsKey("level")
                ? level(requiredText(logging.get("level"), "logging.level"))
                : defaults.level();
        boolean eventFile = booleanValue(logging.get("eventFile"), "logging.eventFile",
                defaults.eventFile());
        int maxRecord = integerValue(logging.get("maxRecordBytes"),
                "logging.maxRecordBytes", defaults.maxRecordBytes());
        long maxFile = longValue(logging.get("maxEventFileBytes"),
                "logging.maxEventFileBytes", defaults.maxEventFileBytes());
        Duration progress = logging.containsKey("progressInterval")
                ? duration(requiredText(logging.get("progressInterval"),
                "logging.progressInterval"), "logging.progressInterval")
                : defaults.progressInterval();
        Duration heartbeat = logging.containsKey("heartbeatInterval")
                ? duration(requiredText(logging.get("heartbeatInterval"),
                "logging.heartbeatInterval"), "logging.heartbeatInterval")
                : defaults.heartbeatInterval();
        Map<String, LogLevel> libraryLevels = new LinkedHashMap<>();
        Map<String, Object> levels = optionalObject(logging, "libraryLevels");
        for (Map.Entry<String, Object> entry : levels.entrySet()) {
            if (entry.getKey().isBlank()) {
                throw new StartException(2, msg("cli.error.config.loggingLibraryLevelEmpty"));
            }
            libraryLevels.put(entry.getKey(), level(requiredText(entry.getValue(),
                    "logging.libraryLevels." + entry.getKey())));
        }
        try {
            return new LoggingConfig(format, level, libraryLevels, eventFile,
                    maxRecord, maxFile, progress, heartbeat);
        } catch (IllegalArgumentException exception) {
            throw new StartException(2, msg("cli.error.config.loggingInvalid", exception.getMessage()));
        }
    }

    private static Map<String, String> params(Map<String, Object> params) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!PARAMETER_NAME.matcher(entry.getKey()).matches()) {
                throw new StartException(2, msg("cli.error.config.paramsInvalidName", entry.getKey()));
            }
            if (!(entry.getValue() instanceof String text)) {
                throw new StartException(2, msg("cli.error.config.paramsNonString", entry.getKey()));
            }
            result.put(entry.getKey(), text);
        }
        return result;
    }

    private static Map<String, SecretSource> secrets(Map<String, Object> secrets, Path base) {
        Map<String, SecretSource> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : secrets.entrySet()) {
            if (!PARAMETER_NAME.matcher(entry.getKey()).matches()) {
                throw new StartException(2, msg("cli.error.config.secretAliasInvalid", entry.getKey()));
            }
            Map<String, Object> source = object(entry.getValue(),
                    "secrets." + entry.getKey());
            rejectUnknown(source, List.of("environment", "file", "plaintext"), "secrets." + entry.getKey());
            if (source.size() != 1) {
                throw new StartException(2, msg("cli.error.config.secretSourceExactlyOne", entry.getKey()));
            }
            if (source.containsKey("environment")) {
                String environment = requiredText(source.get("environment"),
                        "secrets." + entry.getKey() + ".environment");
                if (environment.isBlank() || environment.chars().anyMatch(Character::isISOControl)) {
                    throw new StartException(2, msg("cli.error.config.secretEnvironmentInvalid"));
                }
                result.put(entry.getKey(), new SecretSource(environment, null));
            } else if (source.containsKey("file")) {
                Path file = resolve(base, requiredText(source.get("file"),
                        "secrets." + entry.getKey() + ".file"));
                result.put(entry.getKey(), new SecretSource(null, file));
            } else {
                if (!(source.get("plaintext") instanceof String plaintext)) {
                    throw new StartException(2, msg("cli.error.config.secretPlaintextText",
                            entry.getKey()));
                }
                result.put(entry.getKey(), new SecretSource(null, null, plaintext));
            }
        }
        return result;
    }

    private static Map<String, Map<String, Object>> taskConfig(Map<String, Object> taskConfig) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : taskConfig.entrySet()) {
            if (entry.getKey().isBlank()) {
                throw new StartException(2, msg("cli.error.config.taskConfigEmptyAlias"));
            }
            result.put(entry.getKey(), object(entry.getValue(), "taskConfig." + entry.getKey()));
        }
        return result;
    }

    private static String readSecretFile(Path file, String alias) {
        try {
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException(msg("cli.error.config.secretFileNotFound", alias, file));
            }
            long size = Files.size(file);
            if (size > MAX_SECRET_BYTES) {
                throw new IllegalArgumentException(msg("cli.error.config.secretFileTooLarge"));
            }
            String value = Files.readString(file, StandardCharsets.UTF_8);
            if (value.indexOf('\0') >= 0) {
                throw new IllegalArgumentException(msg("cli.error.config.secretFileNul"));
            }
            if (value.endsWith("\r\n")) {
                return value.substring(0, value.length() - 2);
            }
            return value.endsWith("\n") ? value.substring(0, value.length() - 1) : value;
        } catch (IOException exception) {
            throw new IllegalArgumentException(msg("cli.error.config.secretFileRead"), exception);
        }
    }

    public Optional<Path> file() { return file; }
    public Path configDirectory() { return configDirectory; }
    public Path workRoot() { return workRoot; }
    public Path reportRoot() { return reportRoot; }
    public KeepWork keepWork() { return keepWork; }
    public Duration timeout() { return timeout; }
    public Optional<String> jobName() { return jobName; }
    public Optional<String> correlationId() { return correlationId; }
    public LoggingConfig logging() { return logging; }
    public Map<String, String> params() { return params; }
    public Map<String, SecretSource> secrets() { return secrets; }
    public Map<String, Map<String, Object>> taskConfig() { return taskConfig; }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new StartException(2, msg("cli.error.config.objectExpected", field));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new StartException(2, msg("cli.error.config.objectNonStringKey", field));
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static Map<String, Object> optionalObject(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        return value == null ? Map.of() : object(value, key);
    }

    private static void rejectUnknown(Map<String, Object> map, List<String> allowed,
                                      String field) {
        for (String key : map.keySet()) {
            if (!allowed.contains(key)) {
                throw new StartException(2, msg("cli.error.config.unknownField", field, key));
            }
        }
    }

    private static String requiredText(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new StartException(2, msg("cli.error.config.textRequired", field));
        }
        return text;
    }

    private static String optionalText(Object value, String field, String fallback) {
        return value == null ? fallback : requiredText(value, field);
    }

    private static Optional<String> language(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw new StartException(2, msg("cli.error.config.languageInvalid"));
        }
        if (LocaleSelection.parse(text).isEmpty()) {
            throw new StartException(2, msg("cli.error.config.languageInvalid"));
        }
        return Optional.of(text);
    }

    private static Optional<String> optionalBoundedText(Object value, String field) {
        if (value == null) {
            return Optional.empty();
        }
        String text = requiredText(value, field);
        if (text.length() > 256 || text.chars().anyMatch(Character::isISOControl)) {
            throw new StartException(2, msg("cli.error.config.textTooLong", field));
        }
        return Optional.of(text);
    }

    private static Path optionalPath(Object value, Path base, String field, Path fallback) {
        return value == null ? fallback.toAbsolutePath().normalize()
                : resolve(base, requiredText(value, field));
    }

    private static Duration optionalDuration(Object value, String field) {
        return value == null ? null : duration(requiredText(value, field), field);
    }

    private static Duration duration(String value, String field) {
        try {
            Duration duration = Duration.parse(value);
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("not positive");
            }
            return duration;
        } catch (RuntimeException exception) {
            throw new StartException(2, msg("cli.error.config.durationInvalid", field));
        }
    }

    private static KeepWork keepWork(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "never" -> KeepWork.NEVER;
            case "on-failure" -> KeepWork.ON_FAILURE;
            case "always" -> KeepWork.ALWAYS;
            default -> throw new StartException(2,
                    msg("cli.error.config.keepWorkInvalid"));
        };
    }

    private static LoggingConfig.Format format(String value) {
        try {
            return LoggingConfig.Format.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new StartException(2, msg("cli.error.config.logFormatInvalid"));
        }
    }

    private static LogLevel level(String value) {
        try {
            return LogLevel.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new StartException(2, msg("cli.error.config.logLevelInvalid"));
        }
    }

    private static boolean booleanValue(Object value, String field, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Boolean bool)) {
            throw new StartException(2, msg("cli.error.config.booleanInvalid", field));
        }
        return bool;
    }

    private static int integerValue(Object value, String field, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Long number) || number < Integer.MIN_VALUE
                || number > Integer.MAX_VALUE) {
            throw new StartException(2, msg("cli.error.config.integerInvalid", field));
        }
        return number.intValue();
    }

    private static long longValue(Object value, String field, long fallback) {
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Long number)) {
            throw new StartException(2, msg("cli.error.config.integerInvalid", field));
        }
        return number;
    }

    private static Path resolve(Path base, String value) {
        Path path;
        try {
            path = Path.of(value);
        } catch (RuntimeException exception) {
            throw new StartException(2, msg("cli.error.config.pathInvalid", value));
        }
        return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize();
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getName() : message;
    }
}
