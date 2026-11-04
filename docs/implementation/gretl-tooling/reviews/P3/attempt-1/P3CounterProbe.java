import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.CatalogJsonCodec;
import ch.so.agi.gretl.tooling.catalog.CatalogRepository;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import ch.so.agi.gretl.tooling.edit.*;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.ProjectAnalyzer;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.*;
import ch.so.agi.gretl.tooling.workspace.ProjectResolver.ProjectRequest;
import ch.so.agi.gretl.tooling.workspace.SourceRepository.*;

import java.net.URI;
import java.util.*;

/**
 * Independent P3 counterprobe. It exercises two combinations not present in the phase tests:
 * (A) duplicate assignment plus an opaque sibling expression must block only the affected
 *     target, while an unrelated header option stays safely editable and text-preserving;
 * (B) rename must fail closed when one captured reference document is read-only, while the
 *     same rename succeeds and rewrites every reference when the document is editable.
 */
public final class P3CounterProbe {
    static final URI ROOT = URI.create("file:/work");
    static final URI JOB = URI.create("file:/work/job.groovy");
    static final URI SETTINGS = URI.create("file:/work/settings.gretl");
    static final URI A = URI.create("file:/work/a/job.groovy");
    static final URI B = URI.create("file:/work/b/job.groovy");
    static final SymbolId TASK = new SymbolId(Namespace.TASK, "a");
    static final SymbolId PROJECT_TASK = new SymbolId(Namespace.TASK, ":a:run");

    static CatalogRepository catalog() {
        var catalog = new CatalogRepository();
        var input = new InputDescriptor("input", "Input", true, false, null, List.of(), "input='x'",
                ValueShape.fromMetadata("text"), null, false, Map.of());
        var mode = new InputDescriptor("mode", "Mode", false, false, null, List.of("FAST", "SLOW"),
                "mode='FAST'", ValueShape.fromMetadata("text"), null, false, Map.of());
        var operation = new OperationDescriptor("test.echo", "Echo", "Test", "fixture",
                List.of(input, mode), List.of(), "example", true);
        var codec = new CatalogJsonCodec();
        catalog.loadBundled(codec.encodeCanonical(codec.identify(new OperationCatalog(
                1, "test", "test", "", List.of("fixture"), List.of(operation), "COMPLETE"))), null);
        return catalog;
    }

    static SourceRepository repository(Map<URI, String> files, Set<URI> readOnly) {
        return new SourceRepository() {
            @Override public SourceRead read(URI id, ReadPolicy policy, CancellationToken cancellation) {
                id = DocumentStore.identity(id);
                String text = files.get(id);
                return text == null ? SourceRead.unavailable("SOURCE_MISSING", id)
                        : SourceRead.found(new SourceText(id, text, 1, "DISK", !readOnly.contains(id), text));
            }
            @Override public SourceListing listProjectEntries(URI root, ScanBudget budget, CancellationToken cancellation) {
                return new SourceListing(List.of(), List.of(), true);
            }
        };
    }

    static AnalysisSnapshot capture(Map<URI, String> files, Set<URI> readOnly, URI document) {
        var manager = new WorkspaceManager(new DocumentStore(), catalog());
        manager.addRoot(ROOT, repository(files, readOnly));
        var captured = manager.capture(new ProjectRequest(ROOT, document, false, List.of()), CancellationToken.NONE);
        return AnalysisSnapshot.of(new ProjectAnalyzer().analyze(captured, CancellationToken.NONE));
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static String code(EditPlanningResult result) {
        return result.capability().reasons().isEmpty() ? "<none>" : result.capability().reasons().getFirst().code();
    }

    static void scenarioA() {
        var source = "task('a', Echo) {\r\n  input='first'\r\n  input='last'\r\n  mode=loadDynamicValue()\r\n}\r\n";
        var files = new HashMap<URI, String>();
        files.put(JOB, source);
        var before = capture(files, Set.of(), JOB);
        var planner = new SemanticEditPlanner();

        var duplicate = planner.plan(before,
                new SemanticEditIntent.SetField(TASK, "input", new EditableValue.Literal("new")),
                EditStyle.defaults(), CancellationToken.NONE);
        check(duplicate.capability().state() == EditCapability.State.REJECTED, "duplicate field must be rejected");
        check("EDIT_MULTIPLE_ASSIGNMENTS".equals(code(duplicate)), "duplicate field code: " + code(duplicate));
        check(duplicate.plan().isEmpty(), "duplicate field must not publish a plan");

        var opaque = planner.plan(before,
                new SemanticEditIntent.SetField(TASK, "mode", new EditableValue.Literal("FAST")),
                EditStyle.defaults(), CancellationToken.NONE);
        check(opaque.capability().state() == EditCapability.State.REJECTED, "opaque field must be rejected");
        check("EDIT_UNSAFE_REGION".equals(code(opaque)), "opaque field code: " + code(opaque));
        check(opaque.plan().isEmpty(), "opaque field must not publish a plan");

        var safe = planner.plan(before,
                new SemanticEditIntent.SetTaskOption(TASK, "description", new EditableValue.Literal("reviewed")),
                EditStyle.defaults(), CancellationToken.NONE);
        check(safe.capability().state() == EditCapability.State.ALLOWED, "safe header option must remain allowed: " + safe.capability().reasons());
        var plan = safe.plan().orElseThrow();
        var text = plan.postimages().get(JOB);
        check(text.contains("description: 'reviewed'"), "header option missing: " + text);
        check(text.contains("input='first'\r\n  input='last'\r\n  mode=loadDynamicValue()"),
                "untouched duplicate/opaque text changed: " + text);
        check(source.equals(files.get(JOB)), "fixture source must not be written");
        check(planner.validate(plan, before).current(), "plan must be current for its snapshot");
        check(!planner.validate(plan, plan.postimage()).current(), "plan must be stale for its postimage");
        System.out.println("A duplicate-and-opaque-safe-option: PASS (" + text.replace("\r", "\\r").replace("\n", "\\n") + ")");
    }

    static Scenario project(boolean bEditable) {
        var files = new HashMap<URI, String>();
        files.put(SETTINGS, "include 'a','b'\ndefaults ':a:run', ':b:use'");
        files.put(A, "task('run', Echo) { input='x' }; action('local', dependsOn:'run') { result('run') }");
        files.put(B, "action('use', dependsOn:':a:run', after:':a:run') { resultOptional(\":a:run\") }");
        var readOnly = bEditable ? Set.<URI>of() : Set.of(B);
        return new Scenario(files, readOnly, capture(files, readOnly, A));
    }

    record Scenario(Map<URI, String> files, Set<URI> readOnly, AnalysisSnapshot snapshot) { }

    static void scenarioB() {
        var planner = new SemanticEditPlanner();
        var locked = project(false);
        check(!locked.snapshot().source(B).orElseThrow().stamp().editable(), "reference document must be read-only");
        check(locked.snapshot().references().referencesTo(PROJECT_TASK).uses().size() >= 3,
                "project references must still be closed and visible");
        var refused = planner.plan(locked.snapshot(),
                new SemanticEditIntent.RenameSymbol(PROJECT_TASK, "renamed"),
                EditStyle.defaults(), CancellationToken.NONE);
        check(refused.capability().state() == EditCapability.State.REJECTED, "rename across read-only document must be rejected");
        check("EDIT_READONLY".equals(code(refused)), "read-only rename code: " + code(refused));
        check(refused.plan().isEmpty(), "read-only rename must not publish a plan");

        var editable = project(true);
        var allowed = planner.plan(editable.snapshot(),
                new SemanticEditIntent.RenameSymbol(PROJECT_TASK, "renamed"),
                EditStyle.defaults(), CancellationToken.NONE);
        check(allowed.capability().state() == EditCapability.State.ALLOWED, "editable rename must be allowed: " + allowed.capability().reasons());
        var plan = allowed.plan().orElseThrow();
        check(plan.changes().keySet().containsAll(Set.of(SETTINGS, A, B)), "rename must touch settings and both jobs: " + plan.changes().keySet());
        check(plan.postimages().get(B).contains("dependsOn:':a:renamed'"), "project reference not renamed: " + plan.postimages().get(B));
        check(!plan.postimages().get(B).contains("''"), "rename must not duplicate quotes");
        check(plan.postimage().analysis().defaultTargets().contains(":a:renamed"), "settings default not renamed");
        for (var entry : editable.files().entrySet()) {
            check(entry.getValue().equals(plan.postimage().source(entry.getKey()).orElseThrow().text()) == false,
                    "expected a change in " + entry.getKey());
        }
        System.out.println("B read-only-reference-rename-boundary: PASS (locked=" + code(refused) + ", editable=" + allowed.capability().state() + ")");
    }

    public static void main(String[] args) {
        scenarioA();
        scenarioB();
        System.out.println("COUNTERPROBE PASS");
    }
}
