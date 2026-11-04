package ch.so.agi.gretl.tooling.edit;

import java.util.Objects;
import java.util.Optional;

/** A refusal cannot masquerade as an applicable empty WorkspaceEdit. */
public record EditPlanningResult(EditCapability capability, Optional<PlannedEdit> plan) {
    public EditPlanningResult {
        Objects.requireNonNull(capability);
        plan = Objects.requireNonNull(plan);
        if ((capability.state() == EditCapability.State.ALLOWED) != plan.isPresent()) throw new IllegalArgumentException("EDIT_RESULT_STATE");
    }
}
