import ch.so.agi.gretl.language.PlanSemantics;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.CatalogJsonCodec;
import ch.so.agi.gretl.tooling.catalog.CatalogRepository;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import ch.so.agi.gretl.tooling.semantic.ProjectAnalyzer;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.*;
import ch.so.agi.gretl.tooling.workspace.WorkspaceManager.WorkspaceCapture;
import java.net.URI;
import java.util.*;

/**
 * Reviewer-owned P2 counterprobe (new, not a copy of a product test).
 * A: a mixed after/dependsOn cycle exists for only one of two requested target sets,
 *    while the declared dependsOn graph stays acyclic.
 * B: two same-named SQL resources in two subprojects; only one is a dirty buffer.
 *    The static analysis must use that dirty buffer, keep the two resources apart,
 *    resolve equal local task names per subproject and invalidate the old capture when
 *    the dirty SQL changes.
 */
public final class P2ReviewCounterprobe {
    static int failures = 0;
    static void check(boolean condition, String message) {
        if (condition) System.out.println("PASS " + message);
        else { failures++; System.out.println("FAIL " + message); }
    }
    static void equal(Object expected, Object actual, String message) {
        check(Objects.equals(expected, actual), message + " expected=" + expected + " actual=" + actual);
    }

    // ---------- A: target-dependent mixed cycle ----------
    static void planSemanticsProbe() {
        var semantics = new PlanSemantics();
        var nodes = List.of(
                new PlanSemantics.Node("t1", 0),
                new PlanSemantics.Node("a", 1),
                new PlanSemantics.Node("b", 2));
        // t1 dependsOn a ; a after b ; b dependsOn t1.
        // Selecting t1 alone must not select b, so the after edge b->a is inactive.
        // Selecting t1 and b activates b->a and closes the mixed cycle b->a->t1->b.
        var edges = List.of(
                new PlanSemantics.Edge("a", "t1", PlanSemantics.EdgeKind.DEPENDS_ON),
                new PlanSemantics.Edge("b", "a", PlanSemantics.EdgeKind.AFTER),
                new PlanSemantics.Edge("t1", "b", PlanSemantics.EdgeKind.DEPENDS_ON));
        var graph = new PlanSemantics.DeclaredGraph(nodes, edges);
        check(semantics.validateDeclaredDependencies(graph).isEmpty(),
                "A1 declared dependsOn graph (a->t1, t1->b) is acyclic");
        equal(Set.of("a"), semantics.dataPredecessors(graph, "t1"),
                "A2 after does not grant data access; only a is a data predecessor of t1");
        var single = semantics.plan(graph, List.of("t1"), List.of());
        equal(List.of("a", "t1"), single.orderedIds(), "A3 selecting t1 alone has no after cycle");
        check(single.activeEdges().stream().noneMatch(e -> e.kind() == PlanSemantics.EdgeKind.AFTER),
                "A4 no after edge is active for the single-target plan");
        try {
            semantics.plan(graph, List.of("t1", "b"), List.of());
            check(false, "A5 selecting t1 and b must be rejected with PLAN_CYCLE");
        } catch (PlanSemantics.GraphException cycle) {
            equal("PLAN_CYCLE", cycle.code(), "A5 selecting t1 and b is rejected as PLAN_CYCLE");
            equal(3, cycle.problem().edges().size(), "A6 the reported mixed cycle has three edges");
        }
        try {
            semantics.plan(graph, List.of("b"), List.of());
            check(false, "A7 selecting b alone also reaches the mixed cycle and must be rejected");
        } catch (PlanSemantics.GraphException cycle) {
            equal("PLAN_CYCLE", cycle.code(), "A7 selecting b alone also reaches the mixed cycle (b->t1->a->b)");
        }
    }

    // ---------- B: dirty SQL in one of two same-named subprojects ----------
    static final class Files implements SourceRepository {
        final Map<URI, String> texts = new LinkedHashMap<>();
        @Override public SourceRead read(URI id, ReadPolicy policy, CancellationToken cancellation) {
            cancellation.check();
            String text = texts.get(id);
            return text == null ? SourceRead.unavailable("SOURCE_MISSING", id)
                    : SourceRead.found(new SourceText(id, text, null, "DISK", true, text));
        }
        @Override public SourceListing listProjectEntries(URI root, ScanBudget budget, CancellationToken cancellation) {
            throw new AssertionError("P2 counterprobe must not scan directories");
        }
    }

    static CatalogRepository catalog() {
        var codec = new CatalogJsonCodec();
        var inputs = List.of(
                new InputDescriptor("sqlFiles", "SQL files", true, false, null, List.of(), "example",
                        ValueShape.fromMetadata("list"), null, false, Map.of()),
                new InputDescriptor("sqlParameters", "Parameters", false, false, null, List.of(), "example",
                        ValueShape.fromMetadata("map"), null, false, Map.of()),
                new InputDescriptor("sqlParameterSets", "Parameter sets", false, false, null, List.of(), "example",
                        ValueShape.fromMetadata("list"), null, false, Map.of()));
        var operation = new OperationDescriptor("probe.duckdb", "DuckDbSqlExecutor", "Probe", "probe",
                inputs, List.of(), "example", true);
        var catalog = new OperationCatalog(1, "probe", "probe", "", List.of("probe"), List.of(operation), "COMPLETE");
        var repository = new CatalogRepository();
        var load = repository.loadBundled(codec.encodeCanonical(codec.identify(catalog)), null);
        check(load.result().state() == CatalogJsonCodec.State.AVAILABLE,
                "B0 probe catalog is AVAILABLE state=" + load.result().state() + " problems=" + load.result().problems());
        return repository;
    }

    static void projectProbe() {
        URI root = URI.create("file:/work");
        URI settings = URI.create("file:/work/settings.gretl");
        URI rootJob = URI.create("file:/work/job.groovy");
        URI aJob = URI.create("file:/work/a/job.groovy");
        URI bJob = URI.create("file:/work/b/job.groovy");
        URI aSql = URI.create("file:/work/a/sql/update.sql");
        URI bSql = URI.create("file:/work/b/sql/update.sql");
        String jobBody = "task('sql', DuckDbSqlExecutor) { sqlFiles=[file('sql/update.sql')]; sqlParameters=[shared:'x'] }; action('consumer', dependsOn:'sql') {}";
        var files = new Files();
        files.texts.put(settings, "include 'a', 'b'\ndefaults ':a:sql'");
        files.texts.put(rootJob, "action('root', dependsOn:':a:sql') {}");
        files.texts.put(aJob, jobBody);
        files.texts.put(bJob, jobBody);
        files.texts.put(aSql, "select 'disk-a-should-not-be-used'");   // superseded by the dirty buffer
        files.texts.put(bSql, "select ${diskOnly} from b");

        var store = new DocumentStore();
        store.open(aSql, "sql", 7, "select ${dirtyOnly} from a");
        try (var manager = new WorkspaceManager(store, catalog())) {
            manager.addRoot(root, files);
            var capture1 = manager.capture(new ProjectResolver.ProjectRequest(root, aJob), CancellationToken.NONE);
            var project = capture1.project();
            equal(List.of(rootJob, aJob, bJob), project.jobs().stream().map(ProjectResolver.JobSource::documentId).toList(),
                    "B1 settings includes keep root then a then b");
            equal("select ${dirtyOnly} from a", project.sources().texts().get(aSql).text(),
                    "B2 dirty SQL buffer wins over the disk text");
            equal("select ${diskOnly} from b", project.sources().texts().get(bSql).text(),
                    "B3 unrelated subproject SQL comes from its own file");

            var analysis1 = new ProjectAnalyzer().analyze(capture1, CancellationToken.NONE);
            var errors = analysis1.diagnostics().stream().filter(d -> d.severity() == Severity.ERROR).toList();
            var missing = errors.stream().filter(d -> d.code().equals("SQL_PARAMETER_MISSING")).toList();
            equal(2, missing.size(), "B4 exactly one missing parameter per same-named SQL resource");
            equal(Set.of(aSql, bSql), missing.stream().map(d -> d.anchor().documentId()).collect(java.util.stream.Collectors.toSet()),
                    "B5 both SQL diagnostics stay attached to distinct subproject resources");
            for (var diagnostic : missing) {
                var text = project.sources().texts().get(diagnostic.anchor().documentId()).text();
                var slice = text.substring(diagnostic.anchor().span().startUtf16(), diagnostic.anchor().span().endUtf16());
                check(slice.equals("${dirtyOnly}") || slice.equals("${diskOnly}"),
                        "B6 placeholder span " + slice + " is exact in " + diagnostic.anchor().documentId());
            }
            check(errors.stream().noneMatch(d -> d.code().equals("UNKNOWN_TASK_REFERENCE") || d.code().equals("AMBIGUOUS_REFERENCE")),
                    "B7 equal local task names resolve per subproject without ambiguity");
            check(analysis1.symbols().containsKey(new SymbolId(Namespace.TASK, ":a:sql"))
                            && analysis1.symbols().containsKey(new SymbolId(Namespace.TASK, ":b:sql")),
                    "B8 the two same-named tasks are distinct qualified symbols");
            var qualified = analysis1.references().stream()
                    .filter(r -> r.kind() == ReferenceKind.DEPENDS_ON && ":a:sql".equals(r.spelling())).findFirst().orElse(null);
            check(qualified != null && qualified.target().map(t -> t.qualifiedName().equals(":a:sql")).orElse(false),
                    "B9 qualified root reference targets :a:sql");
            var localA = analysis1.references().stream()
                    .filter(r -> r.kind() == ReferenceKind.DEPENDS_ON && "sql".equals(r.spelling())).toList();
            equal(2, localA.size(), "B10 both subprojects contain a local dependsOn reference");
            check(localA.stream().allMatch(r -> r.resolution() == Resolution.RESOLVED
                            && r.target().map(t -> t.qualifiedName().equals(":a:sql") || t.qualifiedName().equals(":b:sql")).orElse(false)),
                    "B11 local names resolve inside their own subproject only");
            check(analysis1.capabilities().declarationsComplete() && analysis1.capabilities().referencesComplete()
                            && analysis1.capabilities().graphComplete(),
                    "B12 complete capture yields complete capability flags");

            // The dirty SQL changes after the capture: the old capture must become stale,
            // even though no job file changed at all.
            store.applyChanges(aSql, 8, List.of(DocumentStore.TextChange.full("select 1 from a")));
            check(!manager.sameGeneration(capture1), "B13 dirty SQL change invalidates the old project generation");
            check(!manager.isCurrent(capture1, CancellationToken.NONE), "B14 old capture is not current after dirty SQL change");

            var capture2 = manager.capture(new ProjectResolver.ProjectRequest(root, aJob), CancellationToken.NONE);
            var analysis2 = new ProjectAnalyzer().analyze(capture2, CancellationToken.NONE);
            var missing2 = analysis2.diagnostics().stream().filter(d -> d.code().equals("SQL_PARAMETER_MISSING")).toList();
            equal(1, missing2.size(), "B15 only the unchanged subproject still reports a missing parameter");
            equal(bSql, missing2.getFirst().anchor().documentId(),
                    "B16 the remaining diagnostic belongs to the other subproject");
        } catch (RuntimeException failure) {
            failure.printStackTrace(System.out);
            check(false, "B unexpected failure: " + failure);
        }
    }

    public static void main(String[] args) {
        System.out.println("java.version=" + System.getProperty("java.version"));
        planSemanticsProbe();
        projectProbe();
        System.out.println(failures == 0 ? "COUNTERPROBE RESULT: PASS" : "COUNTERPROBE RESULT: FAIL (" + failures + ")");
        if (failures != 0) System.exit(1);
    }
}
