package ch.so.agi.gretl.tooling.semantic;

import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.ParsedDocument;
import ch.so.agi.gretl.tooling.workspace.DocumentStore;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One published analysis identity shared by queries, graph navigation and edit plans. */
public final class AnalysisSnapshot {
    private final UUID id;
    private final SemanticModel.Analysis analysis;
    private final ReferenceIndex references;

    public AnalysisSnapshot(UUID id, SemanticModel.Analysis analysis) {
        this.id = Objects.requireNonNull(id);
        this.analysis = Objects.requireNonNull(analysis);
        this.references = new ReferenceIndex(analysis);
    }

    public UUID id() { return id; }
    public SemanticModel.Analysis analysis() { return analysis; }
    public ReferenceIndex references() { return references; }

    public static AnalysisSnapshot of(SemanticModel.Analysis analysis) {
        return new AnalysisSnapshot(UUID.randomUUID(), analysis);
    }

    public Optional<SourceText> source(URI id) {
        var read = analysis.capture().project().sources().reads().get(DocumentStore.identity(id));
        return read == null ? Optional.empty() : read.source();
    }

    public Optional<ParsedDocument> document(URI id) {
        return Optional.ofNullable(analysis.capture().project().documents().get(DocumentStore.identity(id)));
    }

    public Map<URI, SourceText> sources() {
        return analysis.capture().project().sources().texts();
    }
}
