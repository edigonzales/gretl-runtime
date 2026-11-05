package ch.so.agi.gretl.tooling.query;

import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.ParsedDocument;
import ch.so.agi.gretl.tooling.syntax.TokenIndex;
import ch.so.agi.gretl.tooling.workspace.DocumentStore;
import java.net.URI;
import ch.so.agi.gretl.tooling.workspace.IncludeModel.ApplicationId;
import java.util.Objects;
import java.util.Optional;

/** A position in exactly one captured analysis, never in the host's later mutable buffer. */
public record QueryContext(AnalysisSnapshot snapshot, URI documentId, int offsetUtf16,ApplicationId application) {
    public QueryContext(AnalysisSnapshot snapshot,URI documentId,int offsetUtf16){this(snapshot,documentId,offsetUtf16,infer(snapshot,documentId));}
    private static ApplicationId infer(AnalysisSnapshot snapshot,URI document){
        var id=DocumentStore.identity(document);var applications=snapshot.analysis().capture().project().includeClosure().applications().stream().map(a->a.id()).filter(a->a.sourceDocument().equals(id)).distinct().toList();
        return applications.size()==1?applications.getFirst():null;
    }
    public boolean accepts(ApplicationId candidate){return candidate==null||candidate.equals(application);}
    public boolean accepts(ch.so.agi.gretl.tooling.semantic.SemanticModel.Symbol symbol){return symbol.application()==null||accepts(symbol.application().id());}
    public boolean ambiguous(){return application==null&&snapshot.analysis().capture().project().includeClosure().applications().stream().filter(a->a.id().sourceDocument().equals(documentId)).count()>1;}
    public QueryContext {
        Objects.requireNonNull(snapshot);
        documentId = DocumentStore.identity(documentId);
        if(application!=null&&(!application.sourceDocument().equals(documentId)||!application.project().equals(snapshot.analysis().capture().project().id())))throw new IllegalArgumentException("QUERY_CONTEXT_MISMATCH");
        if(application!=null&&snapshot.analysis().capture().project().includeClosure().applications().stream().noneMatch(a->a.id().equals(application))
                &&snapshot.analysis().capture().ownership().contexts(documentId).stream().noneMatch(a->a.id().equals(application)))throw new IllegalArgumentException("QUERY_CONTEXT_UNKNOWN");
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
