package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.job.runtime.PlanGraphFormat;
import ch.so.agi.gretl.job.runtime.PlanGraphRenderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.runner.ExecutionPlan;
import ch.so.agi.gretl.runtime.runner.ExecutionPlanner;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.JobDefinitionBuilder;
import ch.so.agi.gretl.runtime.runner.PlanEdge;
import ch.so.agi.gretl.runtime.runner.TaskRegistration;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanGraphRendererTest {
    @TempDir
    Path tempDir;

    @Test
    void mermaidShowsSelectedDependenciesTargetsAndEdgeKinds() {
        ExecutionPlan plan = plan();

        String graph = new PlanGraphRenderer().render(plan,
                PlanGraphFormat.MERMAID, "job.groovy", Locale.ENGLISH);

        assertTrue(graph.startsWith("flowchart TB\n"), graph);
        assertTrue(graph.contains("n0[\"download<br/>action<br/>#0\"]"), graph);
        assertTrue(graph.contains("n1[\"unpack<br/>action<br/>#1\"]:::target"), graph);
        assertTrue(graph.contains("n2[\"notify<br/>action<br/>#2\"]:::target"), graph);
        assertTrue(graph.contains("n0 -->|dependsOn| n1"), graph);
        assertTrue(graph.contains("n1 -.->|after| n2"), graph);
        assertFalse(graph.contains("unselected"), graph);
        assertTrue(graph.contains("classDef target"), graph);
    }

    @Test
    void plantUmlShowsTargetsAndBothEdgeKinds() {
        ExecutionPlan plan = plan();

        String graph = new PlanGraphRenderer().render(plan,
                PlanGraphFormat.PLANTUML, "job.groovy", Locale.GERMAN);

        assertTrue(graph.startsWith("@startuml\n"), graph);
        assertTrue(graph.contains("title Ausfuehrungsplan: job.groovy"), graph);
        assertTrue(graph.contains("rectangle \"download\\naction\\n#0\" as t0"), graph);
        assertTrue(graph.contains("rectangle \"unpack\\naction\\n#1\" as t1 #FFE8A3"), graph);
        assertTrue(graph.contains("t0 --> t1 : dependsOn"), graph);
        assertTrue(graph.contains("t1 ..> t2 : after"), graph);
        assertTrue(graph.endsWith("@enduml\n"), graph);
    }

    @Test
    void labelsAreEscapedAndDuplicateEdgesAreRenderedOnce() {
        JobDefinitionBuilder builder = new JobDefinitionBuilder(tempDir);
        builder.register(TaskRegistration.builder("a\"b", Object.class)
                .body(context -> "x").build());
        JobDefinition definition = builder.freeze();
        ExecutionPlan base = new ExecutionPlanner().plan(definition, List.of("a\"b"));
        PlanEdge edge = new PlanEdge("a\"b", "a\"b", PlanEdge.EdgeType.DEPENDS_ON);
        ExecutionPlan plan = new ExecutionPlan(base.definitionId(), base.requestedTargets(),
                base.selectedTasks(), base.dataPredecessors(), List.of(edge, edge));

        String mermaid = new PlanGraphRenderer().render(plan,
                PlanGraphFormat.MERMAID, "job.groovy", Locale.ENGLISH);
        String plantuml = new PlanGraphRenderer().render(plan,
                PlanGraphFormat.PLANTUML, "job.groovy", Locale.ENGLISH);

        assertTrue(mermaid.contains("&quot;"), mermaid);
        assertTrue(plantuml.contains("\\\""), plantuml);
        assertEquals(1, occurrences(mermaid, "-->|dependsOn|"));
        assertEquals(1, occurrences(plantuml, ": dependsOn"));
    }

    private ExecutionPlan plan() {
        JobDefinitionBuilder builder = new JobDefinitionBuilder(tempDir);
        builder.register(TaskRegistration.builder("download", Object.class)
                .body(context -> "x").build());
        builder.register(TaskRegistration.builder("unpack", Object.class)
                .dependsOn("download").body(context -> "x").build());
        builder.register(TaskRegistration.builder("notify", Object.class)
                .after("unpack").body(context -> "x").build());
        builder.register(TaskRegistration.builder("unselected", Object.class)
                .body(context -> "x").build());
        builder.defaults("unpack", "notify");
        JobDefinition definition = builder.freeze();
        return new ExecutionPlanner().plan(definition, List.of("unpack", "notify"));
    }

    private static int occurrences(String value, String needle) {
        return value.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
