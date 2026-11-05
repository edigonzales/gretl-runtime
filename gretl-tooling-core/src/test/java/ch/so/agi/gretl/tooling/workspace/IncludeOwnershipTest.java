package ch.so.agi.gretl.tooling.workspace;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.CatalogRepository;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class IncludeOwnershipTest {
    @TempDir Path root;
    Path write(String name,String text)throws Exception{Path p=root.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,text);return p;}
    @Test void unopenedSecondOwnerIsCapturedAndNewOwnerInvalidatesEarlierProof()throws Exception{
        Path job=write("job.groovy","apply from:'other/shared/tasks.groovy'");Path shared=write("other/shared/tasks.groovy","task('shared',Echo){}");
        var store=new DocumentStore();
        try(var manager=new WorkspaceManager(store,new CatalogRepository())){
            manager.addRoot(root.toUri(),new FileSourceRepository(root.toUri()));
            var request=new ProjectResolver.ProjectRequest(root.toUri(),job.toUri());var first=manager.capture(request,CancellationToken.NONE);
            assertTrue(first.ownership().complete());assertTrue(first.ownership().exclusive(shared.toUri()));
            // No opened panel or buffer for this second conventional entry.
            write("other/job.groovy","apply from:'shared/tasks.groovy'");
            assertFalse(manager.isCurrent(first,CancellationToken.NONE),"New inventory must invalidate even without a delivered watcher event");
            var second=manager.capture(request,CancellationToken.NONE);assertTrue(second.ownership().complete());assertEquals(2,second.ownership().contexts(shared.toUri()).size());assertFalse(second.ownership().exclusive(shared.toUri()));
            manager.onSourcesChanged(Set.of(root.resolve("other/job.groovy").toUri()));assertFalse(manager.sameGeneration(second));
            var current=manager.capture(request,CancellationToken.NONE);store.open(shared.toUri(),"gretl-runtime",1,"task('dirty',Echo){}");assertFalse(manager.sameGeneration(current));
            var dirty=manager.capture(request,CancellationToken.NONE);assertEquals("task('dirty',Echo){}",dirty.project().sources().texts().get(shared.toUri()).text());
            assertEquals(2,dirty.ownership().contexts(shared.toUri()).size());assertTrue(dirty.ownership().generation()>current.ownership().generation());
            store.close(shared.toUri());store.open(shared.toUri(),"gretl-runtime",1,"task('dirty',Echo){}");assertFalse(manager.isCurrent(dirty,CancellationToken.NONE));
        }
    }
    @Test void settingsEntriesAreNotDoubleCountedAndMissingInventoryIsUnknown()throws Exception{
        Path job=write("job.groovy","// root");write("settings.gretl","include 'a','b'");
        write("a/job.groovy","apply from:'../shared/tasks.groovy'");write("b/job.groovy","apply from:'../shared/tasks.groovy'");Path shared=write("shared/tasks.groovy","task('shared',Echo){}");
        try(var manager=new WorkspaceManager(new DocumentStore(),new CatalogRepository())){
            manager.addRoot(root.toUri(),new FileSourceRepository(root.toUri()));var capture=manager.capture(new ProjectResolver.ProjectRequest(root.toUri(),job.toUri()),CancellationToken.NONE);
            assertEquals(1,capture.ownership().projects().size());assertEquals(2,capture.ownership().contexts(shared.toUri()).size());assertTrue(capture.ownership().complete());
        }
        var files=new FileSourceRepository(root.toUri());
        SourceRepository incomplete=new SourceRepository(){
            public SourceRead read(java.net.URI id,ReadPolicy policy,CancellationToken cancel){return files.read(id,policy,cancel);}
            public SourceListing listProjectEntries(java.net.URI root,ScanBudget budget,CancellationToken cancel){return new SourceListing(List.of(),List.of(new SourceIssue("SOURCE_SCAN_LIMIT",root)),false);}
            public List<SourceIssue> checkIncludeTraversal(IncludeTraversal traversal,ReadPolicy policy,CancellationToken cancel){return files.checkIncludeTraversal(traversal,policy,cancel);}
        };
        try(var manager=new WorkspaceManager(new DocumentStore(),new CatalogRepository())){
            manager.addRoot(root.toUri(),incomplete);var capture=manager.capture(new ProjectResolver.ProjectRequest(root.toUri(),job.toUri()),CancellationToken.NONE);
            assertFalse(capture.ownership().complete());assertFalse(capture.ownership().exclusive(shared.toUri()));
        }
    }
    @Test void nestedWorkspaceInventoryIsNotScannedAndIncludeDepthIsBounded()throws Exception{
        Path job=write("job.groovy","task('root',Echo){}");Path nested=Files.createDirectories(root.resolve("foreign"));
        String deep="foreign/"+"deep/".repeat(40);write(deep+"job.groovy","apply from:unknown");
        try(var manager=new WorkspaceManager(new DocumentStore(),new CatalogRepository())){
            manager.addRoot(root.toUri(),new FileSourceRepository(root.toUri()));manager.addRoot(nested.toUri(),new FileSourceRepository(nested.toUri()));
            var capture=manager.capture(new ProjectResolver.ProjectRequest(root.toUri(),job.toUri()),CancellationToken.NONE);
            assertTrue(capture.ownership().complete());assertTrue(capture.project().sources().versions().keySet().stream().noneMatch(id->SourceUris.contains(nested.toUri(),id)));
        }
        write("job.groovy","apply from:'chain/f0.groovy'");
        for(int i=0;i<65;i++)write("chain/f"+i+".groovy",i==64?"task('last',Echo){}":"apply from:'f"+(i+1)+".groovy'");
        var project=new ProjectResolver().resolve(new ProjectResolver.ProjectRequest(root.toUri(),job.toUri()),new FileSourceRepository(root.toUri()),new DocumentStore().capture(),CancellationToken.NONE);
        assertFalse(project.includeClosure().sourcesComplete());assertFalse(project.includeClosure().orderProven());assertTrue(project.problems().stream().anyMatch(p->p.code().equals("APPLY_LIMIT")&&!p.error()));
    }

}
