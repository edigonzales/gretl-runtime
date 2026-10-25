package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.runner.ExecutionPlan;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.ParameterDefinition;
import ch.so.agi.gretl.runtime.runner.TaskDefinition;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

/** Human help sourced from the same operation/definition metadata as the runtime. */
public final class JobHelpPrinter {
    private final PrintStream out;
    private final Locale locale;

    public JobHelpPrinter(PrintStream out) {
        this(out, Locale.GERMAN);
    }

    public JobHelpPrinter(PrintStream out, Locale locale) {
        this.out = out;
        this.locale = java.util.Objects.requireNonNull(locale, "locale");
    }

    private String text(String key, Object... arguments) {
        return Messages.format(locale, key, arguments);
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
                                ? ", " + text("help.default") + "=" + OperationHelpText.displayDefault(parameter.defaultValue().orElseThrow()) : "");
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
}
