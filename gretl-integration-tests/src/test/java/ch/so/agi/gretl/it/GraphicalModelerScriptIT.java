package ch.so.agi.gretl.it;

import ch.so.agi.gretl.runtime.logging.Json;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the exact saved native-Modeler output as SCRIPT, outside normal editor analysis. */
class GraphicalModelerScriptIT {
    @Test void actualGuiGeneratedSourceDownloadsAndExtractsALocalArchiveWithoutRewritingItsBytes()throws Exception {
        Path fixture=ArtifactJobRunner.fixture("modeler-created");
        var provenance=(Map<?,?>)Json.parseFile(fixture.resolve("gui-provenance.json"),65536);
        String inputHash=JobInputSnapshot.hash(fixture.resolve("job.groovy"));
        assertEquals("ACTUAL_NATIVE_GUI_GENERATED_SOURCE",provenance.get("kind"));assertEquals(provenance.get("sha256"),inputHash);
        byte[] payload="Graphical GRETL roundtrip ü 😀\n".getBytes(StandardCharsets.UTF_8);
        var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("nested/payload.txt"));zip.write(payload);zip.closeEntry();}
        byte[] archive=bytes.toByteArray();var calls=new AtomicInteger();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/archive.zip",exchange->{calls.incrementAndGet();exchange.getResponseHeaders().add("Content-Type","application/zip");exchange.sendResponseHeaders(200,archive.length);exchange.getResponseBody().write(archive);exchange.close();});server.start();
        Path root=ArtifactJobRunner.directory("modeler-created-script");
        String url="http://127.0.0.1:"+server.getAddress().getPort()+"/archive.zip";
        try{
            var config=JobRunner.baseConfig(root);config.put("runtime",Map.of("workRoot",root.resolve("work").toString(),"reportRoot",root.resolve("reports").toString(),"keepWork","always"));
            var request=new ArtifactJobRunner.Request("modeler-created-script",fixture,root,List.of(),Map.of("sourceUrl",url),Map.of(),config,Map.of(),Duration.ofSeconds(45));
            var result=ArtifactJobRunner.run(request,ArtifactJobRunner.Mode.SCRIPT);
            assertEquals(0,result.code(),result.err());assertEquals("SCRIPT",result.mode());assertFalse(result.timedOut());assertTrue(result.hasValidArtifactIdentity());assertEquals(1,calls.get());
            Path extracted,downloaded;
            try(var paths=Files.walk(root.resolve("work"))){var outputs=paths.filter(Files::isRegularFile).toList();extracted=outputs.stream().filter(p->p.endsWith(Path.of("out/nested/payload.txt"))).findFirst().orElseThrow();downloaded=outputs.stream().filter(p->p.getFileName().toString().equals("archive.zip")).findFirst().orElseThrow();}
            assertArrayEquals(payload,Files.readAllBytes(extracted));assertArrayEquals(archive,Files.readAllBytes(downloaded));
            assertEquals(inputHash,JobInputSnapshot.hash(root.resolve("source/job.groovy")));assertEquals(inputHash,JobInputSnapshot.hash(fixture.resolve("job.groovy")));
            var evidence=new LinkedHashMap<String,Object>();
            evidence.put("kind","EXACT_GUI_SOURCE_SCRIPT_ROUNDTRIP");evidence.put("inputSha256",inputHash);evidence.put("source",fixture.resolve("job.groovy").toString());evidence.put("stagedSource",root.resolve("source/job.groovy").toString());evidence.put("mode",result.mode());evidence.put("parameters",Map.of("sourceUrl",url));evidence.put("httpRequests",calls.get());evidence.put("zipSha256",JobInputSnapshot.hash(archive));evidence.put("outputSha256",JobInputSnapshot.hash(extracted));evidence.put("expectedOutputSha256",JobInputSnapshot.hash(payload));evidence.put("report",result.report().toString());
            Files.writeString(root.resolve("gui-runtime-identity.json"),Json.write(evidence));
        }finally{server.stop(0);}
    }
}
