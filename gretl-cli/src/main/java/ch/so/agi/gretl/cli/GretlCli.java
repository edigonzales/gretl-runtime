package ch.so.agi.gretl.cli;

import ch.so.agi.gretl.job.runtime.JobRuntime;
import ch.so.agi.gretl.job.runtime.JobOptions;
import ch.so.agi.gretl.job.runtime.RuntimeInfo;
import ch.so.agi.gretl.job.runtime.StartException;

import ch.so.agi.gretl.i18n.LocaleSelection;
import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.packager.JobPackager;
import ch.so.agi.gretl.packager.PackageException;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
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
        String language = JobOptions.requestedLanguage(args);
        Locale locale = LocaleSelection.resolve(language, environment.get("GRETL_LANGUAGE"), null, Locale.getDefault());
        try {
            JobOptions.Command jobCommand = args.length == 0 ? null : switch (args[0]) {
                case "run" -> JobOptions.Command.RUN;
                case "check" -> JobOptions.Command.CHECK;
                case "plan" -> JobOptions.Command.PLAN;
                default -> null;
            };
            if (jobCommand != null) {
                JobOptions options = JobOptions.parse(jobCommand, args, startCwd, locale);
                if (options.command() == JobOptions.Command.COMMAND_HELP) {
                    out.println(Messages.get(locale, "cli.commandHelp"));
                    return 0;
                }
                return new JobRuntime(out, err, environment, startCwd, classLoader, clock).executeScript(options);
            }
            CliOptions options = CliOptions.parse(args, startCwd, locale);
            if (options.language() != null) {
                locale = LocaleSelection.resolve(options.language(), environment.get("GRETL_LANGUAGE"), null, locale);
            }
            return switch (options.command()) {
                case HELP -> {
                    new HelpPrinter(out, locale).global();
                    yield 0;
                }
                case VERSION -> {
                    new HelpPrinter(out, locale).version();
                    yield 0;
                }
                case OPERATIONS -> operations(options, locale);
                case PACKAGE -> packageCommand(options, locale);
            };
        } catch (Throwable failure) {
            return StartException.report(failure, err, clock.instant(), commandId, locale, JobOptions.jsonRequested(args));
        }
    }

    private int packageCommand(CliOptions options, Locale locale) {
        if (options.help()) {
            out.println(Messages.get(locale, "cli.package.help"));
            return 0;
        }
        try {
            var result = new JobPackager(RuntimeInfo.VERSION, locale)
                    .packageJob(options.descriptor(), options.output());
            out.println(Messages.format(locale, "cli.package.created", result.output()));
            out.println(Messages.format(locale, "cli.package.job", result.name(),
                    result.version()));
            out.println(Messages.format(locale, "cli.package.entryClass",
                    result.entryClass()));
            out.println(Messages.format(locale, "cli.package.classes",
                    result.classCount(), result.resourceCount()));
            return 0;
        } catch (PackageException exception) {
            throw new StartException(2, StartException.message(exception), exception,
                    exception.code(), null, null, null);
        }
    }

    private int operations(CliOptions options, Locale locale) {
        OperationRegistry registry = OperationRegistry.load(classLoader, locale);
        if (options.docFormat() == DocFormat.ADOC) {
            return asciiDocOperations(registry, options, locale);
        }
        HelpPrinter help = new HelpPrinter(out, locale);
        if (options.operationSymbol() == null) {
            help.operations(registry);
            return 0;
        }
        help.operation(registry.registered().stream()
                .filter(entry -> entry.operation().symbol().equals(options.operationSymbol()))
                .findFirst()
                .orElseThrow(() -> new StartException(2, unknownSymbol(registry, options.operationSymbol(), locale))));
        return 0;
    }

    private int asciiDocOperations(OperationRegistry registry, CliOptions options, Locale locale) {
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
                            unknownSymbol(registry, options.operationSymbol(), locale)));
            if (options.output() == null) {
                out.print(renderer.renderTaskTable(operation, locale));
            } else {
                renderer.writeTask(operation, options.output(), locale);
            }
            return 0;
        } catch (IOException exception) {
            throw new StartException(2, Messages.format(locale,
                    "cli.error.operations.write", StartException.message(exception)), exception);
        }
    }

    private String unknownSymbol(OperationRegistry registry, String symbol, Locale locale) {
        String suggestion = registry.all().stream()
                .map(operation -> operation.symbol())
                .filter(candidate -> candidate.equalsIgnoreCase(symbol)
                        || candidate.toLowerCase().contains(symbol.toLowerCase()))
                .findFirst().orElse(null);
        String hint = suggestion == null ? ""
                : Messages.format(locale, "cli.error.unknownOperation.suggestion", suggestion);
        return Messages.format(locale, "cli.error.unknownOperation", symbol, hint);
    }
}
