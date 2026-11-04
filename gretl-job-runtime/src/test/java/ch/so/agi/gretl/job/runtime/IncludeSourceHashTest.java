package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.runtime.groovy.UserMessageSink;
import ch.so.agi.gretl.runtime.runner.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class IncludeSourceHashTest {
    @TempDir Path temp;
    void write(Path root,String id,String text)throws Exception{Path file=root.resolve(id);Files.createDirectories(file.getParent());Files.writeString(file,text);}
    Object run(JobDefinition definition,Path root,String task){
        var outcome=new SequentialJobRunner().run(definition,new ExecutionPlanner().plan(definition,List.of()),RunInput.builder().jobRoot(root).startCwd(temp).workRoot(temp.resolve("work")).build());
        assertEquals(RunStatus.SUCCEEDED,outcome.status());return ((ResultSlot.Value)outcome.task(task).orElseThrow().slot()).value();
    }
    @Test void singleHashAndCompiledDefinitionUseOneSnapshotWhileLaterLoadsRefresh()throws Exception{
        Path root=temp.resolve("job ä space");write(root,"job.groovy","apply from:'tasks/f.groovy'\ndefaults 'f'");write(root,"tasks/f.groovy","action('f'){'first'}");
        var source=new ScriptJobSource(root.resolve("job.groovy"),Locale.ENGLISH);var registry=OperationRegistry.fromProviders(List.of());
        var first=source.load(registry,temp,UserMessageSink.NO_OP);String firstHash=source.sha256();
        write(root,"tasks/f.groovy","action('f'){'second'}");
        assertEquals(firstHash,source.sha256());assertEquals("first",run(first,root,"f"));
        var second=source.load(registry,temp,UserMessageSink.NO_OP);assertNotEquals(firstHash,source.sha256());assertEquals("second",run(second,root,"f"));
    }
    @Test void projectHashIncludesTheSameCapturedSettingsAndFragmentBytes()throws Exception{
        Path root=temp.resolve("project");write(root,"settings.gretl","include 'a'\ndefaults ':a:f'");write(root,"a/job.groovy","apply from:'../shared/f.groovy'");write(root,"shared/f.groovy","action('f'){'first'}");
        var source=new ProjectJobSource(root,Locale.ENGLISH);var registry=OperationRegistry.fromProviders(List.of());
        var first=source.load(registry,temp,UserMessageSink.NO_OP);String initial=source.sha256();
        write(root,"settings.gretl","// new settings\ninclude 'a'\ndefaults ':a:f'");write(root,"shared/f.groovy","action('f'){'second'}");
        assertEquals(initial,source.sha256());assertEquals("first",run(first,root,":a:f"));
        var second=source.load(registry,temp,UserMessageSink.NO_OP);assertNotEquals(initial,source.sha256());assertEquals("second",run(second,root,":a:f"));
    }
    @Test void pureHashDoesNotExecuteAndFreshUnloadedSourcesSeeChanges()throws Exception{
        Path root=temp.resolve("pure");Path marker=temp.resolve("hash-marker");
        write(root,"job.groovy","new File('"+marker+"').text='body'\napply from:'f.groovy'\ndefaults 'f'");write(root,"f.groovy","action('f'){'first'}");
        var source=new ScriptJobSource(root.resolve("job.groovy"),Locale.ENGLISH);String first=source.sha256();
        write(root,"f.groovy","action('f'){'second'}");assertNotEquals(first,source.sha256());assertFalse(Files.exists(marker));
    }
}
