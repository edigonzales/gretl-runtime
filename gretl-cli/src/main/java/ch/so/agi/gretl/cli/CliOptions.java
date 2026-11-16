package ch.so.agi.gretl.cli;

import ch.so.agi.gretl.job.runtime.StartException;

import ch.so.agi.gretl.i18n.Messages;
import java.nio.file.Path;
import java.util.Locale;

/** Parsed command-line options; no job/provider is loaded here. */
public record CliOptions(
        Command command, Path descriptor, boolean help,
        String operationSymbol, Path output, String language, DocFormat docFormat) {

    public enum Command { HELP, VERSION, OPERATIONS, PACKAGE }

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
                            throw new StartException(2, msg(locale,
                                    "cli.error.operations.unknownOption", argument));
                        }
                        if (symbol != null) {
                            throw new StartException(2, msg(locale,
                                    "cli.error.operations.maxSymbol"));
                        }
                        symbol = argument;
                        index++;
                    }
                }
            }
            if (docFormat == DocFormat.TEXT && output != null) {
                throw new StartException(2, msg(locale,
                        "cli.error.operations.outputRequiresAdoc"));
            }
            if (docFormat == DocFormat.ADOC && symbol == null && output == null) {
                throw new StartException(2, msg(locale,
                        "cli.error.operations.adocOutputMissing"));
            }
            return new CliOptions(Command.OPERATIONS, null, false, symbol, output, language, docFormat);
        }
        if (first.equals("package")) {
            return parsePackageCommand(args, startCwd, locale);
        }
        throw new StartException(2, msg(locale, "cli.error.unknownCommand", first));
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
                throw new StartException(2, msg(locale,
                        "cli.error.package.unknownOption", argument));
            } else {
                if (descriptor != null) {
                    throw new StartException(2, msg(locale,
                            "cli.error.package.multipleDescriptors"));
                }
                descriptor = resolve(startCwd, argument, locale);
                index++;
            }
        }
        if (!help && descriptor == null) {
            throw new StartException(2, msg(locale, "cli.error.package.descriptorMissing"));
        }
        if (!help && output == null) {
            throw new StartException(2, msg(locale, "cli.error.package.outputMissing"));
        }
        return new CliOptions(Command.PACKAGE, descriptor, help, null, output, language, DocFormat.TEXT);
    }

    private static CliOptions command(Command command) {
        return new CliOptions(command, null, false, null, null, null, DocFormat.TEXT);
    }

    private static String requireValue(String[] args, int index, String option,
                                       Locale locale) {
        if (index >= args.length || args[index].startsWith("-")) {
            throw new StartException(2,
                    msg(locale, "cli.error.option.valueMissing", option));
        }
        return args[index];
    }

    private static Path resolve(Path base, String value, Locale locale) {
        Path path;
        try {
            path = Path.of(value);
        } catch (RuntimeException exception) {
            throw new StartException(2, msg(locale, "cli.error.path.invalid", value));
        }
        return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize();
    }

    private static DocFormat docFormat(String value, Locale locale) {
        try {
            return DocFormat.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new StartException(2, msg(locale,
                    "cli.error.operations.format", value));
        }
    }

    private static String msg(Locale locale, String key, Object... arguments) {
        return Messages.format(locale, key, arguments);
    }

}
