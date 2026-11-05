package ch.so.agi.gretl.tooling.workspace;

import ch.so.agi.gretl.tooling.CancellationToken;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class IncludeResolutionTest {
    @TempDir Path root;
    Path write(String name,String text)throws Exception{Path p=root.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,text);return p;}
    ProjectResolver.ResolvedProject resolve(DocumentStore store)throws Exception{
        return new ProjectResolver().resolve(new ProjectResolver.ProjectRequest(root.toUri(),root.resolve("job.groovy").toUri()),new FileSourceRepository(root.toUri()),store.capture(),CancellationToken.NONE);
    }
    @Test void nestedClosureUsesDirtyBuffersAndKeepsEntriesSeparate()throws Exception{
        write("job.groovy","task('before',Echo){}\napply from:'tasks/start.groovy'\ntask('after',Echo){}");
        write("tasks/start.groovy","apply from:'nested/step.groovy'");write("tasks/step.groovy","task('wrong',Echo){}");
        Path child=write("tasks/nested/step.groovy","task('disk',Echo){}");
        var store=new DocumentStore();store.open(child.toUri(),"gretl-runtime",4,"task('dirty',Echo){}");
        var project=resolve(store);assertEquals(1,project.jobs().size());assertEquals(3,project.includeClosure().applications().size());
        assertEquals(3,project.documents().size());assertTrue(project.includeClosure().sourcesComplete());
        assertEquals(List.of("before","dirty","after"),project.includeClosure().registrationOrder().stream().map(e->e.declaration().name()).toList());
        assertEquals(4,project.sources().texts().get(child.toUri()).stamp().bufferVersion());
        store.close(child.toUri());assertTrue(resolve(store).includeClosure().registrationOrder().stream().anyMatch(e->"disk".equals(e.declaration().name())));
    }
    @Test void unsavedMissingChildThenCreateIsObservedAsSourceDependency()throws Exception{
        write("job.groovy","apply from:'new.groovy'");URI child=root.resolve("new.groovy").toUri();var store=new DocumentStore();
        var missing=resolve(store);assertFalse(missing.includeClosure().sourcesComplete());assertEquals("SOURCE_MISSING",missing.sources().versions().get(child).unavailableCode());
        store.open(child,"gretl-runtime",1,"task('newTask',Echo){}");assertTrue(resolve(store).includeClosure().sourcesComplete());
        store.close(child);write("new.groovy","task('saved',Echo){}");assertTrue(resolve(store).includeClosure().sourcesComplete());
    }
    @Test void cyclesDuplicatesUnsafePathsAndFragmentDefaultsAreExplicit()throws Exception{
        for(var sample:Map.of("apply from:'job.groovy'","APPLY_CYCLE","apply from:'f.groovy'\napply from:'f.groovy'","APPLY_DUPLICATE",
                "apply from:'../outside.groovy'","APPLY_PATH_UNSAFE","apply from:'https://example.test/f.groovy'","APPLY_PATH_UNSAFE").entrySet()){
            write("job.groovy",sample.getKey());write("f.groovy","task('f',Echo){}");var p=resolve(new DocumentStore());assertFalse(p.includeClosure().sourcesComplete());assertTrue(p.problems().stream().anyMatch(x->x.code().equals(sample.getValue())),p.problems().toString());
        }
        write("job.groovy","apply from:'f.groovy'");write("f.groovy","defaults 'f'\ntask('f',Echo){}");assertTrue(resolve(new DocumentStore()).problems().stream().anyMatch(p->p.code().equals("APPLY_DEFAULTS_NOT_ALLOWED")));
    }
    @Test void sharedSourcesHaveOwnerResourceBasesAndSqlCaptures()throws Exception{
        write("settings.gretl","include 'north','south'");write("job.groovy","// root");
        for(String scope:List.of("north","south")){
            write(scope+"/job.groovy","apply from:'../shared/tasks.groovy'");write(scope+"/sql/input.sql","select '"+scope+"'");
        }
        write("shared/tasks.groovy","task('sql',DuckDbSqlExecutor){sqlFiles=[file('sql/input.sql')]}");write("shared/sql/input.sql","wrong");
        var p=resolve(new DocumentStore());assertTrue(p.includeClosure().sourcesComplete(),p.problems().toString());assertEquals(3,p.jobs().size());
        var shared=p.includeClosure().applications().stream().filter(a->a.id().sourceDocument().equals(root.resolve("shared/tasks.groovy").toUri())).toList();assertEquals(2,shared.size());assertNotEquals(shared.get(0).resourceBase(),shared.get(1).resourceBase());
        for(String scope:List.of("north","south"))assertEquals("select '"+scope+"'",p.sources().texts().get(root.resolve(scope+"/sql/input.sql").toUri()).text());
        assertFalse(p.sources().texts().containsKey(root.resolve("shared/sql/input.sql").toUri()));
    }
    @Test void symlinkBeforeParentTraversalCannotDisappearInNormalization()throws Exception{
        write("job.groovy","apply from:'link/../safe.groovy'");write("safe.groovy","task('safe',Echo){}");Files.createSymbolicLink(root.resolve("link"),root);
        var p=resolve(new DocumentStore());assertFalse(p.includeClosure().sourcesComplete());assertTrue(p.problems().stream().anyMatch(x->x.code().equals("APPLY_PATH_UNSAFE")),p.problems().toString());
    }
}
