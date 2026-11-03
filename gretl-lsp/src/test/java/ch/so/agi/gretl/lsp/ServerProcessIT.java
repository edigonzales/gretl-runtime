package ch.so.agi.gretl.lsp;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real packaged process, framed JSON-RPC, no direct server-method substitute. */
class ServerProcessIT {
    @TempDir Path temporary;
    static final Gson JSON=new Gson();
    static final Path ARTIFACT=Path.of("target/gretl-lsp-6.0.0-SNAPSHOT-jar-with-dependencies.jar").toAbsolutePath();
    static final class Wire implements AutoCloseable {
        final Process process;
        final BlockingQueue<JsonObject> incoming=new LinkedBlockingQueue<>();
        final List<JsonObject> notifications=new CopyOnWriteArrayList<>();
        final List<String> failures=new CopyOnWriteArrayList<>();
        final Map<Integer,JsonObject> responses=new HashMap<>();
        int next;
        Wire(Path working,String...args)throws Exception {
            Path installed=working.resolve("installed server ü.jar");Files.copy(ARTIFACT,installed,StandardCopyOption.REPLACE_EXISTING);
            var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-jar",installed.toString()));command.addAll(List.of(args));
            process=new ProcessBuilder(command).directory(working.toFile()).redirectError(working.resolve("server-stderr.log").toFile()).start();
            var reader=new Thread(()->{try {while(true){var message=read(process.getInputStream());if(message==null)break;incoming.add(message);}}catch(Exception failure){failures.add(failure.toString());}},"wire-test-reader");reader.setDaemon(true);reader.start();
        }
        void send(String method,Object params,Integer id)throws Exception {
            var object=new JsonObject();object.addProperty("jsonrpc","2.0");object.addProperty("method",method);if(id!=null)object.addProperty("id",id);object.add("params",JSON.toJsonTree(params));
            byte[] bytes=object.toString().getBytes(StandardCharsets.UTF_8);var output=process.getOutputStream();output.write(("Content-Length: "+bytes.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.write(bytes);output.flush();
        }
        JsonObject response(String method,Object params)throws Exception {int id=++next;send(method,params,id);return await(id);}
        JsonElement call(String method,Object params)throws Exception {var response=response(method,params);assertFalse(response.has("error"),response.toString());assertTrue(response.has("result"),response.toString());return response.get("result");}
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
    Map<String,Object> position(String uri,int line,int character){return Map.of("textDocument",Map.of("uri",uri),"position",Map.of("line",line,"character",character));}
    JsonObject graph(Wire wire,String uri)throws Exception{return wire.call("gretl/graph",Map.of("protocolVersion",1,"documentId",uri)).getAsJsonObject();}
    boolean diagnostic(JsonObject graph,String code){return graph.getAsJsonObject("payload").getAsJsonArray("diagnostics").asList().stream().map(JsonElement::getAsJsonObject).anyMatch(d->code.equals(d.get("code").getAsString()));}

    @Test void standaloneWireLifecycleStandardFeaturesAndCustomEdits()throws Exception {
        Path root=Files.createDirectory(temporary.resolve("workspace ü ; literal"));String uri=root.resolve("job.groovy").toUri().toString();
        String source="parameter('arg',description:'A',defaultValue:'x')\r\n"
                +"task('p', HttpDownload) { url='https://example.invalid/a'; output=workPath('a.zip') }\r\n"
                +"task('c', Extract, dependsOn:'p') { archive=result('p').file; outputDirectory=workPath('out') }\r\ndefaults 'c'\r\n";
        Files.writeString(root.resolve("job.groovy"),source);
        try(var wire=new Wire(root,"--stdio")) {
            var capabilities=wire.initialize(root,true,true).getAsJsonObject("capabilities");assertEquals("utf-16",capabilities.get("positionEncoding").getAsString());
            assertTrue(capabilities.getAsJsonObject("experimental").getAsJsonObject("gretlRuntime").get("graph").getAsBoolean());
            wire.send("initialized",Map.of(),null);wire.opened(uri,1,source);
            var completion=wire.call("textDocument/completion",position(uri,1,12)).getAsJsonArray();assertTrue(completion.asList().stream().anyMatch(x->x.getAsJsonObject().get("label").getAsString().equals("HttpDownload")));
            assertFalse(wire.call("textDocument/hover",position(uri,1,12)).isJsonNull());assertFalse(wire.call("textDocument/signatureHelp",position(uri,1,12)).isJsonNull());
            assertEquals(3,wire.call("textDocument/documentSymbol",Map.of("textDocument",Map.of("uri",uri))).getAsJsonArray().size());
            var definition=wire.call("textDocument/definition",position(uri,2,30)).getAsJsonArray();
            assertEquals(1,definition.size());assertEquals(Path.of(java.net.URI.create(uri)),Path.of(java.net.URI.create(definition.get(0).getAsJsonObject().get("uri").getAsString())));
            var references=new HashMap<String,Object>(position(uri,1,6));references.put("context",Map.of("includeDeclaration",true));assertTrue(wire.call("textDocument/references",references).getAsJsonArray().size()>=2);
            assertFalse(wire.call("textDocument/prepareRename",position(uri,1,6)).isJsonNull());
            var rename=new HashMap<String,Object>(position(uri,1,6));rename.put("newName","producer");var edit=wire.call("textDocument/rename",rename).getAsJsonObject();assertTrue(edit.has("documentChanges"));assertEquals(1,edit.getAsJsonArray("documentChanges").get(0).getAsJsonObject().getAsJsonObject("textDocument").get("version").getAsInt());
            assertTrue(wire.call("textDocument/documentLink",Map.of("textDocument",Map.of("uri",uri))).isJsonArray());
            assertTrue(wire.call("textDocument/foldingRange",Map.of("textDocument",Map.of("uri",uri))).isJsonArray());
            var catalog=wire.call("gretl/catalog",Map.of("protocolVersion",1,"catalogSchemaVersion",1)).getAsJsonObject();assertEquals("AVAILABLE",catalog.get("state").getAsString());assertEquals(44,catalog.getAsJsonObject("catalog").getAsJsonArray("operations").size());
            var graph=graph(wire,uri);String snapshot=graph.getAsJsonObject("context").get("snapshotId").getAsString();
            assertEquals(2,graph.getAsJsonObject("payload").getAsJsonArray("nodes").size());
            var planned=wire.call("gretl/planEdit",Map.of("protocolVersion",1,"documentId",uri,"expectedSnapshotId",snapshot,"requireOpenDocuments",true,
                    "intent",Map.of("kind","SetTaskOption","target",Map.of("namespace","TASK","qualifiedName","c"),"field","description","value",Map.of("kind","Text","scalar","consumer")))).getAsJsonObject();
            assertEquals("ALLOWED",planned.getAsJsonObject("capability").get("state").getAsString());
            var validation=wire.call("gretl/validateEdit",Map.of("protocolVersion",1,"planId",planned.get("planId").getAsString(),"expectedSnapshotId",snapshot)).getAsJsonObject();assertTrue(validation.get("current").getAsBoolean());
            String element=graph.getAsJsonObject("payload").getAsJsonArray("nodes").get(0).getAsJsonObject().get("id").getAsString();
            assertFalse(wire.call("gretl/navigation",Map.of("protocolVersion",1,"documentId",uri,"expectedSnapshotId",snapshot,"elementId",element)).getAsJsonObject().get("location").isJsonNull());
            assertEquals(source,Files.readString(root.resolve("job.groovy")),"Server only returns edits");
            wire.stop();
        }
    }

    @Test void protocolStatesTrustAndExitWithoutShutdownAreRealProcessBoundaries()throws Exception {
        String uri=temporary.resolve("job.groovy").toUri().toString();Files.writeString(temporary.resolve("job.groovy"),"action('a') {}\n");
        try(var wire=new Wire(temporary,"--stdio")) {
            assertEquals(-32002,wire.response("textDocument/completion",position(uri,0,0)).getAsJsonObject("error").get("code").getAsInt());
            var capabilities=wire.initialize(temporary,false,false).getAsJsonObject("capabilities");assertFalse(capabilities.has("renameProvider"));
            wire.send("initialized",Map.of(),null);wire.opened(uri,1,"action('a') {}\n");
            assertTrue(wire.response("gretl/graph",Map.of("protocolVersion",1,"documentId",uri)).getAsJsonObject("error").get("message").getAsString().contains("UNTRUSTED"));
            wire.send("exit",Map.of(),null);assertTrue(wire.process.waitFor(8,TimeUnit.SECONDS));assertEquals(1,wire.process.exitValue());
        }
        try(var wire=new Wire(temporary,"--bad")){assertTrue(wire.process.waitFor(8,TimeUnit.SECONDS));assertEquals(2,wire.process.exitValue());assertTrue(wire.failures.isEmpty());}
        try(var wire=new Wire(temporary,"--stdio")){wire.process.getOutputStream().close();assertTrue(wire.process.waitFor(8,TimeUnit.SECONDS));assertEquals(1,wire.process.exitValue());}
    }

    @Test void incrementalEventsVersionsCloseReopenAndStalePlansUseTheActualBuffer()throws Exception {
        Path job=temporary.resolve("job.groovy");String uri=job.toUri().toString();String source="// 😀\r\naction('a') {}\r\ndefaults 'a'\r\n";Files.writeString(job,source);
        try(var wire=new Wire(temporary,"--stdio")) {
            wire.initialize(temporary,true,true);wire.send("initialized",Map.of(),null);wire.opened(uri,1,source);
            var initial=graph(wire,uri);String original=initial.getAsJsonObject("context").get("snapshotId").getAsString();
            var edit=wire.call("gretl/planEdit",Map.of("protocolVersion",1,"documentId",uri,"expectedSnapshotId",original,"requireOpenDocuments",true,
                    "intent",Map.of("kind","RenameSymbol","target",Map.of("namespace","TASK","qualifiedName","a"),"name","planned"))).getAsJsonObject();
            String plan=edit.get("planId").getAsString();
            wire.send("textDocument/didChange",Map.of("textDocument",Map.of("uri",uri,"version",2),"contentChanges",List.of(
                    Map.of("range",Map.of("start",Map.of("line",1,"character",8),"end",Map.of("line",1,"character",9)),"text","long"),
                    Map.of("range",Map.of("start",Map.of("line",2,"character",10),"end",Map.of("line",2,"character",11)),"text","long"))),null);
            var changed=graph(wire,uri);assertEquals("long",changed.getAsJsonObject("payload").getAsJsonArray("nodes").get(0).getAsJsonObject().get("title").getAsString());
            assertTrue(changed.getAsJsonObject("context").getAsJsonArray("sourceStamps").asList().stream().map(JsonElement::getAsJsonObject).anyMatch(stamp->stamp.has("version")&&stamp.get("version").getAsInt()==2));
            assertFalse(wire.call("gretl/validateEdit",Map.of("protocolVersion",1,"planId",plan,"expectedSnapshotId",original)).getAsJsonObject().get("current").getAsBoolean());
            wire.send("textDocument/didChange",Map.of("textDocument",Map.of("uri",uri,"version",2),"contentChanges",List.of(Map.of("text","action('stale') {}"))),null);
            assertEquals("long",graph(wire,uri).getAsJsonObject("payload").getAsJsonArray("nodes").get(0).getAsJsonObject().get("title").getAsString());
            wire.send("textDocument/didChange",Map.of("textDocument",Map.of("uri",uri,"version",3),"contentChanges",List.of(Map.of("text","action('full') {}\n"))),null);
            assertEquals("full",graph(wire,uri).getAsJsonObject("payload").getAsJsonArray("nodes").get(0).getAsJsonObject().get("title").getAsString());
            wire.send("textDocument/didClose",Map.of("textDocument",Map.of("uri",uri)),null);
            assertEquals("a",graph(wire,uri).getAsJsonObject("payload").getAsJsonArray("nodes").get(0).getAsJsonObject().get("title").getAsString());
            wire.opened(uri,1,"action('reopened') {}\n");
            var reopened=graph(wire,uri);assertEquals("reopened",reopened.getAsJsonObject("payload").getAsJsonArray("nodes").get(0).getAsJsonObject().get("title").getAsString());
            assertNotEquals(original,reopened.getAsJsonObject("context").get("snapshotId").getAsString());
            assertTrue(wire.response("gretl/graph",Map.of("protocolVersion",1,"documentId",uri,"expectedSnapshotId",original)).has("error"));
            assertEquals(source,Files.readString(job));wire.stop();
        }
    }

    @Test void dirtySqlSettingsConfigurationAndFolderChangesReachRealAnalysisWithoutExecution()throws Exception {
        Path job=temporary.resolve("job.groovy"),sql=temporary.resolve("query.sql"),part=Files.createDirectory(temporary.resolve("part"));
        String uri=job.toUri().toString(),sqlUri=sql.toUri().toString();
        Path marker=temporary.resolve("executed.txt");String quoted=marker.toString().replace("\\","\\\\").replace("'","\\'");
        String source="new File('"+quoted+"').text='unsafe'\n"
                +"task('q', DuckDbSqlExecutor) { sqlFiles=[file('query.sql')]; sqlParameters=[:] }\n";
        Files.writeString(job,source);Files.writeString(sql,"select 1");Files.writeString(part.resolve("job.groovy"),"action('child') {}\n");
        try(var wire=new Wire(temporary,"--stdio")) {
            wire.initialize(temporary,true,true);wire.send("initialized",Map.of(),null);wire.opened(uri,1,source);
            assertFalse(diagnostic(graph(wire,uri),"SQL_PARAMETER_MISSING"));
            wire.send("textDocument/didOpen",Map.of("textDocument",Map.of("uri",sqlUri,"languageId","sql","version",7,"text","-- 😀\r\nselect ${missing}")),null);
            var dirty=graph(wire,uri);assertTrue(diagnostic(dirty,"SQL_PARAMETER_MISSING"));
            var sqlDiagnostic=wire.notification("textDocument/publishDiagnostics",params->params.has("version")&&params.get("version").getAsInt()==7&&!params.getAsJsonArray("diagnostics").isEmpty());
            var sqlRange=sqlDiagnostic.getAsJsonArray("diagnostics").get(0).getAsJsonObject().getAsJsonObject("range");
            assertEquals(1,sqlRange.getAsJsonObject("start").get("line").getAsInt());assertEquals(7,sqlRange.getAsJsonObject("start").get("character").getAsInt());
            var error=dirty.getAsJsonObject("payload").getAsJsonArray("diagnostics").asList().stream().map(JsonElement::getAsJsonObject).filter(d->d.get("code").getAsString().equals("SQL_PARAMETER_MISSING")).findFirst().orElseThrow();
            assertTrue(error.get("message").getAsString().startsWith("SQLvorlage"));
            assertEquals(Path.of(java.net.URI.create(sqlUri)),Path.of(java.net.URI.create(error.getAsJsonObject("anchor").get("documentId").getAsString())));
            wire.send("workspace/didChangeConfiguration",Map.of("settings",Map.of("language","fr")),null);
            assertTrue(graph(wire,uri).getAsJsonObject("payload").getAsJsonArray("diagnostics").asList().stream().map(JsonElement::getAsJsonObject).filter(d->d.get("code").getAsString().equals("SQL_PARAMETER_MISSING")).allMatch(d->d.get("message").getAsString().startsWith("SQLvorlage")));
            wire.send("workspace/didChangeConfiguration",Map.of("settings",Map.of("language","en")),null);
            assertTrue(graph(wire,uri).getAsJsonObject("payload").getAsJsonArray("diagnostics").asList().stream().map(JsonElement::getAsJsonObject).filter(d->d.get("code").getAsString().equals("SQL_PARAMETER_MISSING")).allMatch(d->d.get("message").getAsString().startsWith("SQL template")));
            String settingsUri=temporary.resolve("settings.gretl").toUri().toString();wire.opened(settingsUri,1,"include 'part'\ndefaults ':part:child'\n");
            assertTrue(graph(wire,uri).getAsJsonObject("payload").getAsJsonArray("nodes").asList().stream().map(JsonElement::getAsJsonObject).anyMatch(n->n.get("title").getAsString().equals(":part:child")));
            wire.send("textDocument/didClose",Map.of("textDocument",Map.of("uri",settingsUri)),null);
            wire.send("textDocument/didClose",Map.of("textDocument",Map.of("uri",sqlUri)),null);
            assertFalse(diagnostic(graph(wire,uri),"SQL_PARAMETER_MISSING"));
            Files.writeString(sql,"select ${external}");wire.send("workspace/didChangeWatchedFiles",Map.of("changes",List.of(Map.of("uri",sqlUri,"type",2))),null);
            assertTrue(diagnostic(graph(wire,uri),"SQL_PARAMETER_MISSING"));
            String watchedText="select 2";Files.writeString(sql,watchedText);
            String watchedHash=new ch.so.agi.gretl.tooling.source.SourceText(sql.toUri(),watchedText,null,"DISK",true).stamp().contentSha256();
            wire.notification("gretl/analysisReady",params->params.getAsJsonObject("context").getAsJsonArray("sourceStamps").asList().stream().map(JsonElement::getAsJsonObject).anyMatch(stamp->stamp.has("sha256")&&watchedHash.equals(stamp.get("sha256").getAsString())));
            assertFalse(diagnostic(graph(wire,uri),"SQL_PARAMETER_MISSING"),"Native watcher must refresh without didChangeWatchedFiles");
            Files.writeString(sql,"select ${external}");wire.send("workspace/didChangeWatchedFiles",Map.of("changes",List.of(Map.of("uri",sqlUri,"type",2))),null);
            wire.send("workspace/didChangeWorkspaceFolders",Map.of("event",Map.of("added",List.of(),"removed",List.of(Map.of("uri",temporary.toUri().toString(),"name","root")))),null);
            assertTrue(wire.response("gretl/graph",Map.of("protocolVersion",1,"documentId",uri)).has("error"));
            wire.send("workspace/didChangeWorkspaceFolders",Map.of("event",Map.of("added",List.of(Map.of("uri",temporary.toUri().toString(),"name","root")),"removed",List.of())),null);
            assertTrue(diagnostic(graph(wire,uri),"SQL_PARAMETER_MISSING"));
            assertFalse(Files.exists(marker));wire.stop();
        }
    }

    @Test void quickFixCrossFileReadonlyAndMessageLimitsRemainFailClosed()throws Exception {
        String source="task('p', HttpDownload) { url='https://example.invalid/a' }; task('c', Extract) { archive=result('p').file }\n";
        Path job=temporary.resolve("job.groovy");Files.writeString(job,source);String uri=job.toUri().toString();
        try(var wire=new Wire(temporary,"--stdio")) {
            wire.initialize(temporary,true,true);wire.send("initialized",Map.of(),null);wire.opened(uri,1,source);
            var before=graph(wire,uri);assertTrue(diagnostic(before,"RESULT_DEPENDENCY_REQUIRED"));
            int character=source.indexOf("result('p')")+8;
            var actions=wire.call("textDocument/codeAction",Map.of("textDocument",Map.of("uri",uri),"range",Map.of("start",Map.of("line",0,"character",character),"end",Map.of("line",0,"character",character)),"context",Map.of("diagnostics",List.of()))).getAsJsonArray();
            assertEquals(1,actions.size());var changes=actions.get(0).getAsJsonObject().getAsJsonObject("edit").getAsJsonArray("documentChanges");
            String post=source;var edits=changes.get(0).getAsJsonObject().getAsJsonArray("edits").asList().stream().map(JsonElement::getAsJsonObject).sorted(Comparator.comparingInt((JsonObject edit)->edit.getAsJsonObject("range").getAsJsonObject("start").get("character").getAsInt()).reversed()).toList();
            for(var edit:edits){int start=edit.getAsJsonObject("range").getAsJsonObject("start").get("character").getAsInt(),end=edit.getAsJsonObject("range").getAsJsonObject("end").get("character").getAsInt();post=post.substring(0,start)+edit.get("newText").getAsString()+post.substring(end);}
            wire.send("textDocument/didChange",Map.of("textDocument",Map.of("uri",uri,"version",2),"contentChanges",List.of(Map.of("text",post))),null);
            assertFalse(diagnostic(graph(wire,uri),"RESULT_DEPENDENCY_REQUIRED"));assertEquals(source,Files.readString(job));
            assertTrue(wire.response("gretl/graph",Map.of("protocolVersion",2,"documentId",uri)).has("error"));
            assertTrue(wire.response("gretl/graph",Map.of("protocolVersion",1,"documentId","https://example.invalid/job.groovy")).has("error"));
            assertTrue(wire.response("gretl/graph",Map.of("protocolVersion",1,"documentId",temporary.getParent().resolve("outside/job.groovy").toUri().toString())).has("error"));
            wire.stop();
        }
        var original=Files.getPosixFilePermissions(job);Files.setPosixFilePermissions(job,Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ));
        try(var wire=new Wire(temporary,"--stdio")) {
            wire.initialize(temporary,true,true);wire.send("initialized",Map.of(),null);wire.opened(uri,1,source);
            var rename=new HashMap<String,Object>(position(uri,0,6));rename.put("newName","renamed");
            assertTrue(wire.response("textDocument/rename",rename).getAsJsonObject("error").get("message").getAsString().contains("READONLY"));wire.stop();
        } finally {Files.setPosixFilePermissions(job,original);}
        try(var wire=new Wire(temporary,"--stdio")) {
            wire.process.getOutputStream().write("Content-Length: 2097153\r\n\r\n".getBytes(StandardCharsets.US_ASCII));wire.process.getOutputStream().flush();
            assertTrue(wire.process.waitFor(8,TimeUnit.SECONDS));assertEquals(1,wire.process.exitValue());
        }
    }

    @Test void cancellationAndBurstyChangesCannotPublishObsoleteRepliesAsCurrent()throws Exception {
        Path job=temporary.resolve("job.groovy");String uri=job.toUri().toString();
        StringBuilder source=new StringBuilder();for(int i=0;i<1200;i++)source.append("action('a").append(i).append("') {}\n");Files.writeString(job,source);
        try(var wire=new Wire(temporary,"--stdio")) {
            wire.initialize(temporary,true,true);wire.send("initialized",Map.of(),null);wire.opened(uri,1,source.toString());
            int cancelled=++wire.next;wire.send("gretl/graph",Map.of("protocolVersion",1,"documentId",uri),cancelled);
            wire.send("$/cancelRequest",Map.of("id",cancelled),null);
            for(int version=2;version<=12;version++)wire.send("textDocument/didChange",Map.of("textDocument",Map.of("uri",uri,"version",version),"contentChanges",List.of(Map.of("text","action('latest"+version+"') {}\n"))),null);
            var reply=wire.await(cancelled);assertTrue(reply.has("error"),"Cancelled request must not return a graph as fresh");
            assertTrue(Set.of(-32800,-32801).contains(reply.getAsJsonObject("error").get("code").getAsInt()),reply.toString());
            var graph=graph(wire,uri);assertEquals("latest12",graph.getAsJsonObject("payload").getAsJsonArray("nodes").get(0).getAsJsonObject().get("title").getAsString());
            assertTrue(graph.getAsJsonObject("context").getAsJsonArray("sourceStamps").asList().stream().map(JsonElement::getAsJsonObject).anyMatch(stamp->stamp.has("version")&&stamp.get("version").getAsInt()==12));
            assertTrue(wire.notifications.stream().anyMatch(n->n.get("method").getAsString().equals("gretl/analysisReady")));
            wire.stop();
        }
    }

    @Test void everyTypedIntentIsRegisteredAndProducesAVerifiedPlanOnTheWire()throws Exception {
        String source="task('p', HttpDownload) { url='https://example.invalid/a'; output=workPath('a.zip'); overwrite=false }; "
                +"task('c', Extract, after:'p') { archive=file('old.zip') }; action('unused') { ctx.run(HttpDownload) { url='https://example.invalid/inner'; output=workPath('inner.zip') } }; defaults 'c'\n";
        Path job=temporary.resolve("job.groovy");Files.writeString(job,source);String uri=job.toUri().toString();
        var p=Map.of("namespace","TASK","qualifiedName","p");var c=Map.of("namespace","TASK","qualifiedName","c");
        List<Map<String,Object>> intents=List.of(
                Map.of("kind","AddTask","name","addedTask","operationId","gretl.http-download","insertionOffset",source.length(),"fields",Map.of("url",Map.of("kind","Text","scalar","https://example.invalid/new"),"output",Map.of("kind","WorkPathRef","scalar","new.zip"))),
                Map.of("kind","AddAction","name","addedAction","insertionOffset",source.length()),
                Map.of("kind","AddParameter","insertionOffset",source.length(),"parameter",Map.of("name","arg","description","Argument","type","text","required",false,"defaultValue",Map.of("kind","Text","scalar","x"))),
                Map.of("kind","SetField","target",c,"field","archive","value",Map.of("kind","JobFileRef","scalar","new.zip")),
                Map.of("kind","RemoveField","target",p,"field","overwrite"),
                Map.of("kind","SetTaskOption","target",c,"field","description","value",Map.of("kind","Text","scalar","Description")),
                Map.of("kind","AddDependency","producer",p,"target",c,"edgeKind","DEPENDS_ON"),
                Map.of("kind","RemoveDependency","producer",p,"target",c,"edgeKind","AFTER"),
                Map.of("kind","BindResult","producer",p,"outputField","file","target",c,"field","archive"),
                Map.of("kind","RenameSymbol","target",p,"name","producer"),
                Map.of("kind","DeleteTask","target",Map.of("namespace","TASK","qualifiedName","unused")),
                Map.of("kind","SetDefaults","targets",List.of(p,c)),
                Map.of("kind","ConfigureTask","target",c,"fields",Map.of("archive",Map.of("kind","JobFileRef","scalar","new.zip")),"options",Map.of("description",Map.of("kind","Text","scalar","Configured")))
        );
        try(var wire=new Wire(temporary,"--stdio")) {
            wire.initialize(temporary,true,true);wire.send("initialized",Map.of(),null);wire.opened(uri,1,source);
            String snapshot=graph(wire,uri).getAsJsonObject("context").get("snapshotId").getAsString();
            var outline=wire.call("textDocument/documentSymbol",Map.of("textDocument",Map.of("uri",uri))).getAsJsonArray();
            assertEquals(4,outline.size());assertTrue(outline.asList().stream().map(JsonElement::getAsJsonObject).anyMatch(symbol->symbol.has("containerName")&&symbol.get("containerName").getAsString().equals("unused")&&symbol.get("name").getAsString().equals("HttpDownload")));
            for(var intent:intents) {
                var result=wire.call("gretl/planEdit",Map.of("protocolVersion",1,"documentId",uri,"expectedSnapshotId",snapshot,"requireOpenDocuments",true,"intent",intent)).getAsJsonObject();
                assertEquals("ALLOWED",result.getAsJsonObject("capability").get("state").getAsString(),intent+": "+result);
                assertFalse(result.getAsJsonObject("edit").getAsJsonArray("documentChanges").isEmpty(),intent.toString());
            }
            assertEquals(source,Files.readString(job));wire.stop();
        }
    }

    @Test void closedBomFileRenameUsesDisplayCoordinatesAndExplicitNullVersions()throws Exception {
        String source="\uFEFFaction('a') {}\r\naction('b',dependsOn:'a') {}\r\n";Path job=temporary.resolve("job.groovy");Files.writeString(job,source);String uri=job.toUri().toString();
        try(var wire=new Wire(temporary,"--stdio")) {
            wire.initialize(temporary,true,true);wire.send("initialized",Map.of(),null);
            var prepare=wire.call("textDocument/prepareRename",position(uri,0,8)).getAsJsonObject();assertEquals(8,prepare.getAsJsonObject("range").getAsJsonObject("start").get("character").getAsInt());
            var params=new HashMap<String,Object>(position(uri,0,8));params.put("newName","renamed");
            var change=wire.call("textDocument/rename",params).getAsJsonObject().getAsJsonArray("documentChanges").get(0).getAsJsonObject();
            assertTrue(change.getAsJsonObject("textDocument").has("version"));assertTrue(change.getAsJsonObject("textDocument").get("version").isJsonNull());
            assertEquals(2,change.getAsJsonArray("edits").size());assertEquals(source,Files.readString(job));wire.stop();
        }
    }
}
