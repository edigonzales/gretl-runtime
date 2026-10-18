package ch.so.agi.gretl.runtime.runner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Internal lifetime object for one named task execution. */
final class TaskFrame {
    private final TaskDefinition<?> definition;
    private final int taskIndex;
    private final Path taskDirectory;
    private final CloseStack closeStack;
    private final ch.so.agi.gretl.runtime.api.TimeBudget budget;
    private final List<InvocationOutcome> invocations = new ArrayList<>();
    private TaskContextImpl context;

    TaskFrame(TaskDefinition<?> definition, int taskIndex, Path taskDirectory,
              ch.so.agi.gretl.runtime.api.TimeBudget budget) {
        this.definition = definition;
        this.taskIndex = taskIndex;
        this.taskDirectory = taskDirectory;
        this.budget = budget;
        this.closeStack = new CloseStack();
    }

    TaskDefinition<?> definition() {
        return definition;
    }

    int taskIndex() {
        return taskIndex;
    }

    Path taskDirectory() {
        return taskDirectory;
    }

    CloseStack closeStack() {
        return closeStack;
    }

    ch.so.agi.gretl.runtime.api.TimeBudget budget() {
        return budget;
    }

    void context(TaskContextImpl context) {
        this.context = context;
    }

    TaskContextImpl context() {
        return context;
    }

    void addInvocation(InvocationOutcome outcome) {
        invocations.add(outcome);
    }

    List<InvocationOutcome> invocations() {
        return List.copyOf(invocations);
    }
}
