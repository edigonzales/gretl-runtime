package ch.so.agi.gretl.tooling.workspace;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.source.SourceText;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.workspace.SourceRepository.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceViewTest {
    private static final URI ROOT=URI.create("memory:/project/"),JOB=ROOT.resolve("job.groovy"),SQL=ROOT.resolve("sql/update.sql");
    private SourceRepository repository(Map<URI,String> files,AtomicInteger reads){return new SourceRepository(){
        public SourceRead read(URI id,ReadPolicy policy,CancellationToken cancellation){reads.incrementAndGet();return files.containsKey(id)?SourceRead.found(new SourceText(id,files.get(id),null,"DISK",true,files.get(id))):SourceRead.unavailable("SOURCE_MISSING",id);}
        public SourceListing listProjectEntries(URI root,ScanBudget budget,CancellationToken cancellation){return new SourceListing(List.copyOf(files.keySet()),List.of(),true);}
    };}
    @Test void buffersWinWithoutDiskReadsAndOneCaptureNeverSeesLaterBufferOrDiskChanges(){
        var files=new HashMap<URI,String>();files.put(JOB,"disk");files.put(SQL,"sql disk");var reads=new AtomicInteger();var store=new DocumentStore();store.open(JOB,"gretl",1,"dirty");store.open(SQL,"sql",7,"dirty SQL");
        var view=new SourceView(repository(files,reads),new ReadPolicy(ROOT,1000,true),store.capture());assertEquals("dirty",view.read(JOB,CancellationToken.NONE).source().orElseThrow().text());assertEquals("dirty SQL",view.read(SQL,CancellationToken.NONE).source().orElseThrow().text());assertEquals(0,reads.get());
        store.applyChanges(JOB,2,List.of(DocumentStore.TextChange.full("newer")));files.put(JOB,"external change");var capture=view.freeze();assertEquals("dirty",capture.texts().get(JOB).text());
        store.close(JOB);var closed=new SourceView(repository(files,reads),new ReadPolicy(ROOT,1000,true),store.capture());assertEquals("external change",closed.read(JOB,CancellationToken.NONE).source().orElseThrow().text());assertEquals(1,reads.get());assertNull(closed.freeze().versions().get(JOB).bufferIncarnation());
        assertThrows(UnsupportedOperationException.class,()->capture.texts().clear());
    }
    @Test void absenceIsCapturedAndReadOnlyOnceWhileTheFrozenRepositoryCannotPerformNewIo(){
        var reads=new AtomicInteger();var view=new SourceView(repository(Map.of(),reads),new ReadPolicy(ROOT,1000,true),new DocumentStore().capture());view.read(SQL,CancellationToken.NONE);view.read(SQL,CancellationToken.NONE);assertEquals(1,reads.get());
        var frozen=view.freeze();assertEquals("SOURCE_MISSING",frozen.versions().get(SQL).unavailableCode());assertTrue(frozen.read(ROOT.resolve("never-captured.sql"),new ReadPolicy(ROOT,1000,true),CancellationToken.NONE).source().isEmpty());assertEquals(1,reads.get());
    }
    @Test void captureLimitsAndOwnershipBoundariesAreExplicitAndDoNotRetainUnrelatedBuffers(){
        var reads=new AtomicInteger();var store=new DocumentStore();var other=URI.create("memory:/other/job.groovy");store.open(other,"gretl",1,"foreign");
        var view=new SourceView(repository(Map.of(JOB,"1234",SQL,"5678"),reads),new ReadPolicy(ROOT,1000,true),store.capture(),2,6);
        assertTrue(view.read(JOB,CancellationToken.NONE).source().isPresent());assertEquals("SOURCE_CAPTURE_LIMIT",view.read(SQL,CancellationToken.NONE).issues().getFirst().code());assertTrue(view.freeze().limited());assertTrue(view.freeze().buffers().isEmpty());
        var denied=new SourceView(repository(Map.of(),reads),new ReadPolicy(ROOT,1000,true),store.capture());assertEquals("SOURCE_OUTSIDE_POLICY",denied.read(other,CancellationToken.NONE).issues().getFirst().code());assertNull(denied.freeze().versions().get(other).stamp());
    }
    @Test void explicitlyOpenedUntitledBuffersNeedNoFilesystemOrNetworkFallback(){
        var id=URI.create("untitled:Untitled-1");var store=new DocumentStore();store.open(id,"gretl",1,"action('draft') {}");var reads=new AtomicInteger();
        var policy=new ReadPolicy(ROOT,1000,true,List.of(),Set.of(id));var view=new SourceView(repository(Map.of(),reads),policy,store.capture());assertEquals("action('draft') {}",view.read(id,CancellationToken.NONE).source().orElseThrow().text());assertEquals(0,reads.get());
    }
}
