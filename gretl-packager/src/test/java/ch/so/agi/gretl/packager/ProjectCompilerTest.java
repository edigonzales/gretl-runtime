package ch.so.agi.gretl.packager;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.groovy.project.GretlProjectParser;
import ch.so.agi.gretl.runtime.groovy.project.ProjectSourceHash;
import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectCompilerTest {
    @TempDir Path root;
    @Test void prj202203ThreeEntriesHelpersAndSettingsCompileWithoutExecutingCode() throws Exception {
        var descriptor = tree(); var project = new GretlProjectParser().parse(root);
        var compiled = new ProjectCompiler().compile(project, descriptor, JobPackager.currentClasspath());
        assertEquals(3,compiled.entries().size()); assertEquals(3,compiled.entries().stream().map(CompiledProject.Entry::entryClass).distinct().count());
        for(var entry:compiled.entries()) assertTrue(compiled.classes().containsKey(entry.entryClass()+".class"));
        assertTrue(compiled.classes().containsKey("helpers/Label.class"));assertFalse(Files.exists(root.resolve("marker.txt")));
        assertEquals(ProjectSourceHash.sha256(project), compiled.projectSha256());
        assertEquals(List.of("settings.gretl","a/job.groovy","b/c/job.groovy","a_b/job.groovy","helpers/Label.groovy"),compiled.sources().stream().map(JobCompiler.SourceFile::relativePath).toList());
    }
    @Test void prj203HiddenHelperScriptAndDuplicateClassesAreRejectedWithSources() throws Exception {
        var descriptor = tree();var project = new GretlProjectParser().parse(root);
        Files.writeString(root.resolve("helpers/Hidden.groovy"),"throw new AssertionError('must not run')\n");
        assertEquals("PACKAGE_MULTIPLE_SCRIPTS",assertThrows(PackageException.class,()->new ProjectCompiler().compile(project,descriptor,JobPackager.currentClasspath())).code());
        Files.delete(root.resolve("helpers/Hidden.groovy"));
        Files.writeString(root.resolve("a/job.groovy"),"class Duplicate {}\naction('run') {}\n");
        Files.writeString(root.resolve("b/c/job.groovy"),"class Duplicate {}\naction('run') {}\n");
        var failure=assertThrows(PackageException.class,()->new ProjectCompiler().compile(project,descriptor,JobPackager.currentClasspath()));
        assertEquals("PACKAGE_COMPILE_FAILED",failure.code());assertTrue(failure.getMessage().contains("job.groovy"));assertFalse(failure.getMessage().contains("GretlProject_"));
        assertFalse(Files.exists(root.resolve("marker.txt")));
    }
    private PackageDescriptor tree() throws Exception {
        Files.writeString(root.resolve("settings.gretl"),"include 'a', 'b:c', 'a_b'\ndefaults ':a:run'\n");
        for(String dir:List.of("a","b/c","a_b")) {Files.createDirectories(root.resolve(dir));Files.writeString(root.resolve(dir+"/job.groovy"),"import helpers.Label\nnew File('"+root.resolve("marker.txt")+"').text = 'bad'\naction('run') { Label.value() }\n");}
        Files.createDirectories(root.resolve("helpers"));Files.writeString(root.resolve("helpers/Label.groovy"),"package helpers\nclass Label { static String value() { 'ok' } }\n");
        Path file=root.resolve("job-package.json");Files.writeString(file,Json.write(Map.of("schemaVersion",3,"name","project","version","1","runtimeVersion","0.1.0-SNAPSHOT","settingsFile","settings.gretl","sources",List.of("helpers"))));return PackageDescriptor.load(file);
    }
}
