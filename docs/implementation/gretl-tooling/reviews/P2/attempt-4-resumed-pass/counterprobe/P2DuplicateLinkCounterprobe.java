import ch.so.agi.gretl.language.PlanSemantics;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.CatalogRepository;
import ch.so.agi.gretl.tooling.semantic.ProjectAnalyzer;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.DocumentStore;
import ch.so.agi.gretl.tooling.workspace.ProjectResolver.*;
import ch.so.agi.gretl.tooling.workspace.ProjectResolver.ProjectRequest;
import ch.so.agi.gretl.tooling.workspace.SourceRepository;
import ch.so.agi.gretl.tooling.workspace.SourceRepository.*;
import ch.so.agi.gretl.tooling.workspace.WorkspaceManager;
import java.net.URI;
import java.util.*;

/**
 * New P2 counterprobe: duplicate dependency identity through local vs qualified spelling in an
 * included subproject, plus the same target in dependsOn and after (allowed as two edge kinds).
 * Neither combination is covered by PlanSemanticsTest, ProjectAnalyzerTest or the previous
 * attempt-1/attempt-3 counterprobes.
 */
public final class P2DuplicateLinkCounterprobe {
    static final URI ROOT = URI.create("file:/probe");
    static final URI SETTINGS = URI.create("file:/probe/settings.gretl");
    static final URI A_JOB = URI.create("file:/probe/a/job.groovy");
    static final URI B_JOB = URI.create("file:/probe/b/job.groovy");

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        System.out.println("OK " + message);
    }

    static final class Repository implements SourceRepository {
        final Map<URI, String> files = new LinkedHashMap<>();
        public SourceRead read(URI id, ReadPolicy policy, CancellationToken cancellation) {
            cancellation.check();
            String text = files.get(id);
            return text == null ? SourceRead.unavailable("SOURCE_MISSING", id)
                    : SourceRead.found(new SourceText(id, text, null, "DISK", true, text));
        }
        public SourceListing listProjectEntries(URI root, ScanBudget budget, CancellationToken cancellation) {
            return new SourceListing(List.copyOf(files.keySet()), List.of(), true);
        }
    }

    static List<String> codes(Analysis analysis) {
        return analysis.diagnostics().stream().map(Diagnostic::code).toList();
    }

    static long count(List<PlanSemantics.Edge> edges, String from, String to, PlanSemantics.EdgeKind kind) {
        return edges.stream().filter(e -> e.fromId().equals(from) && e.toId().equals(to) && e.kind() == kind).count();
    }

    public static void main(String[] args) {
        var repository = new Repository();
        repository.files.put(A_JOB, """
                action('run') {}
                action('use', dependsOn:['run', ':a:run'], after:[':b:run', ':b:run']) {}
                action('both', dependsOn:'run', after:'run') {}
                """);
        repository.files.put(B_JOB, "action('run') {}");
        repository.files.put(SETTINGS, "include 'a','b'\ndefaults ':a:use'");

        try (var manager = new WorkspaceManager(new DocumentStore(), new CatalogRepository())) {
            manager.addRoot(ROOT, repository);
            manager.updateConfiguration(ROOT, new WorkspaceManager.Configuration("en", Map.of()));
            var capture = manager.capture(new ProjectRequest(ROOT, A_JOB), CancellationToken.NONE);
            check(capture.project().projectMode() && capture.project().complete(), "project mode resolves both subprojects");
            check(capture.project().jobs().stream().map(JobSource::documentId).toList().equals(List.of(A_JOB, B_JOB)),
                    "both subproject jobs are captured");

            var analysis = new ProjectAnalyzer().analyze(capture, CancellationToken.NONE);
            var codeList = codes(analysis);
            check(analysis.capabilities().graphComplete(), "duplicate-link project keeps a complete graph");
            check(analysis.symbols().keySet().stream().filter(id -> id.namespace() == Namespace.TASK).count() == 4,
                    "four distinct qualified tasks exist (a:run, a:use, a:both, b:run)");

            var duplicates = analysis.diagnostics().stream().filter(d -> d.code().equals("DUPLICATE_TASK_LINK")).toList();
            check(duplicates.size() == 2, "exactly two duplicate links are reported");
            check(duplicates.stream().allMatch(d -> d.anchor().documentId().equals(A_JOB)),
                    "both duplicate diagnostics are anchored in subproject a");
            check(duplicates.stream().allMatch(d -> d.severity() == Severity.ERROR),
                    "duplicate links are errors");

            var graph = analysis.graph();
            check(graph.edges().size() == 4, "deduplicated graph has exactly four edges");
            check(count(graph.edges(), ":a:run", ":a:use", PlanSemantics.EdgeKind.DEPENDS_ON) == 1,
                    "local and qualified spellings yield one dependsOn edge, not two");
            check(count(graph.edges(), ":b:run", ":a:use", PlanSemantics.EdgeKind.AFTER) == 1,
                    "duplicated cross-project after spelling yields one after edge");
            check(count(graph.edges(), ":a:run", ":a:both", PlanSemantics.EdgeKind.DEPENDS_ON) == 1
                            && count(graph.edges(), ":a:run", ":a:both", PlanSemantics.EdgeKind.AFTER) == 1,
                    "same target in dependsOn and after is kept as one edge per kind");
            check(codeList.stream().noneMatch(c -> Set.of("UNKNOWN_TASK_REFERENCE", "AMBIGUOUS_REFERENCE", "DYNAMIC_REFERENCE").contains(c)),
                    "no unresolved, ambiguous or dynamic task references");
            check(analysis.defaultTargets().equals(List.of(":a:use")), "settings default is qualified into subproject a");

            var semantics = new PlanSemantics();
            var selected = semantics.plan(graph, List.of(), analysis.defaultTargets());
            check(selected.orderedIds().equals(List.of(":a:run", ":a:use")),
                    "default plan orders a:run before a:use and excludes unselected b:run");
            check(selected.activeEdges().size() == 1 && selected.activeEdges().getFirst().kind() == PlanSemantics.EdgeKind.DEPENDS_ON,
                    "only the selected dependsOn edge is active for the default plan");
            check(selected.dataPredecessors().get(":a:use").equals(Set.of(":a:run")),
                    "a:use has a:run as its sole data predecessor");

            var both = semantics.plan(graph, List.of(":a:both"), List.of());
            check(both.orderedIds().equals(List.of(":a:run", ":a:both")),
                    "cross-kind target plan orders producer before consumer");
            check(both.activeEdges().size() == 2
                            && both.activeEdges().stream().map(PlanSemantics.Edge::kind).collect(java.util.stream.Collectors.toSet())
                            .equals(Set.of(PlanSemantics.EdgeKind.DEPENDS_ON, PlanSemantics.EdgeKind.AFTER)),
                    "cross-kind plan activates both edges without a cycle");
            check(both.dataPredecessors().get(":a:both").equals(Set.of(":a:run")),
                    "after edge adds no data predecessor");
        }
        System.out.println("COUNTERPROBE PASS");
    }
}
