package ch.so.agi.gretl.tooling.workspace;

import ch.so.agi.gretl.tooling.catalog.CatalogRepository;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.workspace.ProjectResolver.*;
import static ch.so.agi.gretl.tooling.workspace.WorkspaceManagerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisSchedulerTest {
    private static void await(CountDownLatch latch){try{assertTrue(latch.await(5,TimeUnit.SECONDS),"Worker did not reach the expected boundary");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new CancellationException();}}
    @Test void supersededAnalysisCannotPublishAfterNewerAnalysisEvenWhenItIgnoresInterruption() throws Exception {
        var files=new Files();files.texts.put(JOB,"action('disk') {}");var store=new DocumentStore();store.open(JOB,"gretl",1,"action('one') {}");var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();var published=new CopyOnWriteArrayList<String>();
        try(var manager=new WorkspaceManager(store,new CatalogRepository())){
            manager.addRoot(ROOT,files);
            var scheduler=new AnalysisScheduler<String>(manager,(capture,cancellation)->{
                String text=capture.project().sources().texts().get(JOB).text();
                if(calls.incrementAndGet()==1){entered.countDown();boolean done=false;while(!done)try{done=release.await(5,TimeUnit.SECONDS);if(!done)throw new IllegalStateException("release timeout");}catch(InterruptedException ignored){/* Deliberately hostile analyzer: publication must still be guarded. */}}
                return text;
            },(capture,value)->published.add(value));
            try{
                var old=scheduler.submit(new ProjectRequest(ROOT,JOB));await(entered);store.applyChanges(JOB,2,List.of(DocumentStore.TextChange.full("action('two') {}")));
                var newer=scheduler.submit(new ProjectRequest(ROOT,JOB)).get(5,TimeUnit.SECONDS);assertEquals(AnalysisScheduler.Status.PUBLISHED,newer.status());release.countDown();assertEquals(AnalysisScheduler.Status.CANCELLED,old.get(5,TimeUnit.SECONDS).status());
                assertEquals(List.of("action('two') {}"),published);assertFalse(scheduler.latest(newer.capture().project().id()).orElseThrow().stale());
                store.close(JOB);assertTrue(scheduler.latest(newer.capture().project().id()).orElseThrow().stale());manager.removeRoot(ROOT);assertTrue(scheduler.latest(newer.capture().project().id()).isEmpty());
            }finally{release.countDown();scheduler.close();}assertTrue(scheduler.isTerminated());
        }
    }
    @Test void queueLimitIsExplicitAndClosingCompletesQueuedTickets() throws Exception {
        var files=new Files();files.texts.put(JOB,"action('root') {}");var entered=new CountDownLatch(1);var released=new CountDownLatch(1);
        try(var manager=new WorkspaceManager(new DocumentStore(),new CatalogRepository())){
            manager.addRoot(ROOT,files);var scheduler=new AnalysisScheduler<String>(manager,(capture,cancellation)->{entered.countDown();try{released.await(5,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}cancellation.check();return "done";},(capture,value)->fail("Cancelled job must not publish"),1,1,1);
            try{
                var running=scheduler.submit(new ProjectRequest(ROOT,JOB));await(entered);
                var queued=scheduler.submit(new ProjectRequest(ROOT,URI.create("file:/workspace/queued/job.groovy")));
                var limited=scheduler.submit(new ProjectRequest(ROOT,URI.create("file:/workspace/limited/job.groovy"))).get(5,TimeUnit.SECONDS);assertEquals(AnalysisScheduler.Status.LIMIT,limited.status());assertEquals("ANALYSIS_QUEUE_LIMIT",limited.problemCode());
                scheduler.close();assertEquals(AnalysisScheduler.Status.CANCELLED,running.get(5,TimeUnit.SECONDS).status());assertEquals(AnalysisScheduler.Status.CANCELLED,queued.get(5,TimeUnit.SECONDS).status());assertEquals(0,scheduler.pendingCount());assertTrue(scheduler.isTerminated());assertThrows(IllegalStateException.class,()->scheduler.submit(new ProjectRequest(ROOT,JOB)));
            }finally{released.countDown();scheduler.close();}
        }
    }
    @Test void externalFileChangeBetweenAnalysisAndPublishRejectsResult() throws Exception {
        var files=new Files();files.texts.put(JOB,"action('first') {}");var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var manager=new WorkspaceManager(new DocumentStore(),new CatalogRepository())){
            manager.addRoot(ROOT,files);
            try(var scheduler=new AnalysisScheduler<String>(manager,(capture,cancellation)->{entered.countDown();await(release);return "obsolete";},(capture,value)->fail("External change must reject publication"))){
                var pending=scheduler.submit(new ProjectRequest(ROOT,JOB));await(entered);files.texts.put(JOB,"action('second') {}");release.countDown();var result=pending.get(5,TimeUnit.SECONDS);assertEquals(AnalysisScheduler.Status.STALE,result.status());assertNull(result.value());assertTrue(scheduler.latest(result.capture().project().id()).isEmpty());
            }finally{release.countDown();}
        }
    }
    @Test void settingsAndCatalogChangesCancelInFlightCapturesBeforePublication() throws Exception {
        for(boolean catalogChange:List.of(false,true)){
            var files=new Files();files.texts.put(JOB,"action('root') {}");files.texts.put(SETTINGS,"defaults 'root'");var store=new DocumentStore();store.open(SETTINGS,"gretl",1,"defaults 'root'");var catalogs=new CatalogRepository();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            try(var manager=new WorkspaceManager(store,catalogs)){
                manager.addRoot(ROOT,files);
                try(var scheduler=new AnalysisScheduler<String>(manager,(capture,cancellation)->{entered.countDown();await(release);return "old";},(capture,value)->fail("Invalidated settings/catalog must not publish"))){
                    var result=scheduler.submit(new ProjectRequest(ROOT,JOB));await(entered);
                    if(catalogChange){catalogs.loadBundled(null,null);manager.onCatalogChanged();}else store.applyChanges(SETTINGS,2,List.of(DocumentStore.TextChange.full("defaults 'changed'")));
                    release.countDown();assertEquals(AnalysisScheduler.Status.CANCELLED,result.get(5,TimeUnit.SECONDS).status());
                }finally{release.countDown();}
            }
        }
    }

}
