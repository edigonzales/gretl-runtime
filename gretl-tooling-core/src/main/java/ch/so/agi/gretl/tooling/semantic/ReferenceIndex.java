package ch.so.agi.gretl.tooling.semantic;

import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.workspace.DocumentStore;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Separates declarations from bound uses; no text search can turn comments or arbitrary strings into references. */
public final class ReferenceIndex {
    public record References(List<Reference> uses, boolean complete) {
        public References { uses = List.copyOf(uses); }
    }
    private final Analysis analysis;
    private final Map<SymbolId, List<Reference>> byTarget;
    private final Map<URI, List<Reference>> byDocument;

    public ReferenceIndex(Analysis analysis) {
        this.analysis = java.util.Objects.requireNonNull(analysis);
        var targets = new LinkedHashMap<SymbolId, List<Reference>>();
        var documents = new LinkedHashMap<URI, List<Reference>>();
        for (var reference : analysis.references()) {
            documents.computeIfAbsent(reference.anchor().documentId(), key -> new ArrayList<>()).add(reference);
            reference.target().ifPresent(id -> targets.computeIfAbsent(id, key -> new ArrayList<>()).add(reference));
        }
        byTarget = freeze(targets);
        byDocument = freeze(documents);
    }

    private static <K> Map<K, List<Reference>> freeze(Map<K, List<Reference>> values) {
        var copy = new LinkedHashMap<K, List<Reference>>();
        values.forEach((key, references) -> copy.put(key, references.stream()
                .sorted(Comparator.comparing((Reference r) -> r.anchor().documentId().toString())
                        .thenComparingInt(r -> r.anchor().span() == null ? -1 : r.anchor().span().startUtf16())).toList()));
        return Collections.unmodifiableMap(copy);
    }

    public References referencesTo(SymbolId target) {
        boolean complete = analysis.capabilities().referencesComplete() && analysis.unique(target).isPresent()
                && analysis.references().stream().noneMatch(r -> r.resolution() == Resolution.AMBIGUOUS && r.candidates().contains(target));
        return new References(byTarget.getOrDefault(target, List.of()), complete);
    }

    public List<Reference> referencesAt(URI document, int offset) {
        return byDocument.getOrDefault(DocumentStore.identity(document), List.of()).stream()
                .filter(r -> at(r.anchor(), offset)).toList();
    }

    public Set<SymbolId> targetsAt(URI document, int offset) {
        URI id = DocumentStore.identity(document);
        var targets = new LinkedHashSet<SymbolId>();
        for (var reference : referencesAt(id, offset)) targets.addAll(reference.candidates());
        for (var entry : analysis.symbols().entrySet()) for (var symbol : entry.getValue()) {
            if (!symbol.anchor().documentId().equals(id)) continue;
            if (at(symbol.anchor(), offset) || symbol.declaration().bindingSpan() != null
                    && symbol.declaration().bindingSpan().contains(offset)) targets.add(entry.getKey());
        }
        return Collections.unmodifiableSet(targets);
    }

    public List<Anchor> definitionsAt(URI document, int offset) {
        return targetsAt(document, offset).stream().flatMap(id -> analysis.symbols().getOrDefault(id, List.of()).stream())
                .map(Symbol::anchor).distinct().toList();
    }

    private static boolean at(Anchor anchor, int offset) {
        return anchor.span() != null && (anchor.span().contains(offset)
                || anchor.span().length() > 0 && offset == anchor.span().endUtf16());
    }
}
