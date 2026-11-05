package ch.so.agi.gretl.tooling.workspace;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.CatalogRepository;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SourceContextServiceTest {
    @TempDir Path root;
    void write(String name,String text)throws Exception{Path p=root.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,text);}
    @Test void confirmedEditorSelectionAndPinnedPanelsAreIndependentAndIncarnationBound()throws Exception{
        write("settings.gretl","include 'north','south'");
        for(String scope:List.of("north","south"))write(scope+"/job.groovy","apply from:'../shared/tasks.groovy'");
        write("shared/tasks.groovy","task('a',Echo){}");var shared=root.resolve("shared/tasks.groovy").toUri();
        var store=new DocumentStore();var service=new SourceContextService();
        try(var manager=new WorkspaceManager(store,new CatalogRepository())){
            manager.addRoot(root.toUri(),new FileSourceRepository(root.toUri()));
            var request=new ProjectResolver.ProjectRequest(root.toUri(),root.resolve("settings.gretl").toUri());
            var capture=manager.capture(request,CancellationToken.NONE);
            var settings=service.contextsFor(capture,root.resolve("settings.gretl").toUri());assertEquals(1,settings.contexts().size());
            assertEquals(root.resolve("settings.gretl").toUri(),settings.contexts().getFirst().application().id().entryDocument());
            assertEquals(capture.ownership().generation(),manager.capture(request,CancellationToken.NONE).ownership().generation());
            var contexts=service.contextsFor(capture,shared);
            assertEquals(2,contexts.contexts().size());assertTrue(contexts.selectionRequired());assertNull(contexts.selectedToken());
            assertEquals("SOURCE_CONTEXT_SELECTION_REQUIRED",assertThrows(IllegalArgumentException.class,()->service.resolveForQuery(capture,shared,null)).getMessage());
            var north=contexts.contexts().get(0);var south=contexts.contexts().get(1);
            var selected=service.select(capture,shared,north.token(),contexts.incarnation(),contexts.ownerGeneration());assertEquals(north.token(),selected.selectedToken());
            assertEquals(south,service.resolveForQuery(capture,shared,south.token()));assertEquals(north,service.resolveForQuery(capture,shared,null));
            assertThrows(IllegalArgumentException.class,()->service.resolveForQuery(capture,shared,"forged"));
            store.open(shared,"gretl-runtime",1,"task('dirty',Echo){}");var dirty=manager.capture(request,CancellationToken.NONE);
            assertThrows(IllegalArgumentException.class,()->service.select(dirty,shared,north.token(),contexts.incarnation(),contexts.ownerGeneration()));
            var dirtyContexts=service.contextsFor(dirty,shared);assertTrue(dirtyContexts.selectionRequired());assertNotNull(dirtyContexts.incarnation());
            var dirtyChoice=dirtyContexts.contexts().getFirst();service.select(dirty,shared,dirtyChoice.token(),dirtyContexts.incarnation(),dirtyContexts.ownerGeneration());
            store.applyChanges(shared,2,List.of(DocumentStore.TextChange.full("task('changed',Echo){}")));var edited=manager.capture(request,CancellationToken.NONE);
            var renewed=service.contextsFor(edited,shared);assertFalse(renewed.selectionRequired());
            assertEquals(dirtyChoice.application().id(),service.resolveForQuery(edited,shared,null).application().id());
            assertThrows(IllegalArgumentException.class,()->service.resolveForQuery(edited,shared,dirtyChoice.token()));
            store.close(shared);store.open(shared,"gretl-runtime",1,"task('dirty',Echo){}");var reopened=manager.capture(request,CancellationToken.NONE);
            assertNotEquals(dirtyContexts.incarnation(),service.contextsFor(reopened,shared).incarnation());
        }
    }
}
