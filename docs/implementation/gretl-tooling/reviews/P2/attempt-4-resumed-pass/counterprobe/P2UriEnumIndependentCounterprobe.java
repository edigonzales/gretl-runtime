import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.CatalogRepository;
import ch.so.agi.gretl.tooling.semantic.ProjectAnalyzer;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.DocumentStore;
import ch.so.agi.gretl.tooling.workspace.ProjectResolver.ProjectRequest;
import ch.so.agi.gretl.tooling.workspace.SourceRepository;
import ch.so.agi.gretl.tooling.workspace.SourceRepository.*;
import ch.so.agi.gretl.tooling.workspace.WorkspaceManager;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Independent P2 URI-enum counterprobe using the actual supplied operations.json.
 * Confirms the two non-Text allowedValues entries are URI scheme hints, that valid
 * HttpDownload/Curl literals are not rejected, and that real text enums still are.
 */
public final class P2UriEnumIndependentCounterprobe {
    static final URI ROOT = URI.create("file:/uri-probe");
    static final URI JOB = URI.create("file:/uri-probe/job.groovy");
    static final Path CATALOG = Path.of("/Users/stefan/.cache/gretl-tooling-review/P2-review-4/evidence/artifacts/gretl-tooling-catalog/target/catalog/operations.json");

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

    public static void main(String[] args) throws Exception {
        var catalog = new CatalogRepository();
        var snapshot = catalog.loadBundled(Files.readAllBytes(CATALOG), null);
        check(snapshot.result().state().name().equals("AVAILABLE"), "actual supplied catalog loads as AVAILABLE");
        var operations = snapshot.result().catalog().orElseThrow().operations();
        var bySymbol = new LinkedHashMap<String, ch.so.agi.gretl.tooling.catalog.OperationCatalog.OperationDescriptor>();
        operations.forEach(o -> bySymbol.put(o.symbol(), o));
        for (String symbol : List.of("HttpDownload", "Curl")) {
            var op = bySymbol.get(symbol);
            check(op != null, symbol + " exists in the actual catalog");
            String uriName = symbol.equals("Curl") ? "serverUrl" : "url";
            var uri = op.inputs().stream().filter(i -> i.name().equals(uriName)).findFirst().orElseThrow();
            var method = op.inputs().stream().filter(i -> i.name().equals("method")).findFirst().orElseThrow();
            check(uri.shape().kind().equals("Opaque") && uri.allowedValues().equals(List.of("http", "https")),
                    symbol + "." + uriName + " is Opaque with scheme hints [http, https]");
            check(method.shape().kind().equals("Text") && method.allowedValues().equals(List.of("GET", "POST")),
                    symbol + ".method is Text with literal enum [GET, POST]");
        }

        var repository = new Repository();
        repository.files.put(JOB, """
                task('http', HttpDownload) { url='https://example.invalid/a.zip'; output=workPath('a.zip'); method='GET' }
                task('curl', Curl) { serverUrl='http://example.invalid/'; expectedStatusCode=200; method='POST' }
                task('badHttp', HttpDownload) { url='https://example.invalid/b.zip'; output=workPath('b.zip'); method='DELETE' }
                task('badCurl', Curl) { serverUrl='https://example.invalid/'; expectedStatusCode=200; method='PATCH' }
                """);
        try (var manager = new WorkspaceManager(new DocumentStore(), catalog)) {
            manager.addRoot(ROOT, repository);
            var analysis = new ProjectAnalyzer().analyze(
                    manager.capture(new ProjectRequest(ROOT, JOB), CancellationToken.NONE), CancellationToken.NONE);
            check(analysis.capabilities().catalogAvailable(), "analysis has the actual catalog available");
            check(analysis.capabilities().graphComplete() && analysis.capabilities().fieldAssignmentsComplete(),
                    "project and field assignments are complete");
            var enums = analysis.diagnostics().stream().filter(d -> d.code().equals("INPUT_ENUM")).toList();
            check(enums.size() == 2, "exactly two INPUT_ENUM diagnostics are produced");
            check(enums.stream().allMatch(d -> d.severity() == Severity.ERROR), "invalid text enums are errors");
            check(enums.stream().allMatch(d -> d.anchor().documentId().equals(JOB)), "enum diagnostics stay in the job");
            var text = repository.files.get(JOB);
            var spellings = new ArrayList<String>();
            for (var d : enums) spellings.add(text.substring(d.anchor().span().startUtf16(), d.anchor().span().endUtf16()));
            check(spellings.equals(List.of("'DELETE'", "'PATCH'")), "only the invalid text enum literals are flagged, not the URLs");
            check(codes(analysis).stream().noneMatch(c -> Set.of("INPUT_TYPE", "REQUIRED_INPUT", "UNKNOWN_INPUT").contains(c)),
                    "valid URI values do not produce type/required/unknown diagnostics");
        }
        System.out.println("URI COUNTERPROBE PASS");
    }
}
