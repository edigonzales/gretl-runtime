package ch.so.agi.gretl.lsp;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Starts the assembly in a foreign working directory and exchanges framed JSON-RPC. */
final class LspProcessHarness implements AutoCloseable {
    private static final Gson JSON = new Gson();

    static Path packagedServer() {
        var configured = System.getProperty("gretl.test.serverJar");
        assertTrue(configured != null && !configured.isBlank(),
                "Failsafe must provide gretl.test.serverJar for the packaged server test");
        var artifact = Path.of(configured);
        assertTrue(artifact.isAbsolute() && Files.isRegularFile(artifact), "Packaged server is missing: " + artifact);
        return artifact;
    }
    final Process process;
    final BlockingQueue<JsonObject> incoming=new LinkedBlockingQueue<>();
    final List<JsonObject> notifications=new CopyOnWriteArrayList<>();
    final List<String> failures=new CopyOnWriteArrayList<>();
    final Map<Integer,JsonObject> responses=new HashMap<>();
    int next;
    LspProcessHarness(Path working,String...args)throws Exception {
        Path installed=working.resolve("installed server ü.jar");Files.copy(packagedServer(),installed,StandardCopyOption.REPLACE_EXISTING);
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-jar",installed.toString()));command.addAll(List.of(args));
        process=new ProcessBuilder(command).directory(working.toFile()).redirectError(working.resolve("server-stderr.log").toFile()).start();
        var reader=new Thread(()->{try {while(true){var message=read(process.getInputStream());if(message==null)break;incoming.add(message);}}catch(Exception failure){failures.add(failure.toString());}},"wire-test-reader");reader.setDaemon(true);reader.start();
    }
    void send(String method,Object params,Integer id)throws Exception {
        var object=new JsonObject();object.addProperty("jsonrpc","2.0");object.addProperty("method",method);if(id!=null)object.addProperty("id",id);object.add("params",JSON.toJsonTree(params));
        byte[] bytes=object.toString().getBytes(StandardCharsets.UTF_8);var output=process.getOutputStream();output.write(("Content-Length: "+bytes.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.write(bytes);output.flush();
    }
    JsonObject response(String method,Object params)throws Exception {int id=++next;send(method,params,id);return await(id);}
    JsonElement call(String method,Object params)throws Exception {return result(response(method,params));}
    private static JsonElement result(JsonObject response){assertFalse(response.has("error"),response.toString());assertTrue(response.has("result"),response.toString());return response.get("result");}
    // Use only for read-only requests or edit proposals that can overlap an expected refresh.
    JsonElement callAfterRefresh(String method,Object params)throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        JsonObject response;
        do {
            response=response(method,params);
            if(!response.has("error"))return result(response);
            var error=response.getAsJsonObject("error");
            assertEquals(-32801,error.get("code").getAsInt(),response.toString());
            assertEquals("STALE_OR_CANCELLED_ANALYSIS",error.get("message").getAsString(),response.toString());
        }while(System.nanoTime()<deadline);
        throw new AssertionError("Analysis did not settle for "+method+": "+response);
    }
    JsonObject graphAfterRefresh(String uri)throws Exception {
        return callAfterRefresh("gretl/graph",Map.of("protocolVersion",1,"documentId",uri)).getAsJsonObject();
    }
    JsonObject await(int id)throws Exception {
        var existing=responses.remove(id);if(existing!=null)return existing;
        long deadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();
        while(System.nanoTime()<deadline) {
            var message=incoming.poll(100,TimeUnit.MILLISECONDS);if(message==null){assertTrue(process.isAlive(),"Process exited "+failures);continue;}
            if(message.has("id")&&message.get("id").getAsInt()==id)return message;
            if(message.has("method"))notifications.add(message);else responses.put(message.get("id").getAsInt(),message);
        }
        throw new AssertionError("Timed out "+id+" "+failures);
    }
    JsonObject notification(String method,java.util.function.Predicate<JsonObject> accepts)throws Exception {
        for(var previous:notifications)if(method.equals(previous.get("method").getAsString())&&accepts.test(previous.getAsJsonObject("params")))return previous.getAsJsonObject("params");
        long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();
        while(System.nanoTime()<deadline) {
            var message=incoming.poll(100,TimeUnit.MILLISECONDS);if(message==null){assertTrue(process.isAlive());continue;}
            if(message.has("method")) {notifications.add(message);if(method.equals(message.get("method").getAsString())&&accepts.test(message.getAsJsonObject("params")))return message.getAsJsonObject("params");}
            else responses.put(message.get("id").getAsInt(),message);
        }
        throw new AssertionError("Notification timeout "+method+" "+failures);
    }
    JsonObject initialize(Path root,boolean trusted,boolean edits)throws Exception {
        return call("initialize",Map.of("rootUri",root.toUri().toString(),"locale","de","capabilities",Map.of("workspace",Map.of("workspaceEdit",Map.of("documentChanges",edits)),"textDocument",Map.of("codeAction",Map.of("codeActionLiteralSupport",Map.of("codeActionKind",Map.of("valueSet",List.of("quickfix")))))),"initializationOptions",Map.of("trusted",trusted))).getAsJsonObject();
    }
    void opened(String uri,int version,String text)throws Exception {send("textDocument/didOpen",Map.of("textDocument",Map.of("uri",uri,"languageId","gretl-runtime","version",version,"text",text)),null);}
    void stop()throws Exception {call("shutdown",Map.of());send("exit",Map.of(),null);assertTrue(process.waitFor(8,TimeUnit.SECONDS),"Exit must finish while stdin remains open");assertEquals(0,process.exitValue());assertTrue(failures.isEmpty(),failures.toString());}
    public void close(){if(process.isAlive()){process.destroy();try{if(!process.waitFor(5,TimeUnit.SECONDS))process.destroyForcibly();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}}
    static JsonObject read(InputStream input)throws Exception {
        var header=new ByteArrayOutputStream();int state=0;
        while(state!=4){int value=input.read();if(value<0){if(header.size()==0)return null;throw new EOFException();}header.write(value);if(header.size()>8192)throw new IOException("Non-RPC stdout: "+header);state=switch(state){case 0->value=='\r'?1:0;case 1->value=='\n'?2:0;case 2->value=='\r'?3:0;default->value=='\n'?4:0;};}
        int length=-1;for(var line:header.toString(StandardCharsets.US_ASCII).split("\r\n"))if(line.startsWith("Content-Length:"))length=Integer.parseInt(line.substring(15).trim());
        if(length<0||length>8_388_608)throw new IOException("Invalid output frame");var bytes=input.readNBytes(length);if(bytes.length!=length)throw new EOFException();return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
