package ch.so.agi.gretl.it;

import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual distribution and source-free java -jar processes; no test runtime classpath. */
class FinalizerJobIT {
    private ArtifactJobRunner.Request request(String id,boolean project,String mode,ArtifactJobRunner.Invocation invocation)throws Exception {
        Path root=ArtifactJobRunner.directory(id);Path source=Files.createDirectory(root.resolve("input"));
        if(project) project(source);else SelectedPackageSupport.copy(Path.of("../examples/task-finalizers").toRealPath(),source);
        return new ArtifactJobRunner.Request(id,source,root,List.of(),
                Map.of(project?":output":"output",root.resolve("output"),project?":mode":"mode",mode),Map.of(),
                JobRunner.baseConfig(root),Map.of(),Duration.ofSeconds(45),project?ArtifactJobRunner.JobInput.project("."):ArtifactJobRunner.JobInput.script("job.groovy"),invocation);
    }
    private void write(Path root,String name,String text)throws Exception {var p=root.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,text);}
    private void project(Path root)throws Exception {
        write(root,"settings.gretl","include 'a','b'\ndefaults ':a:work',':b:work'");
        write(root,"job.groovy","""
                import java.nio.file.Files
                import java.nio.file.StandardOpenOption
                parameter('output',type:'path',required:true,description:'Synthetic output')
                parameter('mode',defaultValue:'success',description:'success or fail')
                action('shared',after:[':a:end',':b:end']){Files.writeString(parameter('output').resolve('trace.txt'),'shared\\n',StandardOpenOption.APPEND)}
                cleanup('global'){Files.writeString(parameter('output').resolve('trace.txt'),'cleanup\\n',StandardOpenOption.APPEND)}
                """);
        for(String id:List.of("a","b")) {
            write(root,id+"/job.groovy","apply from:'../shared/tasks.groovy'");write(root,id+"/input.txt",id.toUpperCase(Locale.ROOT));
        }
        write(root,"shared/input.txt","wrong-resource-base");
        write(root,"shared/tasks.groovy","""
                import java.nio.file.Files
                import java.nio.file.StandardOpenOption
                action('work',finalizedBy:['end',':shared']){
                    def tag=Files.readString(file('input.txt'))
                    Files.writeString(parameter(':output').resolve('trace.txt'),tag+'-owner\\n',StandardOpenOption.CREATE,StandardOpenOption.APPEND)
                    if(tag=='A' && parameter(':mode')=='fail')throw new IllegalStateException('project-owner-failure')
                }
                task('end',Gzip){
                    dataFile=file('input.txt')
                    gzipFile=parameter(':output').resolve(Files.readString(file('input.txt'))+'.gz')
                }
                """);
        write(root,"job-package.json",Json.write(Map.of("schemaVersion",3,"name","finalizer-project","version","1","runtimeVersion","6.0.0-SNAPSHOT",
                "settingsFile","settings.gretl","sources",List.of(),"resources",List.of("a/input.txt","b/input.txt"),"dependencies",List.of())));
    }
    @SuppressWarnings("unchecked") private Map<String,Object> object(Object value){return (Map<String,Object>)value;}
    private Map<String,Object> execution(ArtifactJobRunner.Result result)throws Exception{return object(object(Json.parseFile(result.report(),16L<<20)).get("execution"));}
    private Map<String,Object> task(Map<String,Object> execution,String id){return ((List<?>)execution.get("tasks")).stream().map(this::object).filter(t->t.get("taskId").equals(id)).findFirst().orElseThrow();}
    private void gzip(Path file,String expected)throws Exception {try(var in=new GZIPInputStream(Files.newInputStream(file))){assertEquals(expected,new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}}
    private ArtifactJobRunner.Result execute(ArtifactJobRunner.Request request,ArtifactJobRunner.Mode mode)throws Exception {
        var prepared=ArtifactJobRunner.prepare(request,mode);
        if(mode==ArtifactJobRunner.Mode.JAR) {
            SelectedPackageSupport.delete(request.jobDirectory());
            var result=SelectedPackageSupport.detached(prepared);assertFalse(Files.exists(prepared.source()));
            var isolation=object(Json.parseFile(request.directory().resolve("isolation.json"),1L<<20));assertEquals(true,isolation.get("sourceDeleted"));assertEquals(true,isolation.get("emptyMavenCache"));return result;
        }
        var running=ArtifactJobRunner.start(prepared);try{return ArtifactJobRunner.finish(running,false);}finally{if(running.process().isAlive())ArtifactJobRunner.stop(running);}
    }
    @Test void singleSuccessAndOwnerFailureExecuteFinalizersInScriptAndSourceFreeJar()throws Exception {
        for(String mode:List.of("success","fail"))for(var artifact:List.of(ArtifactJobRunner.Mode.SCRIPT,ArtifactJobRunner.Mode.JAR)) {
            var request=request("finalizer-single-"+mode+"-"+artifact,false,mode,ArtifactJobRunner.Invocation.run());var result=execute(request,artifact);
            assertEquals(mode.equals("success")?0:10,result.code(),result.err());assertFalse(result.timedOut());assertFalse(result.forciblyKilled());assertTrue(result.hasValidArtifactIdentity());
            gzip(request.directory().resolve("output/finalized.gz"),Files.readString(Path.of("../examples/task-finalizers/input.txt")));
            assertEquals(mode.equals("success")?List.of("importData","secondOwner","prepareFinalizer","release","record","cleanup"):List.of("importData","prepareFinalizer","release","record","cleanup"),Files.readAllLines(request.directory().resolve("output/trace.txt")));
            var e=execution(result);assertEquals(mode.equals("success")?"SUCCEEDED":"FAILED",e.get("status"));assertEquals("SUCCEEDED",task(e,"release").get("status"));assertEquals("SUCCEEDED",task(e,"record").get("status"));
            var f=object(task(e,"release").get("finalization"));assertEquals(List.of("importData","secondOwner"),f.get("declaredOwners"));assertEquals(mode.equals("success")?List.of("importData","secondOwner"):List.of("importData"),f.get("activatingOwners"));assertEquals(List.of("FINALIZER"),f.get("executedRoles"));
            if(mode.equals("fail")){assertEquals("NOT_RUN",task(e,"secondOwner").get("status"));assertEquals("synthetic-owner-failure",JobTestJson.primaryError(JobTestJson.read(result.report())).get("message"));}
            assertEquals(1,((List<?>)e.get("invocations")).size());assertEquals("global",Files.readString(request.directory().resolve("output/cleanup.txt")));
        }
    }
    @Test void projectScopesResourcesAndSharedOwnerBarrierWorkWithoutSourceTree()throws Exception {
        for(String mode:List.of("success","fail"))for(var artifact:List.of(ArtifactJobRunner.Mode.SCRIPT,ArtifactJobRunner.Mode.JAR)) {
            var request=request("finalizer-project-"+mode+"-"+artifact,true,mode,ArtifactJobRunner.Invocation.run());var result=execute(request,artifact);
            assertEquals(mode.equals("success")?0:10,result.code(),result.err());gzip(request.directory().resolve("output/A.gz"),"A");
            if(mode.equals("success"))gzip(request.directory().resolve("output/B.gz"),"B");else assertFalse(Files.exists(request.directory().resolve("output/B.gz")));
            var e=execution(result);var shared=object(task(e,":shared").get("finalization"));assertEquals(List.of(":a:work",":b:work"),shared.get("declaredOwners"));assertEquals(mode.equals("success")?List.of(":a:work",":b:work"):List.of(":a:work"),shared.get("activatingOwners"));
            assertEquals(mode.equals("success")?List.of("A-owner","B-owner","shared","cleanup"):List.of("A-owner","shared","cleanup"),Files.readAllLines(request.directory().resolve("output/trace.txt")));
            if(mode.equals("fail")){assertEquals("NOT_RUN",task(e,":b:end").get("status"));assertEquals("FINALIZER_NOT_TRIGGERED",task(e,":b:end").get("reason"));}
        }
    }
    private record Introspection(String name, ArtifactJobRunner.Invocation invocation, List<String> expectedOutput) {
        void assertOutput(String output) {
            for (String expected : expectedOutput) assertTrue(output.contains(expected), output);
        }
    }

    @Test void scriptIntrospectionAndGraphAreLazyAndExplainConditionalCandidates()throws Exception {
        var scenarios = List.of(
                new Introspection("check", ArtifactJobRunner.Invocation.check(), List.of()),
                new Introspection("text-plan", ArtifactJobRunner.Invocation.plan(), List.of("finalizedBy", "normalSelected=false", "prepareFinalizer")),
                new Introspection("job-help", ArtifactJobRunner.Invocation.jobHelp(), List.of("finalizedBy")),
                new Introspection("mermaid", ArtifactJobRunner.Invocation.plan("mermaid", null), List.of("finalizedBy", "==>|finalizedBy|")),
                new Introspection("plantuml", ArtifactJobRunner.Invocation.plan("plantuml", null), List.of("finalizedBy", " : finalizedBy")));
        for (var scenario : scenarios) {
            var request = request("finalizer-controls-" + scenario.name(), false, "fail", scenario.invocation());
            var result = execute(request, ArtifactJobRunner.Mode.SCRIPT);
            assertEquals(0, result.code(), result.err());
            try(var files=Files.list(request.directory().resolve("output"))){assertEquals(0,files.count(),"No predicate/configuration/body/cleanup during introspection");}
            assertFalse(Files.exists(result.report()));
            scenario.assertOutput(result.out());
        }
    }
    @Test void publicActivationDeadlockIsRejectedBeforeAnyTaskSideEffect()throws Exception {
        var request=request("finalizer-activation-deadlock",false,"success",ArtifactJobRunner.Invocation.plan());
        write(request.jobDirectory(),"job.groovy","""
                parameter('output',type:'path',required:true,description:'Synthetic output')
                parameter('mode',description:'Unused control')
                action('a',after:'d',finalizedBy:'f'){throw new AssertionError('a must not start')}
                action('d'){throw new AssertionError('d must remain lazy')}
                action('f',dependsOn:'d'){throw new AssertionError('f must not start')}
                defaults 'a'
                """);
        var result=execute(request,ArtifactJobRunner.Mode.SCRIPT);assertEquals(2,result.code(),result.err());assertTrue(result.err().contains("FINALIZER_ACTIVATION_DEADLOCK"),result.err());assertEquals(0, JobTestJson.events(result.err(), "gretl.run.started").count());assertFalse(Files.exists(result.report()));
        try(var files=Files.list(request.directory().resolve("output"))){assertEquals(0,files.count());}
    }
    @Test void compiledJarIntrospectionExplainsFinalizersWithoutMaterializingSourcesOrRunningBodies()throws Exception {
        var scenarios = List.of(
                new Introspection("text-plan", ArtifactJobRunner.Invocation.plan(), List.of("finalizedBy")),
                new Introspection("job-help", ArtifactJobRunner.Invocation.jobHelp(), List.of("finalizedBy")),
                new Introspection("mermaid", ArtifactJobRunner.Invocation.plan("mermaid", null), List.of("finalizedBy", "==>|finalizedBy|")));
        for (var scenario : scenarios) {
            var request = request("finalizer-jar-controls-" + scenario.name(), false, "fail", scenario.invocation());
            var prepared = ArtifactJobRunner.prepare(request, ArtifactJobRunner.Mode.JAR);
            SelectedPackageSupport.delete(request.jobDirectory());SelectedPackageSupport.delete(prepared.source());
            var running=ArtifactJobRunner.start(prepared);
            try {
                var result=ArtifactJobRunner.finish(running,false);assertEquals(0,result.code(),result.err());scenario.assertOutput(result.out());assertFalse(Files.exists(result.report()));
                try(var files=Files.list(request.directory().resolve("output"))){assertEquals(0,files.count());}
                assertFalse(Files.exists(request.directory().resolve("work")),"Introspection must not start a run workspace");
            }finally{if(running.process().isAlive())ArtifactJobRunner.stop(running);}
        }
    }
    @Test void missingCompiledFinalizerFragmentIsRejectedBeforeRunWithoutSourceFallback()throws Exception {
        var request=request("finalizer-missing-class",false,"success",ArtifactJobRunner.Invocation.run());var prepared=ArtifactJobRunner.prepare(request,ArtifactJobRunner.Mode.JAR);
        SelectedPackageSupport.delete(request.jobDirectory());SelectedPackageSupport.delete(prepared.source());
        Path changed=request.directory().resolve("missing-finalizer.jar");String missingClass;
        try(var in=new JarFile(prepared.jar().toFile());var out=new JarOutputStream(Files.newOutputStream(changed))) {
            var metadata=object(Json.parse(new String(in.getInputStream(in.getJarEntry("META-INF/gretl/job.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)));
            var sources=(List<?>)object(metadata.get("bundle")).get("sources");
            String missing=sources.stream().map(this::object).filter(v->v.get("sourceId").equals("tasks/completion.groovy")).findFirst().orElseThrow().get("className").toString().replace('.','/')+".class";
            missingClass=missing.substring(0,missing.length()-6).replace('/','.');
            assertNotNull(in.getJarEntry(missing));
            for(var entry:Collections.list(in.entries()))if(!entry.getName().equals(missing)) {
                out.putNextEntry(new JarEntry(entry.getName()));try(var bytes=in.getInputStream(entry)){bytes.transferTo(out);}out.closeEntry();
            }
        }
        var altered=ArtifactJobRunner.existingJar(request,prepared.source(),changed,prepared.report(),prepared.inputSnapshot());var running=ArtifactJobRunner.start(altered);
        try {
            var result=ArtifactJobRunner.finish(running,false);assertEquals(2,result.code(),result.err());var error=JobTestJson.events(result.err(), "gretl.cli.error").findFirst().orElseThrow();
            assertEquals(missingClass,error.get("message"),"The declared compiled finalizer class is missing, not a toolchain failure");assertFalse(Files.exists(result.report()));assertEquals(0, JobTestJson.events(result.err(), "gretl.run.started").count());
            try(var files=Files.list(request.directory().resolve("output"))){assertEquals(0,files.count());}
        }finally{if(running.process().isAlive())ArtifactJobRunner.stop(running);}
    }
    @Test void finalizerFailureInSourceFreeJarRetainsFragmentLineAndOriginalOwnerError()throws Exception {
        var request=request("finalizer-source-failure",false,"fail",ArtifactJobRunner.Invocation.run());Path fragment=request.jobDirectory().resolve("tasks/completion.groovy");
        String text=Files.readString(fragment);String anchor="Files.writeString(parameter('output').resolve('record.txt'), 'task-bound')";
        assertTrue(text.contains(anchor));text=text.replace(anchor,anchor+"\n    throw new IllegalStateException('synthetic-finalizer-failure')");Files.writeString(fragment,text);
        long line=text.substring(0,text.indexOf("throw new IllegalStateException")).chars().filter(c->c=='\n').count()+1;
        var result=execute(request,ArtifactJobRunner.Mode.JAR);assertEquals(10,result.code(),result.err());var report=object(Json.parseFile(result.report(),16L<<20));
        var errors=(List<?>)report.get("errors");assertEquals("synthetic-owner-failure",object(errors.getFirst()).get("message"));
        var failure=errors.stream().map(this::object).filter(e->e.get("message").equals("synthetic-finalizer-failure")).findFirst().orElseThrow();var source=object(failure.get("source"));
        assertTrue(source.get("file").toString().endsWith("tasks/completion.groovy"),source.toString());assertEquals(line,source.get("line"));
        var e=object(report.get("execution"));assertEquals(object(errors.getFirst()).get("id"),e.get("primaryErrorRef"));assertTrue(((List<?>)e.get("secondaryErrorRefs")).contains(failure.get("id")));assertEquals("FAILED",task(e,"record").get("status"));assertEquals("SUCCEEDED",task(e,"release").get("status"));assertEquals("global",Files.readString(request.directory().resolve("output/cleanup.txt")));
    }
    @Test void sigtermAfterObservedOwnerStartCompletesTypedFinalizerAndGlobalCleanupInBothModes()throws Exception {
        for(var artifact:List.of(ArtifactJobRunner.Mode.SCRIPT,ArtifactJobRunner.Mode.JAR)) {
            var request=request("finalizer-sigterm-"+artifact,false,"wait",ArtifactJobRunner.Invocation.run());var prepared=ArtifactJobRunner.prepare(request,artifact);
            if(artifact==ArtifactJobRunner.Mode.JAR){SelectedPackageSupport.delete(request.jobDirectory());SelectedPackageSupport.delete(prepared.source());}
            try(var watch=FileSystems.getDefault().newWatchService()) {
                request.directory().resolve("output").register(watch,StandardWatchEventKinds.ENTRY_CREATE);
                var running=ArtifactJobRunner.start(prepared);
                try {
                    long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();var ready=request.directory().resolve("output/started");
                    while(!Files.exists(ready)) {assertTrue(running.process().isAlive(),"Owner process ended before handshake");assertTrue(System.nanoTime()<deadline,"Owner handshake deadline");var key=watch.poll(100,TimeUnit.MILLISECONDS);if(key!=null){key.pollEvents();key.reset();}}
                    ArtifactJobRunner.cancel(running);var result=ArtifactJobRunner.finish(running,true);
                    assertTrue(result.code()==143||result.code()==20,result.err());assertFalse(result.timedOut());assertFalse(result.forciblyKilled());
                    var e=execution(result);assertEquals("CANCELLED",e.get("status"));assertEquals("REQUESTED",e.get("cancelReason"));assertEquals("CANCELLED",task(e,"importData").get("status"));assertEquals("SUCCEEDED",task(e,"release").get("status"));assertEquals("NOT_RUN",task(e,"secondOwner").get("status"));
                    assertEquals(List.of("importData","prepareFinalizer","release","record","cleanup"),Files.readAllLines(request.directory().resolve("output/trace.txt")));
                    gzip(request.directory().resolve("output/finalized.gz"),Files.readString(Path.of("../examples/task-finalizers/input.txt")));
                    assertEquals(1,JobTestJson.events(result.err(), "gretl.run.finished").count());
                }finally{if(running.process().isAlive())ArtifactJobRunner.stop(running);}
            }
        }
    }
}
