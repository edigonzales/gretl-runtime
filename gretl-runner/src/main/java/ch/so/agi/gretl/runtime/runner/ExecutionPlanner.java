package ch.so.agi.gretl.runtime.runner;

import ch.so.agi.gretl.runtime.api.TaskRef;
import ch.so.agi.gretl.language.PlanSemantics;
import java.util.*;

/** Runtime identity adapter to the shared, non-executing graph contract. */
public final class ExecutionPlanner {
    public ExecutionPlan plan(JobDefinition definition,List<String> requestedTargets){
        Objects.requireNonNull(definition,"definition");var tasks=new LinkedHashMap<String,TaskDefinition<?>>();
        for(var task:definition.tasks())tasks.put(task.id(),task);
        // Definition UUID/reference ownership is a runtime concern, before logical graph validation.
        validateLinks(definition,tasks);
        var nodes=new ArrayList<PlanSemantics.Node>();var edges=new ArrayList<PlanSemantics.Edge>();
        for(var task:definition.tasks()){
            nodes.add(new PlanSemantics.Node(task.id(),task.registrationIndex()));
            for(var ref:task.dependsOn())edges.add(new PlanSemantics.Edge(ref.id(),task.id(),PlanSemantics.EdgeKind.DEPENDS_ON));
            for(var ref:task.after())edges.add(new PlanSemantics.Edge(ref.id(),task.id(),PlanSemantics.EdgeKind.AFTER));
            for(var ref:task.finalizedBy())edges.add(new PlanSemantics.Edge(task.id(),ref.id(),PlanSemantics.EdgeKind.FINALIZED_BY));
        }
        try {
            var selection=new PlanSemantics().plan(new PlanSemantics.DeclaredGraph(nodes,edges),requestedTargets,definition.defaultTargets());
            var ordered=new ArrayList<TaskDefinition<?>>();for(String id:selection.orderedIds())ordered.add(tasks.get(id));
            var selectedEdges=selection.activeEdges().stream().map(e->new PlanEdge(e.fromId(),e.toId(),PlanEdge.EdgeType.valueOf(e.kind().name()))).toList();
            return new ExecutionPlan(definition.definitionId(),selection.requestedTargets(),ordered,selection.dataPredecessors(),selectedEdges,selection);
        }catch(PlanSemantics.GraphException invalid){throw new PlanException(invalid.code(),invalid.getMessage());}
    }

    private static void validateLinks(
            JobDefinition definition, Map<String, TaskDefinition<?>> tasks) {
        for (TaskDefinition<?> task : definition.tasks()) {
            for (TaskRef<?> predecessor : task.dependsOn()) {
                if (!tasks.containsKey(predecessor.id()) || !definition.containsRef(predecessor)) {
                    throw new PlanException("UNKNOWN_TASK_REFERENCE",
                            "Task " + task.id() + " has unknown predecessor: " + predecessor);
                }
            }
            for (TaskRef<?> after : task.after()) {
                if (!tasks.containsKey(after.id()) || !definition.containsRef(after)) {
                    throw new PlanException("UNKNOWN_TASK_REFERENCE",
                            "Task " + task.id() + " has unknown after reference: " + after);
                }
            }
            for (TaskRef<?> finalizer : task.finalizedBy()) {
                if (!tasks.containsKey(finalizer.id()) || !definition.containsRef(finalizer)) {
                    throw new PlanException("UNKNOWN_TASK_REFERENCE",
                            "Task " + task.id() + " has unknown finalizer: " + finalizer);
                }
            }
        }
    }

}
