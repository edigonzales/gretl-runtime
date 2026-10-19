package ch.so.agi.gretl.cli;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.runner.ExecutionPlan;
import ch.so.agi.gretl.runtime.runner.PlanEdge;
import ch.so.agi.gretl.runtime.runner.TaskDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Renders the selected execution plan as portable Mermaid or PlantUML source. */
final class PlanGraphRenderer {

    String render(ExecutionPlan plan, PlanGraphFormat format, String sourceName,
                  Locale locale) {
        java.util.Objects.requireNonNull(plan, "plan");
        java.util.Objects.requireNonNull(format, "format");
        java.util.Objects.requireNonNull(locale, "locale");
        String source = sourceName == null || sourceName.isBlank() ? "job" : sourceName;
        return switch (format) {
            case MERMAID -> mermaid(plan, source);
            case PLANTUML -> plantuml(plan, source, locale);
        };
    }

    private String mermaid(ExecutionPlan plan, String source) {
        Map<String, String> aliases = aliases(plan, "n");
        Set<String> targets = Set.copyOf(plan.requestedTargets());
        StringBuilder out = new StringBuilder();
        out.append("flowchart TB\n");
        out.append("%% gretl plan: ").append(singleLine(source)).append('\n');
        out.append("%% solid = dependsOn, dotted = after, highlighted = target\n");
        if (!targets.isEmpty()) {
            out.append("  classDef target fill:#ffe8a3,stroke:#b26a00,stroke-width:2px;\n");
        }
        for (TaskDefinition<?> task : plan.selectedTasks()) {
            out.append("  ").append(aliases.get(task.id())).append("[\"")
                    .append(mermaidLabel(task)).append("\"]");
            if (targets.contains(task.id())) {
                out.append(":::target");
            }
            out.append('\n');
        }
        for (PlanEdge edge : uniqueEdges(plan)) {
            String from = alias(aliases, edge.fromId());
            String to = alias(aliases, edge.toId());
            if (edge.type() == PlanEdge.EdgeType.DEPENDS_ON) {
                out.append("  ").append(from).append(" -->|dependsOn| ").append(to).append('\n');
            } else {
                out.append("  ").append(from).append(" -.->|after| ").append(to).append('\n');
            }
        }
        return out.toString();
    }

    private String plantuml(ExecutionPlan plan, String source, Locale locale) {
        Map<String, String> aliases = aliases(plan, "t");
        Set<String> targets = Set.copyOf(plan.requestedTargets());
        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        out.append("' gretl plan: ").append(singleLine(source)).append('\n');
        out.append("title ").append(Messages.format(locale,
                "plan.graph.title", singleLine(source))).append('\n');
        out.append("skinparam shadowing false\n");
        for (TaskDefinition<?> task : plan.selectedTasks()) {
            out.append("rectangle \"").append(plantumlLabel(task)).append("\" as ")
                    .append(aliases.get(task.id()));
            if (targets.contains(task.id())) {
                out.append(" #FFE8A3");
            }
            out.append('\n');
        }
        for (PlanEdge edge : uniqueEdges(plan)) {
            String from = alias(aliases, edge.fromId());
            String to = alias(aliases, edge.toId());
            if (edge.type() == PlanEdge.EdgeType.DEPENDS_ON) {
                out.append(from).append(" --> ").append(to).append(" : dependsOn\n");
            } else {
                out.append(from).append(" ..> ").append(to).append(" : after\n");
            }
        }
        out.append("@enduml\n");
        return out.toString();
    }

    private static Map<String, String> aliases(ExecutionPlan plan, String prefix) {
        Map<String, String> aliases = new LinkedHashMap<>();
        int index = 0;
        for (TaskDefinition<?> task : plan.selectedTasks()) {
            aliases.put(task.id(), prefix + index++);
        }
        return aliases;
    }

    private static String alias(Map<String, String> aliases, String taskId) {
        String alias = aliases.get(taskId);
        if (alias == null) {
            throw new IllegalArgumentException("plan edge references unselected task: " + taskId);
        }
        return alias;
    }

    private static List<PlanEdge> uniqueEdges(ExecutionPlan plan) {
        Set<String> seen = new LinkedHashSet<>();
        List<PlanEdge> result = new ArrayList<>();
        for (PlanEdge edge : plan.edges()) {
            String key = edge.fromId() + '\u0000' + edge.toId() + '\u0000' + edge.type();
            if (seen.add(key)) {
                result.add(edge);
            }
        }
        return result;
    }

    private static String mermaidLabel(TaskDefinition<?> task) {
        List<String> parts = new ArrayList<>();
        parts.add(task.id());
        parts.add(task.operation() == null ? "action" : task.operation().symbol());
        parts.add("#" + task.registrationIndex());
        return String.join("<br/>", parts.stream().map(PlanGraphRenderer::mermaidText).toList());
    }

    private static String plantumlLabel(TaskDefinition<?> task) {
        List<String> parts = new ArrayList<>();
        parts.add(task.id());
        parts.add(task.operation() == null ? "action" : task.operation().symbol());
        parts.add("#" + task.registrationIndex());
        return String.join("\\n", parts.stream().map(PlanGraphRenderer::plantumlText).toList());
    }

    private static String mermaidText(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\r", "")
                .replace("\n", "<br/>");
    }

    private static String plantumlText(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    private static String singleLine(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').strip();
    }
}
