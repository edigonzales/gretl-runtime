package ch.so.agi.gretl.cli;

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
import java.util.Map;

/** Human help sourced from the same operation/definition metadata as the runtime. */
public final class HelpPrinter {
    private final PrintStream out;

    public HelpPrinter(PrintStream out) {
        this.out = out;
    }

    public void global() {
        out.println("""
                gretl-runtime 0.1.0-M6 — sequenzieller GRETL-Jobrunner

                Aufrufe:
                  gretl --help
                  gretl --version
                  gretl operations [Symbol]
                  gretl check job.groovy [Optionen]
                  gretl plan job.groovy [Optionen]
                  gretl run job.groovy [Optionen]
                  gretl run job.groovy --help
                  gretl package job-package.json --output job.jar

                Laufzeitoptionen fuer run:
                  --target <task>        Wiederholbar; sonst Scriptdefaults
                  --config <file>        UTF-8-JSON-Runtimekonfiguration
                  --params-file <file>   JSON-Map mit Stringwerten
                  --param <name=value>   Wiederholbare Ueberschreibung
                  --work-dir <dir>       Wurzel neuer Runarbeitsbereiche
                  --report <file>        Abschliessender JSON-Runbericht
                  --timeout <duration>   Positives ISO-8601-Zeitbudget
                  --log-format text|json
                  --log-level TRACE|DEBUG|INFO|WARN|ERROR
                  --keep-work never|on-failure|always

                check und plan laden die Jobdefinition, fuehren aber keine Taskarbeit aus.
                Globale Hilfe und Version laden weder Job noch Taskprovider.
                """);
    }

    public void version() {
        out.println("gretl-runtime " + RuntimeInfo.VERSION);
        out.println("package: " + RuntimeInfo.PACKAGE_IDENTITY);
        out.println("Java: 25 (Bytecodeziel 25)");
        out.println("Groovy: 6.0.0");
    }

    public void operations(OperationRegistry registry) {
        out.println("Verfuegbare Bausteine:");
        for (OperationRegistry.RegisteredOperation entry : registry.registered()) {
            OperationType<?, ?> operation = entry.operation();
            out.printf("  %-16s %-28s %s (%s)%n", operation.symbol(), operation.id(),
                    operation.description(), entry.origin());
        }
    }

    public void operation(OperationRegistry.RegisteredOperation entry) {
        OperationType<?, ?> operation = entry.operation();
        out.println("Baustein: " + operation.symbol()
                + " (" + operation.id() + ")");
        out.println(operation.description());
        out.println("Herkunft: " + entry.origin());
        out.println();
        out.println("Eingabefelder:");
        for (InputField<?, ?> field : operation.inputs()) {
            out.printf("  %-20s %-10s %s%s%n", field.name(), field.valueType(),
                    field.required() ? "Pflicht" : "optional",
                    field.defaultValue().isPresent()
                            ? ", Default=" + display(field.defaultValue().orElseThrow()) : "");
            out.println("      " + field.description());
            if (!field.allowedValues().isEmpty()) {
                out.println("      erlaubt: " + String.join(", ", field.allowedValues()));
            }
            out.println("      Beispiel: " + field.example());
        }
        out.println();
        out.println("Ergebnisfelder:");
        for (OutputField<?, ?> field : operation.outputs()) {
            out.printf("  %-20s %-10s %s%n", field.name(), field.valueType(),
                    field.nullable() ? "nullable" : "nicht null");
            out.println("      " + field.description());
        }
        out.println();
        out.println("Beispiel:");
        out.println(operation.example());
    }

    public void jobHelp(JobDefinition definition, Path jobFile) {
        out.println("Job: " + jobFile.getFileName());
        out.println("Hinweis: Die Jobdefinition wurde geladen; Toplevelcode wurde ausgefuehrt.");
        out.println();
        out.println("Parameter:");
        if (definition.parameters().isEmpty()) {
            out.println("  (keine)");
        } else {
            for (ParameterDefinition parameter : definition.parameters()) {
                out.printf("  %-20s %-8s %s%s%n", parameter.name(),
                        parameter.type().name().toLowerCase(),
                        parameter.required() ? "Pflicht" : "optional",
                        parameter.defaultValue().isPresent()
                                ? ", Default=" + display(parameter.defaultValue().orElseThrow()) : "");
                out.println("      " + parameter.description());
            }
        }
        out.println();
        out.println("Standardziele: " + (definition.defaultTargets().isEmpty()
                ? "(keine; --target erforderlich)" : String.join(", ", definition.defaultTargets())));
        out.println();
        out.println("Tasks:");
        for (TaskDefinition<?> task : definition.tasks()) {
            out.printf("  %-20s %s%n", task.id(), task.description());
            if (!task.dependsOn().isEmpty()) {
                out.println("      dependsOn: " + task.dependsOn().stream()
                        .map(ref -> ref.id()).toList());
            }
            if (!task.after().isEmpty()) {
                out.println("      after: " + task.after().stream()
                        .map(ref -> ref.id()).toList());
            }
        }
    }

    public void plan(ExecutionPlan plan) {
        out.println("Ziele: " + String.join(", ", plan.requestedTargets()));
        out.println("Reihenfolge:");
        for (TaskDefinition<?> task : plan.selectedTasks()) {
            out.printf("  %-24s registrationIndex=%d dependsOn=%s after=%s%n",
                    task.id(), task.registrationIndex(),
                    task.dependsOn().stream().map(ref -> ref.id()).toList(),
                    task.after().stream().map(ref -> ref.id()).toList());
        }
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
