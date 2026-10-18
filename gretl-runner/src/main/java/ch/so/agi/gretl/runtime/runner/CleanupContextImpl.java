package ch.so.agi.gretl.runtime.runner;

import ch.so.agi.gretl.runtime.api.CleanupContext;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** Restricted job-cleanup context; it cannot call tasks or finish the job. */
final class CleanupContextImpl extends BaseContext implements CleanupContext {
    CleanupContextImpl(RunEnvironment environment, Path workBase, CloseStack closeStack,
                       ch.so.agi.gretl.runtime.api.TimeBudget budget, String cleanupName) {
        super(environment, environment.identity(null, null), workBase, closeStack, budget,
                Optional.empty(), Map.of("cleanup.name", cleanupName));
    }
}
