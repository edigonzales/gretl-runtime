import ch.so.agi.gretl.language.PlanSemantics;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.*;
import static ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import ch.so.agi.gretl.tooling.semantic.*;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.*;
import ch.so.agi.gretl.tooling.workspace.WorkspaceManager.WorkspaceCapture;
import ch.so.agi.gretl.tooling.workspace.ProjectResolver.*;
import ch.so.agi.gretl.tooling.workspace.SourceRepository.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** New P2 counterprobe: dirty settings/SQL in two subprojects + transitive target-dependent mixed cycle. */
public final class P2NewCounterprobe {
    static final URI ROOT = URI.create("file:/probe");
    static final URI SETTINGS = URI.create("file:/probe/settings.gretl");
    static final URI A_JOB = URI.create("file:/probe/a/job.groovy");
    static final URI B_JOB = URI.create("file:/probe/b/job.groovy");
    static final URI A_SQL = URI.create("file:/probe/a/sql/update.sql");
    static final URI B_SQL = URI.create("file:/probe/b/sql/update.sql");

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        System.out.println("OK " + message);
    }

    static InputDescriptor input(String name, String type, boolean required, List<String> allowed) {
        return new InputDescriptor(name, "Input", required, false, null, allowed, "example",
                ValueShape.fromMetadata(type), null, false, Map.of());
    }

    static CatalogRepository catalog() {
        var repository = new CatalogRepository();
        var codec = new CatalogJsonCodec();
        var operations = List.of(
                new OperationDescriptor("probe.echo", "Echo", "Probe echo", "counterprobe",
                        List.of(input("text", "text", true, List.of())),
                        List.of(new OutputDescriptor("file", "File", ValueShape.fromMetadata("path"), false)),
                        "task('x', Echo) { text='x' }", false),
                new OperationDescriptor("probe.duckdb", "DuckDbSqlExecutor", "Probe SQL", "counterprobe",
                        List.of(input("sqlFiles", "list", true, List.of()),
                                input("sqlParameters", "map", false, List.of()),
                                input("sqlParameterSets", "list", false, List.of())),
                        List.of(), "task('sql', DuckDbSqlExecutor) { sqlFiles=[file('sql/q.sql')] }", true));
        var catalog = new OperationCatalog(1, "probe", "probe", "", List.of("counterprobe"), operations, "COMPLETE");
        var load = repository.loadBundled(codec.encodeCanonical(codec.identify(catalog)), null);
        check(load.result().state() == CatalogJsonCodec.State.AVAILABLE, "probe catalog is available");
        return repository;
    }

    static final class Repository implements SourceRepository {
        final Map<URI, String> files = new LinkedHashMap<>();
        final AtomicBoolean analysisIo = new AtomicBoolean();
        public SourceRead read(URI id, ReadPolicy policy, CancellationToken cancellation) {
            if (analysisIo.get()) throw new AssertionError("analysis performed source IO after capture: " + id);
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

    public static void main(String[] args) {
        // Part 1: transitive after/dependsOn mixed cycle activated only by one target.
        var semantics = new PlanSemantics();
        var nodes = List.of(new PlanSemantics.Node("a", 0), new PlanSemantics.Node("b", 1),
                new PlanSemantics.Node("c", 2), new PlanSemantics.Node("outside1", 3),
                new PlanSemantics.Node("outside2", 4));
        var edges = List.of(
                new PlanSemantics.Edge("a", "b", PlanSemantics.EdgeKind.DEPENDS_ON),
                new PlanSemantics.Edge("b", "c", PlanSemantics.EdgeKind.DEPENDS_ON),
                new PlanSemantics.Edge("c", "a", PlanSemantics.EdgeKind.AFTER),
                new PlanSemantics.Edge("outside1", "outside2", PlanSemantics.EdgeKind.AFTER),
                new PlanSemantics.Edge("outside2", "outside1", PlanSemantics.EdgeKind.AFTER));
        var graph = new PlanSemantics.DeclaredGraph(nodes, edges);
        check(semantics.validateDeclaredDependencies(graph).isEmpty(),
                "unselected after-only cycle is not a declared dependsOn problem");
        var onlyA = semantics.plan(graph, List.of("a"), List.of());
        check(onlyA.orderedIds().equals(List.of("a")) && onlyA.activeEdges().isEmpty(),
                "target a alone does not activate the transitive mixed cycle");
        var cycle = assertCycle(() -> semantics.plan(graph, List.of("c"), List.of()));
        check(cycle.code().equals("PLAN_CYCLE") && cycle.problem().edges().size() == 3,
                "target c pulls in b and a transitively and exposes the three-edge mixed cycle");
        check(semantics.dataPredecessors(graph, "c").equals(Set.of("a", "b")),
                "c has a and b as transitive data predecessors");
        check(semantics.dataPredecessors(graph, "a").isEmpty(),
                "after edge grants a no data predecessor");

        // Part 2: dirty settings and dirty SQL in two included subprojects with equal task names.
        var repository = new Repository();
        repository.files.put(A_JOB,
                "action('run') {}; action('a', after:'c') {}; action('b', dependsOn:'a') {}; action('c', dependsOn:'b') {}");
        repository.files.put(B_JOB,
                "action('run') {}; action('use', dependsOn:'run') {}; task('sql', DuckDbSqlExecutor) { sqlFiles=[file('sql/update.sql')]; sqlParameters=[onlyB:'x'] }");
        repository.files.put(A_SQL, "select ${onlyA}");
        repository.files.put(B_SQL, "select ${diskOnly}");
        repository.files.put(SETTINGS, "include 'a','b'\ndefaults ':a:a', ':a:c'");
        var store = new DocumentStore();
        store.open(SETTINGS, "gretl", 1, "include 'a','b'\ndefaults ':a:a'");
        store.open(B_SQL, "sql", 7, "select ${onlyB}, ${missingB}");
        try (var manager = new WorkspaceManager(store, catalog())) {
            manager.addRoot(ROOT, repository);
            WorkspaceCapture first = manager.capture(new ProjectRequest(ROOT, A_JOB), CancellationToken.NONE);
            repository.analysisIo.set(true);
            Analysis safe = new ProjectAnalyzer().analyze(first, CancellationToken.NONE);
            repository.analysisIo.set(false);
            check(first.project().projectMode() && first.project().complete(),
                    "project mode resolves both included subprojects");
            check(first.project().jobs().stream().map(JobSource::documentId).toList().equals(List.of(A_JOB, B_JOB)),
                    "both subproject jobs are captured in include order");
            check(codes(safe).stream().noneMatch(c -> c.equals("PLAN_CYCLE")),
                    "dirty safe settings buffer prevents the disk default cycle");
            check(safe.symbols().containsKey(new SymbolId(Namespace.TASK, ":a:run"))
                            && safe.symbols().containsKey(new SymbolId(Namespace.TASK, ":b:run")),
                    "equal local task names remain separate qualified symbols");
            check(safe.references().stream().anyMatch(r -> r.kind() == ReferenceKind.DEPENDS_ON
                            && r.spelling().equals("run") && r.resolution() == Resolution.RESOLVED
                            && r.target().orElseThrow().equals(new SymbolId(Namespace.TASK, ":b:run"))),
                    "subproject b resolves run locally and never to a");
            check(codes(safe).stream().noneMatch(c -> c.equals("UNKNOWN_TASK_REFERENCE") || c.equals("AMBIGUOUS_REFERENCE")),
                    "no false cross-project reference diagnostic");
            var sqlProblems = safe.diagnostics().stream().filter(d -> d.code().equals("SQL_PARAMETER_MISSING")).toList();
            check(sqlProblems.size() == 1 && sqlProblems.getFirst().anchor().documentId().equals(B_SQL),
                    "only b/sql/update.sql reports the missing SQL parameter");
            check(safe.capture().project().sources().texts().get(B_SQL).slice(sqlProblems.getFirst().anchor().span())
                            .equals("${missingB}"),
                    "dirty SQL buffer is analyzed, not the disk text");
            check(manager.sameGeneration(first), "first capture is current before any buffer change");

            store.applyChanges(SETTINGS, 2, List.of(DocumentStore.TextChange.full(
                    "include 'a','b'\ndefaults ':a:a', ':a:c'")));
            check(!manager.sameGeneration(first), "dirty settings change invalidates the old capture");
            WorkspaceCapture second = manager.capture(new ProjectRequest(ROOT, A_JOB), CancellationToken.NONE);
            repository.analysisIo.set(true);
            Analysis cyclic = new ProjectAnalyzer().analyze(second, CancellationToken.NONE);
            repository.analysisIo.set(false);
            check(codes(cyclic).contains("PLAN_CYCLE"),
                    "changed dirty settings defaults activate the transitive target-dependent mixed cycle");
            check(cyclic.diagnostics().stream().anyMatch(d -> d.code().equals("PLAN_CYCLE")
                            && d.anchor().documentId().equals(A_JOB)),
                    "mixed cycle is anchored in subproject a, not globally invented");
            check(cyclic.diagnostics().stream().noneMatch(d -> d.code().equals("DEPENDENCY_CYCLE")),
                    "mixed cycle is not misreported as an unconditional dependsOn cycle");
        }
        System.out.println("COUNTERPROBE PASS");
    }

    @FunctionalInterface interface Planner { PlanSemantics.PlanSelection plan(); }
    static PlanSemantics.GraphException assertCycle(Planner planner) {
        try { planner.plan(); }
        catch (PlanSemantics.GraphException expected) { return expected; }
        throw new AssertionError("expected PLAN_CYCLE");
    }
}
