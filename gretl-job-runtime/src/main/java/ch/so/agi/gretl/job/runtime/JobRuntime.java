package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.runtime.groovy.GroovyDslException;
import ch.so.agi.gretl.runtime.groovy.UserMessageSink;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.i18n.LocaleSelection;
import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.logging.LogContext;
import ch.so.agi.gretl.runtime.logging.LogbackBridge;
import ch.so.agi.gretl.runtime.logging.LoggingConfig;
import ch.so.agi.gretl.runtime.logging.LoggingPipeline;
import ch.so.agi.gretl.runtime.logging.LoggingState;
import ch.so.agi.gretl.runtime.logging.Redactor;
import ch.so.agi.gretl.runtime.logging.RunReportInput;
import ch.so.agi.gretl.runtime.logging.RunReportWriter;
import ch.so.agi.gretl.runtime.runner.DefinitionException;
import ch.so.agi.gretl.runtime.runner.ExecutionPlan;
import ch.so.agi.gretl.runtime.runner.ExecutionPlanner;
import ch.so.agi.gretl.runtime.runner.FailureInfo;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.JobOutcome;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import ch.so.agi.gretl.runtime.runner.ParameterException;
import ch.so.agi.gretl.runtime.runner.ParameterMode;
import ch.so.agi.gretl.runtime.runner.ParameterResolver;
import ch.so.agi.gretl.runtime.runner.ParameterSources;
import ch.so.agi.gretl.runtime.runner.ResolvedParameters;
import ch.so.agi.gretl.runtime.runner.RunControl;
import ch.so.agi.gretl.runtime.runner.RunEvent;
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
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Shared script/JAR orchestration, including the single run finalization path. */
public final class JobRuntime {
    private final PrintStream out;
    private final PrintStream err;
    private final Map<String, String> environment;
    private final Path startCwd;
    private final ClassLoader classLoader;
    private final Clock clock;
    private final java.util.function.Supplier<UUID> runIds;
    private Locale locale = Locale.GERMAN;

    public JobRuntime(PrintStream out, PrintStream err, Map<String, String> environment,
                    Path startCwd, ClassLoader classLoader) {
        this(out, err, environment, startCwd, classLoader, Clock.systemUTC());
    }

    public JobRuntime(PrintStream out, PrintStream err, Map<String, String> environment,
             Path startCwd, ClassLoader classLoader, Clock clock) {
        this(out, err, environment, startCwd, classLoader, clock, UUID::randomUUID);
    }

    JobRuntime(PrintStream out, PrintStream err, Map<String,String> environment,
               Path startCwd, ClassLoader classLoader, Clock clock, java.util.function.Supplier<UUID> runIds) {
        this.runIds = java.util.Objects.requireNonNull(runIds);
        this.out = java.util.Objects.requireNonNull(out, "out");
        this.err = java.util.Objects.requireNonNull(err, "err");
        this.environment = Map.copyOf(environment);
        this.startCwd = startCwd.toAbsolutePath().normalize();
        this.classLoader = java.util.Objects.requireNonNull(classLoader, "classLoader");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    public int executeScript(JobOptions options) {
        locale = LocaleSelection.resolve(options.language(), environment.get("GRETL_LANGUAGE"), null, Locale.getDefault());
        return execute(options, null);
    }

    public int executePackaged(JobSource source, String[] args) {
        locale = LocaleSelection.resolve(JobOptions.requestedLanguage(args), environment.get("GRETL_LANGUAGE"), null, Locale.getDefault());
        try {
            return executePackaged(source, JobOptions.parsePackaged(args, startCwd, locale));
        } catch (StartException failure) {
            return StartException.report(failure, err, clock.instant(), UUID.randomUUID().toString(),
                    locale, JobOptions.jsonRequested(args));
        }
    }

    public int executePackaged(JobSource source, JobOptions options) {
        java.util.Objects.requireNonNull(source, "source");
        locale = LocaleSelection.resolve(options.language(), environment.get("GRETL_LANGUAGE"), null, Locale.getDefault());
        return execute(options, source);
    }

    private int execute(JobOptions options, JobSource source) {
        String commandId = UUID.randomUUID().toString();
        boolean json = options.logFormat() == LoggingConfig.Format.JSON;
        try {
            JobSource selected = source == null ? sourceFor(options.job(), locale) : source;
            RuntimeConfig config = options.config() == null
                    ? RuntimeConfig.defaults(startCwd)
                    : RuntimeConfig.load(options.config(), startCwd, locale);
            locale = LocaleSelection.resolve(options.language(), environment.get("GRETL_LANGUAGE"),
                    config.language().orElse(null), locale);
            LoggingConfig logging = config.logging().withOverrides(options.logFormat(), options.logLevel());
            json = logging.format() == LoggingConfig.Format.JSON;
            return jobCommand(selected, options, config, logging, commandId);
        } catch (Throwable failure) {
            return StartException.report(failure, err, clock.instant(), commandId, locale, json);
        }
    }

    private JobSource sourceFor(Path input, Locale language) {
        if (input != null && Files.isRegularFile(input)) return new ScriptJobSource(input);
        if (input != null && Files.isDirectory(input)) return new ProjectJobSource(input);
        throw new StartException(2, Messages.format(language, "cli.error.jobNotFound", input));
    }

    private int jobCommand(JobSource source, JobOptions options, RuntimeConfig config,
                           LoggingConfig logging, String commandId) {
        if (source.projectMode()) {
            for (String target : options.targets()) {
                try {
                    if (ch.so.agi.gretl.runtime.groovy.project.ProjectPath.parseQualified(target).isRoot()) {
                        throw new IllegalArgumentException("task required");
                    }
                } catch (IllegalArgumentException failure) {
                    throw new ch.so.agi.gretl.runtime.groovy.project.ProjectException("PROJECT_TARGET_INVALID",
                            Messages.format(locale, "project.cliTargetInvalid", target),
                            new ch.so.agi.gretl.runtime.groovy.SourceLocation(source.displayFile().toString(), 0, 0), failure);
                }
            }
        }
        String jobName = config.jobName()
                .or(() -> Optional.ofNullable(environment.get("GRETL_JOB_NAME")))
                .orElseGet(source::jobName);
        jobName = requireJobName(jobName);
        Optional<String> correlationId = config.correlationId()
                .or(() -> Optional.ofNullable(environment.get("GRETL_CORRELATION_ID")));
        correlationId.ifPresent(value -> {
            if (value.isBlank() || value.length() > 256
                    || value.chars().anyMatch(Character::isISOControl)) {
                throw new StartException(2, "runtime.correlationId / GRETL_CORRELATION_ID: "
                        + "expected non-blank text up to 256 characters without control characters");
            }
        });
        config = config.withCorrelationId(correlationId);
        config = config.withOverrides(options.workDir(), null,
                options.keepWork(), options.timeout(), logging, Optional.of(jobName));
        OperationRegistry registry = OperationRegistry.load(classLoader, locale);

        MutableSink sink = new MutableSink();
        JobSource.Loaded loaded = loadDefinition(source, registry, sink);
        JobDefinition definition = loaded.definition();
        if (options.help()) {
            sink.flushTo(message -> err.println("SCRIPT: " + message));
            new JobHelpPrinter(out, locale).jobHelp(definition, source.displayFile());
            return 0;
        }

        Map<String, String> fileParameters = ParameterFile.read(options.parameterFile(), locale);
        ParameterResolver resolver = new ParameterResolver(startCwd, locale);
        ParameterMode mode = options.command() == JobOptions.Command.RUN
                ? ParameterMode.RUN : ParameterMode.CHECK;
        ResolvedParameters parameters = resolver.resolve(definition,
                new ParameterSources(config.params(), fileParameters, options.parameters()), mode);
        List<String> targets = options.targets().isEmpty()
                ? definition.defaultTargets() : options.targets();
        if (targets.isEmpty()) {
            throw new StartException(2, Messages.get(locale, "cli.error.noTargets"));
        }
        ExecutionPlan plan = new ExecutionPlanner().plan(definition, targets);

        if (options.command() == JobOptions.Command.CHECK) {
            sink.flushTo(message -> err.println("SCRIPT: " + message));
            out.println(Messages.format(locale, "cli.check.ok",
                    source.displayFile().getFileName()));
            if (!parameters.missingRequired().isEmpty()) {
                out.println(Messages.format(locale, "cli.check.openRequired",
                        String.join(", ", parameters.missingRequired())));
            }
            return 0;
        }
        if (options.command() == JobOptions.Command.PLAN) {
            if (options.graphFormat() == null) {
                new JobHelpPrinter(out, locale).plan(plan);
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
                        throw new StartException(2, Messages.format(locale,
                                "cli.error.planGraphWrite", options.output()), exception);
                    }
                }
            }
            return 0;
        }
        return run(source, loaded, plan, parameters, config, sink,
                options.report(), commandId);
    }

    private int run(JobSource source, JobSource.Loaded loaded, ExecutionPlan plan,
                    ResolvedParameters parameters, RuntimeConfig config,
                    MutableSink userSink,
                    Path explicitReport, String commandId) {
        JobDefinition definition = loaded.definition();
        UUID runId = runIds.get();
        prepareDirectory(config.workRoot(), "workRoot");
        Path report = explicitReport != null
                ? explicitReport
                : config.reportRoot().resolve(runId + ".json");
        prepareDirectory(report.toAbsolutePath().normalize().getParent(), "report parent");

        SecretRegistry secrets = new SecretRegistry();
        Redactor redactor = new Redactor(secrets);
        SecretResolver secretResolver = config.secretResolver(environment, secrets, locale);
        LogContext context = new LogContext(commandId, runId,
                config.jobName().orElseGet(source::jobName), RuntimeInfo.VERSION,
                Optional.ofNullable(environment.get("GRETL_DEPLOYMENT_ENVIRONMENT"))
                        .filter(value -> !value.isBlank()), locale, config.correlationId());
        Path eventFile = config.reportRoot().resolve(runId + ".events.jsonl");
        LoggingPipeline logging = new LoggingPipeline(config.logging(), context, redactor, err, eventFile, clock);
        LogbackBridge bridge = null;
        RunControl control = new RunControl(SystemMonotonicClock.INSTANCE,
                Optional.ofNullable(config.timeout()));
        java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
        Thread hook = new Thread(() -> {
            control.requestCancel(ch.so.agi.gretl.runtime.api.CancelReason.REQUESTED);
            long nanos = control.completionBudget().remaining().orElseThrow().toNanos();
            try {
                completed.await(nanos, java.util.concurrent.TimeUnit.NANOSECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, "gretl-shutdown");
        // The final process event must reflect required-report failures as well.
        RunEvent[] terminal = new RunEvent[1];
        boolean restoreInterrupt = false;
        try {
            userSink.attach(logging::scriptMessage);
            try {
                bridge = LogbackBridge.install(logging, config.logging());
            } catch (RuntimeException exception) {
                throw new StartException(2, Messages.format(locale,
                        "cli.error.loggingProvider", StartException.message(exception)), exception);
            }
            Runtime.getRuntime().addShutdownHook(hook);
            RunInput input = RunInput.builder()
                    .parameters(parameters)
                    .taskConfig(config.taskConfig())
                    .jobRoot(definition.jobRoot())
                    .workspacePreparation(root -> source.prepareRun(root, definition.jobRoot(), locale))
                    .locale(locale)
                    .startCwd(startCwd)
                    .workRoot(config.workRoot())
                    .jobName(config.jobName().orElseGet(source::jobName))
                    .runId(runId)
                    .keepWork(config.keepWork())
                    .clock(clock)
                    .monotonicClock(SystemMonotonicClock.INSTANCE)
                    .eventSink(new ch.so.agi.gretl.runtime.runner.RunEventSink() {
                        @Override public void emit(RunEvent event) {
                            if ("gretl.run.finished".equals(event.eventName())) {
                                terminal[0] = event;
                            } else {
                                logging.emit(event);
                            }
                        }
                        @Override public Scope openScope(ch.so.agi.gretl.runtime.api.ExecutionIdentity identity,
                                                         Optional<String> operationId) {
                            return logging.openScope(identity, operationId);
                        }
                    })
                    .secretResolver(secretResolver)
                    .secretRegistry(secrets)
                    .runControl(control)
                    .build();
            JobOutcome outcome;
            try {
                outcome = new SequentialJobRunner().run(definition, plan, input);
            } catch (ch.so.agi.gretl.runtime.runner.RunStartException failure) {
                throw new StartException(2, redactor.redact(StartException.message(failure)), failure);
            }
            restoreInterrupt = Thread.interrupted();
            LoggingState loggingState = logging.state();
            Optional<String> revision = Optional.ofNullable(environment.get("GRETL_JOB_REVISION"))
                    .filter(value -> !value.isBlank());
            int exitCode = outcome.exitCode();
            FailureInfo reportFailure = null;
            try {
                new RunReportWriter(redactor).write(new RunReportInput(
                        context, outcome, definition, plan, source.displayFile(),
                        loaded.sha256(), revision, loggingState), report);
            } catch (IOException | SecurityException exception) {
                String message = Messages.format(locale,
                        "cli.error.reportWrite", redactor.redact(StartException.message(exception)));
                reportFailure = FailureInfo.from(exception);
                logging.emit(new RunEvent("gretl.report.write_failed", message, clock.instant(),
                        LogLevel.ERROR, Optional.of(runId), Optional.of(context.jobName()),
                        Optional.empty(), Optional.empty(), Optional.empty(), "report", "FAILED", 0,
                        Map.of("code", "REPORT_WRITE_FAILED"), Optional.of(reportFailure)));
                if (exitCode == 0) {
                    exitCode = 10;
                }
            }
            String terminationPath = environment.get("GRETL_TERMINATION_MESSAGE_PATH");
            if (terminationPath != null && !terminationPath.isBlank()) {
                try {
                    TerminationMessage.write(startCwd.resolve(terminationPath), outcome, exitCode);
                } catch (IOException | SecurityException exception) {
                    err.println(Messages.format(locale,
                            "cli.warning.terminationMessage", redactor.redact(StartException.message(exception))));
                }
            }
            RunEvent finished = java.util.Objects.requireNonNull(terminal[0], "run terminal event");
            Map<String, String> attributes = new LinkedHashMap<>(finished.attributes());
            attributes.put("exit_code", Integer.toString(exitCode));
            logging.emit(new RunEvent(finished.eventName(), finished.message(), clock.instant(),
                    exitCode == 10 ? LogLevel.ERROR : finished.level(), finished.runId(),
                    finished.jobName(), finished.taskId(), finished.invocationId(),
                    finished.operationId(), finished.phase(),
                    exitCode == 10 ? "FAILED" : finished.outcome(), finished.durationMillis(),
                    attributes, finished.failure().isPresent() ? finished.failure()
                            : Optional.ofNullable(reportFailure)));
            return exitCode;
        } catch (Throwable failure) {
            // Buffered script or library events can precede a startup failure.
            synchronized (logging) {
                return StartException.report(failure, err, clock.instant(), commandId, locale,
                        config.logging().format() == LoggingConfig.Format.JSON, logging.nextSequence());
            }
        } finally {
            try {
                if (bridge != null) {
                    try {
                        bridge.close();
                    } catch (RuntimeException ignored) {
                    }
                }
                logging.close();
            } finally {
                completed.countDown();
                removeHook(hook);
                if (restoreInterrupt) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private JobSource.Loaded loadDefinition(JobSource source, OperationRegistry registry,
                                            UserMessageSink sink) {
        try {
            return source.load(registry, startCwd, sink, locale);
        } catch (DefinitionException | ParameterException | GroovyDslException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new StartException(2, Messages.format(locale,
                    "cli.error.jobDefinition", StartException.message(exception)), exception);
        }
    }

    private void prepareDirectory(Path path, String label) {
        try {
            try {
                Files.createDirectories(path, PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
            } catch (UnsupportedOperationException exception) {
                Files.createDirectories(path);
            }
            if (!Files.isDirectory(path) || !Files.isWritable(path)) {
                throw new IOException("not a writable directory");
            }
        } catch (IOException | SecurityException exception) {
            throw new StartException(2, Messages.format(locale,
                    "cli.error.notWritable", label, path), exception);
        }
    }

    private void removeHook(Thread hook) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down.
        }
    }

    private String requireJobName(String value) {
        if (value == null || value.isBlank() || value.length() > 256
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new StartException(2, Messages.get(locale, "cli.error.jobName"));
        }
        return value;
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
