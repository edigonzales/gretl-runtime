package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import org.junit.jupiter.api.Test;

class ProjectArtifactLocalJobIT {
    private static final List<String> ORDER=List.of(":download:run",":validation:nested:run",":import:run");
    private Path fixture() throws Exception { return Path.of(getClass().getResource("/projects/pipeline").toURI()); }
    private ArtifactJobRunner.Request request(String id, String mode, List<String> targets, String keep) throws Exception {
        Path root=ArtifactJobRunner.directory(id);
        var config=JobRunner.baseConfig(root);
        var runtime=new LinkedHashMap<>(object(config.get("runtime")));runtime.put("keepWork",keep);config.put("runtime",runtime);
        Path input=root.resolve("fixture with spaces");SelectedPackageSupport.copy(fixture(),input);
        var descriptor=object(Json.parseFile(input.resolve("job-package.json"),1L<<20));descriptor.put("name",id);
        Files.writeString(input.resolve("job-package.json"),Json.write(descriptor));
        return new ArtifactJobRunner.Request(id,input,root,targets,Map.of(":output",root.resolve("output/result.txt"),":mode",mode,":import:count","9"),
                Map.of(),config,Map.of(),Duration.ofSeconds(60),".");
    }
    @Test void prj202203204206210211SameIndependentBytesAndGraphInScriptAndDetachedJar() throws Exception {
        var sourceRequest=request("project-script","success",List.of(),"never");
        var sourcePrepared=ArtifactJobRunner.prepare(sourceRequest,ArtifactJobRunner.Mode.JAR);
        assertFalse(Files.exists(sourceRequest.directory().resolve("toplevel-marker.txt")),"No Toplevel during packaging");
        assertFalse(Files.exists(sourceRequest.directory().resolve("reports/report.json")));
        var script=ArtifactJobRunner.finish(ArtifactJobRunner.start(sourcePrepared,true),false);
        assertEquals(0,script.code(),script.err());assertRun(script,ORDER,"SUCCEEDED");
        String expected="download-A\n|import-C\n|9|true|helper";
        assertEquals(expected,Files.readString(sourceRequest.directory().resolve("output/result.txt")));
        assertEquals("ivd",Files.readString(sourceRequest.directory().resolve("output/cleanup.txt")));
        var jarRequest=request("project-detached","success",List.of(),"never");
        var prepared=ArtifactJobRunner.prepare(jarRequest,ArtifactJobRunner.Mode.JAR);
        var result=SelectedPackageSupport.detached(prepared);
        assertEquals(0,result.code(),result.err());assertRun(result,ORDER,"SUCCEEDED");
        assertEquals(expected,Files.readString(jarRequest.directory().resolve("output/result.txt")));
        assertEquals("ivd",Files.readString(jarRequest.directory().resolve("output/cleanup.txt")));
        assertFalse(Files.exists(prepared.source()));assertTrue(result.hasValidArtifactIdentity());
        var isolation=object(Json.parseFile(jarRequest.directory().resolve("isolation.json"),1L<<20));
        assertEquals(true,isolation.get("sourceDeleted"));assertEquals(true,isolation.get("emptyMavenCache"));
        assertEquals("/gretl-no-build-tools",isolation.get("path"));
        try(var jar=new JarFile(prepared.jar().toFile())) {
            var metadata=object(Json.parse(new String(jar.getInputStream(jar.getJarEntry("META-INF/gretl/job.json")).readAllBytes(),StandardCharsets.UTF_8)));
            assertEquals(2L,metadata.get("schemaVersion"));
            assertEquals(5,((List<?>)metadata.get("entries")).size());
            assertEquals(5,((List<?>)metadata.get("entries")).stream().map(e->object(e).get("entryClass")).distinct().count());
            assertNotEquals(new String(jar.getInputStream(jar.getJarEntry("META-INF/gretl/job-resources/download/sql/update.sql")).readAllBytes(),StandardCharsets.UTF_8),
                    new String(jar.getInputStream(jar.getJarEntry("META-INF/gretl/job-resources/validation/nested/sql/update.sql")).readAllBytes(),StandardCharsets.UTF_8));
            assertEquals(object(report(script).get("source")).get("sha256"),metadata.get("projectSha256"));
            assertEquals(metadata.get("projectSha256"),object(report(result).get("source")).get("sha256"));
        }
    }
    @Test void prj205VersionHelpPlanHaveNoMaterializationAndCompiledClassesNeedNoSourceTexts() throws Exception {
        var request=request("project-controls","success",List.of(),"always");
        var prepared=ArtifactJobRunner.prepare(request,ArtifactJobRunner.Mode.JAR);
        // Remove provenance text after compilation: startup must never recompile or parse settings.
        Path stripped=request.directory().resolve("stripped.jar");rewrite(prepared.jar(),stripped,"META-INF/gretl/sources/",null);
        Path noEntry=request.directory().resolve("no-entry.jar");
        rewrite(stripped,noEntry,ch.so.agi.gretl.runtime.groovy.project.ProjectSourceNames.entryClassName(ch.so.agi.gretl.runtime.groovy.project.ProjectPath.root())+".class",null);
        assertEquals(0,control(request.directory(),noEntry,"version-no-class",List.of("--version")));
        assertEquals(0,control(request.directory(),stripped,"version",List.of("--version")));
        assertFalse(Files.exists(request.directory().resolve("controls/version/toplevel-marker.txt")));
        assertFalse(Files.exists(request.directory().resolve("controls/version-no-class/toplevel-marker.txt")));
        assertEquals(0,control(request.directory(),stripped,"help",List.of("--help")));
        assertEquals(0,control(request.directory(),stripped,"plan",List.of("--plan")));
        assertTrue(Files.readString(request.directory().resolve("controls/help/control.stdout.log")).contains(":import:count"));
        assertTrue(Files.readString(request.directory().resolve("controls/plan/control.stdout.log")).contains(":validation:nested:run"));
        for(String id:List.of("version","help","plan")) try(var paths=Files.walk(request.directory().resolve("controls/"+id))) {
            assertFalse(paths.anyMatch(p->p.getFileName().toString().equals("job-resources")));
        }
        var strippedPrepared=new ArtifactJobRunner.Prepared(request,prepared.source(),stripped,hash(stripped),prepared.report(),null);
        var run=ArtifactJobRunner.finish(ArtifactJobRunner.start(strippedPrepared,false),false);
        assertEquals(0,run.code(),run.err());assertRun(run,ORDER,"SUCCEEDED");
        // Keep audit bound to the actual executed JAR, not the superseded package input.
        assertEquals(strippedPrepared.sha256(),hash(stripped));Files.delete(prepared.jar());Files.createLink(prepared.jar(),stripped);
    }
    @Test void prj209FailurePreservesCauseCleanupOriginalLineAndRetainedProjectResources() throws Exception {
        var request=request("project-failure","fail",List.of(),"on-failure");
        var prepared=ArtifactJobRunner.prepare(request,ArtifactJobRunner.Mode.JAR);
        var result=ArtifactJobRunner.finish(ArtifactJobRunner.start(prepared,false),false);
        assertEquals(10,result.code(),result.err());assertRun(result,ORDER,"FAILED");
        assertEquals("ivd",Files.readString(request.directory().resolve("output/cleanup.txt")));
        assertFalse(Files.exists(request.directory().resolve("output/result.txt")));
        var report=report(result);var execution=object(report.get("execution"));assertEquals(true,execution.get("workRetained"));
        String errors=Json.write(report.get("errors"));assertTrue(errors.contains("synthetic-project-failure"));assertTrue(errors.contains("original-cause"));
        var failure=((List<?>)report.get("errors")).stream().map(ProjectArtifactLocalJobIT::object).filter(e->e.get("message").toString().contains("synthetic-project-failure")).findFirst().orElseThrow();
        assertTrue(object(failure.get("source")).get("file").toString().endsWith("import/job.groovy"));assertEquals(6L,object(failure.get("source")).get("line"));
        try(var paths=Files.walk(request.directory().resolve("work"))) {
            var resource=paths.filter(p->p.endsWith("job-resources/validation/nested/sql/update.sql")).findFirst().orElseThrow();assertEquals("validation-B\n",Files.readString(resource));
        }
    }
    @Test void prj209InnerHandlerFailuresKeepOriginalSourceBeforeCaptureEvenWhenCaught() throws Exception {
        for (boolean script : List.of(true, false)) for (boolean caught : List.of(false, true)) {
            var request = request("project-inner-" + (caught ? "caught-" : "failed-") + (script ? "script" : "jar"),
                    caught ? "caught" : "nested-fail", List.of(), "on-failure");
            var prepared = ArtifactJobRunner.prepare(request, ArtifactJobRunner.Mode.JAR);
            var result = ArtifactJobRunner.finish(ArtifactJobRunner.start(prepared, script), false);
            assertEquals(caught ? 0 : 10, result.code(), result.err());
            assertRun(result, ORDER, caught ? "SUCCEEDED" : "FAILED");
            assertEquals("ivd", Files.readString(request.directory().resolve("output/cleanup.txt")));
            assertEquals(!caught, object(report(result).get("execution")).get("workRetained"));
            var report = report(result);
            var invocation = ((List<?>) object(report.get("execution")).get("invocations")).stream()
                    .map(ProjectArtifactLocalJobIT::object)
                    .filter(i -> ":import:run".equals(i.get("parentTaskId")) && "FAILED".equals(i.get("status")))
                    .findFirst().orElseThrow();
            var failure = ((List<?>) report.get("errors")).stream().map(ProjectArtifactLocalJobIT::object)
                    .filter(e -> invocation.get("errorRef").equals(e.get("id"))).findFirst().orElseThrow();
            assertEquals("ch.so.agi.gretl.tasks.io.archive.GzipTaskException", failure.get("type"));
            assertOriginalInnerSource(object(failure.get("source")));
            assertNoVirtualFileNames(report.get("errors"));
            List<Map<String, Object>> rows;
            try (var paths = Files.list(result.directory().resolve("reports"))) {
                rows = Files.readAllLines(paths.filter(p -> p.toString().endsWith(".events.jsonl")).findFirst().orElseThrow())
                        .stream().map(Json::parse).map(ProjectArtifactLocalJobIT::object).toList();
            }
            var failed = rows.stream().filter(e -> "gretl.operation.failed".equals(e.get("event_name"))
                    && ":import:run".equals(e.get("task_id"))).findFirst().orElseThrow();
            assertOriginalInnerSource(object(failed.get("source")));
            assertNoVirtualFileNames(rows);
            assertTrue(rows.stream().anyMatch(e -> "observed-inner-failure".equals(e.get("message"))));
            if (caught) assertEquals("ch.so.agi.gretl.tasks.io.archive.GzipTaskException",
                    Files.readString(request.directory().resolve("output/caught.txt")));
        }
    }
    private void assertOriginalInnerSource(Map<String, Object> source) {
        assertTrue(source.get("file").toString().endsWith("import/job.groovy"), source.toString());
        assertEquals(10L, source.get("line"));
    }
    private void assertNoVirtualFileNames(Object value) {
        if (value instanceof Map<?, ?> map) {
            if (map.get("file") instanceof String file) assertFalse(file.startsWith("GretlProject_"), file);
            map.values().forEach(this::assertNoVirtualFileNames);
        } else if (value instanceof List<?> list) list.forEach(this::assertNoVirtualFileNames);
    }
    @Test void prj209TargetSelectionAndNoDataRetainOneCommonCompletion() throws Exception {
        for(boolean empty:List.of(false,true)) {
            var request=request(empty?"project-no-data":"project-selection",empty?"empty":"success",empty?List.of():List.of(":validation:nested:run"),"never");
            var result=ArtifactJobRunner.run(request);assertEquals(0,result.code(),result.err());
            assertRun(result,empty?ORDER:ORDER.subList(0,2),empty?"NO_DATA":"SUCCEEDED");
            assertFalse(Files.exists(request.directory().resolve("output/result.txt")));
            assertEquals("ivd",Files.readString(request.directory().resolve("output/cleanup.txt")));
        }
    }
    @Test void prj207TamperedMetadataAndMissingMaterializedResourceAreRealStartFailures() throws Exception {
        for(String mode:List.of("metadata","syntax","resource")) {
            var request=request("project-broken-"+mode,"success",List.of(),"always");var prepared=ArtifactJobRunner.prepare(request,ArtifactJobRunner.Mode.JAR);
            Path changed=request.directory().resolve("changed.jar");
            if(mode.equals("metadata")) rewrite(prepared.jar(),changed,"META-INF/gretl/job.json","{\"schemaVersion\":2,\"project\":true}");
            else if(mode.equals("syntax")) rewrite(prepared.jar(),changed,"META-INF/gretl/job.json","{");
            else rewrite(prepared.jar(),changed,"META-INF/gretl/job-resources/validation/nested/sql/update.sql",null);
            var altered=new ArtifactJobRunner.Prepared(request,prepared.source(),changed,hash(changed),prepared.report(),null);
            var result=ArtifactJobRunner.finish(ArtifactJobRunner.start(altered,false),false);
            assertEquals(2,result.code(),result.err()); assertTrue(result.err().contains(mode.equals("resource")?"JOB_RESOURCE_MISSING":"JOB_METADATA_INVALID"),result.err());
            assertFalse(result.err().contains("gretl.run.started"));assertFalse(Files.exists(prepared.report()));
            assertEquals(altered.sha256(),hash(changed));Files.delete(prepared.jar());Files.createLink(prepared.jar(),changed);
            assertFalse(Files.exists(request.directory().resolve("output/cleanup.txt")));
            if(Files.exists(request.directory().resolve("work")))try(var paths=Files.list(request.directory().resolve("work"))){assertEquals(0,paths.count());}
        }
    }
    private int control(Path root,Path jar,String id,List<String> options) throws Exception {
        Path dir=Files.createDirectories(root.resolve("controls/"+id));
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-jar",jar.toString()));command.addAll(options);
        var process=ArtifactJobRunner.process(command,dir,Map.of(),"control").start();
        boolean finished=process.waitFor(60,TimeUnit.SECONDS);if(!finished)process.destroyForcibly();assertTrue(finished);
        Files.writeString(dir.resolve("control-execution.json"),Json.write(Map.of("command",command,"exitCode",process.exitValue(),"artifactSha256",hash(jar))));
        assertFalse(Files.exists(dir.resolve("gretl-reports")));assertFalse(Files.exists(dir.resolve(".gretl-work")));
        return process.exitValue();
    }
    private void assertRun(ArtifactJobRunner.Result result,List<String> expected,String status) throws Exception {
        var report=report(result);var execution=object(report.get("execution"));assertEquals(status,execution.get("status"));
        assertEquals(expected,((List<?>)execution.get("tasks")).stream().map(t->object(t).get("taskId")).toList());
        var eventFiles=Files.list(result.directory().resolve("reports"));List<Path> events;try(eventFiles){events=eventFiles.filter(p->p.toString().endsWith(".events.jsonl")).toList();}assertEquals(1,events.size());
        var rows=Files.readAllLines(events.getFirst()).stream().map(Json::parse).map(ProjectArtifactLocalJobIT::object).toList();
        assertEquals(1,rows.stream().filter(e->"gretl.run.started".equals(e.get("event_name"))).count());assertEquals(1,rows.stream().filter(e->"gretl.run.finished".equals(e.get("event_name"))).count());
        String runId=object(report.get("identity")).get("runId").toString();assertTrue(rows.stream().allMatch(e->!e.containsKey("run_id")||runId.equals(e.get("run_id"))));
    }
    private Map<String,Object> report(ArtifactJobRunner.Result result) throws Exception {return object(Json.parseFile(result.report(),1L<<24));}
    @SuppressWarnings("unchecked")private static Map<String,Object> object(Object value){return (Map<String,Object>)value;}
    private static String hash(Path file)throws Exception{try(var input=Files.newInputStream(file)){var digest=java.security.MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];for(int n;(n=input.read(buffer))>=0;)digest.update(buffer,0,n);return HexFormat.of().formatHex(digest.digest());}}
    private void rewrite(Path original,Path output,String prefix,String replacement)throws Exception{
        try(var in=new JarFile(original.toFile());var out=new JarOutputStream(Files.newOutputStream(output))){
            for(var entry:in.stream().toList()){
                if(entry.getName().startsWith(prefix)){if(replacement!=null){out.putNextEntry(new JarEntry(entry.getName()));out.write(replacement.getBytes(StandardCharsets.UTF_8));out.closeEntry();}continue;}
                out.putNextEntry(new JarEntry(entry.getName()));try(var stream=in.getInputStream(entry)){stream.transferTo(out);}out.closeEntry();
            }
        }
    }
}
