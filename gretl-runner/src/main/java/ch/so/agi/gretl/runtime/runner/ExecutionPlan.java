package ch.so.agi.gretl.runtime.runner;

import ch.so.agi.gretl.language.PlanSemantics.PlanSelection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Binds a logical plan to the executable tasks of one job definition. */
public record ExecutionPlan(
        UUID definitionId,
        List<TaskDefinition<?>> selectedTasks,
        PlanSelection selection) {

    public ExecutionPlan {
        Objects.requireNonNull(definitionId, "definitionId");
        selectedTasks = List.copyOf(selectedTasks);
        Objects.requireNonNull(selection, "selection");
        if (!selectedTasks.stream().map(TaskDefinition::id).toList().equals(selection.orderedIds())) {
            throw new IllegalArgumentException("Selected tasks must match the plan order");
        }
    }
}
