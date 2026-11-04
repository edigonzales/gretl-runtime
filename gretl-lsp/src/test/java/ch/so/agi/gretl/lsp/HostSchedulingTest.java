package ch.so.agi.gretl.lsp;

import ch.so.agi.gretl.tooling.catalog.CatalogRepository;
import ch.so.agi.gretl.tooling.semantic.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HostSchedulingTest {
    @TempDir Path root;
    @Test void cancelledAndSupersededAnalysisNeverPublishesAndShutdownCompletesQueuedRequests()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var published=new CopyOnWriteArrayList<AnalysisSnapshot>();var count=new AtomicInteger();
        var job=root.resolve("job.groovy");Files.writeString(job,"action('a') {}\n");
        try(var host=new ServerWorkspace(new CatalogRepository(),(snapshot,cancel)->published.add(snapshot),(capture,cancel)-> {
            if(count.incrementAndGet()==1) {entered.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}cancel.check();}
            return AnalysisSnapshot.of(new ProjectAnalyzer().analyze(capture,cancel));
        })) {
            host.addRoot(root.toUri());host.configure(new ServerConfiguration("en",true));
            host.documents.open(job.toUri(),"gretl-runtime",1,"action('a') {}\n");
            var old=host.request(job.toUri(),List.of(),(snapshot,cancel)->snapshot);
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            host.documents.applyChanges(job.toUri(),2,List.of(ch.so.agi.gretl.tooling.workspace.DocumentStore.TextChange.full("action('b') {}\n")));host.changed();
            var fresh=host.request(job.toUri(),List.of(),(snapshot,cancel)->snapshot);
            release.countDown();assertThrows(ExecutionException.class,()->old.get(10,TimeUnit.SECONDS));
            var result=fresh.get(10,TimeUnit.SECONDS);assertTrue(result.analysis().symbols().keySet().stream().anyMatch(id->id.qualifiedName().equals("b")));
            assertTrue(published.stream().noneMatch(snapshot->snapshot.analysis().symbols().keySet().stream().anyMatch(id->id.qualifiedName().equals("a"))));
            var blocked=new CountDownLatch(1);var gate=new CountDownLatch(1);
            var active=host.request(job.toUri(),List.of(),(snapshot,cancel)->{blocked.countDown();try{gate.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}cancel.check();return snapshot;});
            assertTrue(blocked.await(5,TimeUnit.SECONDS));assertTrue(active.cancel(false));gate.countDown();assertTrue(active.isCancelled());
            var requests=new ArrayList<CompletableFuture<?>>();
            for(int i=0;i<40;i++)requests.add(host.request(job.toUri(),List.of(),(snapshot,cancel)->{try{Thread.sleep(20);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}cancel.check();return snapshot;}));
            host.close();assertTrue(requests.stream().allMatch(CompletableFuture::isDone));
        }
    }
    @Test void anOverflowedControlQueueRequiresRestartInsteadOfServingUnsynchronizedBuffers()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var server=new GretlLanguageServer(new CatalogRepository())) {
            var initialize=new org.eclipse.lsp4j.InitializeParams();initialize.setCapabilities(new org.eclipse.lsp4j.ClientCapabilities());initialize.setRootUri(root.toUri().toString());
            server.initialize(initialize).get(5,TimeUnit.SECONDS);
            server.notification(()->{entered.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            for(int i=0;i<70;i++)server.notification(()->{});
            var failure=assertThrows(ExecutionException.class,()->server.query(root.resolve("job.groovy").toUri().toString(),(snapshot,cancel)->snapshot).get(5,TimeUnit.SECONDS));
            assertTrue(failure.getCause().getMessage().contains("CONTROL_QUEUE_LIMIT_RESTART_REQUIRED"));release.countDown();
        }finally{release.countDown();}
    }
}
