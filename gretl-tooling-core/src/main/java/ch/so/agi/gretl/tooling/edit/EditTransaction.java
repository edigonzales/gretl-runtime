package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.language.ProjectNameRules;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.Symbol;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.SymbolId;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.source.TextSpan;
import ch.so.agi.gretl.tooling.workspace.DocumentStore;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Accumulates one intent's source changes and expected semantic effects before the single verifier pass. */
final class EditTransaction {
    final AnalysisSnapshot before;
    final EditStyle style;
    private final Map<URI, SourceEditBuilder> builders = new LinkedHashMap<>();
    private final List<Predicate<AnalysisSnapshot>> expectations = new ArrayList<>();
    private final List<String> notices = new ArrayList<>();
    boolean literalRename;

    EditTransaction(AnalysisSnapshot before, EditStyle style) {
        this.before = before;
        this.style = style;
    }

    SourceText source(URI id) {
        return before.source(id).orElseThrow(() -> new IllegalArgumentException("EDIT_SOURCE_NOT_CAPTURED"));
    }

    Symbol symbol(SymbolId id) {
        return before.analysis().unique(id).orElseThrow(() -> new IllegalArgumentException("EDIT_AMBIGUOUS_TARGET"));
    }

    SourceEditBuilder edits(URI id) {
        return builders.computeIfAbsent(DocumentStore.identity(id), key -> new SourceEditBuilder(source(key)));
    }

    void replace(URI id, TextSpan span, String text) {
        edits(id).replace(span, source(id).slice(span), text);
    }

    void expect(Predicate<AnalysisSnapshot> expectation) { expectations.add(expectation); }
    void notice(String notice) { notices.add(notice); }

    SourceValueWriter writer(Symbol scope) {
        return new SourceValueWriter(target -> spelling(scope.scope(), target));
    }

    String spelling(String scope, SymbolId target) {
        symbol(target);
        return before.analysis().capture().project().projectMode()
                ? ProjectNameRules.parseQualified(scope).renderReference(target.qualifiedName()) : target.qualifiedName();
    }

    PlannedEdit finish(String summary, CancellationToken cancellation) {
        return finish(summary,cancellation,false);
    }

    PlannedEdit finish(String summary, CancellationToken cancellation,boolean intermediateConfiguration) {
        return new EditVerifier().verify(before, changes(), summary,
                after -> expectations.stream().allMatch(expected -> expected.test(after)), notices, cancellation, literalRename,intermediateConfiguration);
    }

    Map<URI,List<SourceReplacement>> changes() {
        var changes = new LinkedHashMap<URI, List<SourceReplacement>>();
        builders.forEach((id, builder) -> changes.put(id, builder.build()));
        return Map.copyOf(changes);
    }
}
