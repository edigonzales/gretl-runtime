package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Real product containers; selected by job-image, never skipped by a JUnit assumption. */
class ImageRuntimeIT {
    @Test void runsGeneratedTaskFamilyFromExternalReadOnlySources() throws Exception {
        TaskFamilyLocalJobIT.run(ArtifactJobRunner.Mode.IMAGE, List.of());
    }

    @Test void runsAnExternalProjectWithoutPackaging() throws Exception {
        ProjectProcessLocalJobIT.externalProjectNeedsNoPackageDescriptor(ArtifactJobRunner.Mode.IMAGE);
    }

    @Test void reachesPostgresAndCancelsItsActiveTransaction() throws Exception {
        DatabaseArtifactIT.realDatabaseChainAndCancellationRollback(ArtifactJobRunner.Mode.IMAGE);
    }

    @Test void uploadsToRealS3WithMountedCredentials() throws Exception {
        ArtifactServicesIT.imageS3Upload();
    }

    @Test void obtainsAnOAuth2TokenAndUsesItFromTheNonRootImage() throws Exception {
        OAuth2ClientCredentialsLocalJobIT.verify(200, ArtifactJobRunner.Mode.IMAGE);
    }

    @Test void reachesHttpAndReportsAnExpectedFailure() throws Exception {
        IoLocalJobIT.imageHttpDownloadAndFailure();
    }

    @Test void defaultEntrypointReusesOneGenericImageForNewJobsAndChangedExternalSource() throws Exception {
        var settings = ImageExecutionAdapter.Settings.configured();
        String image = settings.image();
        Path fixture = Files.createTempDirectory("gretl-new-image-job-ü ");
        String marker = "created-after-image-" + UUID.randomUUID();
        try {
            Files.writeString(fixture.resolve("payload.txt"),marker);
            Files.writeString(fixture.resolve("erster Job ü.groovy"),job("first"));
            var first=run(fixture,"erster Job ü.groovy",Map.of());
            assertEquals(marker+"|first",Files.readString(first.directory().resolve("output/result.txt")));
            Files.writeString(fixture.resolve("zweiter Job.groovy"),job("second"));
            Files.writeString(fixture.resolve("job-package.json"),"{ deliberately invalid");
            var second=run(fixture,"zweiter Job.groovy",Map.of());
            assertEquals(marker+"|second",Files.readString(second.directory().resolve("output/result.txt")));
            Files.writeString(fixture.resolve("erster Job ü.groovy"),job("edited"));
            var edited=run(fixture,"erster Job ü.groovy",Map.of());
            assertEquals(marker+"|edited",Files.readString(edited.directory().resolve("output/result.txt")));
            for (var result:List.of(first,second,edited)) {
                assertEquals(image,result.artifactIdentity()); assertFalse(Files.exists(result.directory().resolve("job.jar")));
                var command=(List<?>)Json.parseFile(result.directory().resolve("command.json"),1L<<20);
                assertFalse(command.contains("--entrypoint")); assertTrue(command.contains("--read-only"));
                assertFalse(command.contains("-jar")); assertFalse(command.contains("package"));
                var proof=(Map<?,?>)Json.parseFile(result.directory().resolve("image-proof.json"),8L<<20);
                assertEquals(image,proof.get("id")); assertEquals(true,proof.get("removed"));
                assertEquals(false,((Map<?,?>)proof.get("container")).get("running"));
            }
            var a=(Map<?,?>)Json.parseFile(first.directory().resolve("input-manifest.json"),1L<<20);
            var b=(Map<?,?>)Json.parseFile(edited.directory().resolve("input-manifest.json"),1L<<20);
            assertNotEquals(a.get("treeSha256"),b.get("treeSha256"));
            assertEquals(image,ImageExecutionAdapter.Settings.configured().image());
        } finally { SelectedPackageSupport.delete(fixture); }
    }
    @Test void nonRootReadOnlyRuntimeExecutesRealSqliteAndDuckdbNativeLibraries() throws Exception {
        Path fixture=Files.createTempDirectory("gretl-image-native-");
        try {
            Files.writeString(fixture.resolve("job.groovy"),nativeScript());
            Path root=ArtifactJobRunner.directory("image-native", ArtifactJobRunner.Mode.IMAGE);
            var result=ArtifactJobRunner.run(new ArtifactJobRunner.Request("image-native",fixture,root,List.of(),Map.of("output",root.resolve("output")),Map.of(),JobRunner.baseConfig(root),Map.of(),Duration.ofSeconds(60)),ArtifactJobRunner.Mode.IMAGE);
            assertEquals(0,result.code(),result.err()); assertFalse(result.timedOut());
            long uid=new com.sun.security.auth.module.UnixSystem().getUid();
            assertTrue(uid>0); assertTrue(Files.readString(root.resolve("output/uid.txt")).contains("Uid:\t"+uid+"\t"));
            for (String engine:List.of("sqlite","duckdb"))
                try (var db=DriverManager.getConnection("jdbc:"+engine+":"+root.resolve("output/native."+engine));var statement=db.createStatement();var rows=statement.executeQuery("select value from probe")) {
                    assertTrue(rows.next()); assertEquals("native-ü",rows.getString(1)); assertFalse(rows.next());
                }
        } finally { SelectedPackageSupport.delete(fixture); }
    }
    @Test void uncooperativeContainerIsForcedAndRemovedWithoutClaimingControlledCancellation() throws Exception {
        Path fixture=Files.createTempDirectory("gretl-image-force-");
        try {
            Files.writeString(fixture.resolve("job.groovy"), """
                    parameter('output', type: 'path', required: true, description: 'Checkpoint')
                    Runtime.runtime.addShutdownHook(new Thread({ while(true) { try { Thread.sleep(1000) } catch(InterruptedException ignored) {} } } as Runnable))
                    action('block') {
                        java.nio.file.Files.writeString(parameter('output').resolve('ready'), 'ready')
                        while(true) { try { Thread.sleep(1000) } catch(InterruptedException ignored) {} }
                    }; defaults 'block'
                    """);
            Path root=ArtifactJobRunner.directory("image-forced-stop", ArtifactJobRunner.Mode.IMAGE);
            var request=new ArtifactJobRunner.Request("image-forced-stop",fixture,root,List.of(),Map.of("output",root.resolve("output")),Map.of(),JobRunner.baseConfig(root),Map.of(),Duration.ofSeconds(2));
            var running=ArtifactJobRunner.start(ArtifactJobRunner.prepare(request,ArtifactJobRunner.Mode.IMAGE));
            try {
                long deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos();
                while(!Files.exists(root.resolve("output/ready"))) {
                    assertTrue(running.process().isAlive(),"Container ended before actual checkpoint");
                    assertTrue(System.nanoTime()<deadline,"No container checkpoint"); Thread.sleep(10);
                }
                var result=ArtifactJobRunner.finish(running,false);
                assertTrue(result.timedOut());
                assertEquals(137,result.code(),result.err()); assertTrue(result.forciblyKilled()); assertFalse(running.process().isAlive());
                var row=(Map<?,?>)Json.parseFile(root.resolve("execution.json"),1L<<20);
                assertEquals("FORCED_TERMINATION",((Map<?,?>)row.get("process")).get("state"));
                var proof=(Map<?,?>)Json.parseFile(root.resolve("image-proof.json"),8L<<20);
                assertEquals(false,((Map<?,?>)proof.get("container")).get("oomKilled")); assertEquals(true,proof.get("removed"));
                assertTrue(new ImageExecutionAdapter.Docker().inspect(List.of("container","ls","--all","--quiet","--filter","name=^/"+running.prepared().image().name()+"$")).isBlank());
            } finally { if(running.process().isAlive()) ArtifactJobRunner.stop(running); else running.prepared().image().remove(); }
        } finally { SelectedPackageSupport.delete(fixture); }
    }
    @Test void openShiftArbitraryUidAndRootGroupNeedNoPrivilegeOrPasswdChange() throws Exception {
        final long assignedUid=1001234567L;
        Path fixture=Files.createTempDirectory("gretl-openshift-");
        try {
            Files.writeString(fixture.resolve("job.groovy"),nativeScript("/work","/tmp"));
            Path root=ArtifactJobRunner.directory("image-openshift", ArtifactJobRunner.Mode.IMAGE);
            var request=new ArtifactJobRunner.Request("image-openshift",fixture,root,List.of(),Map.of("output",root.resolve("output")),Map.of(),JobRunner.baseConfig(root),Map.of(),Duration.ofSeconds(60));
            var base=ArtifactJobRunner.prepare(request,ArtifactJobRunner.Mode.IMAGE);
            // Read-only projected sources/config; writes use root-owned group-writable volumes.
            try(var files=Files.walk(base.source())) {
                for(Path file:files.toList()) Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString(Files.isDirectory(file)?"rwxr-xr-x":"r--r--r--"));
            }
            Path publicConfig=root.resolve("openshift-runtime.json");
            Files.copy(root.resolve("image-runtime.json"),publicConfig);
            Files.setPosixFilePermissions(publicConfig,java.nio.file.attribute.PosixFilePermissions.fromString("r--r--r--"));
            var original=base.image(); var args=new ArrayList<String>();
            for(int i=0;i<original.arguments().size();i++) {
                String arg=original.arguments().get(i);
                if(arg.equals("--tmpfs")) {
                    String mount=original.arguments().get(++i);
                    if(mount.startsWith("/tmp:")) mount="/tmp:rw,exec,nosuid,gid=0,mode=0770,size=256m";
                    args.add(arg);args.add(mount);continue;
                }
                if(arg.equals("--user")) { args.add(arg);args.add(assignedUid+":0");i++;continue; }
                if(arg.equals("--env")) {
                    String env=original.arguments().get(++i);
                    if(env.startsWith("HOME=")||env.startsWith("TMPDIR=")||env.equals("JAVA_TOOL_OPTIONS")) continue;
                    args.add(arg);args.add(env);continue;
                }
                if(arg.equals("--mount")) {
                    String mount=original.arguments().get(++i);String target=mount.split(",target=",2)[1].split(",",2)[0];
                    if(List.of("/work","/outputs","/reports","/run").contains(target)) continue;
                    if(target.equals("/config/runtime.json")) mount="type=bind,source="+publicConfig+",target=/config/runtime.json,readonly";
                    args.add(arg);args.add(mount);continue;
                }
                args.add(arg);
            }
            var volumes=new ArrayList<String>();
            for(String target:List.of("/work","/outputs","/reports","/run")) volumes.addAll(List.of("--tmpfs",target+":rw,exec,gid=0,mode=0770,size="+(target.equals("/work")?"512m":"64m")));
            args.addAll(args.indexOf(original.imageId()),volumes);
            args.addAll(args.indexOf(original.imageId()),List.of("--env","GRETL_IMAGE_HOLD"));
            var environment=new LinkedHashMap<>(original.environment()); environment.put("GRETL_IMAGE_HOLD","true");
            environment.remove("HOME");environment.remove("TMPDIR");environment.remove("JAVA_TOOL_OPTIONS");
            var launch=new ImageExecutionAdapter.Launch(original.engine(),original.name(),original.imageId(),args,environment,original.config(),original.paths(),original.proofFile(),original.cancellation());
            var prepared=new ArtifactJobRunner.Prepared(base.request(),base.source(),base.report(),base.inputSnapshot(),new ArtifactJobRunner.ImageLaunch(base.launch().distribution(),launch));
            var running=ArtifactJobRunner.start(prepared);
            try {
                var docker=new ImageExecutionAdapter.Docker();
                long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();
                while(docker.inspect(List.of("container","ls","--quiet","--filter","name=^/"+launch.name()+"$")).isBlank()) {
                    assertTrue(running.process().isAlive(),Files.readString(running.err()));
                    assertTrue(System.nanoTime()<deadline,"Container never reached running state"); Thread.sleep(20);
                }
                while(docker.inspect(List.of("exec",launch.name(),"sh","-c","test -f /run/probe-finished && echo ready || true")).isBlank()) {
                    assertTrue(running.process().isAlive(),Files.readString(running.err()));
                    assertTrue(System.nanoTime()<deadline,"No OpenShift native completion checkpoint"); Thread.sleep(20);
                }
                // tmpfs is discarded when the container stops: retain completed outputs before release.
                copyLiveVolume(launch.name(),"/outputs",root.resolve("output"));
                copyLiveVolume(launch.name(),"/reports",root.resolve("reports"));
                docker.inspect(List.of("exec",launch.name(),"sh","-c","printf release > /run/release"));
                var result=ArtifactJobRunner.finish(running,false);assertEquals(0,result.code(),result.err());
                String status=Files.readString(root.resolve("output/uid.txt"));
                assertTrue(status.contains("Uid:\t"+assignedUid+"\t")); assertTrue(status.contains("Gid:\t0\t"));
                assertTrue(status.contains("CapEff:\t0000000000000000")); assertTrue(status.contains("NoNewPrivs:\t1"));
                for(String engine:List.of("sqlite","duckdb")) try(var db=DriverManager.getConnection("jdbc:"+engine+":"+root.resolve("output/native."+engine));var statement=db.createStatement();var rows=statement.executeQuery("select value from probe")) {
                    assertTrue(rows.next());assertEquals("native-ü",rows.getString(1));assertFalse(rows.next());
                }
                var proof=(Map<?,?>)Json.parseFile(root.resolve("image-proof.json"),8L<<20);
                assertEquals(assignedUid+":0",((Map<?,?>)proof.get("container")).get("user"));
                assertEquals(true,((Map<?,?>)proof.get("container")).get("readOnlyRoot"));assertEquals(true,proof.get("removed"));
            } finally { if(running.process().isAlive()) ArtifactJobRunner.stop(running); else launch.remove(); }
        } finally { SelectedPackageSupport.delete(fixture); }
    }
    @Test void changedPreparedSourceIsRejectedBeforeAnyContainerStarts() throws Exception {
        Path fixture=Files.createTempDirectory("gretl-image-changed-source-");
        try {
            Files.writeString(fixture.resolve("job.groovy"),job("original"));
            Path root=ArtifactJobRunner.directory("image-changed-source", ArtifactJobRunner.Mode.IMAGE);
            var request=new ArtifactJobRunner.Request("image-changed-source",fixture,root,List.of(),Map.of("output",root.resolve("output/result.txt")),Map.of(),JobRunner.baseConfig(root),Map.of(),Duration.ofSeconds(30));
            var prepared=ArtifactJobRunner.prepare(request,ArtifactJobRunner.Mode.IMAGE);
            Files.writeString(prepared.source().resolve("job.groovy"),job("tampered"));
            var failure=assertThrows(java.io.IOException.class,()->ArtifactJobRunner.start(prepared));
            assertTrue(failure.getMessage().contains("Input changed"));
            assertFalse(Files.exists(root.resolve("container.id")));assertFalse(Files.exists(prepared.report()));
            assertFalse(Files.exists(root.resolve("output/result.txt")));
            var row=(Map<?,?>)Json.parseFile(root.resolve("execution.json"),1L<<20);
            assertEquals("LAUNCH_FAILED",((Map<?,?>)row.get("process")).get("state"));
            assertTrue(new ImageExecutionAdapter.Docker().inspect(List.of("container","ls","--all","--quiet","--filter","name=^/"+prepared.image().name()+"$")).isBlank());
        } finally { SelectedPackageSupport.delete(fixture); }
    }
    private void copyLiveVolume(String container,String source,Path destination) throws Exception {
        Files.createDirectories(destination);
        Path archive=Files.createTempFile("gretl-live-volume-", ".tar");
        Path errors=Files.createTempFile("gretl-live-volume-", ".log");
        try {
            // Docker's archive API does not reliably expose tmpfs contents; stream from the running container.
            var copy=new ProcessBuilder("docker","exec",container,"tar","-C",source,"-cf","-",".")
                    .redirectOutput(archive.toFile()).redirectError(errors.toFile()).start();
            try { assertTrue(copy.waitFor(20,java.util.concurrent.TimeUnit.SECONDS),"Volume export timed out"); assertEquals(0,copy.exitValue(),Files.readString(errors)); }
            finally { if(copy.isAlive()) { copy.destroyForcibly(); assertTrue(copy.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)); } }
            var extract=new ProcessBuilder("tar","-xf",archive.toString(),"-C",destination.toString()).redirectErrorStream(true).redirectOutput(errors.toFile()).start();
            try { assertTrue(extract.waitFor(20,java.util.concurrent.TimeUnit.SECONDS),"Volume extraction timed out"); assertEquals(0,extract.exitValue(),Files.readString(errors)); }
            finally { if(extract.isAlive()) { extract.destroyForcibly(); assertTrue(extract.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)); } }
        } finally { Files.deleteIfExists(archive);Files.deleteIfExists(errors); }
    }
    private String nativeScript() { return nativeScript("/work/home","/work/tmp"); }
    private String nativeScript(String home, String temporary) { return """
                    parameter('output', type: 'path', required: true, description: 'External output')
                    if (System.getenv('GRETL_IMAGE_HOLD') == 'true') Runtime.runtime.addShutdownHook(new Thread({
                        java.nio.file.Files.writeString(java.nio.file.Path.of('/run/probe-finished'),'ready')
                        while(!java.nio.file.Files.exists(java.nio.file.Path.of('/run/release'))) Thread.sleep(10)
                    } as Runnable))
                    action('native') {
                        assert Runtime.version().feature() == 25
                        assert !java.nio.file.Files.isWritable(java.nio.file.Path.of('/opt/gretl/bin/gretl'))
                        assert !java.nio.file.Files.isWritable(java.nio.file.Path.of('/etc/passwd'))
                        assert System.getProperty('user.home') == '__HOME__'
                        assert System.getProperty('java.io.tmpdir') == '__TEMP__'
                        Class.forName('org.sqlite.JDBC'); Class.forName('org.duckdb.DuckDBDriver')
                        ['sqlite', 'duckdb'].each { engine ->
                            def db = java.sql.DriverManager.getConnection('jdbc:' + engine + ':' + parameter('output').resolve('native.' + engine))
                            try { def s = db.createStatement(); try { s.execute('create table probe(value varchar)'); s.execute("insert into probe values('native-ü')") } finally { s.close() } } finally { db.close() }
                        }
                        java.nio.file.Files.writeString(parameter('output').resolve('uid.txt'), java.nio.file.Files.readString(java.nio.file.Path.of('/proc/self/status')))
                    }; defaults 'native'
                    """.replace("__HOME__",home).replace("__TEMP__",temporary); }
    private String job(String value) {
        return "parameter('output', type: 'path', required: true, description: 'Output')\naction('write') { boolean denied=false; try { java.nio.file.Files.writeString(file('payload.txt'), 'bad') } catch (java.nio.file.FileSystemException expected) { denied=true }; assert denied; java.nio.file.Files.writeString(parameter('output'),java.nio.file.Files.readString(file('payload.txt'))+'|"+value+"') }; defaults 'write'\n";
    }
    private ArtifactJobRunner.Result run(Path fixture,String name,Map<String,Object> extra) throws Exception {
        Path root=ArtifactJobRunner.directory("generic-image-job", ArtifactJobRunner.Mode.IMAGE);
        var parameters=new LinkedHashMap<>(extra);parameters.put("output",root.resolve("output/result.txt"));
        var result=ArtifactJobRunner.run(new ArtifactJobRunner.Request("generic-image-job",fixture,root,List.of(),parameters,Map.of(),JobRunner.baseConfig(root),Map.of(),Duration.ofSeconds(45),ArtifactJobRunner.JobInput.script(name),ArtifactJobRunner.Invocation.run()),ArtifactJobRunner.Mode.IMAGE);
        assertEquals(0,result.code(),result.err()); assertFalse(result.timedOut());return result;
    }
}
