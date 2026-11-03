package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.CatalogJsonCodec;
import ch.so.agi.gretl.tooling.catalog.CatalogRepository;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import ch.so.agi.gretl.tooling.query.LanguageQueries;
import ch.so.agi.gretl.tooling.query.QueryContext;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.ProjectAnalyzer;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.Namespace;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.SymbolId;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.*;
import ch.so.agi.gretl.tooling.workspace.ProjectResolver.ProjectRequest;
import java.net.URI;
import java.util.*;

/** Independent read-only counterprobe for the T15/P3-03 refusal path. No product file is modified. */
public final class P3ReadonlyProbe {
    static final URI ROOT = URI.create("file:/work");
    static final URI JOB = URI.create("file:/work/job.groovy");
    static final SymbolId TASK = new SymbolId(Namespace.TASK, "a");
    private static int passed, failed;

    private static void check(String name, boolean ok, String detail) {
        if (ok) { passed++; System.out.println("PASS " + name + " :: " + detail); }
        else { failed++; System.out.println("FAIL " + name + " :: " + detail); }
    }

    private static void loadCatalog(CatalogRepository catalog) {
        var input = new InputDescriptor("input", "Input", true, false, null, List.of(), "input='x'",
                ValueShape.fromMetadata("text"), null, false, Map.of());
        var mode = new InputDescriptor("mode", "Mode", false, false, null, List.of("FAST", "SLOW"), "mode='FAST'",
                ValueShape.fromMetadata("text"), null, false, Map.of());
        var operation = new OperationDescriptor("test.echo", "Echo", "Test", "fixture", List.of(input, mode), List.of(), "example", true);
        var codec = new CatalogJsonCodec();
        catalog.loadBundled(codec.encodeCanonical(codec.identify(new OperationCatalog(1, "test", "test", "", List.of("fixture"), List.of(operation), "COMPLETE"))), null);
    }

    public static void main(String[] args) {
        var files = new HashMap<URI, String>();
        files.put(JOB, "task('a', Echo) { input='old' }\n");
        var store = new DocumentStore();
        var catalog = new CatalogRepository();
        try (var manager = new WorkspaceManager(store, catalog)) {
            loadCatalog(catalog);
            manager.addRoot(ROOT, new SourceRepository() {
                @Override public SourceRepository.SourceRead read(URI id, SourceRepository.ReadPolicy policy, CancellationToken cancellation) {
                    String text = files.get(id);
                    return text == null ? SourceRepository.SourceRead.unavailable("SOURCE_MISSING", id)
                            : SourceRepository.SourceRead.found(new SourceText(id, text, null, "DISK", false, text));
                }
                @Override public SourceRepository.SourceListing listProjectEntries(URI root, SourceRepository.ScanBudget budget, CancellationToken cancellation) {
                    throw new AssertionError("Unexpected scan");
                }
            });
            var captured = manager.capture(new ProjectRequest(ROOT, JOB, false, List.of(), List.of()), CancellationToken.NONE);
            var snapshot = AnalysisSnapshot.of(new ProjectAnalyzer().analyze(captured, CancellationToken.NONE));
            boolean capturedReadonly = snapshot.source(JOB).map(s -> !s.stamp().editable()).orElse(false);
            var planner = new SemanticEditPlanner();
            var set = planner.plan(snapshot, new SemanticEditIntent.SetField(TASK, "input", new EditableValue.Literal("new")),
                    EditStyle.defaults(), CancellationToken.NONE);
            String setReason = set.capability().reasons().isEmpty() ? "<none>" : set.capability().reasons().getFirst().code();
            check("readonly-setfield-refused", capturedReadonly
                            && set.capability().state() == EditCapability.State.REJECTED
                            && set.plan().isEmpty() && "EDIT_READONLY".equals(setReason),
                    "capturedReadonly=" + capturedReadonly + " state=" + set.capability().state()
                            + " plan=" + set.plan().isPresent() + " reason=" + setReason);

            var queries = new LanguageQueries();
            String text = files.get(JOB);
            int nameOffset = text.indexOf("'a'") + 1;
            var preparation = queries.prepareRename(new QueryContext(snapshot, JOB, nameOffset));
            boolean prepared = preparation.isPresent() && preparation.orElseThrow().target().equals(TASK);
            String prepareReason = preparation.isEmpty() || preparation.orElseThrow().capability().reasons().isEmpty()
                    ? "<none>" : preparation.orElseThrow().capability().reasons().getFirst().code();
            var renamed = queries.rename(new QueryContext(snapshot, JOB, nameOffset), "b");
            String renameReason = renamed.capability().reasons().isEmpty() ? "<none>" : renamed.capability().reasons().getFirst().code();
            check("readonly-rename-refused", prepared
                            && preparation.orElseThrow().capability().state() == EditCapability.State.REJECTED
                            && "EDIT_READONLY".equals(prepareReason)
                            && renamed.capability().state() == EditCapability.State.REJECTED
                            && renamed.plan().isEmpty() && "EDIT_READONLY".equals(renameReason),
                    "prepared=" + prepared + " prepareReason=" + prepareReason
                            + " renameState=" + renamed.capability().state() + " renameReason=" + renameReason);

            var value = snapshot.analysis().unique(TASK).orElseThrow().declaration().fields().getFirst().value();
            String verifierReason;
            try {
                new EditVerifier().verify(snapshot, Map.of(JOB, List.of(new SourceReplacement(value.span(), "'old'", "'new'"))),
                        "readonly", post -> true, List.of(), CancellationToken.NONE);
                verifierReason = "<accepted>";
            } catch (IllegalArgumentException refusal) {
                verifierReason = refusal.getMessage();
            }
            check("readonly-verifier-defence", "EDIT_READONLY".equals(verifierReason)
                            && text.equals(files.get(JOB)),
                    "verifierReason=" + verifierReason + " sourceUnchanged=" + text.equals(files.get(JOB)));
            System.out.println("SUMMARY passed=" + passed + " failed=" + failed);
            if (failed != 0) System.exit(1);
        }
    }
}
