package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import ch.so.agi.gretl.tooling.edit.EditVerifierTest.Fixture;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Additional independent P3 counterprobe for the verifier's postimage capture set.
 * It uses the candidate's own Fixture.capture(additional) path and the candidate
 * test classes; no product file is modified.
 */
public final class P3SupplementalSourceProbe {
    static final URI SQL = URI.create("file:/work/sql/query.sql");
    private static int passed, failed;

    private static void check(String name, boolean ok, String detail) {
        if (ok) { passed++; System.out.println("PASS " + name + " :: " + detail); }
        else { failed++; System.out.println("FAIL " + name + " :: " + detail); }
    }

    private static void sqlCatalog(Fixture fixture) {
        var files = new InputDescriptor("sqlFiles", "Files", true, false, null, List.of(), "files",
                ValueShape.fromMetadata("list"), null, false, Map.of());
        var parameters = new InputDescriptor("sqlParameters", "Parameters", false, false, null, List.of(), "parameters",
                ValueShape.fromMetadata("map"), null, false, Map.of());
        fixture.catalog.mergeExplicitDescriptors(List.of(new OperationDescriptor("test.duckdb",
                "DuckDbSqlExecutor", "SQL", "fixture", List.of(files, parameters), List.of(), "example", true)));
        fixture.manager.onCatalogChanged();
    }

    private static String reason(EditPlanningResult result) {
        return result.capability().reasons().isEmpty() ? "<none>" : result.capability().reasons().getFirst().code();
    }

    public static void main(String[] args) {
        requestedButUnreferencedSourceIsDroppedFromPostimage();
        referencedSqlSourceSurvivesPostimageReanalysis();
        uncapturedNewSqlDependencyStillBlocksThePlan();
        System.out.println("SUMMARY passed=" + passed + " failed=" + failed);
        if (failed != 0) System.exit(1);
    }

    /**
     * An explicitly requested but not (yet) semantically referenced SQL source is part of
     * the preimage and of the plan preconditions. The reanalyzed postimage drops it
     * because EditVerifier.reanalyze() builds ProjectRequest without requestedSources.
     * This documents the exact behaviour; it is not treated as a product failure because
     * the dropped source contributes no project semantics.
     */
    private static void requestedButUnreferencedSourceIsDroppedFromPostimage() {
        try (var fixture = new Fixture()) {
            sqlCatalog(fixture);
            fixture.files.put(SQL, "select ${key}");
            var before = fixture.capture(List.of(SQL));
            boolean beforeHasSql = before.source(SQL).isPresent()
                    && before.analysis().capture().project().sources().versions().containsKey(SQL);
            var result = new SemanticEditPlanner().plan(before,
                    new SemanticEditIntent.SetField(EditVerifierTest.TASK, "mode", new EditableValue.Literal("SLOW")),
                    EditStyle.defaults(), CancellationToken.NONE);
            var plan = result.plan().orElse(null);
            boolean allowed = result.capability().state() == EditCapability.State.ALLOWED && plan != null;
            boolean preconditionsHaveSql = plan != null && plan.preconditions().sources().containsKey(SQL);
            boolean postimageDropsSql = plan != null && plan.postimage().source(SQL).isEmpty()
                    && !plan.postimage().analysis().capture().project().sources().versions().containsKey(SQL);
            check("requested-source-postimage-drop", beforeHasSql && allowed && preconditionsHaveSql && postimageDropsSql,
                    "beforeHasSql=" + beforeHasSql + " state=" + result.capability().state() + " reason=" + reason(result)
                            + " preconditionsHaveSql=" + preconditionsHaveSql + " postimageDropsSql=" + postimageDropsSql);
        }
    }

    /** The same source stays in the postimage as soon as the job itself references it. */
    private static void referencedSqlSourceSurvivesPostimageReanalysis() {
        try (var fixture = new Fixture()) {
            sqlCatalog(fixture);
            fixture.files.put(SQL, "select ${key}");
            fixture.files.put(EditVerifierTest.JOB,
                    "task('a', Echo) { input='old' }\n"
                            + "task('d', DuckDbSqlExecutor) { sqlFiles=[file('sql/query.sql')]; sqlParameters=[key:'x'] }\n");
            var before = fixture.capture();
            boolean beforeHasSql = before.source(SQL).isPresent();
            var result = new SemanticEditPlanner().plan(before,
                    new SemanticEditIntent.SetField(EditVerifierTest.TASK, "mode", new EditableValue.Literal("SLOW")),
                    EditStyle.defaults(), CancellationToken.NONE);
            var plan = result.plan().orElse(null);
            boolean allowed = result.capability().state() == EditCapability.State.ALLOWED && plan != null;
            boolean postimageHasSql = plan != null && plan.postimage().source(SQL).isPresent();
            boolean noSqlError = plan != null && plan.postimage().analysis().diagnostics().stream()
                    .noneMatch(d -> d.severity() == ch.so.agi.gretl.tooling.semantic.SemanticModel.Severity.ERROR);
            check("referenced-source-postimage-kept", beforeHasSql && allowed && postimageHasSql && noSqlError,
                    "beforeHasSql=" + beforeHasSql + " state=" + result.capability().state() + " reason=" + reason(result)
                            + " postimageHasSql=" + postimageHasSql + " noError=" + noSqlError);
        }
    }

    /** P3-08: a new SQL dependency that was not captured must still refuse the plan. */
    private static void uncapturedNewSqlDependencyStillBlocksThePlan() {
        try (var fixture = new Fixture()) {
            sqlCatalog(fixture);
            fixture.files.put(SQL, "select ${key}");
            var before = fixture.capture();
            boolean beforeLacksSql = before.source(SQL).isEmpty();
            var result = new SemanticEditPlanner().plan(before,
                    new SemanticEditIntent.AddTask(EditVerifierTest.JOB, fixture.files.get(EditVerifierTest.JOB).length(),
                            "d", "test.duckdb",
                            Map.of("sqlFiles", new EditableValue.Sequence(List.of(new EditableValue.JobFileRef("sql/query.sql"))),
                                    "sqlParameters", new EditableValue.Mapping(Map.of("key", new EditableValue.Literal("yes"))))),
                    EditStyle.defaults(), CancellationToken.NONE);
            check("new-sql-dependency-blocked", beforeLacksSql
                            && result.capability().state() == EditCapability.State.REJECTED
                            && result.plan().isEmpty() && "EDIT_SOURCE_NOT_CAPTURED".equals(reason(result)),
                    "beforeLacksSql=" + beforeLacksSql + " state=" + result.capability().state()
                            + " plan=" + result.plan().isPresent() + " reason=" + reason(result));
        }
    }
}
