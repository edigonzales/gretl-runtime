package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.packager.*;
import ch.so.agi.gretl.runtime.groovy.project.GretlProjectParser;
import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FragmentJarProcessTest {
    @TempDir Path temp;
    @ParameterizedTest @ValueSource(ints={1,2})
    void singleIncludesAndLegacyDescriptorsRunFromForeignCwdWithoutSources(int schema)throws Exception{
        Path root=Files.createDirectory(temp.resolve("sources"));Path marker=temp.resolve("packaging-marker");
        write(root,"job.groovy","new File('"+marker+"').text='loaded'\napply from:'tasks/prepare.groovy'\napply from:'tasks/check.groovy'\ndefaults 'verify'");
        write(root,"input.txt","payload äöü\n");
        write(root,"tasks/prepare.groovy","task('prepare',Gzip){new File('config-marker').text='yes';dataFile=file('input.txt');gzipFile=workPath('result.gz')}\ncleanup('end'){new File('cleanup-marker').text='yes'}");
        write(root,"tasks/check.groovy","action('verify',dependsOn:'prepare'){def text=new java.util.zip.GZIPInputStream(java.nio.file.Files.newInputStream(result('prepare').file)).getText('UTF-8');assert text=='payload äöü\\n';new File('verified.txt').text=text}\naction('bad'){\n throw new IllegalStateException('fragment-jar-failure')\n}\n");
        var data=descriptor(schema);data.put("entryScript","job.groovy");data.put("resources",List.of("input.txt"));
        Path descriptor=root.resolve("package.json");Files.writeString(descriptor,Json.write(data));
        var d=PackageDescriptor.load(descriptor);var cp=classpath();var compiled=new JobCompiler().compile(d,cp);
        Path jar=temp.resolve("job.jar");new JobPackageBuilder(cp,"test").build(d,compiled,jar);assertFalse(Files.exists(marker));
        try(var file=new JarFile(jar.toFile())){var metadata=(Map<?,?>)Json.parse(new String(file.getInputStream(file.getJarEntry("META-INF/gretl/job.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));assertEquals(3,((Number)metadata.get("schemaVersion")).intValue());assertEquals("single",metadata.get("definitionMode"));}
        delete(root);Path cwd=Files.createDirectory(temp.resolve("foreign"));
        if(schema==2){
            Path broken=temp.resolve("missing-fragment.jar");
            String missing=compiled.bundle().sources().getLast().className().replace('.','/')+".class";
            try(var in=new JarFile(jar.toFile());var out=new java.util.jar.JarOutputStream(Files.newOutputStream(broken))){
                for(var item:Collections.list(in.entries()))if(!item.getName().equals(missing)){
                    out.putNextEntry(new java.util.jar.JarEntry(item.getName()));try(var bytes=in.getInputStream(item)){bytes.transferTo(out);}out.closeEntry();
                }
            }
            String failure=process(broken,cwd,List.of("--plan"),2);assertTrue(failure.contains("JOB_LOAD_FAILED"),failure);assertFalse(Files.exists(marker),"All classes must be checked before root registration");
        }
        for(var args:List.of(List.of("--plan"),List.of("--help"))){process(jar,cwd,args);assertFalse(Files.exists(cwd.resolve("config-marker")));assertFalse(Files.exists(cwd.resolve("cleanup-marker")));assertFalse(Files.exists(cwd.resolve("verified.txt")));}
        process(jar,cwd,List.of());assertEquals("payload äöü\n",Files.readString(cwd.resolve("verified.txt")));assertTrue(Files.exists(cwd.resolve("cleanup-marker")));
        String failure=process(jar,cwd,List.of("--target","bad"),10);assertTrue(failure.contains("tasks/check.groovy:3"),failure);
    }
    @Test void sharedProjectFragmentKeepsScopesResourcesAndCrossProjectResultsInJar()throws Exception{
        Path root=Files.createDirectory(temp.resolve("project"));
        write(root,"settings.gretl","include 'a','b'\ndefaults ':b:verify'");
        write(root,"a/job.groovy","apply from:'../shared/tasks.groovy'");
        write(root,"b/job.groovy","apply from:'../shared/tasks.groovy'\naction('verify',dependsOn:[':a:prepare',':b:prepare']){assert result(':a:prepare')=='A:1';assert result(':b:prepare')=='B:1';new File('verified.txt').text='scopes'}");
        write(root,"shared/tasks.groovy","def count=0\naction('prepare'){count++;java.nio.file.Files.readString(file('input.txt'))+':'+count}\ncleanup('end'){new File('cleanup-'+java.nio.file.Files.readString(file('input.txt'))).text='done'}");
        write(root,"a/input.txt","A");write(root,"b/input.txt","B");write(root,"shared/input.txt","wrong");
        var data=descriptor(3);data.put("settingsFile","settings.gretl");data.put("resources",List.of("a/input.txt","b/input.txt"));
        Path descriptor=root.resolve("package.json");Files.writeString(descriptor,Json.write(data));var d=PackageDescriptor.load(descriptor);var cp=classpath();
        var compiled=new ProjectCompiler().compile(new GretlProjectParser().parse(root),d,cp);Path jar=temp.resolve("project.jar");new JobPackageBuilder(cp,"test").buildProject(d,compiled,jar);
        delete(root);Path cwd=Files.createDirectory(temp.resolve("foreign"));process(jar,cwd,List.of("--plan"));assertFalse(Files.exists(cwd.resolve("verified.txt")));
        process(jar,cwd,List.of());assertEquals("scopes",Files.readString(cwd.resolve("verified.txt")));for(String name:List.of("A","B"))assertEquals("done",Files.readString(cwd.resolve("cleanup-"+name)));
    }
    @Test void oldSingleAndProjectMetadataStillRunFromBytecode()throws Exception{
        for(boolean project:List.of(false,true)){
            Path root=Files.createDirectory(temp.resolve(project?"legacy-project":"legacy-single"));
            write(root,project?"a/job.groovy":"job.groovy","action('run'){new File('legacy-result').text='legacy'}\ndefaults 'run'");
            if(project)write(root,"settings.gretl","include 'a'\ndefaults ':a:run'");
            var data=descriptor(project?3:2);data.put(project?"settingsFile":"entryScript",project?"settings.gretl":"job.groovy");
            Path descriptor=root.resolve("package.json");Files.writeString(descriptor,Json.write(data));var d=PackageDescriptor.load(descriptor);var cp=classpath();Path jar=temp.resolve(project?"legacy-project.jar":"legacy-single.jar");
            var builder=new JobPackageBuilder(cp,"test");
            if(project)builder.buildProject(d,new ProjectCompiler().compile(new GretlProjectParser().parse(root),d,cp),jar);else builder.build(d,new JobCompiler().compile(d,cp),jar);
            try(var file=new JarFile(jar.toFile())){var metadata=(Map<?,?>)Json.parse(new String(file.getInputStream(file.getJarEntry("META-INF/gretl/job.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));assertEquals(project?2:1,((Number)metadata.get("schemaVersion")).intValue());}
            delete(root);Path cwd=Files.createDirectory(temp.resolve(project?"old-project-cwd":"old-single-cwd"));process(jar,cwd,List.of());assertEquals("legacy",Files.readString(cwd.resolve("legacy-result")));
        }
    }
    private Map<String,Object> descriptor(int schema){var m=new LinkedHashMap<String,Object>();m.put("schemaVersion",schema);m.put("name","fragment-test");m.put("version","1");m.put("runtimeVersion","6.0.0-SNAPSHOT");m.put("sources",List.of());m.put("dependencies",List.of());if(schema>1)m.put("taskModules",List.of("io"));return m;}
    private List<Path> classpath(){return JobPackager.currentClasspath().stream().filter(p->!p.toString().contains("test-classes")&&!p.toString().contains("gretl-tooling-core")&&!p.toString().contains("gretl-packager/target")&&!p.toString().contains("gretl-cli/target")).toList();}
    private void write(Path root,String file,String text)throws Exception{Path path=root.resolve(file);Files.createDirectories(path.getParent());Files.writeString(path,text);}
    private void delete(Path root)throws Exception{try(var files=Files.walk(root)){for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
    private Path compilerGuard()throws Exception{
        Path agent=temp.resolve("compiler-guard.jar");if(Files.exists(agent))return agent;
        var manifest=new java.util.jar.Manifest();manifest.getMainAttributes().putValue("Manifest-Version","1.0");manifest.getMainAttributes().putValue("Premain-Class",NoSourceCompilerAgent.class.getName());
        try(var out=new java.util.jar.JarOutputStream(Files.newOutputStream(agent),manifest)){
            for(String suffix:List.of("","$1")){
                String name=NoSourceCompilerAgent.class.getName().replace('.','/')+suffix+".class";out.putNextEntry(new java.util.jar.JarEntry(name));try(var input=getClass().getClassLoader().getResourceAsStream(name)){assertNotNull(input);input.transferTo(out);}out.closeEntry();
            }
        }
        Path log=temp.resolve("compiler-guard-control.log");
        var control=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-javaagent:"+agent,"-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),NoSourceCompilerAgent.class.getName()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean done=control.waitFor(20,TimeUnit.SECONDS);if(!done)control.destroyForcibly();assertTrue(done);assertEquals(0,control.exitValue(),Files.readString(log));assertTrue(Files.readString(log).contains("COMPILER_BLOCKED"));return agent;
    }
    private String process(Path jar,Path cwd,List<String> args)throws Exception{return process(jar,cwd,args,0);}
    private String process(Path jar,Path cwd,List<String> args,int expected)throws Exception{
        Path agent=compilerGuard();Path processTmp=Files.createDirectory(temp.resolve("process-tmp-"+UUID.randomUUID()));
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Djava.io.tmpdir="+processTmp,"-javaagent:"+agent,"-jar",jar.toString()));command.addAll(args);
        Path log=temp.resolve("jar-"+UUID.randomUUID()+".log");Process p=new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean done=p.waitFor(45,TimeUnit.SECONDS);if(!done)p.destroyForcibly();assertTrue(done,"JAR timeout");String output=Files.readString(log);assertEquals(expected,p.exitValue(),command+"\n"+output);
        assertTrue(output.contains("COMPILER_GUARD_INSTALLED"),output);assertFalse(output.contains("SOURCE_COMPILER_USED"),output);
        if(args.contains("--help")||args.contains("--plan")){
            try(var files=Files.walk(processTmp)){assertFalse(files.anyMatch(path->path.getFileName().toString().startsWith("gretl-work")||path.getFileName().toString().equals("job-resources")),"Introspection must not materialize resources");}
            assertFalse(Files.exists(cwd.resolve("gretl-work")));
        }
        return output;
    }
}
