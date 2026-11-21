package ch.so.agi.gretl.job.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.runner.ExecutionPlan;
import ch.so.agi.gretl.runtime.runner.ExecutionPlanner;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.JobDefinitionBuilder;
import ch.so.agi.gretl.runtime.runner.TaskDefinition;
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
    void finalizersHaveTheirOwnForwardEdgeInEachGraphFormat() {
        var builder=new JobDefinitionBuilder(tempDir);
        builder.register(TaskRegistration.builder("owner",Object.class).finalizedBy("release").body(ctx->null).build());
        builder.register(TaskRegistration.builder("release",Object.class).body(ctx->null).build());
        var plan=new ExecutionPlanner().plan(builder.freeze(),List.of("owner"));
        String mermaid=new PlanGraphRenderer().render(plan,PlanGraphFormat.MERMAID,"probe",Locale.ENGLISH);
        assertTrue(mermaid.contains("n0 ==>|finalizedBy| n1"),mermaid);assertTrue(mermaid.contains("completion candidate"),mermaid);assertFalse(mermaid.contains("|after|"),mermaid);
        String uml=new PlanGraphRenderer().render(plan,PlanGraphFormat.PLANTUML,"probe",Locale.ENGLISH);
        assertTrue(uml.contains("t0 -[#7B1FA2,bold]-> t1 : finalizedBy"),uml);assertTrue(uml.contains("completion candidate"),uml);assertFalse(uml.contains(": after"),uml);
    }

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
    void labelsAreEscaped() {
        JobDefinitionBuilder builder = new JobDefinitionBuilder(tempDir);
        String id = "a\"<&>\\\r\nb";
        builder.register(TaskRegistration.builder(id, Object.class)
                .body(context -> "x").build());
        JobDefinition definition = builder.freeze();
        ExecutionPlan plan = new ExecutionPlanner().plan(definition, List.of(id));

        String mermaid = new PlanGraphRenderer().render(plan,
                PlanGraphFormat.MERMAID, "job.groovy", Locale.ENGLISH);
        String plantuml = new PlanGraphRenderer().render(plan,
                PlanGraphFormat.PLANTUML, "job.groovy", Locale.ENGLISH);

        assertTrue(mermaid.contains("a&quot;&lt;&amp;&gt;\\<br/>b"), mermaid);
        assertTrue(plantuml.contains("a\\\"<&>\\\\\\nb"), plantuml);
    }

    @Test
    void duplicateLinksInAJavaDefinitionRenderOnce() {
        JobDefinitionBuilder builder = new JobDefinitionBuilder(tempDir);
        var producer = builder.register(TaskRegistration.builder("producer", Object.class)
                .body(context -> null).build());
        var consumer = builder.register(TaskRegistration.builder("consumer", Object.class)
                .body(context -> null).build());
        JobDefinition registered = builder.freeze();
        var linkedConsumer = new TaskDefinition<>(consumer, 1, "consumer",
                List.of(producer, producer), List.of(), List.of(), null, null,
                context -> null, null, null);
        var definition = new JobDefinition(registered.definitionId(),
                List.of(registered.requireTask(producer), linkedConsumer),
                List.of(), List.of("consumer"), List.of(), tempDir);
        var plan = new ExecutionPlanner().plan(definition, List.of());
        var renderer = new PlanGraphRenderer();

        assertEquals(2, plan.selection().activeEdges().size());
        String mermaid = renderer.render(plan, PlanGraphFormat.MERMAID, "probe", Locale.ENGLISH);
        String plantuml = renderer.render(plan, PlanGraphFormat.PLANTUML, "probe", Locale.ENGLISH);
        assertEquals(1, mermaid.lines().filter("  n0 -->|dependsOn| n1"::equals).count(), mermaid);
        assertEquals(1, plantuml.lines().filter("t0 --> t1 : dependsOn"::equals).count(), plantuml);
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
}
