package ch.so.agi.gretl.packager;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleSources;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FragmentCompilerTest {
    @TempDir Path root;
    @Test void capturesClosureWithoutRunningBodiesAndStillRejectsUnreferencedScripts()throws Exception{
        Path marker=root.resolve("marker");Files.createDirectories(root.resolve("tasks"));
        Files.writeString(root.resolve("job.groovy"),"new File('"+marker+"').text='root'\napply from:'tasks/f.groovy'\ndefaults 'run'");
        Files.writeString(root.resolve("tasks/f.groovy"),"new File('"+marker+"').text='fragment'\naction('run'){'value'}\n");
        Path file=root.resolve("package.json");Files.writeString(file,"{\"schemaVersion\":2,\"name\":\"fragments\",\"version\":\"1\",\"runtimeVersion\":\"6.0.0-SNAPSHOT\",\"entryScript\":\"job.groovy\",\"sources\":[\"tasks\"]}");
        var descriptor=PackageDescriptor.load(file);var compiled=new JobCompiler().compile(descriptor,JobPackager.currentClasspath());
        assertEquals(ScriptBundleSources.single(root.resolve("job.groovy"),Locale.GERMAN).bundle().sha256(),compiled.scriptSha256());
        assertFalse(Files.exists(marker));assertEquals(2,compiled.sources().size());
        Files.writeString(root.resolve("tasks/hidden.groovy"),"action('hidden'){}");
        assertEquals("PACKAGE_MULTIPLE_SCRIPTS",assertThrows(PackageException.class,()->new JobCompiler().compile(descriptor,JobPackager.currentClasspath())).code());
        assertFalse(Files.exists(marker));
    }
    @Test void emptyAndPackagedClassOnlyFragmentsHaveExactlyTheirOwnScriptClasses()throws Exception{
        Files.createDirectories(root.resolve("tasks"));
        Files.writeString(root.resolve("job.groovy"),"import sample.Helper\napply from:'tasks/helper.groovy'\napply from:'tasks/empty.groovy'\naction('run'){Helper.value()}\ndefaults 'run'");
        Files.writeString(root.resolve("tasks/helper.groovy"),"package sample\nclass Helper{static String value(){'helper'}}");
        Files.writeString(root.resolve("tasks/empty.groovy"),"// intentionally empty");
        Path file=root.resolve("package.json");Files.writeString(file,"{\"schemaVersion\":2,\"name\":\"classes\",\"version\":\"1\",\"runtimeVersion\":\"6.0.0-SNAPSHOT\",\"entryScript\":\"job.groovy\"}");
        var compiled=new JobCompiler().compile(PackageDescriptor.load(file),JobPackager.currentClasspath());
        assertEquals(3,compiled.bundle().sources().size());assertTrue(compiled.classes().containsKey("sample/Helper.class"));
        assertTrue(compiled.bundle().sources().stream().anyMatch(source->source.className().startsWith("sample.GretlSource_")));
    }

}
