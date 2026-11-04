package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** A checked transaction proposal; it has no method that writes a document or filesystem. */
public record PlannedEdit(UUID id, EditPreconditions preconditions, String summary,
        Map<URI, List<SourceReplacement>> changes, Map<URI, String> postimages,
        AnalysisSnapshot postimage, List<String> notices) {
    public PlannedEdit {
        Objects.requireNonNull(id);
        Objects.requireNonNull(preconditions);
        Objects.requireNonNull(summary);
        Objects.requireNonNull(postimage);
        var copy = new LinkedHashMap<URI, List<SourceReplacement>>();
        changes.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        changes = Collections.unmodifiableMap(copy);
        postimages = Map.copyOf(postimages);
        notices = List.copyOf(notices);
        if (!changes.keySet().equals(postimages.keySet())) throw new IllegalArgumentException("EDIT_POSTIMAGE_SET");
    }

    public boolean noOp() {
        return changes.isEmpty();
    }

    public boolean isCurrent(AnalysisSnapshot current) {
        return preconditions.matches(current);
    }
}
