package ch.so.agi.gretl.tooling.workspace;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.CatalogRepository;
import ch.so.agi.gretl.tooling.source.SourceText;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.workspace.SourceRepository.*;
import static ch.so.agi.gretl.tooling.workspace.ProjectResolver.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceManagerTest {
    static final URI ROOT=URI.create("file:/workspace"), JOB=URI.create("file:/workspace/job.groovy"), SETTINGS=URI.create("file:/workspace/settings.gretl");
    static final class Files implements SourceRepository {
        final Map<URI,String> texts=new ConcurrentHashMap<>();
        public SourceRead read(URI id,ReadPolicy policy,CancellationToken cancellation){cancellation.check();String text=texts.get(id);return text==null?SourceRead.unavailable("SOURCE_MISSING",id):SourceRead.found(new SourceText(id,text,null,"DISK",true,text));}
        public SourceListing listProjectEntries(URI root,ScanBudget budget,CancellationToken cancellation){return new SourceListing(List.copyOf(texts.keySet()),List.of(),true);}
    }
    @Test void projectResolutionUsesDirtySettingsExplicitIncludeOrderAndJobRelativeSql(){
        var files=new Files();files.texts.put(JOB,"action('root') {}");files.texts.put(SETTINGS,"include 'old'");
        var nested=URI.create("file:/workspace/checks/local/job.groovy");var sql=URI.create("file:/workspace/checks/local/sql/input.sql");
        files.texts.put(nested,"task('read', DuckDbSqlExecutor) { sqlFiles = [file('sql/input.sql')] }");files.texts.put(URI.create("file:/workspace/checks/job.groovy"),"action('mustNotAppear') {}");files.texts.put(sql,"disk SQL");
        var store=new DocumentStore();store.open(SETTINGS,"gretl",1,"include ':checks:local'\ndefaults ':checks:local:read'");store.open(sql,"sql",2,"dirty SQL");
        try(var manager=new WorkspaceManager(store,new CatalogRepository())){
            manager.addRoot(ROOT,files);var capture=manager.capture(new ProjectRequest(ROOT,nested),CancellationToken.NONE);var project=capture.project();
            assertTrue(project.complete(),()->project.problems().toString());assertTrue(project.projectMode());assertEquals(List.of(JOB,nested),project.jobs().stream().map(JobSource::documentId).toList());
            assertEquals(List.of(":checks:local:read"),project.defaultTargets().stream().map(n->n.canonical()).toList());assertEquals("dirty SQL",project.sources().texts().get(sql).text());
            assertTrue(manager.isCurrent(capture,CancellationToken.NONE));store.applyChanges(sql,3,List.of(DocumentStore.TextChange.full("new SQL")));assertFalse(manager.sameGeneration(capture));assertEquals("dirty SQL",project.sources().texts().get(sql).text());
        }
    }
    @Test void missingIncludedEntryHasTheRealDirtySettingsAnchorAndKeepsItsSourcePrecondition(){
        var files=new Files();files.texts.put(JOB,"action('root') {}\n");files.texts.put(SETTINGS,"include 'sub'\n");
        files.texts.put(URI.create("file:/workspace/sub/job.groovy"),"action('child') {}\n");
        String dirty="// 😀\r\ninclude 'missing'\r\n";
        var store=new DocumentStore();store.open(SETTINGS,"gretl-runtime",4,dirty);
        try(var manager=new WorkspaceManager(store,new CatalogRepository())){
            manager.addRoot(ROOT,files);var project=manager.capture(new ProjectRequest(ROOT,JOB),CancellationToken.NONE).project();
            var problem=project.problems().stream().filter(p->p.code().equals("PROJECT_ENTRY_MISSING")).findFirst().orElseThrow();
            assertTrue(problem.error());assertEquals(SETTINGS,problem.documentId());assertNotNull(problem.span());
            assertEquals("'missing'",dirty.substring(problem.span().startUtf16(),problem.span().endUtf16()));
            assertEquals(List.of(JOB),project.jobs().stream().map(JobSource::documentId).toList());
            assertTrue(project.sources().versions().containsKey(URI.create("file:/workspace/missing/job.groovy")));
            assertEquals(4,project.sources().texts().get(SETTINGS).stamp().bufferVersion());assertFalse(project.complete());
        }
    }

    @Test void closeReopenExternalChangesConfigurationAndCatalogInvalidateWithoutTouchingOtherRoots(){
        var files=new Files();files.texts.put(JOB,"action('disk') {}");var otherRoot=URI.create("file:/other");var other=URI.create("file:/other/job.groovy");files.texts.put(other,"action('other') {}");
        var store=new DocumentStore();var catalogs=new CatalogRepository();store.open(JOB,"gretl",1,"action('dirty') {}");
        try(var manager=new WorkspaceManager(store,catalogs)){
            manager.addRoot(ROOT,files);manager.addRoot(otherRoot,files);var first=manager.capture(new ProjectRequest(ROOT,JOB),CancellationToken.NONE);var independent=manager.capture(new ProjectRequest(otherRoot,other),CancellationToken.NONE);
            store.close(JOB);store.open(JOB,"gretl",1,"action('dirty') {}");assertFalse(manager.isCurrent(first,CancellationToken.NONE));assertTrue(manager.isCurrent(independent,CancellationToken.NONE));
            var reopened=manager.capture(first.project().id(),CancellationToken.NONE);assertNotEquals(first.project().sources().versions().get(JOB).bufferIncarnation(),reopened.project().sources().versions().get(JOB).bufferIncarnation());
            store.close(JOB);var disk=manager.capture(first.project().id(),CancellationToken.NONE);files.texts.put(JOB,"action('external') {}");assertFalse(manager.isCurrent(disk,CancellationToken.NONE));
            var current=manager.capture(first.project().id(),CancellationToken.NONE);manager.updateConfiguration(ROOT,new WorkspaceManager.Configuration("de",Map.of("mode","strict")));assertFalse(manager.sameGeneration(current));assertTrue(manager.sameGeneration(independent));
            current=manager.capture(first.project().id(),CancellationToken.NONE);catalogs.loadBundled(null,null);assertFalse(manager.sameGeneration(current));assertFalse(manager.sameGeneration(independent));
            manager.removeRoot(ROOT);assertFalse(manager.containsProject(first.project().id()));assertThrows(IllegalArgumentException.class,()->manager.capture(new ProjectRequest(ROOT,JOB),CancellationToken.NONE));
        }
    }
    @Test void nestedWorkspaceOwnershipAndMissingIncludedEntriesRemainVisible(){
        var files=new Files();files.texts.put(SETTINGS,"include 'nested', 'missing'");files.texts.put(JOB,"action('root') {}");var nestedRoot=URI.create("file:/workspace/nested");files.texts.put(URI.create("file:/workspace/nested/job.groovy"),"action('nested') {}");
        try(var manager=new WorkspaceManager(new DocumentStore(),new CatalogRepository())){
            manager.addRoot(ROOT,files);var before=manager.capture(new ProjectRequest(ROOT,JOB),CancellationToken.NONE);assertTrue(before.project().problems().stream().anyMatch(p->p.code().equals("PROJECT_ENTRY_MISSING")&&p.error()));
            manager.addRoot(nestedRoot,files);assertFalse(manager.sameGeneration(before));var after=manager.capture(new ProjectRequest(ROOT,JOB),CancellationToken.NONE);assertEquals(List.of(JOB),after.project().jobs().stream().map(JobSource::documentId).toList());assertTrue(after.project().problems().stream().anyMatch(p->p.code().equals("PROJECT_SOURCE_NOT_INSPECTED")&&!p.error()));
            assertThrows(IllegalArgumentException.class,()->manager.addRoot(URI.create("file:/workspace/"),files));
        }
    }
    @Test void missingSourceAppearingDuringCaptureCannotBePublished(){
        var files=new Files();files.texts.put(JOB,"action('root') {}");var calls=new java.util.concurrent.atomic.AtomicInteger();
        SourceRepository racing=new SourceRepository(){
            public SourceRead read(URI id,ReadPolicy policy,CancellationToken cancellation){if(id.equals(SETTINGS)&&calls.incrementAndGet()==2)files.texts.put(SETTINGS,"defaults 'root'");return files.read(id,policy,cancellation);}
            public SourceListing listProjectEntries(URI root,ScanBudget budget,CancellationToken cancellation){return files.listProjectEntries(root,budget,cancellation);}
        };
        try(var manager=new WorkspaceManager(new DocumentStore(),new CatalogRepository())){manager.addRoot(ROOT,racing);assertThrows(CancellationException.class,()->manager.capture(new ProjectRequest(ROOT,JOB),CancellationToken.NONE));}
    }
    @Test void unrelatedGroovyChangesDoNotInvalidateKnownProjectOrOwnership() {
        var files=new Files();files.texts.put(JOB,"action('a'){}");var unrelated=URI.create("file:/workspace/unrelated.groovy");files.texts.put(unrelated,"not a job");
        try(var manager=new WorkspaceManager(new DocumentStore(),new CatalogRepository())){
            manager.addRoot(ROOT,files);var before=manager.capture(new ProjectRequest(ROOT,JOB),CancellationToken.NONE);
            files.texts.put(unrelated,"still not a job");manager.onSourcesChanged(Set.of(unrelated));
            assertTrue(manager.sameGeneration(before));assertTrue(manager.isCurrent(before,CancellationToken.NONE));
        }
    }

}
