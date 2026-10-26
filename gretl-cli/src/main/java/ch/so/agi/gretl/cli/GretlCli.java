package ch.so.agi.gretl.cli;

import ch.so.agi.gretl.job.runtime.JobRuntime;
import ch.so.agi.gretl.job.runtime.RuntimeInfo;
import ch.so.agi.gretl.job.runtime.StartException;

import ch.so.agi.gretl.runtime.groovy.GroovyDslException;
import ch.so.agi.gretl.i18n.LocaleSelection;
import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.logging.Json;
import ch.so.agi.gretl.runtime.logging.LoggingConfig;
import ch.so.agi.gretl.runtime.logging.TextFormatter;
import ch.so.agi.gretl.packager.JobPackager;
import ch.so.agi.gretl.packager.PackageException;
import ch.so.agi.gretl.runtime.runner.DefinitionException;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import ch.so.agi.gretl.runtime.runner.ParameterException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
        } catch (StartException exception) {
            error(exception, jsonRequested(args));
            return exception.code();
        }
        String explicitLanguage = options.language() == null
                ? requestedLanguage(args) : options.language();
        locale = LocaleSelection.resolve(explicitLanguage,
                environment.get("GRETL_LANGUAGE"), null, locale);
        return execute(options);
    }

    private int execute(CliOptions options) {
        try {
            return dispatch(options);
        } catch (StartException exception) {
            error(exception, options.logFormat() == LoggingConfig.Format.JSON);
            return exception.code();
        } catch (ParameterException exception) {
            error(new StartException(2, safeMessage(exception), exception,
                            exception.code(), exception.parameterName(), null, null),
                    options.logFormat() == LoggingConfig.Format.JSON);
            return 2;
        } catch (DefinitionException exception) {
            error(new StartException(2, safeMessage(exception), exception,
                            exception.code(), null, null, null),
                    options.logFormat() == LoggingConfig.Format.JSON);
            return 2;
        } catch (GroovyDslException exception) {
            error(new StartException(2, safeMessage(exception), exception,
                            exception.code(), exception.fieldName(), exception.taskId(),
                            exception.source() == null ? null : exception.source().file()
                                    + ":" + exception.source().line()),
                    options.logFormat() == LoggingConfig.Format.JSON);
            return 2;
        } catch (Throwable throwable) {
            error(new StartException(40, "Interner Runnerfehler: " + safeMessage(throwable),
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
            case RUN, CHECK, PLAN -> new JobRuntime(out, err, environment, startCwd, classLoader, clock)
                    .executeScript(options.jobOptions());
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
            throw new StartException(2, safeMessage(exception), exception,
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
                    .orElseThrow(() -> new StartException(2,
                            unknownSymbol(registry, options.operationSymbol()))));
            return 0;
        } catch (DefinitionException exception) {
            throw new StartException(2, safeMessage(exception), exception);
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
                    .orElseThrow(() -> new StartException(2,
                            unknownSymbol(registry, options.operationSymbol())));
            if (options.output() == null) {
                out.print(renderer.renderTaskTable(operation, locale));
            } else {
                renderer.writeTask(operation, options.output(), locale);
            }
            return 0;
        } catch (IOException exception) {
            throw new StartException(2, Messages.format(locale,
                    "cli.error.operations.write", safeMessage(exception)), exception);
        }
    }

    private OperationRegistry loadRegistry() {
        try {
            return OperationRegistry.load(classLoader, locale);
        } catch (DefinitionException exception) {
            throw new StartException(2, safeMessage(exception), exception);
        }
    }

    private void commandHelp() {
        out.println(Messages.get(locale, "cli.commandHelp"));
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

    private void error(StartException exception, boolean json) {
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

}
