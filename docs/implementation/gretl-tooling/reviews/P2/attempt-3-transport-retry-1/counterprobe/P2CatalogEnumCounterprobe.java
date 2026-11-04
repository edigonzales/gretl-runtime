import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.*;
import ch.so.agi.gretl.tooling.semantic.*;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.*;
import ch.so.agi.gretl.tooling.workspace.ProjectResolver.ProjectRequest;
import ch.so.agi.gretl.tooling.workspace.SourceRepository.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** New P2 counterprobe: actual bundled catalog, URI field allowed-values semantics. */
public final class P2CatalogEnumCounterprobe {
    static final URI ROOT = URI.create("memory:/catalog-enum");
    static final URI JOB = URI.create("memory:/catalog-enum/job.groovy");
    static final Path CATALOG = Path.of("/Users/stefan/.cache/gretl-tooling-review/P2-review-3/evidence/artifacts/gretl-tooling-catalog/target/catalog/operations.json");

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        System.out.println("OK " + message);
    }

    static CatalogRepository catalog() throws Exception {
        var repository = new CatalogRepository();
        var load = repository.loadBundled(Files.readAllBytes(CATALOG), null);
        check(load.result().state() == CatalogJsonCodec.State.AVAILABLE,
                "actual bundled catalog is AVAILABLE: " + load.result().problems());
        return repository;
    }

    public static void main(String[] args) throws Exception {
        var catalogs = catalog();
        var http = catalogs.snapshot().result().catalog().orElseThrow().operations().stream()
                .filter(o -> o.symbol().equals("HttpDownload")).findFirst().orElseThrow();
        var url = http.inputs().stream().filter(i -> i.name().equals("url")).findFirst().orElseThrow();
        System.out.println("HttpDownload.url shape=" + url.shape() + " allowedValues=" + url.allowedValues() + " required=" + url.required());
        check(url.shape().kind().equals("Opaque") && url.shape().runtimeType().equals("uri"),
                "url is a URI-shaped input with scheme-like allowed metadata");
        check(url.allowedValues().equals(List.of("http", "https")),
                "catalog carries the two URI schemes as allowedValues");

        String text = "task('download', HttpDownload) { url = 'https://example.invalid/data.zip'; output = workPath('data.zip'); method = 'GET' }; defaults 'download'";
        var store = new DocumentStore();
        store.open(JOB, "gretl", 1, text);
        SourceRepository repository = new SourceRepository() {
            public SourceRead read(URI id, ReadPolicy policy, CancellationToken cancellation) {
                return SourceRead.unavailable("SOURCE_MISSING", id);
            }
            public SourceListing listProjectEntries(URI id, ScanBudget budget, CancellationToken cancellation) {
                throw new AssertionError("no scan");
            }
        };
        try (var manager = new WorkspaceManager(store, catalogs)) {
            manager.addRoot(ROOT, repository);
            var capture = manager.capture(new ProjectRequest(ROOT, JOB), CancellationToken.NONE);
            var analysis = new ProjectAnalyzer().analyze(capture, CancellationToken.NONE);
            System.out.println("capabilities=" + analysis.capabilities());
            analysis.diagnostics().forEach(d -> System.out.println("diagnostic " + d.code() + " " + d.severity()
                    + " span=" + d.anchor().span() + " message=" + d.message()));
            var inputEnum = analysis.diagnostics().stream().filter(d -> d.code().equals("INPUT_ENUM")).toList();
            check(inputEnum.size() == 1 && inputEnum.getFirst().severity() == Severity.ERROR,
                    "the valid https URL is falsely reported as one INPUT_ENUM ERROR");
            check(analysis.diagnostics().stream().noneMatch(d -> d.code().equals("REQUIRED_INPUT")),
                    "required inputs are otherwise present");
            check(analysis.diagnostics().stream().noneMatch(d -> d.code().equals("UNKNOWN_INPUT")),
                    "no invented unknown input for the real catalog");
        }
        System.out.println("COUNTERPROBE CONFIRMED: valid HttpDownload URL yields INPUT_ENUM ERROR");
    }
}
