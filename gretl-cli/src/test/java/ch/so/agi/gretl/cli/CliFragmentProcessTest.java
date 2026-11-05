package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real CLI processes: definition commands stay lazy, execution uses the same include closure. */
class CliFragmentProcessTest {
    @TempDir Path temp;
    @Test void scriptCommandsShareIncludesAndRunDecompressesTheActualInput()throws Exception{
        Path job=Files.createDirectories(temp.resolve("job"));Files.createDirectories(job.resolve("tasks"));
        String input="fragment payload äöü\nsecond line\n";Files.writeString(job.resolve("input.txt"),input);
        Files.writeString(job.resolve("job.groovy"),"new File('top-marker').text='loaded'\napply from:'tasks/prepare.groovy'\napply from:'tasks/verify.groovy'\ndefaults 'verify'\n");
        Files.writeString(job.resolve("tasks/prepare.groovy"),"task('prepare',Gzip){new File('config-marker').text='configured';dataFile=file('input.txt');gzipFile=file('output.gz')}\ncleanup('end'){new File('cleanup-marker').text='cleaned'}\n");
        Files.writeString(job.resolve("tasks/verify.groovy"),"action('verify',dependsOn:'prepare'){assert result('prepare').file==file('output.gz');new File('action-marker').text='ran'}\n");
        for(var command:List.of(List.of("check"),List.of("plan"),List.of("run","--help"))){
            var args=new ArrayList<String>();args.add(command.getFirst());args.add(job.resolve("job.groovy").toString());args.addAll(command.subList(1,command.size()));
            String out=process(args);assertTrue(Files.exists(temp.resolve("top-marker")),out);
            for(String name:List.of("config-marker","action-marker","cleanup-marker"))assertFalse(Files.exists(temp.resolve(name)),name+": "+out);
            assertFalse(Files.exists(job.resolve("output.gz")));Files.delete(temp.resolve("top-marker"));
        }
        process(List.of("run",job.resolve("job.groovy").toString()));
        for(String name:List.of("top-marker","config-marker","action-marker","cleanup-marker"))assertTrue(Files.exists(temp.resolve(name)),name);
        try(var gzip=new GZIPInputStream(Files.newInputStream(job.resolve("output.gz")))){assertEquals(input,new String(gzip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
    }
    private String process(List<String> args)throws Exception{
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),Main.class.getName()));command.addAll(args);
        Path log=temp.resolve("process-"+UUID.randomUUID()+".log");
        Process process=new ProcessBuilder(command).directory(temp.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean done=process.waitFor(45,TimeUnit.SECONDS);if(!done)process.destroyForcibly();assertTrue(done,"CLI timeout: "+command);
        String output=Files.readString(log);assertEquals(0,process.exitValue(),command+"\n"+output);return output;
    }
}
