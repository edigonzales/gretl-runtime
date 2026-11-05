package ch.so.agi.gretl.language;

import java.util.*;
import static ch.so.agi.gretl.language.PlanSemantics.*;

/** Run-local, sequential scheduling state. No clocks, executable objects or I/O.
 * next() is an idempotent query; callers acknowledge every decision explicitly.
 */
public final class TaskSchedule {
    public enum Terminal { SUCCEEDED, SKIPPED, FAILED, CANCELLED, NOT_RUN }
    public sealed interface Decision permits Execute, NotRun, Done { }
    public record Execute(String taskId) implements Decision { }
    public record NotRun(String taskId, String reason) implements Decision { }
    public record Done() implements Decision { }

    private final PlanSelection plan;
    private final Map<String,List<String>> dependencies = new LinkedHashMap<>();
    private final Map<String,List<String>> predecessors = new LinkedHashMap<>();
    private final Map<String,List<String>> finalizers = new LinkedHashMap<>();
    private final Map<String,Terminal> terminals = new LinkedHashMap<>();
    private final Map<String,Set<String>> triggers = new LinkedHashMap<>();
    private String running;
    private String normalStop;
    private String completionStop;

    public TaskSchedule(PlanSelection plan) {
        this.plan = Objects.requireNonNull(plan);
        for (var edge : plan.activeEdges()) {
            predecessors.computeIfAbsent(edge.toId(), id -> new ArrayList<>()).add(edge.fromId());
            if (edge.kind() == EdgeKind.DEPENDS_ON)
                dependencies.computeIfAbsent(edge.toId(), id -> new ArrayList<>()).add(edge.fromId());
            if (edge.kind() == EdgeKind.FINALIZED_BY)
                finalizers.computeIfAbsent(edge.fromId(), id -> new ArrayList<>()).add(edge.toId());
        }
    }

    public Decision next() {
        if (running != null) throw new IllegalStateException("Task still running: " + running);
        if (terminals.size() == plan.orderedIds().size()) return new Done();
        if (completionStop != null)
            return new NotRun(firstPending(), completionStop);
        Set<String> demand = demand();
        // Failed real data prerequisites can never recover, in any role.
        for (String id : plan.registrationOrder()) if (pending(id) && (demand.contains(id) || plan.normalDemand().contains(id)) && blocked(id))
            return new NotRun(id, !demand.contains(id) && normalStop != null ? normalStop : "DEPENDENCY_FAILED");
        Set<String> potential = potential(demand);
        // Resolve inactive owners before checking the finalizer owner barriers.
        for (String id : plan.registrationOrder()) if (pending(id) && !potential.contains(id))
            return new NotRun(id, isFinalizer(id) && !plan.normalDemand().contains(id) ? "FINALIZER_NOT_TRIGGERED"
                    : normalStop == null ? "FINALIZER_NOT_TRIGGERED" : normalStop);
        for (String id : plan.registrationOrder()) {
            if (pending(id) && demand.contains(id)
                    && predecessors.getOrDefault(id,List.of()).stream().allMatch(terminals::containsKey))
                return new Execute(id);
        }
        throw new IllegalStateException("FINALIZER_ACTIVATION_DEADLOCK: pending "
                + plan.registrationOrder().stream().filter(this::pending).toList()
                + "; demand " + demand + "; edges " + plan.activeEdges());
    }

    public void started(String id) {
        if (!new Execute(id).equals(next())) throw new IllegalStateException("Task is not ready: " + id);
        running = id;
    }

    public void completed(String id, Terminal status) {
        Objects.requireNonNull(status);
        if (!pending(id)) throw new IllegalStateException("Unknown or terminal task: " + id);
        if (status == Terminal.NOT_RUN) {
            if (running != null || !(next() instanceof NotRun n) || !n.taskId().equals(id))
                throw new IllegalStateException("Task cannot become NOT_RUN: " + id);
        } else {
            if (!id.equals(running)) throw new IllegalStateException("Task did not start: " + id);
            for (String finalizer : finalizers.getOrDefault(id,List.of()))
                triggers.computeIfAbsent(finalizer, key -> new LinkedHashSet<>()).add(id);
            running = null;
        }
        terminals.put(id,status);
    }

    public void stopNormalWork(String reason) {
        if (normalStop == null) normalStop = Objects.requireNonNull(reason);
    }
    public void stopCompletionWork(String reason) {
        if (completionStop == null) completionStop = Objects.requireNonNull(reason);
    }
    public Map<String,Terminal> terminals() { return Collections.unmodifiableMap(new LinkedHashMap<>(terminals)); }
    public Set<String> activatingOwners(String id) { return Set.copyOf(triggers.getOrDefault(id,Set.of())); }
    public boolean normalDemand(String id) { return plan.normalDemand().contains(id); }
    public boolean draining() { return normalStop != null; }

    private boolean pending(String id) { return plan.dataPredecessors().containsKey(id) && !terminals.containsKey(id); }
    private String firstPending() { return plan.registrationOrder().stream().filter(this::pending).findFirst().orElseThrow(); }
    private boolean isFinalizer(String id) {
        return plan.activeEdges().stream().anyMatch(e -> e.kind() == EdgeKind.FINALIZED_BY && e.toId().equals(id));
    }
    private boolean blocked(String id) {
        return dependencies.getOrDefault(id,List.of()).stream().anyMatch(p -> {
            Terminal state = terminals.get(p);
            return state == Terminal.FAILED || state == Terminal.CANCELLED || state == Terminal.NOT_RUN;
        });
    }
    private Set<String> demand() {
        var result = new LinkedHashSet<String>();
        if (normalStop == null) plan.normalDemand().stream().filter(this::pending).forEach(result::add);
        triggers.keySet().stream().filter(this::pending).forEach(result::add);
        var queue = new ArrayDeque<>(result);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (terminals.containsKey(id) || blocked(id)) continue;
            for (String dependency : dependencies.getOrDefault(id,List.of()))
                if (result.add(dependency)) queue.addLast(dependency);
        }
        return result;
    }
    private Set<String> potential(Set<String> demand) {
        var result = new LinkedHashSet<String>();
        var pending = new ArrayDeque<>(demand);
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (!result.add(id) || terminals.containsKey(id) || blocked(id)) continue;
            pending.addAll(dependencies.getOrDefault(id,List.of()));
            pending.addAll(finalizers.getOrDefault(id,List.of()));
        }
        return result;
    }
}
