package ch.so.agi.gretl.tooling.query;

import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.ParsedDocument;
import ch.so.agi.gretl.tooling.syntax.TokenIndex;
import ch.so.agi.gretl.tooling.workspace.DocumentStore;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/** A position in exactly one captured analysis, never in the host's later mutable buffer. */
public record QueryContext(AnalysisSnapshot snapshot, URI documentId, int offsetUtf16) {
    public QueryContext {
        Objects.requireNonNull(snapshot);
        documentId = DocumentStore.identity(documentId);
        var source = snapshot.source(documentId).orElseThrow(() -> new IllegalArgumentException("QUERY_SOURCE_NOT_CAPTURED"));
        if (offsetUtf16 < 0 || offsetUtf16 > source.text().length()) throw new IllegalArgumentException("QUERY_POSITION");
    }

    public SourceText source() {
        return snapshot.source(documentId).orElseThrow();
    }

    public Optional<ParsedDocument> document() {
        return snapshot.document(documentId);
    }

    public Optional<TokenIndex> tokens() {
        if (document().isPresent()) return document().map(ParsedDocument::tokens);
        var settings = snapshot.analysis().capture().project().settings();
        return settings != null && settings.source().stamp().documentId().equals(documentId)
                ? Optional.of(settings.tokens()) : Optional.empty();
    }
}
