package ch.so.agi.gretl.runtime.groovy;

import ch.so.agi.gretl.runtime.groovy.project.GretlProjectParser;
import ch.so.agi.gretl.runtime.runner.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GroovyFinalizerTest {
    @TempDir Path root;
    Path write(GroovyHarness h,String name,String text)throws Exception {
        var p=h.jobDir.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,text);return p;
    }
    JobDefinition load(GroovyHarness h){return new GroovyJobLoader().load(h.jobDir.resolve("job.groovy"),h.environment);}
    @Test void forwardFragmentLinksTypedTasksAndGlobalCleanupUseOnePipeline()throws Exception {
        var h=new GroovyHarness(root);
        write(h,"job.groovy","apply from:'tasks/owner.groovy'\napply from:'tasks/end.groovy'\ndefaults 'owner'");
        write(h,"tasks/owner.groovy","action('owner',finalizedBy:['typed','report']){println 'owner';throw new IllegalStateException('owner-failed')}");
        write(h,"tasks/end.groovy","task('typed',Echo){text='finished'}\naction('report',after:'typed'){println 'report'}\ncleanup('global'){println 'cleanup'}");
        var d=load(h);var p=new ExecutionPlanner().plan(d,List.of());assertEquals(List.of("owner","typed","report"),p.selectedTasks().stream().map(TaskDefinition::id).toList());
        assertEquals(2,p.edges().stream().filter(e->e.type()==PlanEdge.EdgeType.FINALIZED_BY).count());
        var out=h.run(d,Map.of());assertEquals(RunStatus.FAILED,out.status());assertEquals("owner-failed",out.primaryFailure().orElseThrow().message());
        assertEquals(TaskStatus.SUCCEEDED,out.task("typed").orElseThrow().status());assertEquals(InvocationStatus.SUCCEEDED,out.invocations().getFirst().status());
        assertEquals(List.of("owner","report","cleanup"),h.messages.messages());
    }
    @Test void nullEmptyFlatArraysAndTaskRefsFollowExistingLinkNormalization()throws Exception {
        var h=new GroovyHarness(root);var d=h.loadSource("forms.groovy","""
                def end=action('end',after:'a'){}
                action('a',finalizedBy:end){}
                action('b',finalizedBy:['end'] as Object[]){}
                action('c',finalizedBy:null){}
                action('d',finalizedBy:[]){}
                defaults 'a','b','c','d'
                """);
        assertEquals(List.of("end"),d.task("a").orElseThrow().finalizedBy().stream().map(r->r.id()).toList());
        assertTrue(d.task("c").orElseThrow().finalizedBy().isEmpty());assertTrue(d.task("d").orElseThrow().finalizedBy().isEmpty());
        assertEquals(RunStatus.SUCCEEDED,h.run(d,Map.of()).status());
        assertThrows(GroovyDslException.class,()->h.loadSource("nested.groovy","action('a',finalizedBy:[['end']]){}\naction('end'){}\ndefaults 'a'"));
    }
    @Test void invalidFinalizerRelationsCarryOwnerAndFragmentSource()throws Exception {
        for(String option:List.of("'missing'","['f','f']","'a'")) {
            var h=new GroovyHarness(Files.createDirectory(root.resolve("case"+option.hashCode())));
            write(h,"job.groovy","apply from:'tasks/owner.groovy'\naction('f'){}\ndefaults 'a'");
            var fragment=write(h,"tasks/owner.groovy","// first\naction('a',finalizedBy:"+option+"){}");
            var error=assertThrows(DefinitionException.class,()->load(h));
            assertEquals(option.equals("'missing'")?"UNKNOWN_TASK_REFERENCE":option.equals("'a'")?"SELF_FINALIZER":"DUPLICATE_TASK_LINK",error.code());
            assertEquals(fragment.toString(),error.failureSource().orElseThrow().file());assertEquals(2,error.failureSource().orElseThrow().line());
        }
    }
    @Test void sharedProjectFragmentHasSeparateFinalizersAndOwnResourceBase()throws Exception {
        var h=new GroovyHarness(root);write(h,"settings.gretl","include 'a','b'\ndefaults ':a:run',':b:run'");
        for(String id:List.of("a","b")){write(h,id+"/job.groovy","apply from:'../shared/tasks.groovy'");write(h,id+"/input.txt",id);}
        write(h,"shared/input.txt","wrong");
        write(h,"shared/tasks.groovy","def count=0\naction('run',finalizedBy:'end'){count++;println 'owner-'+java.nio.file.Files.readString(file('input.txt'))}\naction('end'){println 'end-'+java.nio.file.Files.readString(file('input.txt'))+'-'+count}");
        var d=new GroovyJobLoader().loadProject(new GretlProjectParser().parse(h.jobDir),h.environment);var out=h.run(d,Map.of());assertEquals(RunStatus.SUCCEEDED,out.status());
        assertEquals(List.of("owner-a","end-a-1","owner-b","end-b-1"),h.messages.messages());assertEquals(4,out.tasks().size());
    }
    @Test void qualifiedFinalizerUsesTargetDeclarationResourcesAndTrueSourcePosition()throws Exception {
        var h=new GroovyHarness(root);write(h,"settings.gretl","include 'a','b'\ndefaults ':a:run'");
        write(h,"a/job.groovy","action('run',finalizedBy:':b:end'){println 'owner'}");write(h,"b/job.groovy","apply from:'tasks/end.groovy'");
        write(h,"a/input.txt","wrong");write(h,"b/input.txt","right");
        var fragment=write(h,"b/tasks/end.groovy","action('end'){\n assert java.nio.file.Files.readString(file('input.txt'))=='right'\n throw new IllegalStateException('finalizer-source')\n}");
        var d=new GroovyJobLoader().loadProject(new GretlProjectParser().parse(h.jobDir),h.environment);var out=h.run(d,Map.of());
        assertEquals(RunStatus.FAILED,out.status());assertEquals("finalizer-source",out.primaryFailure().orElseThrow().message());
        var source=out.primaryFailure().orElseThrow().source().orElseThrow();assertEquals(fragment.toRealPath(),Path.of(source.file()).toRealPath());assertEquals(3,source.line());
    }
    @Test void finalizationAloneGrantsNoOptionalResultRights()throws Exception {
        var h=new GroovyHarness(root);var d=h.loadSource("rights.groovy","action('a',finalizedBy:'f'){'value'}\naction('f'){try{resultOptional('a');throw new AssertionError('rights granted')}catch(ch.so.agi.gretl.runtime.runner.ResultAccessException e){assert e.code()=='UNDECLARED_RESULT_DEPENDENCY';throw e}}\ndefaults 'a'");
        var out=h.run(d,Map.of());assertEquals(RunStatus.FAILED,out.status());assertEquals(ResultAccessException.class.getName(),out.primaryFailure().orElseThrow().type());
    }
    @Test void finishOwnerAllowsTypedFinalizerAndSuppressesOrdinaryFollowup()throws Exception {
        var h=new GroovyHarness(root);var d=h.loadSource("finish.groovy","""
                action('a',finalizedBy:'f'){finishJob('empty')}
                action('normal'){throw new AssertionError('normal work resumed')}
                task('f',Echo){text='completed'}
                defaults 'a','normal'
                """);
        var out=h.run(d,Map.of());assertEquals(RunStatus.NO_DATA,out.status());assertEquals(Optional.of("empty"),out.finishReason());
        assertEquals(TaskStatus.SUCCEEDED,out.task("f").orElseThrow().status());assertEquals(TaskStatus.NOT_RUN,out.task("normal").orElseThrow().status());assertEquals(InvocationStatus.SUCCEEDED,out.invocations().getFirst().status());
    }
    @Test void selectionAndFreezeDoNotExecuteAnyFinalizerConfigurationPredicateOrCleanup()throws Exception {
        var h=new GroovyHarness(root);var d=h.loadSource("lazy.groovy","""
                action('a',finalizedBy:'f'){throw new AssertionError('owner executed')}
                task('f',Echo,when:{throw new AssertionError('when executed')}){throw new AssertionError('config executed')}
                cleanup('global'){throw new AssertionError('cleanup executed')}
                defaults 'a'
                """);
        assertEquals(List.of("a","f"),new ExecutionPlanner().plan(d,List.of()).selectedTasks().stream().map(TaskDefinition::id).toList());
        assertTrue(h.events.events().isEmpty());assertTrue(h.messages.messages().isEmpty());
    }
}
