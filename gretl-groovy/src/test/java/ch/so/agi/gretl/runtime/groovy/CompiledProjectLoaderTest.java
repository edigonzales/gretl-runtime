package ch.so.agi.gretl.runtime.groovy;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.groovy.project.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompiledProjectLoaderTest {
    @TempDir Path temp;
    @Test void prj207EmptyDuplicateOrForeignEntryClassesFailBeforeRun() throws Exception {
        var h=new GroovyHarness(temp);var loader=new GroovyJobLoader();var classes=getClass().getClassLoader();
        var entry=new CompiledProjectScript(ProjectPath.root(),"java.lang.String","job.groovy",ProjectSourceNames.virtualGroovyName(ProjectPath.root()));
        for(var entries:List.of(List.<CompiledProjectScript>of(),List.of(entry,entry))) {
            assertEquals("PROJECT_DUPLICATE_SCRIPT_CLASS",assertThrows(GroovyDslException.class,()->loader.loadCompiledProject(entries,List.of(),h.environment,classes)).code());
        }
        assertEquals("INVALID_SCRIPT_BASE",assertThrows(GroovyDslException.class,()->loader.loadCompiledProject(List.of(entry),List.of(),h.environment,classes)).code());
        assertEquals(0,h.events.count("gretl.run.started"));
    }
}
