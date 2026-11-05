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
    private final TaskExecutionControl control;
    private final ch.so.agi.gretl.runtime.api.TimeBudget budget;
    private final List<InvocationOutcome> invocations = new ArrayList<>();
    // Identity only: a caught child failure creates no obligation. If that same
    // exception escapes later, preserve its ordering before release cancellation.
    private final java.util.Set<Throwable> failuresBeforeCancellation =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private TaskContextImpl context;

    TaskFrame(TaskDefinition<?> definition, int taskIndex, Path taskDirectory,
              TaskExecutionControl control) {
        this.definition = definition;
        this.taskIndex = taskIndex;
        this.taskDirectory = taskDirectory;
        this.control = control;
        this.budget = control.budget();
        this.closeStack = new CloseStack();
    }

    TaskExecutionControl control() { return control; }

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

    void recordFailure(Throwable failure) {
        failuresBeforeCancellation.add(failure);
    }

    boolean failureBeforeCancellation(Throwable failure) {
        return failuresBeforeCancellation.contains(failure);
    }
}
