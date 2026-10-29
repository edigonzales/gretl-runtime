package ch.so.agi.gretl.packager;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.groovy.project.GretlProjectParser;
import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectPackageIsolationTest {
    @TempDir Path root;
    @Test void prj210SelectedCompilerNeverInheritsHostPackagerClassesAndGrabStaysDisabled() throws Exception {
        Files.writeString(root.resolve("settings.gretl"),"defaults ':run'\n");
        Path file=root.resolve("job-package.json");Files.writeString(file,Json.write(Map.of("schemaVersion",3,"name","isolated","version","1","runtimeVersion","0.1.0-SNAPSHOT","settingsFile","settings.gretl")));
        var descriptor=PackageDescriptor.load(file);
        Path host = Path.of(JobPackager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var selected=JobPackager.currentClasspath().stream().filter(path->!path.equals(host)).toList();
        Files.writeString(root.resolve("job.groovy"),"import ch.so.agi.gretl.packager.JobPackager\naction('run') { JobPackager.currentClasspath() }");
        var project=new GretlProjectParser().parse(root);
        var failure=assertThrows(PackageException.class,()->new ProjectCompiler().compile(project,descriptor,selected));
        assertEquals("PACKAGE_COMPILE_FAILED",failure.code());assertTrue(failure.getMessage().contains("JobPackager"),failure.getMessage());
        // Disabled transformation means this unavailable coordinate is never resolved.
        // The existing compiler accepts the inert annotation; it is not a dependency declaration.
        Files.writeString(root.resolve("job.groovy"),"@Grab('invalid:never-download:1')\nimport java.nio.file.Path\naction('run') {} ");
        var inert=new ProjectCompiler().compile(new GretlProjectParser().parse(root),descriptor,selected);
        assertEquals(1,inert.entries().size());assertTrue(inert.classes().keySet().stream().noneMatch(name->name.startsWith("invalid/")));
    }
}
