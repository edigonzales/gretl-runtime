package ch.so.agi.gretl.runtime.groovy;

import ch.so.agi.gretl.runtime.groovy.project.GretlProjectParser;
import ch.so.agi.gretl.runtime.runner.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class FragmentLoaderTest {
    @TempDir Path temp;
    @BeforeEach void reset(){TestGroovyOperations.reset();}
    Path write(GroovyHarness h,String name,String text)throws Exception{
        Path file=h.jobDir.resolve(name);Files.createDirectories(file.getParent());Files.writeString(file,text);return file;
    }
    JobDefinition load(GroovyHarness h){return new GroovyJobLoader().load(h.jobDir.resolve("job.groovy"),h.environment);}
    Object value(JobOutcome outcome,String id){return ((ResultSlot.Value)outcome.task(id).orElseThrow().slot()).value();}
    @Test void forwardTypedResultsParametersAndOneCleanupShareTheDefinition()throws Exception{
        var h=new GroovyHarness(temp);
        write(h,"job.groovy","apply from:'tasks/consumer.groovy'\napply(from:'tasks/producer.groovy')\ndefaults 'consume'\n");
        write(h,"tasks/consumer.groovy","task('consume', Echo, dependsOn:'produce'){text=result('produce').result+'-'+parameter('suffix')}\n");
        write(h,"tasks/producer.groovy","parameter('suffix',description:'S',defaultValue:'tail')\ntask('produce',Echo){text='seed'}\ncleanup('end'){println 'cleaned'}\n");
        var definition=load(h);assertEquals(List.of("consume","produce"),definition.tasks().stream().map(TaskDefinition::id).toList());
        assertEquals(0,TestGroovyOperations.BUILDER_CREATIONS.get());assertEquals(0,TestGroovyOperations.HANDLER_CALLS.get());
        var outcome=h.run(definition,Map.of());assertEquals(RunStatus.SUCCEEDED,outcome.status());
        assertEquals("seed-tail",((TestGroovyOperations.EchoResult)value(outcome,"consume")).result());
        assertEquals(List.of("produce","consume"),outcome.tasks().stream().map(TaskOutcome::taskId).toList());
        assertEquals(1,h.messages.messages().stream().filter("cleaned"::equals).count());
    }
    @Test void registrationIsDepthFirstAtApplyAndDoesNotCreateDependencies()throws Exception{
        var h=new GroovyHarness(temp);
        write(h,"job.groovy","action('before'){}\napply from:'tasks/a.groovy'\naction('after'){}\ndefaults 'after'");
        write(h,"tasks/a.groovy","action('a1'){}\napply from:'b.groovy'\naction('a2'){}");write(h,"tasks/b.groovy","action('b'){}");
        var d=load(h);assertEquals(List.of("before","a1","b","a2","after"),d.tasks().stream().map(TaskDefinition::id).toList());
        assertEquals(List.of("after"),new ExecutionPlanner().plan(d,List.of()).selectedTasks().stream().map(TaskDefinition::id).toList());
        assertTrue(d.tasks().stream().allMatch(t->t.dependsOn().isEmpty()&&t.after().isEmpty()));
    }
    @Test void scriptInstancesBindingsLocalVariablesAndImportsAreIndependent()throws Exception{
        var h=new GroovyHarness(temp);
        write(h,"job.groovy","import java.time.LocalDate as RootOnlyAlias\nbindingValue='root'\ndef own='root'\napply from:'tasks/f.groovy'\naction('root'){own}\ndefaults 'root','fragment'");
        write(h,"tasks/f.groovy","import java.time.LocalDate as Day\nassert !binding.hasVariable('bindingValue')\ndef own=Day.of(2026,1,2).toString()\naction('fragment'){own}");
        var outcome=h.run(load(h),Map.of());assertEquals(RunStatus.SUCCEEDED,outcome.status());assertEquals("root",value(outcome,"root"));assertEquals("2026-01-02",value(outcome,"fragment"));
        write(h,"tasks/f.groovy","action('fragment'){RootOnlyAlias.of(2026,1,2)}");
        var failed=h.run(load(h),Map.of());assertEquals(RunStatus.FAILED,failed.status());
        assertEquals("UNKNOWN_PROPERTY",failed.task("fragment").orElseThrow().failure().orElseThrow().diagnostic().orElseThrow().code());
    }
    @Test void includeBaseAndFileContextBaseAreDifferentEvenWithDecoys()throws Exception{
        var h=new GroovyHarness(temp);write(h,"data.txt","owner");write(h,"tasks/data.txt","wrong");
        write(h,"job.groovy","apply from:'tasks/start.groovy'\ndefaults 'read'");write(h,"tasks/start.groovy","apply from:'nested/read.groovy'");
        write(h,"tasks/nested/read.groovy","action('read'){ctx->java.nio.file.Files.readString(file('data.txt'))+'-'+java.nio.file.Files.readString(ctx.file('data.txt'))}");
        var outcome=h.run(load(h),Map.of());assertEquals(RunStatus.SUCCEEDED,outcome.status());assertEquals("owner-owner",value(outcome,"read"));
    }
    @Test void wholeClosureIsValidatedBeforeAnyScriptBody()throws Exception{
        var h=new GroovyHarness(temp);var marker=temp.resolve("body-marker");
        write(h,"job.groovy","new File('"+marker+"').text='ran'\naction('before'){}\napply from:'a.groovy'\ndefaults 'before'");
        write(h,"a.groovy","apply from:'missing.groovy'");
        assertEquals("APPLY_NOT_FOUND",assertThrows(GroovyDslException.class,()->load(h)).code());assertFalse(Files.exists(marker));
    }
    @Test void fragmentDefaultsFailBeforeRegistrationWithTheFragmentPosition()throws Exception{
        var h=new GroovyHarness(temp);var marker=temp.resolve("defaults-marker");
        write(h,"job.groovy","new File('"+marker+"').text='ran'\napply from:'f.groovy'\naction('x'){}");write(h,"f.groovy","// first\ndefaults 'x'\n");
        var failure=assertThrows(GroovyDslException.class,()->load(h));assertEquals("APPLY_DEFAULTS_NOT_ALLOWED",failure.code());assertTrue(failure.source().file().endsWith("f.groovy"));assertEquals(2,failure.source().line());assertFalse(Files.exists(marker));
    }
    @ParameterizedTest @ValueSource(strings={"action('x'){}","parameter('p',description:'P')","cleanup('end'){}"})
    void declarationsCannotOverwriteAcrossFiles(String declaration)throws Exception{
        var h=new GroovyHarness(temp);write(h,"job.groovy",declaration+"\napply from:'f.groovy'\n");write(h,"f.groovy",declaration);
        String code=declaration.startsWith("action")?"DUPLICATE_TASK_ID":declaration.startsWith("parameter")?"DUPLICATE_PARAMETER":"DUPLICATE_CLEANUP_NAME";
        assertEquals(code,assertThrows(GroovyDslException.class,()->load(h)).code());
    }
    @Test void sharedFragmentHasFreshStateAndOwnerResourcesInEachProject()throws Exception{
        var h=new GroovyHarness(temp);write(h,"settings.gretl","include 'a','b'\ndefaults ':a:run',':b:run'\n");
        for(String scope:List.of("a","b")){write(h,scope+"/job.groovy","apply from:'../shared/tasks.groovy'");write(h,scope+"/data.txt",scope);}
        write(h,"shared/data.txt","wrong");write(h,"shared/tasks.groovy","parameter('tag',description:'Tag')\ndef own=0\naction('run'){own++; parameter('tag')+':'+java.nio.file.Files.readString(file('data.txt'))+':'+own}");
        var d=new GroovyJobLoader().loadProject(new GretlProjectParser().parse(h.jobDir),h.environment);
        assertEquals(List.of(":a:run",":b:run"),d.tasks().stream().map(TaskDefinition::id).toList());
        var outcome=h.run(d,Map.of(":a:tag","A",":b:tag","B"));assertEquals(RunStatus.SUCCEEDED,outcome.status());assertEquals("A:a:1",value(outcome,":a:run"));assertEquals("B:b:1",value(outcome,":b:run"));
    }
    @Test void lateClosureFailureUsesTheActualSameBasenameFragmentAndLine()throws Exception{
        var h=new GroovyHarness(temp);write(h,"job.groovy","apply from:'tasks/job.groovy'\ndefaults 'bad'");
        Path fragment=write(h,"tasks/job.groovy","// header\naction('bad'){\n throw new IllegalStateException('fragment-error')\n}\n");
        var d=load(h);assertEquals(fragment.toString(),d.task("bad").orElseThrow().source().file());
        var outcome=h.run(d,Map.of());assertEquals(RunStatus.FAILED,outcome.status());var source=outcome.task("bad").orElseThrow().failure().orElseThrow().source().orElseThrow();assertEquals(fragment.toString(),source.file());assertEquals(3,source.line());
    }
    @Test void eachLoadCompilesFreshCapturedBytes()throws Exception{
        var h=new GroovyHarness(temp);write(h,"job.groovy","apply from:'f.groovy'\ndefaults 'f'");write(h,"f.groovy","action('f'){'first'}");
        var first=load(h);write(h,"f.groovy","action('f'){'second'}");var second=load(h);
        assertEquals("first",value(h.run(first,Map.of()),"f"));assertEquals("second",value(h.run(second,Map.of()),"f"));
    }
    @Test void emptyAndClassOnlyFragmentsCompileTogetherWithoutInitializingHelpers()throws Exception{
        var h=new GroovyHarness(temp);Path marker=temp.resolve("helper-initialized");
        write(h,"job.groovy","apply from:'tasks/helper.groovy'\napply from:'empty.groovy'\naction('value'){new Helper().value()}\ndefaults 'value'");
        write(h,"tasks/helper.groovy","class Helper{static{new File('"+marker+"').text='initialized'}; String value(){'captured'}}");
        write(h,"empty.groovy","// empty fragment");write(h,"Helper.groovy","class Helper{String value(){'disk decoy'}}");
        var definition=load(h);assertFalse(Files.exists(marker));
        var outcome=h.run(definition,Map.of());assertEquals(RunStatus.SUCCEEDED,outcome.status());assertEquals("captured",value(outcome,"value"));assertTrue(Files.exists(marker));
    }
    @Test void explicitSnapshotCompilesCapturedSourcesAfterFilesChange()throws Exception{
        var h=new GroovyHarness(temp);Path entry=write(h,"job.groovy","apply from:'f.groovy'\ndefaults 'f'");Path fragment=write(h,"f.groovy","action('f'){'captured'}");
        var snapshot=ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleSources.single(entry,Locale.ENGLISH);String hash=snapshot.bundle().sha256();
        Files.delete(entry);Files.writeString(fragment,"action('f'){'disk'}");
        var definition=new GroovyJobLoader().loadBundle(snapshot,h.environment,List.of(),Map.of());assertEquals(hash,snapshot.bundle().sha256());assertEquals("captured",value(h.run(definition,Map.of()),"f"));
    }
    @Test void applyAfterFreezeAndUnknownDynamicTargetsCannotLoadSources()throws Exception{
        var h=new GroovyHarness(temp);write(h,"f.groovy","action('f'){'f'}");
        write(h,"job.groovy","apply from:'f.groovy'\ndef script=this\naction('late'){script.invokeMethod('apply',[from:'f.groovy'])}\ndefaults 'late'");
        var outcome=h.run(load(h),Map.of());assertEquals(RunStatus.FAILED,outcome.status());assertEquals("APPLY_OUTSIDE_DEFINITION",outcome.task("late").orElseThrow().failure().orElseThrow().diagnostic().orElseThrow().code());
        Path marker=temp.resolve("unknown-marker");write(h,"evil.groovy","new File('"+marker+"').text='evil'");
        write(h,"job.groovy","apply from:'f.groovy'\ndef script=this\nscript.apply([from:'evil.groovy'])\ndefaults 'f'");
        assertEquals("APPLY_OUTSIDE_DEFINITION",assertThrows(GroovyDslException.class,()->load(h)).code());assertFalse(Files.exists(marker));
    }

    @Test void configurationConditionAndCleanupFailuresKeepFragmentSources()throws Exception{
        record Sample(String body,String code,int line,boolean cleanup){}
        for(var sample:List.of(
                new Sample("// heading\ntask('run', Echo){\n unknown='bad'\n}","UNKNOWN_CONFIGURATION_FIELD",3,false),
                new Sample("// heading\naction('run',when:{'bad'}){}","CONDITION_NOT_BOOLEAN",2,false),
                new Sample("action('run'){}\ncleanup('end'){\n missingProperty\n}","UNKNOWN_PROPERTY",3,true))){
            var h=new GroovyHarness(Files.createTempDirectory(temp,"late-"));
            write(h,"job.groovy","apply from:'tasks/job.groovy'\ndefaults 'run'");
            Path fragment=write(h,"tasks/job.groovy",sample.body());
            var outcome=h.run(load(h),Map.of());assertEquals(RunStatus.FAILED,outcome.status());
            var failure=sample.cleanup()?outcome.primaryFailure().orElseThrow():outcome.task("run").orElseThrow().failure().orElseThrow();
            assertEquals(sample.code(),failure.diagnostic().orElseThrow().code());
            assertEquals(fragment.toString(),failure.source().orElseThrow().file());assertEquals(sample.line(),failure.source().orElseThrow().line());
        }
    }
    @Test void compileAndRegistrationFailuresPointToFragmentBeforeAnyTaskRuns()throws Exception{
        for(String body:List.of("// heading\nthrow new IllegalStateException('registration')", "// heading\nMissingType variable")){
            var h=new GroovyHarness(Files.createTempDirectory(temp,"definition-"));
            write(h,"job.groovy","apply from:'tasks/job.groovy'");Path fragment=write(h,"tasks/job.groovy",body);
            var failure=assertThrows(GroovyDslException.class,()->load(h));assertEquals("JOB_LOAD_FAILED",failure.code());
            assertEquals(fragment.toString(),failure.source().file());assertEquals(2,failure.source().line());assertEquals(0,h.events.count("gretl.run.started"));
        }
    }
    @Test void orderingAcrossFragmentsDoesNotGrantResultAccess()throws Exception{
        var h=new GroovyHarness(temp);write(h,"job.groovy","apply from:'producer.groovy'\napply from:'consumer.groovy'\ndefaults 'produce','consume'");
        write(h,"producer.groovy","action('produce'){'value'}");write(h,"consumer.groovy","action('consume',after:'produce'){result('produce')}");
        var outcome=h.run(load(h),Map.of());assertEquals(RunStatus.FAILED,outcome.status());assertEquals(TaskStatus.SUCCEEDED,outcome.task("produce").orElseThrow().status());
        assertEquals("UNDECLARED_RESULT_DEPENDENCY",outcome.task("consume").orElseThrow().failure().orElseThrow().diagnostic().orElseThrow().code());
    }

    @Test void crossProjectReferencesFromFragmentsKeepDependencyRights()throws Exception{
        for(String link:List.of("dependsOn","after")){
            var h=new GroovyHarness(Files.createTempDirectory(temp,"cross-"));
            write(h,"settings.gretl","include 'a','b'\ndefaults ':a:produce',':b:consume'");
            write(h,"a/job.groovy","apply from:'tasks.groovy'");write(h,"a/tasks.groovy","action('produce'){'cross-value'}");
            write(h,"b/job.groovy","apply from:'tasks.groovy'");write(h,"b/tasks.groovy","action('consume',"+link+":':a:produce'){result(':a:produce')}");
            var outcome=h.run(new GroovyJobLoader().loadProject(new GretlProjectParser().parse(h.jobDir),h.environment),Map.of());
            if(link.equals("dependsOn")){assertEquals(RunStatus.SUCCEEDED,outcome.status());assertEquals("cross-value",value(outcome,":b:consume"));}
            else{assertEquals(RunStatus.FAILED,outcome.status());assertEquals("UNDECLARED_RESULT_DEPENDENCY",outcome.task(":b:consume").orElseThrow().failure().orElseThrow().diagnostic().orElseThrow().code());}
        }
    }

}
