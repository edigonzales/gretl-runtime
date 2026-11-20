package ch.so.agi.gretl.packager;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.groovy.project.GretlProjectParser;
import ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleSources;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectCompilerTest {
    @TempDir Path root;
    @Test void threeEntriesHelpersAndSettingsCompileWithoutExecutingCode() throws Exception {
        var descriptor = tree(); var project = new GretlProjectParser().parse(root);
        var compiled = BundleCompiler.compile(descriptor, JobPackager.currentClasspath(), java.util.Locale.GERMAN);
        assertEquals(3,compiled.bundle().entries().size()); assertEquals(3,compiled.bundle().sources().stream().map(source -> source.className()).distinct().count());
        for (var source : compiled.bundle().sources()) assertTrue(compiled.classes().containsKey(source.className() + ".class"));
        assertTrue(compiled.classes().containsKey("helpers/Label.class"));assertFalse(Files.exists(root.resolve("marker.txt")));
        assertEquals(ScriptBundleSources.project(project, java.util.Locale.GERMAN).bundle().sha256(), compiled.bundle().sha256());
        assertEquals("settings.gretl", compiled.bundle().settingsId());
        assertEquals(java.util.Set.of("a/job.groovy", "b/c/job.groovy", "a_b/job.groovy"),
                new java.util.HashSet<>(compiled.bundle().sources().stream().map(source -> source.sourceId()).toList()));
    }
    @Test void hiddenHelperScriptAndDuplicateClassesAreRejectedWithSources() throws Exception {
        var descriptor = tree();
        Files.writeString(root.resolve("helpers/Hidden.groovy"),"throw new AssertionError('must not run')\n");
        assertEquals("PACKAGE_MULTIPLE_SCRIPTS",assertThrows(PackageException.class,()->BundleCompiler.compile(descriptor, JobPackager.currentClasspath(), java.util.Locale.GERMAN)).code());
        Files.delete(root.resolve("helpers/Hidden.groovy"));
        Files.writeString(root.resolve("a/job.groovy"),"class Duplicate {}\naction('run') {}\n");
        Files.writeString(root.resolve("b/c/job.groovy"),"class Duplicate {}\naction('run') {}\n");
        var failure=assertThrows(PackageException.class,()->BundleCompiler.compile(descriptor, JobPackager.currentClasspath(), java.util.Locale.GERMAN));
        assertEquals("PACKAGE_COMPILE_FAILED",failure.code());assertTrue(failure.getMessage().contains("job.groovy"));assertFalse(failure.getMessage().contains("GretlProject_"));
        assertFalse(Files.exists(root.resolve("marker.txt")));
    }
    private PackageDescriptor tree() throws Exception {
        Files.writeString(root.resolve("settings.gretl"),"include 'a', 'b:c', 'a_b'\ndefaults ':a:run'\n");
        for(String dir:List.of("a","b/c","a_b")) {Files.createDirectories(root.resolve(dir));Files.writeString(root.resolve(dir+"/job.groovy"),"import helpers.Label\nnew File('"+root.resolve("marker.txt")+"').text = 'bad'\naction('run') { Label.value() }\n");}
        Files.createDirectories(root.resolve("helpers"));Files.writeString(root.resolve("helpers/Label.groovy"),"package helpers\nclass Label { static String value() { 'ok' } }\n");
        Path file=root.resolve("job-package.json");Files.writeString(file,new JsonMapper().writeValueAsString(Map.of("schemaVersion",1,"name","project","version","1","runtimeVersion","6.0.0-SNAPSHOT","settingsFile","settings.gretl","sources",List.of("helpers"))));return PackageDescriptor.load(file);
    }
}
