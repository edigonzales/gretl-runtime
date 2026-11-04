import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.*;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import ch.so.agi.gretl.tooling.semantic.ProjectAnalyzer;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.*;
import java.net.URI;
import java.util.*;

/**
 * Reviewer-owned static probe for the exact job whose runtime counterpart is
 * SKIPPED in P2RuntimeWhenProbe: a task with when:{false} and an unset required field.
 */
public final class P2StaticWhenProbe {
    static final class Files implements SourceRepository {
        final Map<URI, String> texts = new LinkedHashMap<>();
        public SourceRead read(URI id, ReadPolicy policy, CancellationToken cancellation) {
            cancellation.check();
            String text = texts.get(id);
            return text == null ? SourceRead.unavailable("SOURCE_MISSING", id)
                    : SourceRead.found(new SourceText(id, text, null, "DISK", true, text));
        }
        public SourceListing listProjectEntries(URI root, ScanBudget budget, CancellationToken cancellation) { return new SourceListing(List.of(), List.of(), true); }
    }
    static CatalogRepository catalog() {
        var codec = new CatalogJsonCodec();
        var text = new InputDescriptor("text", "Required text", true, false, null, List.of(), "example",
                ValueShape.fromMetadata("text"), null, false, Map.of());
        var operation = new OperationDescriptor("probe.required", "RequiredText", "Required", "probe",
                List.of(text), List.of(), "example", true);
        var repository = new CatalogRepository();
        var result = repository.loadBundled(codec.encodeCanonical(codec.identify(new OperationCatalog(1, "probe", "probe", "", List.of("probe"), List.of(operation), "COMPLETE"))), null);
        if (result.result().state() != CatalogJsonCodec.State.AVAILABLE) throw new IllegalStateException(result.result().problems().toString());
        return repository;
    }
    public static void main(String[] args) {
        URI root = URI.create("file:/when"), job = URI.create("file:/when/job.groovy");
        for (String source : List.of(
                "task('t', RequiredText, when: { false }) { }; defaults 't'",
                "parameter('flag',description:'F',type:'boolean',defaultValue:false); task('t', RequiredText, when: { parameter('flag') }) { }; defaults 't'",
                "task('t', RequiredText) { text = null }; defaults 't'",
                "task('t', RequiredText) { text = 'ok' }; defaults 't'")) {
            var files = new Files();
            files.texts.put(job, source);
            var store = new DocumentStore();
            try (var manager = new WorkspaceManager(store, catalog())) {
                manager.addRoot(root, files);
                var capture = manager.capture(new ProjectResolver.ProjectRequest(root, job), CancellationToken.NONE);
                var analysis = new ProjectAnalyzer().analyze(capture, CancellationToken.NONE);
                System.out.println("SOURCE: " + source);
                System.out.println("capabilities=" + analysis.capabilities());
                analysis.diagnostics().forEach(d -> System.out.println("diagnostic " + d.code() + " " + d.severity() + " span=" + d.anchor().span()));
                System.out.println("staticReportsRequiredInput=" + analysis.diagnostics().stream().anyMatch(d -> d.code().equals("REQUIRED_INPUT")));
            }
        }
    }
}
