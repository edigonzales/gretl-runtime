import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.*;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import ch.so.agi.gretl.tooling.edit.*;
import ch.so.agi.gretl.tooling.semantic.*;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.*;
import java.net.URI;
import java.util.*;

/** Observe whether SetField can bypass the port check used by BindResult. */
public final class P3PortProbe {
    static final URI ROOT = URI.create("file:/work");
    static final URI JOB = URI.create("file:/work/job.groovy");
    static final SymbolId P = new SymbolId(Namespace.TASK, "p");
    static final SymbolId C = new SymbolId(Namespace.TASK, "c");

    static AnalysisSnapshot capture() {
        var catalog = new CatalogRepository();
        var input = new InputDescriptor("input", "Input", true, false, null, List.of(), "input='x'",
                ValueShape.fromMetadata("text"), null, false, Map.of());
        var output = new OutputDescriptor("file", "File", ValueShape.fromMetadata("path"), false);
        var source = new OperationDescriptor("test.source", "Source", "Source", "fixture", List.of(), List.of(output), "example", false);
        var sink = new OperationDescriptor("test.sink", "Sink", "Sink", "fixture", List.of(input), List.of(), "example", true);
        var codec = new CatalogJsonCodec();
        catalog.loadBundled(codec.encodeCanonical(codec.identify(new OperationCatalog(1, "test", "test", "",
                List.of("fixture"), List.of(source, sink), "COMPLETE"))), null);
        var files = Map.of(JOB, "task('p', Source) {}; task('c', Sink, dependsOn:'p') { input='old' }");
        var manager = new WorkspaceManager(new DocumentStore(), catalog);
        manager.addRoot(ROOT, new SourceRepository() {
            public SourceRead read(URI id, ReadPolicy policy, CancellationToken cancellation) {
                id = DocumentStore.identity(id);
                String text = files.get(id);
                return text == null ? SourceRead.unavailable("SOURCE_MISSING", id)
                        : SourceRead.found(new SourceText(id, text, 1, "DISK", true, text));
            }
            public SourceListing listProjectEntries(URI root, ScanBudget budget, CancellationToken cancellation) {
                return new SourceListing(List.of(), List.of(), true);
            }
        });
        return AnalysisSnapshot.of(new ProjectAnalyzer().analyze(
                manager.capture(new ProjectResolver.ProjectRequest(ROOT, JOB, false, List.of()), CancellationToken.NONE),
                CancellationToken.NONE));
    }

    public static void main(String[] args) {
        var before = capture();
        var planner = new SemanticEditPlanner();
        var bind = planner.plan(before, new SemanticEditIntent.BindResult(P, "file", C, "input"),
                EditStyle.defaults(), CancellationToken.NONE);
        var set = planner.plan(before, new SemanticEditIntent.SetField(C, "input", new EditableValue.ResultFieldRef(P, "file")),
                EditStyle.defaults(), CancellationToken.NONE);
        System.out.println("BindResult state=" + bind.capability().state() + " code="
                + (bind.capability().reasons().isEmpty() ? "<none>" : bind.capability().reasons().getFirst().code()));
        System.out.println("SetField state=" + set.capability().state() + " code="
                + (set.capability().reasons().isEmpty() ? "<none>" : set.capability().reasons().getFirst().code()));
        set.plan().ifPresent(plan -> System.out.println("SetField postimage=" + plan.postimages().get(JOB).replace("\n", "\\n")));
        var form = planner.planConfiguration(before, C,
                Map.of("input", new EditableValue.ResultFieldRef(P, "file")), Set.of(),
                Map.of("dependsOn", new EditableValue.Literal("p")), EditStyle.defaults(), CancellationToken.NONE);
        System.out.println("planConfiguration state=" + form.capability().state() + " code="
                + (form.capability().reasons().isEmpty() ? "<none>" : form.capability().reasons().getFirst().code()));
        form.plan().ifPresent(plan -> System.out.println("planConfiguration postimage=" + plan.postimages().get(JOB).replace("\n", "\\n")));
    }
}
