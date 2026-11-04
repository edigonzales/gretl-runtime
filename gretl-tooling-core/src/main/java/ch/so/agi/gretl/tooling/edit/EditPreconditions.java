package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.workspace.ProjectResolver.ProjectId;
import ch.so.agi.gretl.tooling.workspace.SourceView.SourceVersion;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Includes absent sources and buffer incarnations, not only the files receiving edits. */
public record EditPreconditions(UUID snapshotId, UUID serverInstance, ProjectId project,
        long generation, long rootConfigurationGeneration, String configurationDigest,
        long catalogGeneration, String catalogId, Map<URI, SourceVersion> sources) {
    public EditPreconditions {
        Objects.requireNonNull(snapshotId);
        Objects.requireNonNull(serverInstance);
        Objects.requireNonNull(project);
        Objects.requireNonNull(configurationDigest);
        Objects.requireNonNull(catalogId);
        sources = Map.copyOf(sources);
    }

    public static EditPreconditions capture(AnalysisSnapshot snapshot) {
        var capture = snapshot.analysis().capture();
        return new EditPreconditions(snapshot.id(), capture.serverInstance(), capture.project().id(),
                capture.generation(), capture.rootConfigurationGeneration(), capture.configurationDigest(),
                capture.catalog().generation(), capture.catalog().result().catalog().map(c -> c.catalogId()).orElse(""),
                capture.project().sources().versions());
    }

    /** The host additionally checks current disk/buffer acquisition before supplying this snapshot. */
    public boolean matches(AnalysisSnapshot current) {
        return equals(capture(current));
    }
}
