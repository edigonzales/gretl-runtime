package ch.so.agi.gretl.cli;

import ch.so.agi.gretl.job.runtime.OperationHelpText;
import ch.so.agi.gretl.job.runtime.RuntimeInfo;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.io.PrintStream;
import java.util.Locale;

/** Human help sourced from the same operation/definition metadata as the runtime. */
public final class HelpPrinter {
    private final PrintStream out;
    private final Locale locale;

    public HelpPrinter(PrintStream out) {
        this(out, Locale.GERMAN);
    }

    public HelpPrinter(PrintStream out, Locale locale) {
        this.out = out;
        this.locale = java.util.Objects.requireNonNull(locale, "locale");
    }

    private String text(String key, Object... arguments) {
        return Messages.format(locale, key, arguments);
    }

    public void global() {
        out.println(text("help.global.title", RuntimeInfo.VERSION));
        out.println();
        out.println(text("help.global.usage"));
        out.println("  gretl --help");
        out.println("  gretl --version");
        out.println("  gretl operations [Symbol] [--format text|adoc] [--output <dir>]");
        out.println("  gretl check job.groovy [Optionen]");
        out.println("  gretl plan job.groovy [Optionen]");
        out.println("  gretl run job.groovy [Optionen]");
        out.println("  gretl run job.groovy --help");
        out.println("  gretl package job-package.json --output job.jar");
        out.println();
        out.println(text("help.global.runtimeOptions"));
        out.println("  --target <task>        " + text("help.global.target"));
        out.println("  --config <file>        " + text("help.global.config"));
        out.println("  --params-file <file>   " + text("help.global.paramsFile"));
        out.println("  --param <name=value>   " + text("help.global.param"));
        out.println("  --work-dir <dir>       " + text("help.global.workDir"));
        out.println("  --report <file>        " + text("help.global.report"));
        out.println("  --timeout <duration>   " + text("help.global.timeout"));
        out.println("  --log-format text|json");
        out.println("  --log-level TRACE|DEBUG|INFO|WARN|ERROR");
        out.println("  --keep-work             " + text("help.global.keepWork"));
        out.println();
        out.println(text("help.global.planOptions"));
        out.println("  --graph mermaid|plantuml  " + text("help.plan.graph"));
        out.println("  --output <file>           " + text("help.plan.output"));
        out.println();
        out.println(text("help.global.note"));
        out.println(text("help.global.safe"));
    }

    public void version() {
        out.println("gretl-runtime " + RuntimeInfo.VERSION);
        out.println(text("help.version.package") + ": " + RuntimeInfo.PACKAGE_IDENTITY);
        out.println(text("help.version.java") + ": 25 ("
                + text("help.version.bytecode") + " 25)");
        out.println(text("help.version.groovy") + ": 6.0.0");
    }

    public void operations(OperationRegistry registry) {
        out.println(text("help.available"));
        for (OperationRegistry.RegisteredOperation entry : registry.registered()) {
            OperationType<?, ?> operation = entry.operation();
            out.printf("  %-16s %-28s %s (%s)%n", operation.symbol(), operation.id(),
                    OperationHelpText.description(operation, locale), entry.origin());
        }
    }

    public void operation(OperationRegistry.RegisteredOperation entry) {
        OperationType<?, ?> operation = entry.operation();
        out.println(text("help.buildingBlock") + ": " + operation.symbol()
                + " (" + operation.id() + ")");
        out.println(OperationHelpText.description(operation, locale));
        out.println(text("help.origin") + ": " + entry.origin());
        out.println();
        out.println(text("help.inputFields") + ":");
        for (InputField<?, ?> field : operation.inputs()) {
            out.printf("  %-20s %-10s %s%s%n", field.name(), field.valueType(),
                    field.required() ? text("help.required") : text("help.optional"),
                    field.defaultValue().isPresent()
                            ? ", " + text("help.default") + "=" + OperationHelpText.displayDefault(field.defaultValue().orElseThrow()) : "");
            out.println("      " + OperationHelpText.fieldDescription(operation, field.name(), locale));
            if (!field.allowedValues().isEmpty()) {
                out.println("      " + text("help.allowed") + ": " + String.join(", ", field.allowedValues()));
            }
            out.println("      " + text("help.example") + ": " + field.example());
        }
        out.println();
        out.println(text("help.outputFields") + ":");
        for (OutputField<?, ?> field : operation.outputs()) {
            out.printf("  %-20s %-10s %s%n", field.name(), field.valueType(),
                    field.nullable() ? text("help.nullable") : text("help.notNull"));
            out.println("      " + OperationHelpText.outputDescription(operation, field.name(), locale));
        }
        out.println();
        out.println(text("help.example") + ":");
        out.println(operation.example());
    }

}
