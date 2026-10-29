package ch.so.agi.gretl.job;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.groovy.project.ProjectPath;
import ch.so.agi.gretl.runtime.groovy.project.ProjectSourceNames;
import ch.so.agi.gretl.runtime.logging.Json;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;

class ProjectMetadataTest {
    @TempDir Path temp;
    @Test void prj207ValidProjectMetadataIsImmutableAndOldConstructorStillWorks() throws Exception {
        var metadata=read(valid()); assertEquals(2,metadata.schemaVersion());assertEquals(1,metadata.projectEntries().size());
        assertThrows(UnsupportedOperationException.class,()->metadata.projectEntries().clear());
        assertEquals(List.of(":a:run"),metadata.defaultTargets());
        var old = new JobJarMain.JobMetadata(1,"old","1","0.1.0-SNAPSHOT",25,"job","job.groovy",JobJarMain.RESOURCE_ROOT,List.of(),List.of("job.groovy"),"a".repeat(64));
        assertTrue(old.projectEntries().isEmpty());assertEquals("job",old.entryClass());
    }
    @Test void prj207MalformedFieldsClassesPathsAndMixedSchemasFailBeforeInstantiation() throws Exception {
        for(var mutation:List.<java.util.function.Consumer<Map<String,Object>>>of(
                m->m.put("unknown",true), m->m.put("schemaVersion",2.5),m->m.put("project",false),
                m->m.put("entryClass","job"),m->m.put("entryScript","job.groovy"),m->m.put("scriptSha256","a".repeat(64)),
                m->m.put("settingsFile","../settings.gretl"),m->m.put("projectSha256","bad"),
                m->m.put("resourceRoot","elsewhere/"),m->m.put("dependencyInventory","elsewhere"),
                m->m.put("resourceFiles",List.of("../outside")),m->m.put("resourceFiles",List.of("C:/outside")),
                m->m.put("resourceFiles",List.of("x//y")),m->m.put("resourceFiles",List.of("x","x")),
                m->m.put("sourceFiles",List.of("settings.gretl")), m->m.put("defaultTargets",List.of("run")),
                m->m.put("defaultTargets",List.of(":a:run",":a:run")),m->m.put("entries",List.of()),
                m->m.put("entries",List.of(entry(":a"),entry(":a"))),
                m->m.put("entries",List.of(Map.of("projectPath",":a","entryClass","java/lang/System","entryScript","a/job.groovy"))),
                m->m.put("entries",List.of(Map.of("projectPath",":a","entryClass","Hidden","entryScript","b/job.groovy")))
        )) {
            var input=valid();mutation.accept(input);
            var failure=assertThrows(JobJarMain.JobJarException.class,()->read(input),input.toString());
            assertTrue(failure.getMessage().startsWith("JOB_METADATA_INVALID:"),failure.getMessage());
        }
    }
    @Test void prj206NormalizedResourcesAllowDotsWithinNamesAndUseOwnedRunRoot() throws Exception {
        var input=valid(); input.put("resourceFiles",List.of("a/data..txt"));
        var metadata=read(input); Path run=Files.createDirectory(temp.resolve("run"));
        var loader=new ClassLoader(null) {
            @Override public InputStream getResourceAsStream(String name) {
                return name.equals(JobJarMain.RESOURCE_ROOT+"a/data..txt")?new ByteArrayInputStream("synthetic".getBytes(StandardCharsets.UTF_8)):null;
            }
        };
        var source=new JobJarMain.PackagedProjectJobSource(metadata,temp,loader,Locale.ENGLISH);
        Path resources=source.prepareRun(run);
        assertEquals(run.resolve("job-resources"),resources);
        assertEquals("synthetic",Files.readString(resources.resolve("a/data..txt")));
        assertFalse(Files.exists(temp.resolve("a/data..txt")));
    }

    private Map<String,Object> valid() {
        var m=new LinkedHashMap<String,Object>();
        m.putAll(Map.of("schemaVersion",2,"name","project","version","1","runtimeVersion","0.1.0-SNAPSHOT","javaFeatureVersion",25,"project",true,"settingsFile","settings.gretl","entries",List.of(entry(":a")),"defaultTargets",List.of(":a:run")));
        m.put("resourceRoot",JobJarMain.RESOURCE_ROOT);m.put("resourceFiles",List.of("a/data.txt"));m.put("sourceFiles",List.of("settings.gretl","a/job.groovy"));m.put("projectSha256","a".repeat(64));m.put("dependencyInventory","META-INF/gretl/dependencies.json");return m;
    }
    private Map<String,String> entry(String path) {var project=ProjectPath.parseQualified(path);return Map.of("projectPath",path,"entryClass",ProjectSourceNames.entryClassName(project),"entryScript","a/job.groovy");}
    private JobJarMain.JobMetadata read(Map<String,Object> metadata) throws Exception {
        byte[] bytes=Json.write(metadata).getBytes(StandardCharsets.UTF_8);
        return JobJarMain.JobMetadata.load(new ClassLoader(null) {
            @Override public InputStream getResourceAsStream(String name) {return name.equals(JobJarMain.METADATA_RESOURCE)?new ByteArrayInputStream(bytes):null;}
        },Locale.ENGLISH);
    }
}
