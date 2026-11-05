package ch.so.agi.gretl.runtime.runner;

import ch.so.agi.gretl.runtime.api.TaskRef;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FinalizerDefinitionTest {
    @TempDir Path root;
    private TaskRegistration.Builder<String> task(String id) { return TaskRegistration.builder(id,String.class).body(ctx -> id); }
    @Test void forwardNamesAndRefAndLinkFormsAreResolvedAtFreeze() {
        var builder=new JobDefinitionBuilder(root);
        var f=builder.register(task("f").build());
        builder.register(task("a").finalizedBy(f).after(f).build());
        builder.register(task("b").finalizedBy("g").build());
        builder.register(task("c").finalizedBy(TaskLink.of(f)).build());
        builder.register(task("d").finalizedBy(List.of(TaskLink.named("g"))).build());
        builder.register(task("g").finalizedBy(List.of()).build());builder.defaults("b");
        var definition=builder.freeze();var plan=new ExecutionPlanner().plan(definition,List.of());
        assertEquals(List.of("b","g"),plan.selectedTasks().stream().map(TaskDefinition::id).toList());
        assertEquals(List.of(f),definition.tasks().get(1).finalizedBy());
        assertEquals(List.of(f),definition.tasks().get(1).after());
        assertThrows(UnsupportedOperationException.class,()->definition.tasks().get(1).finalizedBy().clear());
        assertTrue(plan.dataPredecessors("g").isEmpty());
        assertEquals(new PlanEdge("b","g",PlanEdge.EdgeType.FINALIZED_BY),plan.edges().getFirst());
    }
    @Test void invalidFinalizerDefinitionsAreSpecificAndOwnerLocated() {
        for(String kind:List.of("missing","duplicate","self","foreign")) {
            var b=new JobDefinitionBuilder(root);var owner=task("owner");
            String code;
            switch(kind) {
                case "missing" -> {owner.finalizedBy("missing");code="UNKNOWN_TASK_REFERENCE";}
                case "duplicate" -> {owner.finalizedBy("f","f");code="DUPLICATE_TASK_LINK";}
                case "self" -> {owner.finalizedBy("owner");code="SELF_FINALIZER";}
                default -> {var other=new JobDefinitionBuilder(root);TaskRef<?> f=other.register(task("f").build());owner.finalizedBy(f);code="FOREIGN_TASK_REFERENCE";}
            }
            b.register(owner.build());b.register(task("f").build());
            var error=assertThrows(DefinitionException.class,b::freeze);assertEquals(code,error.code());assertTrue(error.getMessage().contains("owner"));
        }
    }
    @Test void oldConstructorsAndEmptyListsPreserveLegacyDefinitions() {
        var old=new TaskRegistration<>("a",String.class,"A",List.of(),List.of(),null,null,ctx->"a",null,null);
        assertTrue(old.finalizedBy().isEmpty());
        var b=new JobDefinitionBuilder(root);var ref=b.register(old);b.defaults("a");var d=b.freeze();
        var task=d.tasks().getFirst();
        var compatibility=new TaskDefinition<>(ref,0,"A",List.of(),List.of(),null,null,ctx->"a",null,null);
        assertTrue(compatibility.finalizedBy().isEmpty());assertEquals(List.of("a"),new ExecutionPlanner().plan(d,List.of()).selection().normalDemand().stream().toList());
    }

}
