package ch.so.agi.gretl.cli;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import ch.so.agi.gretl.runtime.runner.ExecutionPlan;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import ch.so.agi.gretl.runtime.runner.ParameterDefinition;
import ch.so.agi.gretl.runtime.runner.TaskDefinition;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        out.println("  gretl operations [Symbol]");
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
                    operationDescription(operation), entry.origin());
        }
    }

    public void operation(OperationRegistry.RegisteredOperation entry) {
        OperationType<?, ?> operation = entry.operation();
        out.println(text("help.buildingBlock") + ": " + operation.symbol()
                + " (" + operation.id() + ")");
        out.println(operationDescription(operation));
        out.println(text("help.origin") + ": " + entry.origin());
        out.println();
        out.println(text("help.inputFields") + ":");
        for (InputField<?, ?> field : operation.inputs()) {
            out.printf("  %-20s %-10s %s%s%n", field.name(), field.valueType(),
                    field.required() ? text("help.required") : text("help.optional"),
                    field.defaultValue().isPresent()
                            ? ", " + text("help.default") + "=" + display(field.defaultValue().orElseThrow()) : "");
            out.println("      " + fieldDescription(operation, field.name()));
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
            out.println("      " + outputDescription(operation, field.name()));
        }
        out.println();
        out.println(text("help.example") + ":");
        out.println(operation.example());
    }

    public void jobHelp(JobDefinition definition, Path jobFile) {
        out.println(text("help.job") + ": " + jobFile.getFileName());
        out.println(text("help.loadedHint"));
        out.println();
        out.println(text("help.parameters") + ":");
        if (definition.parameters().isEmpty()) {
            out.println("  " + text("help.none"));
        } else {
            for (ParameterDefinition parameter : definition.parameters()) {
                out.printf("  %-20s %-8s %s%s%n", parameter.name(),
                        parameter.type().name().toLowerCase(),
                        parameter.required() ? text("help.required") : text("help.optional"),
                        parameter.defaultValue().isPresent()
                                ? ", " + text("help.default") + "=" + display(parameter.defaultValue().orElseThrow()) : "");
                out.println("      " + parameter.description());
            }
        }
        out.println();
        out.println(text("help.defaultTargets") + ": " + (definition.defaultTargets().isEmpty()
                ? text("help.noTargets") : String.join(", ", definition.defaultTargets())));
        out.println();
        out.println(text("help.tasks") + ":");
        for (TaskDefinition<?> task : definition.tasks()) {
            out.printf("  %-20s %s%n", task.id(), task.description());
            if (!task.dependsOn().isEmpty()) {
                out.println("      " + text("help.dependsOn") + ": " + task.dependsOn().stream()
                        .map(ref -> ref.id()).toList());
            }
            if (!task.after().isEmpty()) {
                out.println("      " + text("help.after") + ": " + task.after().stream()
                        .map(ref -> ref.id()).toList());
            }
        }
    }

    public void plan(ExecutionPlan plan) {
        out.println(text("help.targets") + ": " + String.join(", ", plan.requestedTargets()));
        out.println(text("help.order") + ":");
        for (TaskDefinition<?> task : plan.selectedTasks()) {
            out.printf("  %-24s registrationIndex=%d dependsOn=%s after=%s%n",
                    task.id(), task.registrationIndex(),
                    task.dependsOn().stream().map(ref -> ref.id()).toList(),
                    task.after().stream().map(ref -> ref.id()).toList());
        }
    }

    private String fieldDescription(OperationType<?, ?> operation, String field) {
        String key = "operation." + operation.id() + ".field." + field + ".description";
        return Messages.contains(locale, key)
                ? Messages.get(locale, key)
                : operation.inputs().stream().filter(value -> value.name().equals(field))
                        .findFirst().map(value -> value.description()).orElse(field);
    }

    private String outputDescription(OperationType<?, ?> operation, String field) {
        String key = "operation." + operation.id() + ".output." + field + ".description";
        return Messages.contains(locale, key)
                ? Messages.get(locale, key)
                : operation.outputs().stream().filter(value -> value.name().equals(field))
                        .findFirst().map(value -> value.description()).orElse(field);
    }

    private String operationDescription(OperationType<?, ?> operation) {
        String key = "operation." + operation.id() + ".description";
        return Messages.contains(locale, key)
                ? Messages.get(locale, key) : operation.description();
    }

    private static String display(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.toString();
        }
        if (value instanceof String text) {
            return "'" + text + "'";
        }
        return String.valueOf(value);
    }
}
