package ch.so.agi.gretl.cli;

import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.groovy.DefinitionEnvironment;
import ch.so.agi.gretl.runtime.groovy.GretlScript;
import ch.so.agi.gretl.runtime.groovy.GroovyDslException;
import ch.so.agi.gretl.runtime.groovy.GroovyJobLoader;
import ch.so.agi.gretl.runtime.groovy.UserMessageSink;
import ch.so.agi.gretl.i18n.LocaleSelection;
import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.logging.Json;
import ch.so.agi.gretl.runtime.logging.LogContext;
import ch.so.agi.gretl.runtime.logging.LogbackBridge;
import ch.so.agi.gretl.runtime.logging.LoggingConfig;
import ch.so.agi.gretl.runtime.logging.TextFormatter;
import ch.so.agi.gretl.runtime.logging.LoggingPipeline;
import ch.so.agi.gretl.runtime.logging.LoggingState;
import ch.so.agi.gretl.runtime.logging.ProviderInventory;
import ch.so.agi.gretl.packager.JobPackager;
import ch.so.agi.gretl.packager.PackageException;
import ch.so.agi.gretl.runtime.logging.Redactor;
import ch.so.agi.gretl.runtime.logging.RunReportInput;
import ch.so.agi.gretl.runtime.logging.RunReportWriter;
import ch.so.agi.gretl.runtime.runner.DefinitionException;
import ch.so.agi.gretl.runtime.runner.ExecutionPlan;
import ch.so.agi.gretl.runtime.runner.ExecutionPlanner;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.JobOutcome;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import ch.so.agi.gretl.runtime.runner.ParameterException;
import ch.so.agi.gretl.runtime.runner.ParameterMode;
import ch.so.agi.gretl.runtime.runner.ParameterResolver;
import ch.so.agi.gretl.runtime.runner.ParameterSources;
import ch.so.agi.gretl.runtime.runner.ResolvedParameters;
import ch.so.agi.gretl.runtime.runner.RunControl;
import ch.so.agi.gretl.runtime.runner.RunInput;
import ch.so.agi.gretl.runtime.runner.SecretRegistry;
import ch.so.agi.gretl.runtime.runner.SecretResolver;
import ch.so.agi.gretl.runtime.runner.SequentialJobRunner;
import ch.so.agi.gretl.runtime.runner.SystemMonotonicClock;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Testable CLI entry point; Main only supplies process streams and exits. */
public final class GretlCli {
    private final PrintStream out;
    private final PrintStream err;
    private final Map<String, String> environment;
    private final Path startCwd;
    private final ClassLoader classLoader;
    private final Clock clock;
    private final String commandId = UUID.randomUUID().toString();
    private Locale locale = Locale.GERMAN;

    public GretlCli(PrintStream out, PrintStream err, Map<String, String> environment,
                    Path startCwd, ClassLoader classLoader) {
        this(out, err, environment, startCwd, classLoader, Clock.systemUTC());
    }

    GretlCli(PrintStream out, PrintStream err, Map<String, String> environment,
             Path startCwd, ClassLoader classLoader, Clock clock) {
        this.out = java.util.Objects.requireNonNull(out, "out");
        this.err = java.util.Objects.requireNonNull(err, "err");
        this.environment = Map.copyOf(environment);
        this.startCwd = startCwd.toAbsolutePath().normalize();
        this.classLoader = java.util.Objects.requireNonNull(classLoader, "classLoader");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    public int run(String[] args) {
        locale = LocaleSelection.resolve(requestedLanguage(args),
                environment.get("GRETL_LANGUAGE"), null, Locale.getDefault());
        CliOptions options;
        try {
            options = CliOptions.parse(args, startCwd, locale);
        } catch (CliException exception) {
            error(exception, jsonRequested(args));
            return exception.code();
        }
        String explicitLanguage = options.language() == null
                ? requestedLanguage(args) : options.language();
        locale = LocaleSelection.resolve(explicitLanguage,
                environment.get("GRETL_LANGUAGE"), null, locale);
        return execute(options, null);
    }

    /** Entry point used by a packaged JAR; the job source is already fixed. */
    public int runPackaged(JobSource source, String[] args) {
        locale = LocaleSelection.resolve(requestedLanguage(args),
                environment.get("GRETL_LANGUAGE"), null, Locale.getDefault());
        CliOptions options;
        try {
            options = CliOptions.parsePackaged(args, startCwd, locale);
        } catch (CliException exception) {
            error(exception, jsonRequested(args));
            return exception.code();
        }
        String explicitLanguage = options.language() == null
                ? requestedLanguage(args) : options.language();
        locale = LocaleSelection.resolve(explicitLanguage,
                environment.get("GRETL_LANGUAGE"), null, locale);
        return execute(options, source);
    }

    private int execute(CliOptions options, JobSource source) {
        try {
            return source == null ? dispatch(options) : jobCommand(source, options);
        } catch (CliException exception) {
            error(exception, options.logFormat() == LoggingConfig.Format.JSON);
            return exception.code();
        } catch (ParameterException exception) {
            error(new CliException(2, safeMessage(exception), exception,
                            exception.code(), exception.parameterName(), null, null),
                    options.logFormat() == LoggingConfig.Format.JSON);
            return 2;
        } catch (DefinitionException exception) {
            error(new CliException(2, safeMessage(exception), exception,
                            exception.code(), null, null, null),
                    options.logFormat() == LoggingConfig.Format.JSON);
            return 2;
        } catch (GroovyDslException exception) {
            error(new CliException(2, safeMessage(exception), exception,
                            exception.code(), exception.fieldName(), exception.taskId(),
                            exception.source() == null ? null : exception.source().file()
                                    + ":" + exception.source().line()),
                    options.logFormat() == LoggingConfig.Format.JSON);
            return 2;
        } catch (Throwable throwable) {
            error(new CliException(40, "Interner Runnerfehler: " + safeMessage(throwable),
                            throwable, "INTERNAL_ERROR", null, null, null),
                    options.logFormat() == LoggingConfig.Format.JSON);
            return 40;
        }
    }

    private int dispatch(CliOptions options) {
        return switch (options.command()) {
            case HELP, COMMAND_HELP -> {
                if (options.command() == CliOptions.Command.COMMAND_HELP) {
                    commandHelp();
                } else {
                    new HelpPrinter(out, locale).global();
                }
                yield 0;
            }
            case VERSION -> {
                new HelpPrinter(out, locale).version();
                yield 0;
            }
            case OPERATIONS -> operations(options);
            case PACKAGE -> packageCommand(options);
            case RUN, CHECK, PLAN -> jobCommand(options);
        };
    }

    private int packageCommand(CliOptions options) {
        if (options.help()) {
            out.println(Messages.get(locale, "cli.package.help"));
            return 0;
        }
        try {
            var result = new JobPackager(RuntimeInfo.VERSION, locale)
                    .packageJob(options.job(), options.output());
            out.println(Messages.format(locale, "cli.package.created", result.output()));
            out.println(Messages.format(locale, "cli.package.job", result.name(),
                    result.version()));
            out.println(Messages.format(locale, "cli.package.entryClass",
                    result.entryClass()));
            out.println(Messages.format(locale, "cli.package.classes",
                    result.classCount(), result.resourceCount()));
            return 0;
        } catch (PackageException exception) {
            throw new CliException(2, safeMessage(exception), exception,
                    exception.code(), null, null, null);
        }
    }

    private int operations(CliOptions options) {
        OperationRegistry registry = loadRegistry();
        if (options.docFormat() == DocFormat.ADOC) {
            return asciiDocOperations(registry, options);
        }
        HelpPrinter help = new HelpPrinter(out, locale);
        if (options.operationSymbol() == null) {
            help.operations(registry);
            return 0;
        }
        try {
            help.operation(registry.registered().stream()
                    .filter(entry -> entry.operation().symbol()
                            .equals(options.operationSymbol()))
                    .findFirst()
                    .orElseThrow(() -> new CliException(2,
                            unknownSymbol(registry, options.operationSymbol()))));
            return 0;
        } catch (DefinitionException exception) {
            throw new CliException(2, safeMessage(exception), exception);
        }
    }

    private int asciiDocOperations(OperationRegistry registry, CliOptions options) {
        AsciiDocTaskRenderer renderer = new AsciiDocTaskRenderer();
        try {
            if (options.operationSymbol() == null) {
                renderer.render(registry, options.output(), locale);
                return 0;
            }
            var operation = registry.registered().stream()
                    .map(OperationRegistry.RegisteredOperation::operation)
                    .filter(entry -> entry.symbol().equals(options.operationSymbol()))
                    .findFirst()
                    .orElseThrow(() -> new CliException(2,
                            unknownSymbol(registry, options.operationSymbol())));
            if (options.output() == null) {
                out.print(renderer.renderTaskTable(operation, locale));
            } else {
                renderer.writeTask(operation, options.output(), locale);
            }
            return 0;
        } catch (IOException exception) {
            throw new CliException(2, Messages.format(locale,
                    "cli.error.operations.write", safeMessage(exception)), exception);
        }
    }

    private int jobCommand(CliOptions options) {
        Path job = options.job();
        if (job == null || !Files.isRegularFile(job)) {
            throw new CliException(2, Messages.format(locale,
                    "cli.error.jobNotFound", job));
        }
        return jobCommand(new ScriptJobSource(job, locale), options);
    }

    private int jobCommand(JobSource source, CliOptions options) {
        RuntimeConfig config = options.config() == null
                ? RuntimeConfig.defaults(startCwd)
                : RuntimeConfig.load(options.config(), startCwd, locale);
        locale = LocaleSelection.resolve(options.language(),
                environment.get("GRETL_LANGUAGE"),
                config.language().orElse(null), locale);
        String jobName = config.jobName()
                .or(() -> Optional.ofNullable(environment.get("GRETL_JOB_NAME")))
                .orElseGet(source::jobName);
        jobName = requireJobName(jobName);
        LoggingConfig logging = config.logging().withOverrides(
                options.logFormat(), options.logLevel());
        config = config.withOverrides(options.workDir(), null,
                options.keepWork(), options.timeout(), logging, Optional.of(jobName));
        OperationRegistry registry = loadRegistry();

        if (options.help()) {
            MutableSink sink = new MutableSink();
            JobDefinition definition = loadDefinition(source, registry, sink);
            sink.flushTo(message -> err.println("SCRIPT: " + message));
            new HelpPrinter(out, locale).jobHelp(definition, source.displayFile());
            return 0;
        }

        MutableSink sink = new MutableSink();
        JobDefinition definition = loadDefinition(source, registry, sink);
        Map<String, String> fileParameters = ParameterFile.read(options.parameterFile(), locale);
        ParameterResolver resolver = new ParameterResolver(startCwd, locale);
        ParameterMode mode = options.command() == CliOptions.Command.RUN
                ? ParameterMode.RUN : ParameterMode.CHECK;
        ResolvedParameters parameters = resolver.resolve(definition,
                new ParameterSources(config.params(), fileParameters, options.parameters()), mode);
        List<String> targets = options.targets().isEmpty()
                ? definition.defaultTargets() : options.targets();
        if (targets.isEmpty()) {
            throw new CliException(2, Messages.get(locale, "cli.error.noTargets"));
        }
        ExecutionPlan plan = new ExecutionPlanner().plan(definition, targets);

        if (options.command() == CliOptions.Command.CHECK) {
            sink.flushTo(message -> err.println("SCRIPT: " + message));
            out.println(Messages.format(locale, "cli.check.ok",
                    source.displayFile().getFileName()));
            if (!parameters.missingRequired().isEmpty()) {
                out.println(Messages.format(locale, "cli.check.openRequired",
                        String.join(", ", parameters.missingRequired())));
            }
            return 0;
        }
        if (options.command() == CliOptions.Command.PLAN) {
            if (options.graphFormat() == null) {
                new HelpPrinter(out, locale).plan(plan);
            } else {
                String graph = new PlanGraphRenderer().render(plan, options.graphFormat(),
                        source.displayFile().getFileName().toString(), locale);
                if (options.output() == null) {
                    out.print(graph);
                } else {
                    try {
                        Files.writeString(options.output(), graph,
                                java.nio.charset.StandardCharsets.UTF_8);
                    } catch (java.io.IOException exception) {
                        throw new CliException(2, Messages.format(locale,
                                "cli.error.planGraphWrite", options.output()), exception);
                    }
                }
            }
            return 0;
        }
        return run(source, definition, plan, parameters, config, registry, sink,
                options.report());
    }

    private int run(JobSource source, JobDefinition definition, ExecutionPlan plan,
                    ResolvedParameters parameters, RuntimeConfig config,
                    OperationRegistry registry, MutableSink userSink,
                    Path explicitReport) {
        UUID runId = UUID.randomUUID();
        Instant commandStarted = clock.instant();
        prepareDirectory(config.workRoot(), "workRoot");
        prepareDirectory(config.reportRoot(), "reportRoot");
        Path report = explicitReport != null
                ? explicitReport
                : config.reportRoot().resolve(runId + ".json");
        prepareDirectory(report.toAbsolutePath().normalize().getParent(), "report parent");

        SecretRegistry secrets = new SecretRegistry();
        Redactor redactor = new Redactor(secrets);
        SecretResolver secretResolver = config.secretResolver(environment, secrets);
        LogContext context = new LogContext(commandId, runId,
                config.jobName().orElseGet(source::jobName), RuntimeInfo.VERSION,
                Optional.ofNullable(environment.get("GRETL_DEPLOYMENT_ENVIRONMENT"))
                        .filter(value -> !value.isBlank()), locale);
        Path eventFile = config.reportRoot().resolve(runId + ".events.jsonl");
        LoggingPipeline logging;
        try {
            logging = new LoggingPipeline(config.logging(), context, redactor, err,
                    config.logging().eventFile() ? eventFile : null, clock);
        } catch (IOException exception) {
            throw new CliException(2, Messages.format(locale,
                    "cli.error.eventFile", safeMessage(exception)), exception);
        }
        LogbackBridge bridge = null;
        RunControl control = new RunControl(SystemMonotonicClock.INSTANCE,
                Optional.ofNullable(config.timeout()));
        Thread hook = new Thread(() -> control.requestCancel(
                ch.so.agi.gretl.runtime.api.CancelReason.REQUESTED), "gretl-shutdown");
        try {
            userSink.attach(logging::scriptMessage);
            try {
                ProviderInventory.verify(classLoader);
                bridge = LogbackBridge.install(logging, config.logging());
            } catch (RuntimeException exception) {
                throw new CliException(2, Messages.format(locale,
                        "cli.error.loggingProvider", safeMessage(exception)), exception);
            }
            Runtime.getRuntime().addShutdownHook(hook);
            RunInput input = RunInput.builder()
                    .parameters(parameters)
                    .taskConfig(config.taskConfig())
                    .jobRoot(source.jobRoot())
                    .locale(locale)
                    .startCwd(startCwd)
                    .workRoot(config.workRoot())
                    .jobName(config.jobName().orElseGet(source::jobName))
                    .runId(runId)
                    .keepWork(config.keepWork())
                    .clock(clock)
                    .monotonicClock(SystemMonotonicClock.INSTANCE)
                    .eventSink(logging)
                    .secretResolver(secretResolver)
                    .secretRegistry(secrets)
                    .runControl(control)
                    .build();
            JobOutcome outcome = new SequentialJobRunner().run(definition, plan, input);
            removeHook(hook);
            String terminationPath = environment.get("GRETL_TERMINATION_MESSAGE_PATH");
            if (terminationPath != null && !terminationPath.isBlank()) {
                try {
                    TerminationMessage.write(startCwd.resolve(terminationPath), outcome);
                } catch (IOException exception) {
                    err.println(Messages.format(locale,
                            "cli.warning.terminationMessage", safeMessage(exception)));
                }
            }
            if (bridge != null) {
                bridge.close();
            }
            logging.close();
            LoggingState loggingState = logging.state();
            Optional<String> revision = Optional.ofNullable(environment.get("GRETL_JOB_REVISION"))
                    .filter(value -> !value.isBlank());
            try {
                new RunReportWriter(redactor).write(new RunReportInput(
                        context, outcome, definition, plan, source.displayFile(),
                        source.sha256(), revision,
                        RuntimeInfo.inventoryHash(registry), loggingState,
                        commandStarted, context.deploymentEnvironment()), report);
            } catch (IOException exception) {
                String message = Messages.format(locale,
                        "cli.error.reportWrite", safeMessage(exception));
                err.println(new TextFormatter().formatProblem(clock.instant(),
                        "REPORT_WRITE_FAILED", null, message, Map.of()));
                return 10;
            }
            return outcome.exitCode();
        } finally {
            removeHook(hook);
            if (bridge != null) {
                try {
                    bridge.close();
                } catch (RuntimeException ignored) {
                }
            }
            logging.close();
        }
    }

    private JobDefinition loadDefinition(JobSource source, OperationRegistry registry,
                                         UserMessageSink sink) {
        try {
            return source.load(registry, startCwd, sink);
        } catch (DefinitionException | ParameterException | GroovyDslException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CliException(2, Messages.format(locale,
                    "cli.error.jobDefinition", safeMessage(exception)), exception);
        }
    }

    private OperationRegistry loadRegistry() {
        try {
            return OperationRegistry.load(classLoader, locale);
        } catch (DefinitionException exception) {
            throw new CliException(2, safeMessage(exception), exception);
        }
    }

    private void commandHelp() {
        out.println(Messages.get(locale, "cli.commandHelp"));
    }

    private void prepareDirectory(Path path, String label) {
        try {
            Files.createDirectories(path);
            setPrivateDirectory(path);
            if (!Files.isDirectory(path) || !Files.isWritable(path)) {
                throw new IOException("not a writable directory");
            }
        } catch (IOException exception) {
            throw new CliException(2, Messages.format(locale,
                    "cli.error.notWritable", label, path), exception);
        }
    }

    private static void setPrivateDirectory(Path path) {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }

    private void removeHook(Thread hook) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down.
        }
    }

    private static String baseName(Path job) {
        String name = job.getFileName().toString();
        return name.endsWith(".groovy") ? name.substring(0, name.length() - 7) : name;
    }

    private String requireJobName(String value) {
        if (value == null || value.isBlank() || value.length() > 256
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new CliException(2, Messages.get(locale, "cli.error.jobName"));
        }
        return value;
    }

    private String unknownSymbol(OperationRegistry registry, String symbol) {
        String suggestion = registry.all().stream()
                .map(operation -> operation.symbol())
                .filter(candidate -> candidate.equalsIgnoreCase(symbol)
                        || candidate.toLowerCase().contains(symbol.toLowerCase()))
                .findFirst().orElse(null);
        String hint = suggestion == null ? ""
                : Messages.format(locale, "cli.error.unknownOperation.suggestion", suggestion);
        return Messages.format(locale, "cli.error.unknownOperation", symbol, hint);
    }

    private void error(CliException exception, boolean json) {
        String message = safeMessage(exception);
        if (json) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("schema_version", 1);
            event.put("timestamp", clock.instant().toString());
            event.put("sequence", 1);
            event.put("event_id", commandId + ":1");
            event.put("level", "ERROR");
            event.put("event_name", "gretl.cli.error");
            event.put("message", message);
            event.put("command_id", commandId);
            event.put("exit_code", exception.code());
            err.println(Json.write(event));
        } else {
            Map<String, String> details = new LinkedHashMap<>();
            if (exception.field() != null && !exception.field().isBlank()) {
                details.put(Messages.get(locale, "log.field.field"), exception.field());
            }
            if (exception.task() != null && !exception.task().isBlank()) {
                details.put(Messages.get(locale, "log.field.task"), exception.task());
            }
            if (exception.source() != null && !exception.source().isBlank()) {
                details.put(Messages.get(locale, "log.field.source"), exception.source());
            }
            err.println(new TextFormatter(locale).formatProblem(clock.instant(),
                    exception.diagnosticCode(), null, message, details));
        }
    }

    private static String requestedLanguage(String[] args) {
        for (int index = 0; index < args.length - 1; index++) {
            if (args[index].equals("--lang")) {
                return args[index + 1];
            }
        }
        return null;
    }

    private static boolean jsonRequested(String[] args) {
        for (int index = 0; index < args.length - 1; index++) {
            if (args[index].equals("--log-format")
                    && args[index + 1].equalsIgnoreCase("json")) {
                return true;
            }
        }
        return false;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getName() : message;
    }

    private static final class MutableSink implements UserMessageSink {
        private final java.util.List<String> buffered = new java.util.ArrayList<>();
        private UserMessageSink delegate;

        synchronized void attach(UserMessageSink value) {
            this.delegate = java.util.Objects.requireNonNull(value, "delegate");
            for (String message : buffered) {
                delegate.println(message);
            }
            buffered.clear();
        }

        synchronized void flushTo(UserMessageSink value) {
            for (String message : buffered) {
                value.println(message);
            }
            buffered.clear();
        }

        @Override
        public synchronized void println(String message) {
            if (delegate == null) {
                buffered.add(message);
            } else {
                delegate.println(message);
            }
        }
    }
}
