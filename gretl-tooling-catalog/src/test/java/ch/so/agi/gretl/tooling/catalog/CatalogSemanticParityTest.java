package ch.so.agi.gretl.tooling.catalog;

import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.ProjectAnalyzer;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.*;
import ch.so.agi.gretl.tooling.workspace.ProjectResolver.ProjectRequest;
import ch.so.agi.gretl.tooling.workspace.SourceRepository.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Trusted current SPI converters are positive controls; analysis receives JSON data only. */
class CatalogSemanticParityTest {
    private static final URI ROOT=URI.create("file:/catalog-parity"), JOB=URI.create("file:/catalog-parity/job.groovy");
    private Map<String,List<OperationType<?,?>>> operations() {
        return RuntimeCatalogBootstrap.collectOperations(getClass().getClassLoader());
    }
    private InputField<?,?> field(String symbol,String name) {
        return operations().values().stream().flatMap(Collection::stream).filter(o->o.symbol().equals(symbol))
                .findFirst().orElseThrow().inputs().stream().filter(f->f.name().equals(name)).findFirst().orElseThrow();
    }
    private Analysis analyze(String job) throws Exception {
        var loader=getClass().getClassLoader();
        var data=new OperationCatalogExporter().export(operations(),"test",new MetadataSupplementLoader().loadFromOwnedResources(loader));
        var catalog=new CatalogRepository();
        assertEquals(CatalogJsonCodec.State.AVAILABLE,catalog.loadBundled(new CatalogJsonCodec().encodeCanonical(data),data.catalogId()).result().state());
        var acquisitionComplete=new AtomicBoolean();
        SourceRepository sources=new SourceRepository() {
            public SourceRead read(URI id,ReadPolicy policy,CancellationToken cancellation) {
                assertFalse(acquisitionComplete.get(),"Semantic analysis must use only captured sources");
                cancellation.check();
                return id.equals(JOB)?SourceRead.found(new SourceText(id,job,null,"DISK",true,job)):SourceRead.unavailable("SOURCE_MISSING",id);
            }
            public SourceListing listProjectEntries(URI root,ScanBudget budget,CancellationToken cancellation) {
                assertFalse(acquisitionComplete.get(),"Semantic analysis must reuse the captured owner inventory");
                cancellation.check();
                assertEquals(SourceUris.directoryIdentity(ROOT),SourceUris.directoryIdentity(root));
                assertTrue(budget.maximumEntries()>=1);
                return new SourceListing(List.of(JOB),List.of(),true);
            }
        };
        try(var manager=new WorkspaceManager(new DocumentStore(),catalog)) {
            manager.addRoot(ROOT,sources);
            var captured=manager.capture(new ProjectRequest(ROOT,JOB),CancellationToken.NONE);
            assertTrue(captured.ownership().complete());
            assertTrue(captured.ownership().exclusive(JOB));
            acquisitionComplete.set(true);
            return new ProjectAnalyzer().analyze(captured,CancellationToken.NONE);
        }
    }
    @Test void actualUriSchemeHintsDoNotRejectCompleteUrls() throws Exception {
        for(String symbol:List.of("HttpDownload","Curl")) {
            String name=symbol.equals("Curl")?"serverUrl":"url";
            var input=field(symbol,name);
            assertEquals("uri",input.valueType());
            assertEquals(List.of("http","https"),input.allowedValues());
            for(String scheme:input.allowedValues()) {
                String url=scheme+"://example.invalid/data.zip";
                assertEquals(URI.create(url),input.convert().apply(url));
                String rest=symbol.equals("Curl")?"expectedStatusCode=200":"output=workPath('data.zip')";
                var result=analyze("task('request', "+symbol+") { "+name+"='"+url+"'; "+rest+"; method='GET' }");
                assertTrue(result.capabilities().catalogAvailable());
                assertTrue(result.diagnostics().stream().noneMatch(d->d.severity()==Severity.ERROR),result.diagnostics().toString());
            }
        }
    }
    @Test void actualTextEnumsStillRejectInvalidMethodsAndAcceptValidMethods() throws Exception {
        for(String symbol:List.of("HttpDownload","Curl")) {
            var input=field(symbol,"method");
            assertEquals("text",input.valueType());
            assertEquals("GET",input.convert().apply("GET"));
            assertThrows(IllegalArgumentException.class,()->input.convert().apply("DELETE"));
            String fields=symbol.equals("Curl")?"serverUrl='https://example.invalid/'; expectedStatusCode=200":"url='https://example.invalid/'; output=workPath('data.zip')";
            var valid=analyze("task('request', "+symbol+") { "+fields+"; method='GET' }");
            assertTrue(valid.diagnostics().stream().noneMatch(d->d.code().equals("INPUT_ENUM")),valid.diagnostics().toString());
            String invalidJob="task('request', "+symbol+") { "+fields+"; method='DELETE' }";
            var invalid=analyze(invalidJob);
            var enums=invalid.diagnostics().stream().filter(d->d.code().equals("INPUT_ENUM")).toList();
            assertEquals(1,enums.size(),invalid.diagnostics().toString());
            assertEquals(Severity.ERROR,enums.getFirst().severity());
            var span=enums.getFirst().anchor().span();
            assertNotNull(span);
            assertEquals("'DELETE'",invalidJob.substring(span.startUtf16(),span.endUtf16()));
        }
    }
}
