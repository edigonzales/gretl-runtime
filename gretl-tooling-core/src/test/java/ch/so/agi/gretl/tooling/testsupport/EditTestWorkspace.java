package ch.so.agi.gretl.tooling.testsupport;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.CatalogJsonCodec;
import ch.so.agi.gretl.tooling.catalog.CatalogRepository;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.ProjectAnalyzer;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.Namespace;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.SymbolId;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.*;
import java.net.URI;
import java.util.*;

import static ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import static org.junit.jupiter.api.Assertions.*;

/** In-memory capture with an IO guard while verifying edits and queries. */
public final class EditTestWorkspace implements AutoCloseable {
    public static final URI ROOT = URI.create("file:/work"), JOB = URI.create("file:/work/job.groovy"), SETTINGS = URI.create("file:/work/settings.gretl");
    public static final SymbolId TASK = new SymbolId(Namespace.TASK, "a");

    public final Map<URI, String> files = new HashMap<>();
    public final CatalogRepository catalog = new CatalogRepository();
    public final DocumentStore buffers = new DocumentStore();
    public final WorkspaceManager manager = new WorkspaceManager(buffers, catalog);
    private boolean prohibitReads;
    public EditTestWorkspace() {
        files.put(JOB, "// keep\r\ntask('a', Echo) { input = 'old' }\r\ndefaults 'a'\r\n");
        loadCatalog();
        manager.addRoot(ROOT, new SourceRepository() {
            public SourceRead read(URI id, ReadPolicy policy, CancellationToken cancellation) {
                assertFalse(prohibitReads, "Planning must not reacquire external sources");
                String text = files.get(id);
                return text == null ? SourceRead.unavailable("SOURCE_MISSING", id)
                        : SourceRead.found(new SourceText(id, text, null, "DISK", true, text));
            }
            public SourceListing listProjectEntries(URI root, ScanBudget budget, CancellationToken cancellation) {
                // Planning and postimages reuse the captured owner inventory without IO.
                assertFalse(prohibitReads,"Planning must not reacquire the owner inventory");
                assertEquals(SourceUris.directoryIdentity(ROOT),SourceUris.directoryIdentity(root));
                var entries=files.keySet().stream().filter(policyId->SourceUris.contains(root,policyId))
                        .filter(id->id.getPath().endsWith("/job.groovy")||id.getPath().endsWith("/settings.gretl")).sorted().toList();
                assertTrue(entries.size()<=budget.maximumEntries());return new SourceListing(entries,List.of(),true);
            }
        });
    }
    public void loadCatalog() {
        var input = new InputDescriptor("input", "Input", true, false, null, List.of(), "input='x'",
                ValueShape.fromMetadata("text"), null, false, Map.of());
        var mode = new InputDescriptor("mode", "Mode", false, false, null, List.of("FAST","SLOW"), "mode='FAST'", ValueShape.fromMetadata("text"), null, false, Map.of());
        var operation = new OperationDescriptor("test.echo", "Echo", "Test", "fixture", List.of(input, mode), List.of(), "example", true);
        var codec = new CatalogJsonCodec();
        catalog.loadBundled(codec.encodeCanonical(codec.identify(new OperationCatalog(1,"test","test","",List.of("fixture"),List.of(operation),"COMPLETE"))), null);
    }
    public AnalysisSnapshot capture() {
        return capture(List.of());
    }
    public AnalysisSnapshot capture(List<URI> additional) {
        prohibitReads = false;
        var captured = manager.capture(new ProjectResolver.ProjectRequest(ROOT, JOB, false, List.of(), additional), CancellationToken.NONE);
        prohibitReads = true;
        return AnalysisSnapshot.of(new ProjectAnalyzer().analyze(captured, CancellationToken.NONE));
    }
    @Override public void close() { manager.close(); }

    public void addResultOperations() {
        var input=new InputDescriptor("input","Input",true,false,null,List.of(),"input",ValueShape.fromMetadata("path"),null,false,Map.of());
        var output=new OutputDescriptor("file","File",ValueShape.fromMetadata("path"),false);
        catalog.mergeExplicitDescriptors(List.of(
                new OperationDescriptor("test.source","Source","Source","fixture",List.of(),List.of(output),"example",false),
                new OperationDescriptor("test.sink","Sink","Sink","fixture",List.of(input),List.of(),"example",true)));
        manager.onCatalogChanged();
    }

}
