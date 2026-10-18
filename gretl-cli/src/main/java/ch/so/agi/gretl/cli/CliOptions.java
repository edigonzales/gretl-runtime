package ch.so.agi.gretl.cli;

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
        String operationSymbol, Path output) {

    public enum Command { HELP, VERSION, OPERATIONS, RUN, CHECK, PLAN, PACKAGE, COMMAND_HELP }

    public CliOptions {
        java.util.Objects.requireNonNull(command, "command");
        targets = List.copyOf(targets);
        parameters = Map.copyOf(parameters);
    }

    public static CliOptions parse(String[] args, Path startCwd) {
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
            if (args.length > 2) {
                throw new CliException(2, "operations akzeptiert hoechstens ein Symbol");
            }
            String symbol = args.length == 2 ? args[1] : null;
            if (symbol != null && symbol.startsWith("-")) {
                throw new CliException(2, "Unbekannte Option fuer operations: " + symbol);
            }
            return new CliOptions(Command.OPERATIONS, null, false, List.of(), null,
                    null, Map.of(), null, null, null, null, null, null, symbol, null);
        }
        Command command = switch (first) {
            case "run" -> Command.RUN;
            case "check" -> Command.CHECK;
            case "plan" -> Command.PLAN;
            case "package" -> Command.PACKAGE;
            default -> throw new CliException(2,
                    "Unbekannter Befehl: " + first + ". Siehe gretl --help");
        };
        if (command == Command.PACKAGE) {
            return parsePackageCommand(args, startCwd);
        }
        return parseJobCommand(command, args, startCwd);
    }

    private static CliOptions parsePackageCommand(String[] args, Path startCwd) {
        Path descriptor = null;
        Path output = null;
        boolean help = false;
        int index = 1;
        while (index < args.length) {
            String argument = args[index];
            if (argument.equals("--help") || argument.equals("-h")) {
                help = true;
                index++;
            } else if (argument.equals("--output") || argument.equals("-o")) {
                output = resolve(startCwd, requireValue(args, ++index, argument));
                index++;
            } else if (argument.startsWith("-")) {
                throw new CliException(2, "Unbekannte Option fuer package: " + argument);
            } else {
                if (descriptor != null) {
                    throw new CliException(2, "Mehr als ein Paketdeskriptor angegeben");
                }
                descriptor = resolve(startCwd, argument);
                index++;
            }
        }
        if (help) {
            return new CliOptions(Command.PACKAGE, descriptor, true, List.of(), null,
                    null, Map.of(), null, null, null, null, null, null, null, output);
        }
        if (descriptor == null) {
            throw new CliException(2, "Paketdeskriptor fehlt. Aufruf: gretl package job-package.json --output job.jar");
        }
        if (output == null) {
            throw new CliException(2, "gretl package benoetigt --output job.jar");
        }
        return new CliOptions(Command.PACKAGE, descriptor, false, List.of(), null,
                null, Map.of(), null, null, null, null, null, null, null, output);
    }

    /** Parse options for a fixed packaged job (no job path or command). */
    public static CliOptions parsePackaged(String[] args, Path startCwd) {
        List<String> expanded = new ArrayList<>();
        boolean plan = args.length > 0 && args[0].equals("--plan");
        expanded.add(plan ? "plan" : "run");
        expanded.add("<packaged>");
        int start = plan ? 1 : 0;
        for (int index = start; index < args.length; index++) {
            expanded.add(args[index]);
        }
        return parse(expanded.toArray(String[]::new), startCwd);
    }

    private static CliOptions command(Command command) {
        return new CliOptions(command, null, false, List.of(), null, null,
                Map.of(), null, null, null, null, null, null, null, null);
    }

    private static CliOptions parseJobCommand(Command command, String[] args, Path startCwd) {
        Path job = null;
        boolean help = false;
        List<String> targets = new ArrayList<>();
        Path config = null;
        Path parameterFile = null;
        Map<String, String> parameters = new LinkedHashMap<>();
        Path workDir = null;
        Path report = null;
        Duration timeout = null;
        LoggingConfig.Format format = null;
        LogLevel level = null;
        KeepWork keepWork = null;

        int index = 1;
        while (index < args.length) {
            String argument = args[index];
            if (!argument.startsWith("-")) {
                if (job != null) {
                    throw new CliException(2, "Mehr als eine Jobdatei angegeben: " + argument);
                }
                job = resolve(startCwd, argument);
                index++;
                continue;
            }
            switch (argument) {
                case "--help", "-h" -> {
                    help = true;
                    index++;
                }
                case "--target" -> {
                    targets.add(requireValue(args, ++index, argument));
                    index++;
                }
                case "--config" -> {
                    config = resolve(startCwd, requireValue(args, ++index, argument));
                    index++;
                }
                case "--params-file" -> {
                    parameterFile = resolve(startCwd, requireValue(args, ++index, argument));
                    index++;
                }
                case "--param" -> {
                    String value = requireValue(args, ++index, argument);
                    int separator = value.indexOf('=');
                    if (separator <= 0) {
                        throw new CliException(2,
                                "--param benoetigt name=value: " + value);
                    }
                    String name = value.substring(0, separator);
                    String parameterValue = value.substring(separator + 1);
                    if (parameters.putIfAbsent(name, parameterValue) != null) {
                        throw new CliException(2, "Parameter doppelt angegeben: " + name);
                    }
                    index++;
                }
                case "--work-dir" -> {
                    workDir = resolve(startCwd, requireValue(args, ++index, argument));
                    index++;
                }
                case "--report" -> {
                    report = resolve(startCwd, requireValue(args, ++index, argument));
                    index++;
                }
                case "--timeout" -> {
                    timeout = duration(requireValue(args, ++index, argument));
                    index++;
                }
                case "--log-format" -> {
                    String value = requireValue(args, ++index, argument);
                    try {
                        format = LoggingConfig.Format.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException exception) {
                        throw new CliException(2, "--log-format muss text oder json sein");
                    }
                    index++;
                }
                case "--log-level" -> {
                    String value = requireValue(args, ++index, argument);
                    try {
                        level = LogLevel.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException exception) {
                        throw new CliException(2,
                                "--log-level muss TRACE, DEBUG, INFO, WARN oder ERROR sein");
                    }
                    index++;
                }
                case "--keep-work" -> {
                    String value = requireValue(args, ++index, argument).toLowerCase(Locale.ROOT);
                    keepWork = switch (value) {
                        case "never" -> KeepWork.NEVER;
                        case "on-failure" -> KeepWork.ON_FAILURE;
                        case "always" -> KeepWork.ALWAYS;
                        default -> throw new CliException(2,
                                "--keep-work muss never, on-failure oder always sein");
                    };
                    index++;
                }
                default -> throw new CliException(2, "Unbekannte Option: " + argument);
            }
        }

        if (command != Command.RUN) {
            if (workDir != null || report != null || keepWork != null || timeout != null) {
                throw new CliException(2,
                        "--work-dir, --report, --keep-work und --timeout gelten nur fuer run");
            }
        }
        if (job == null) {
            if (help) {
                return new CliOptions(Command.COMMAND_HELP, null, true, List.of(), null,
                        null, Map.of(), null, null, null, null, null, null, null, null);
            }
            throw new CliException(2, "Jobdatei fehlt. Aufruf: gretl "
                    + command.name().toLowerCase(Locale.ROOT) + " job.groovy");
        }
        return new CliOptions(command, job, help, targets, config, parameterFile,
                parameters, workDir, report, timeout, format, level, keepWork, null, null);
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length || args[index].startsWith("-")) {
            throw new CliException(2, "Option " + option + " benoetigt einen Wert");
        }
        return args[index];
    }

    private static Path resolve(Path base, String value) {
        Path path;
        try {
            path = Path.of(value);
        } catch (RuntimeException exception) {
            throw new CliException(2, "Ungueltiger Pfad: " + value);
        }
        return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize();
    }

    private static Duration duration(String value) {
        try {
            Duration duration = Duration.parse(value);
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("not positive");
            }
            return duration;
        } catch (RuntimeException exception) {
            throw new CliException(2, "--timeout muss eine positive ISO-8601-Dauer sein");
        }
    }
}
