package ch.so.agi.gretl.cli;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.logging.LoggingConfig;
import ch.so.agi.gretl.runtime.runner.KeepWork;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parsed command-line options; no job/provider is loaded here. */
public record CliOptions(
        Command command, Path job, boolean help, List<String> targets,
        Path config, Path parameterFile, Map<String, String> parameters,
        Path workDir, Path report, Duration timeout,
        LoggingConfig.Format logFormat, LogLevel logLevel, KeepWork keepWork,
        String operationSymbol, Path output, String language,
        PlanGraphFormat graphFormat, DocFormat docFormat) {

    public enum Command { HELP, VERSION, OPERATIONS, RUN, CHECK, PLAN, PACKAGE, COMMAND_HELP }

    public CliOptions {
        java.util.Objects.requireNonNull(command, "command");
        targets = List.copyOf(targets);
        parameters = Map.copyOf(parameters);
    }

    public static CliOptions parse(String[] args, Path startCwd) {
        return parse(args, startCwd, Locale.GERMAN);
    }

    public static CliOptions parse(String[] args, Path startCwd, Locale locale) {
        if (args.length == 0) {
            return command(Command.HELP);
        }
        String first = args[0];
        if (first.equals("--help") || first.equals("-h")) {
            return command(Command.HELP);
        }
        if (first.equals("--version") || first.equals("-V")) {
            return command(Command.VERSION);
        }
        if (first.equals("operations")) {
            String symbol = null;
            String language = null;
            DocFormat docFormat = DocFormat.TEXT;
            Path output = null;
            int index = 1;
            while (index < args.length) {
                String argument = args[index];
                switch (argument) {
                    case "--lang" -> {
                        language = requireValue(args, ++index, argument, locale);
                        index++;
                    }
                    case "--format" -> {
                        String value = requireValue(args, ++index, argument, locale);
                        docFormat = docFormat(value, locale);
                        index++;
                    }
                    case "--output", "-o" -> {
                        output = resolve(startCwd,
                                requireValue(args, ++index, argument, locale), locale);
                        index++;
                    }
                    default -> {
                        if (argument.startsWith("-")) {
                            throw new CliException(2, msg(locale,
                                    "cli.error.operations.unknownOption", argument));
                        }
                        if (symbol != null) {
                            throw new CliException(2, msg(locale,
                                    "cli.error.operations.maxSymbol"));
                        }
                        symbol = argument;
                        index++;
                    }
                }
            }
            if (docFormat == DocFormat.TEXT && output != null) {
                throw new CliException(2, msg(locale,
                        "cli.error.operations.outputRequiresAdoc"));
            }
            if (docFormat == DocFormat.ADOC && symbol == null && output == null) {
                throw new CliException(2, msg(locale,
                        "cli.error.operations.adocOutputMissing"));
            }
            return new CliOptions(Command.OPERATIONS, null, false, List.of(), null,
                    null, Map.of(), null, null, null, null, null, null, symbol, output,
                    language, null, docFormat);
        }
        Command command = switch (first) {
            case "run" -> Command.RUN;
            case "check" -> Command.CHECK;
            case "plan" -> Command.PLAN;
            case "package" -> Command.PACKAGE;
            default -> throw new CliException(2,
                    msg(locale, "cli.error.unknownCommand", first));
        };
        if (command == Command.PACKAGE) {
            return parsePackageCommand(args, startCwd, locale);
        }
        return parseJobCommand(command, args, startCwd, locale);
    }

    private static CliOptions parsePackageCommand(String[] args, Path startCwd,
                                                   Locale locale) {
        Path descriptor = null;
        Path output = null;
        String language = null;
        boolean help = false;
        int index = 1;
        while (index < args.length) {
            String argument = args[index];
            if (argument.equals("--help") || argument.equals("-h")) {
                help = true;
                index++;
            } else if (argument.equals("--lang")) {
                language = requireValue(args, ++index, argument, locale);
                index++;
            } else if (argument.equals("--output") || argument.equals("-o")) {
                output = resolve(startCwd, requireValue(args, ++index, argument, locale), locale);
                index++;
            } else if (argument.startsWith("-")) {
                throw new CliException(2, msg(locale,
                        "cli.error.package.unknownOption", argument));
            } else {
                if (descriptor != null) {
                    throw new CliException(2, msg(locale,
                            "cli.error.package.multipleDescriptors"));
                }
                descriptor = resolve(startCwd, argument, locale);
                index++;
            }
        }
        if (help) {
            return new CliOptions(Command.PACKAGE, descriptor, true, List.of(), null,
                    null, Map.of(), null, null, null, null, null, null, null, output,
                    language, null, DocFormat.TEXT);
        }
        if (descriptor == null) {
            throw new CliException(2, msg(locale,
                    "cli.error.package.descriptorMissing"));
        }
        if (output == null) {
            throw new CliException(2, msg(locale,
                    "cli.error.package.outputMissing"));
        }
        return new CliOptions(Command.PACKAGE, descriptor, false, List.of(), null,
                null, Map.of(), null, null, null, null, null, null, null, output, language,
                null, DocFormat.TEXT);
    }

    /** Parse options for a fixed packaged job (no job path or command). */
    public static CliOptions parsePackaged(String[] args, Path startCwd) {
        return parsePackaged(args, startCwd, Locale.GERMAN);
    }

    public static CliOptions parsePackaged(String[] args, Path startCwd, Locale locale) {
        List<String> expanded = new ArrayList<>();
        boolean plan = args.length > 0 && args[0].equals("--plan");
        expanded.add(plan ? "plan" : "run");
        expanded.add("<packaged>");
        int start = plan ? 1 : 0;
        for (int index = start; index < args.length; index++) {
            expanded.add(args[index]);
        }
        return parse(expanded.toArray(String[]::new), startCwd, locale);
    }

    private static CliOptions command(Command command) {
        return new CliOptions(command, null, false, List.of(), null, null,
                Map.of(), null, null, null, null, null, null, null, null, null, null,
                DocFormat.TEXT);
    }

    private static CliOptions parseJobCommand(Command command, String[] args, Path startCwd,
                                               Locale locale) {
        Path job = null;
        boolean help = false;
        List<String> targets = new ArrayList<>();
        Path config = null;
        Path parameterFile = null;
        Map<String, String> parameters = new LinkedHashMap<>();
        Path workDir = null;
        Path report = null;
        Path output = null;
        Duration timeout = null;
        LoggingConfig.Format format = null;
        LogLevel level = null;
        KeepWork keepWork = null;
        String language = null;
        PlanGraphFormat graphFormat = null;

        int index = 1;
        while (index < args.length) {
            String argument = args[index];
            if (!argument.startsWith("-")) {
                if (job != null) {
                    throw new CliException(2, msg(locale,
                            "cli.error.job.multipleFiles", argument));
                }
                job = resolve(startCwd, argument, locale);
                index++;
                continue;
            }
            switch (argument) {
                case "--help", "-h" -> {
                    help = true;
                    index++;
                }
                case "--target" -> {
                    targets.add(requireValue(args, ++index, argument, locale));
                    index++;
                }
                case "--config" -> {
                    config = resolve(startCwd,
                            requireValue(args, ++index, argument, locale), locale);
                    index++;
                }
                case "--params-file" -> {
                    parameterFile = resolve(startCwd,
                            requireValue(args, ++index, argument, locale), locale);
                    index++;
                }
                case "--param" -> {
                    String value = requireValue(args, ++index, argument, locale);
                    int separator = value.indexOf('=');
                    if (separator <= 0) {
                        throw new CliException(2,
                                msg(locale, "cli.error.param.invalid", value));
                    }
                    String name = value.substring(0, separator);
                    String parameterValue = value.substring(separator + 1);
                    if (parameters.putIfAbsent(name, parameterValue) != null) {
                        throw new CliException(2,
                                msg(locale, "cli.error.param.duplicate", name));
                    }
                    index++;
                }
                case "--work-dir" -> {
                    workDir = resolve(startCwd,
                            requireValue(args, ++index, argument, locale), locale);
                    index++;
                }
                case "--report" -> {
                    report = resolve(startCwd,
                            requireValue(args, ++index, argument, locale), locale);
                    index++;
                }
                case "--timeout" -> {
                    timeout = duration(requireValue(args, ++index, argument, locale), locale);
                    index++;
                }
                case "--log-format" -> {
                    String value = requireValue(args, ++index, argument, locale);
                    try {
                        format = LoggingConfig.Format.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException exception) {
                        throw new CliException(2,
                                msg(locale, "cli.error.logFormat"));
                    }
                    index++;
                }
                case "--log-level" -> {
                    String value = requireValue(args, ++index, argument, locale);
                    try {
                        level = LogLevel.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException exception) {
                        throw new CliException(2,
                                msg(locale, "cli.error.logLevel"));
                    }
                    index++;
                }
                case "--graph" -> {
                    String value = requireValue(args, ++index, argument, locale);
                    graphFormat = PlanGraphFormat.parse(value).orElseThrow(() ->
                            new CliException(2, msg(locale,
                                    "cli.error.planGraphFormat", value)));
                    index++;
                }
                case "--output" -> {
                    output = resolve(startCwd,
                            requireValue(args, ++index, argument, locale), locale);
                    index++;
                }
                case "--lang" -> {
                    language = requireValue(args, ++index, argument, locale);
                    index++;
                }
                case "--keep-work" -> {
                    String value = requireValue(args, ++index, argument, locale)
                            .toLowerCase(Locale.ROOT);
                    keepWork = switch (value) {
                        case "never" -> KeepWork.NEVER;
                        case "on-failure" -> KeepWork.ON_FAILURE;
                        case "always" -> KeepWork.ALWAYS;
                        default -> throw new CliException(2,
                                msg(locale, "cli.error.keepWork"));
                    };
                    index++;
                }
                default -> throw new CliException(2,
                        msg(locale, "cli.error.option.unknown", argument));
            }
        }

        if (command != Command.RUN) {
            if (workDir != null || report != null || keepWork != null || timeout != null) {
                throw new CliException(2, msg(locale, "cli.error.runOnly"));
            }
        }
        if (graphFormat != null && command != Command.PLAN) {
            throw new CliException(2, msg(locale, "cli.error.planGraphOnly"));
        }
        if (output != null && command != Command.PLAN) {
            throw new CliException(2, msg(locale, "cli.error.planOutputOnly"));
        }
        if (output != null && graphFormat == null) {
            throw new CliException(2, msg(locale, "cli.error.planOutputRequiresGraph"));
        }
        if (job == null) {
            if (help) {
                return new CliOptions(Command.COMMAND_HELP, null, true, List.of(), null,
                        null, Map.of(), null, null, null, null, null, null, null, null, null, null,
                        DocFormat.TEXT);
            }
            throw new CliException(2, msg(locale, "cli.error.job.missing",
                    command.name().toLowerCase(Locale.ROOT)));
        }
        return new CliOptions(command, job, help, targets, config, parameterFile,
                parameters, workDir, report, timeout, format, level, keepWork, null, output,
                language, graphFormat, DocFormat.TEXT);
    }

    private static String requireValue(String[] args, int index, String option,
                                       Locale locale) {
        if (index >= args.length || args[index].startsWith("-")) {
            throw new CliException(2,
                    msg(locale, "cli.error.option.valueMissing", option));
        }
        return args[index];
    }

    private static Path resolve(Path base, String value, Locale locale) {
        Path path;
        try {
            path = Path.of(value);
        } catch (RuntimeException exception) {
            throw new CliException(2, msg(locale, "cli.error.path.invalid", value));
        }
        return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize();
    }

    private static DocFormat docFormat(String value, Locale locale) {
        try {
            return DocFormat.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new CliException(2, msg(locale,
                    "cli.error.operations.format", value));
        }
    }

    private static String msg(Locale locale, String key, Object... arguments) {
        return Messages.format(locale, key, arguments);
    }

    private static Duration duration(String value, Locale locale) {
        try {
            Duration duration = Duration.parse(value);
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("not positive");
            }
            return duration;
        } catch (RuntimeException exception) {
            throw new CliException(2, msg(locale, "cli.error.timeout"));
        }
    }
}
