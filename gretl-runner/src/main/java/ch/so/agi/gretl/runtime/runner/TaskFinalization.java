package ch.so.agi.gretl.runtime.runner;

import java.util.List;

/** Selection and actual execution causes, distinct from a task's terminal outcome. */
public record TaskFinalization(boolean normalSelected, List<String> declaredOwners,
        List<String> activatingOwners, List<String> executedRoles) {
    public TaskFinalization {
        declaredOwners=List.copyOf(declaredOwners);
        activatingOwners=List.copyOf(activatingOwners);
        executedRoles=List.copyOf(executedRoles);
    }
}
