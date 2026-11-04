package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import ch.so.agi.gretl.tooling.edit.EditVerifierTest.Fixture;
import ch.so.agi.gretl.tooling.query.LanguageQueries;
import ch.so.agi.gretl.tooling.query.QueryContext;
import ch.so.agi.gretl.tooling.query.QueryResults.CompletionProposal;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.Namespace;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.Severity;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.SymbolId;
import java.util.*;

/**
 * New independent P3 counterprobe. It is compiled and executed against the
 * classes built from the reviewed candidate and asserts P3 behaviour that is
 * not identical to an existing test case.
 */
public final class P3CounterProbe {
    private static int passed, failed;

    private static void check(String name, boolean ok, String detail) {
        if (ok) { passed++; System.out.println("PASS " + name + " :: " + detail); }
        else { failed++; System.out.println("FAIL " + name + " :: " + detail); }
    }

    private static SymbolId id(String name) { return new SymbolId(Namespace.TASK, name); }

    private static InputDescriptor input(String name, ValueShape shape, boolean required) {
        return new InputDescriptor(name, name, required, false, null, List.of(), name, shape, null, false, Map.of());
    }

    private static OperationDescriptor operation(String symbol, List<InputDescriptor> inputs, List<OutputDescriptor> outputs, boolean voidResult) {
        return new OperationDescriptor("probe." + symbol.toLowerCase(Locale.ROOT), symbol, symbol, "probe", inputs, outputs, "example", voidResult);
    }

    private static void installProbeCatalog(Fixture fixture) {
        // Reuse the candidate's Source/Sink fixture operations (Path output/input).
        new DependencySourceEditsTest().catalog(fixture);
        var text = ValueShape.fromMetadata("text");
        var path = ValueShape.fromMetadata("path");
        var pathList = new ValueShape("ListOf", "list", List.of(path));
        var mapOfTextPath = new ValueShape("MapOf", "map", List.of(text, path));
        var wideInputs = List.of(
                input("input", text, true),
                input("mode", text, false),
                input("aaa", text, false),
                input("zzz", text, false));
        fixture.catalog.mergeExplicitDescriptors(List.of(
                operation("SourceList", List.of(), List.of(new OutputDescriptor("file", "File", pathList, false)), false),
                operation("MapSink", List.of(input("input", mapOfTextPath, true)), List.of(), true),
                operation("Wide", wideInputs, List.of(), true)));
        fixture.manager.onCatalogChanged();
    }

    private static String reason(EditPlanningResult result) {
        return result.capability().reasons().isEmpty() ? "<none>" : result.capability().reasons().getFirst().code();
    }

    public static void main(String[] args) {
        mapNoOpCompatible();
        mapNoOpIncompatible();
        setFieldWithoutDataEdge();
        deleteTaskReferencedOnlyByDefaults();
        formRemovalAndHeaderInOneVerifiedPlan();
        formWithOneInvalidIntentPublishesNothing();
        multipleAssignmentRefused();
        completionCrLfCommentRanges();
        System.out.println("SUMMARY passed=" + passed + " failed=" + failed);
        if (failed != 0) System.exit(1);
    }

    /** P3-05/P3-07: a MapOf(Text,Path) no-op must still run the recursive port guard. */
    private static void mapNoOpCompatible() {
        try (var fixture = new Fixture()) {
            installProbeCatalog(fixture);
            String source = "task('p', Source) {}; task('c', MapSink, dependsOn:'p') { input=['k': result('p').file] }";
            fixture.files.put(EditVerifierTest.JOB, source);
            var snapshot = fixture.capture();
            var value = new EditableValue.Mapping(Map.of("k", new EditableValue.ResultFieldRef(id("p"), "file")));
            var result = new SemanticEditPlanner().plan(snapshot,
                    new SemanticEditIntent.SetField(id("c"), "input", value), EditStyle.defaults(), CancellationToken.NONE);
            check("map-noop-compatible", result.capability().state() == EditCapability.State.ALLOWED
                            && result.plan().isPresent() && result.plan().orElseThrow().noOp(),
                    "state=" + result.capability().state() + " plan=" + result.plan().isPresent()
                            + " noOp=" + result.plan().map(PlannedEdit::noOp).orElse(null));
        }
    }

    /** P3-F1/P3-05: the guard runs before no-op detection even for mapping values. */
    private static void mapNoOpIncompatible() {
        try (var fixture = new Fixture()) {
            installProbeCatalog(fixture);
            String source = "task('p', SourceList) {}; task('c', MapSink, dependsOn:'p') { input=['k': result('p').file] }";
            fixture.files.put(EditVerifierTest.JOB, source);
            var snapshot = fixture.capture();
            var value = new EditableValue.Mapping(Map.of("k", new EditableValue.ResultFieldRef(id("p"), "file")));
            var result = new SemanticEditPlanner().plan(snapshot,
                    new SemanticEditIntent.SetField(id("c"), "input", value), EditStyle.defaults(), CancellationToken.NONE);
            check("map-noop-incompatible", result.capability().state() == EditCapability.State.REJECTED
                            && result.plan().isEmpty() && "EDIT_PORT_COMPATIBILITY".equals(reason(result)),
                    "state=" + result.capability().state() + " reason=" + reason(result));
        }
    }

    /** P3-05/P3-08: a scalar result binding without the required data edge must be refused, not published. */
    private static void setFieldWithoutDataEdge() {
        try (var fixture = new Fixture()) {
            installProbeCatalog(fixture);
            String source = "task('p', Source) {}; task('c', Sink) { input=workPath('old') }";
            fixture.files.put(EditVerifierTest.JOB, source);
            var snapshot = fixture.capture();
            var result = new SemanticEditPlanner().plan(snapshot,
                    new SemanticEditIntent.SetField(id("c"), "input", new EditableValue.ResultFieldRef(id("p"), "file")),
                    EditStyle.defaults(), CancellationToken.NONE);
            check("setfield-missing-data-edge", result.capability().state() == EditCapability.State.REJECTED
                            && result.plan().isEmpty(),
                    "state=" + result.capability().state() + " reason=" + reason(result));
        }
    }

    /** P3-06: defaults are references; deleting their target must not leave a dangling default. */
    private static void deleteTaskReferencedOnlyByDefaults() {
        try (var fixture = new Fixture()) {
            installProbeCatalog(fixture);
            String source = "task('d', Echo) { input='x' }\ndefaults 'd'\n";
            fixture.files.put(EditVerifierTest.JOB, source);
            var snapshot = fixture.capture();
            var result = new SemanticEditPlanner().plan(snapshot,
                    new SemanticEditIntent.DeleteTask(id("d")), EditStyle.defaults(), CancellationToken.NONE);
            check("delete-default-referenced", result.capability().state() == EditCapability.State.REJECTED
                            && result.plan().isEmpty(),
                    "state=" + result.capability().state() + " reason=" + reason(result)
                            + " textUnchanged=" + source.equals(fixture.files.get(EditVerifierTest.JOB)));
        }
    }

    /** P3-08/P3-07: removal, field replacement and header insertion in one plan, exact CRLF text. */
    private static void formRemovalAndHeaderInOneVerifiedPlan() {
        try (var fixture = new Fixture()) {
            installProbeCatalog(fixture);
            String source = "task('a', Wide) {\r\n  input='old' // keep\r\n  mode='FAST'\r\n}\r\n";
            fixture.files.put(EditVerifierTest.JOB, source);
            var before = fixture.capture();
            var planner = new SemanticEditPlanner();
            var result = planner.planConfiguration(before, id("a"),
                    Map.of("input", new EditableValue.Literal("new")), Set.of("mode"),
                    Map.of("description", new EditableValue.Literal("Desc")), EditStyle.defaults(), CancellationToken.NONE);
            // Minimal edits remove the assignment span but deliberately keep the line's
            // indentation/EOL, as the existing FieldSourceEditsTest also specifies.
            String expected = "task('a', Wide, description: 'Desc') {\r\n  input='new' // keep\r\n  \r\n}\r\n";
            boolean allowed = result.capability().state() == EditCapability.State.ALLOWED && result.plan().isPresent();
            var plan = result.plan().orElse(null);
            String post = plan == null ? null : plan.postimages().get(EditVerifierTest.JOB);
            boolean exact = expected.equals(post);
            boolean noError = plan != null && plan.postimage().analysis().diagnostics().stream().noneMatch(d -> d.severity() == Severity.ERROR);
            boolean ranges = plan != null && plan.changes().get(EditVerifierTest.JOB).size() == 3;
            boolean preserved = post != null && post.contains("// keep\r\n") && !post.contains("mode") && post.contains("description: 'Desc'");
            check("form-remove-replace-header", allowed && exact && noError && ranges && preserved,
                    "state=" + result.capability().state() + " reason=" + reason(result) + " ranges="
                            + (plan == null ? null : plan.changes().get(EditVerifierTest.JOB).size()) + " post=" + post);
            if (plan != null) {
                var replay = planner.planConfiguration(plan.postimage(), id("a"),
                        Map.of("input", new EditableValue.Literal("new")), Set.of("mode"),
                        Map.of("description", new EditableValue.Literal("Desc")), EditStyle.defaults(), CancellationToken.NONE);
                check("form-idempotent-postimage", replay.capability().state() == EditCapability.State.ALLOWED
                                && replay.plan().isPresent() && replay.plan().orElseThrow().noOp(),
                        "state=" + replay.capability().state() + " noOp=" + replay.plan().map(PlannedEdit::noOp).orElse(null));
                check("plan-current-before-stale-after", planner.validate(plan, before).current()
                                && !planner.validate(plan, fixture.capture()).current(),
                        "currentBefore=" + planner.validate(plan, before).current()
                                + " currentAfterNewCapture=" + planner.validate(plan, fixture.capture()).current());
            } else {
                check("form-idempotent-postimage", false, "no plan");
                check("plan-current-before-stale-after", false, "no plan");
            }
            check("source-untouched", source.equals(fixture.files.get(EditVerifierTest.JOB)), "inMemorySourceUnchanged");
        }
    }

    /** P3-08: a later invalid intent must not publish the earlier valid intermediate edit. */
    private static void formWithOneInvalidIntentPublishesNothing() {
        try (var fixture = new Fixture()) {
            installProbeCatalog(fixture);
            String source = "task('p', Source) {}; task('a', Wide) { input='old' }";
            fixture.files.put(EditVerifierTest.JOB, source);
            var before = fixture.capture();
            var fields = new LinkedHashMap<String, EditableValue>();
            fields.put("aaa", new EditableValue.Literal("first"));
            fields.put("zzz", new EditableValue.ResultFieldRef(id("p"), "file"));
            var result = new SemanticEditPlanner().planConfiguration(before, id("a"), fields, Set.of(), Map.of(),
                    EditStyle.defaults(), CancellationToken.NONE);
            check("form-partial-refused", result.capability().state() == EditCapability.State.REJECTED
                            && result.plan().isEmpty() && source.equals(fixture.files.get(EditVerifierTest.JOB)),
                    "state=" + result.capability().state() + " reason=" + reason(result)
                            + " plan=" + result.plan().isPresent());
        }
    }

    /** P3-04/T15: multiple assignments of the same field must be refused, not arbitrarily resolved. */
    private static void multipleAssignmentRefused() {
        try (var fixture = new Fixture()) {
            installProbeCatalog(fixture);
            String source = "task('a', Wide) { input='first'; input='last' }";
            fixture.files.put(EditVerifierTest.JOB, source);
            var snapshot = fixture.capture();
            var result = new SemanticEditPlanner().plan(snapshot,
                    new SemanticEditIntent.SetField(id("a"), "input", new EditableValue.Literal("new")),
                    EditStyle.defaults(), CancellationToken.NONE);
            check("multiple-assignment-refused",
                    result.capability().state() == EditCapability.State.REJECTED && result.plan().isEmpty()
                            && "EDIT_MULTIPLE_ASSIGNMENTS".equals(reason(result))
                            && source.equals(fixture.files.get(EditVerifierTest.JOB)),
                    "state=" + result.capability().state() + " reason=" + reason(result)
                            + " plan=" + result.plan().isPresent()
                            + " sourceUnchanged=" + source.equals(fixture.files.get(EditVerifierTest.JOB)));
        }
    }

    /** P3-01/P3-07: CRLF/emoji comment position and exact quoted replacement including quotes once. */
    private static void completionCrLfCommentRanges() {
        try (var fixture = new Fixture()) {
            String source = "task('a', Echo) {\r\n  mode='FAST' // 😀\r\n}\r\n";
            fixture.files.put(EditVerifierTest.JOB, source);
            var snapshot = fixture.capture();
            var queries = new LanguageQueries();
            int valueOffset = source.indexOf("'FAST'") + 2;
            var proposals = queries.complete(new QueryContext(snapshot, EditVerifierTest.JOB, valueOffset));
            CompletionProposal slow = proposals.stream().filter(p -> p.label().equals("SLOW")).findFirst().orElse(null);
            boolean exact = slow != null
                    && "'FAST'".equals(source.substring(slow.replacement().startUtf16(), slow.replacement().endUtf16()))
                    && "'SLOW'".equals(slow.newText());
            String applied = slow == null ? null : source.substring(0, slow.replacement().startUtf16()) + slow.newText()
                    + source.substring(slow.replacement().endUtf16());
            boolean appliedOk = "task('a', Echo) {\r\n  mode='SLOW' // 😀\r\n}\r\n".equals(applied);
            int commentOffset = source.indexOf("//") + 3;
            boolean quiet = queries.complete(new QueryContext(snapshot, EditVerifierTest.JOB, commentOffset)).isEmpty()
                    && queries.signatureHelp(new QueryContext(snapshot, EditVerifierTest.JOB, commentOffset)).isEmpty();
            check("completion-crlf-comment", exact && appliedOk && quiet,
                    "proposal=" + (slow == null ? null : slow.label()) + " exact=" + exact + " applied=" + appliedOk + " quiet=" + quiet);
        }
    }
}
