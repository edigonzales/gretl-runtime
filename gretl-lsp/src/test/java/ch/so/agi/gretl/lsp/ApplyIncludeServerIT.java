package ch.so.agi.gretl.lsp;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Include compatibility over the installed stdio server; no direct API/mock replacement. */
class ApplyIncludeServerIT {
    @TempDir Path root;
    private Map<String,String> task(String name){return Map.of("namespace","TASK","qualifiedName",name);}
    private JsonObject graph(ServerProcessIT.Wire wire,String uri)throws Exception{return wire.call("gretl/graph",Map.of("protocolVersion",1,"documentId",uri)).getAsJsonObject();}
    private JsonObject plan(ServerProcessIT.Wire wire,String uri,String snapshot,Map<String,?> intent)throws Exception{return wire.call("gretl/planEdit",Map.of("protocolVersion",1,"documentId",uri,"expectedSnapshotId",snapshot,"requireOpenDocuments",true,"intent",intent)).getAsJsonObject();}
    @Test void invalidFragmentSafeLocalEditUnsafeRoutesAndStaleIncludeLiteralUseRealWire()throws Exception{
        String source="// ä😀 preserve\r\napply from: 'tasks/extra.groovy' // opaque include\r\ntask('p',Gzip){dataFile=file('input.txt');gzipFile=workPath('p.gz')}\r\ntask('visible',Gzip,dependsOn:'p',after:'p'){\r\n dataFile=file('input.txt')\r\n gzipFile=workPath('visible.gz')\r\n overwrite = true // local comment\r\n}\r\ndefaults 'visible'\r\n";
        Path job=root.resolve("job.groovy"),fragment=root.resolve("tasks/extra.groovy"),marker=root.resolve("executed");Files.createDirectories(fragment.getParent());Files.writeString(job,source);Files.writeString(fragment,"new File('"+marker+"').text='executed'\nthis is deliberately invalid {");
        String uri=job.toUri().toString();
        try(var wire=new ServerProcessIT.Wire(root,"--stdio")){
            wire.initialize(root,true,true);wire.send("initialized",Map.of(),null);wire.opened(uri,1,source);
            var graph=graph(wire,uri);var payload=graph.getAsJsonObject("payload");assertEquals("INVALID",payload.get("status").getAsString());
            assertTrue(payload.getAsJsonArray("nodes").asList().stream().anyMatch(n->n.getAsJsonObject().get("kind").getAsString().equals("DYNAMIC_CODE")));
            assertTrue(payload.getAsJsonArray("nodes").asList().stream().anyMatch(n->n.getAsJsonObject().get("title").getAsString().equals("visible")));
            assertTrue(graph.getAsJsonObject("context").getAsJsonArray("sourceStamps").asList().stream().anyMatch(s->java.net.URI.create(s.getAsJsonObject().get("documentId").getAsString()).equals(fragment.toUri())));
            String snapshot=graph.getAsJsonObject("context").get("snapshotId").getAsString();
            var local=plan(wire,uri,snapshot,Map.of("kind","SetField","target",task("visible"),"field","overwrite","value",Map.of("kind","Boolean","scalar","false")));
            assertEquals("ALLOWED",local.getAsJsonObject("capability").get("state").getAsString());
            var changes=local.getAsJsonObject("edit").getAsJsonArray("documentChanges");assertEquals(1,changes.size());var edit=changes.get(0).getAsJsonObject();assertEquals(1,edit.getAsJsonObject("textDocument").get("version").getAsInt());assertEquals(1,edit.getAsJsonArray("edits").size());assertEquals("false",edit.getAsJsonArray("edits").get(0).getAsJsonObject().get("newText").getAsString());
            String planId=local.get("planId").getAsString();assertTrue(wire.call("gretl/validateEdit",Map.of("protocolVersion",1,"planId",planId,"expectedSnapshotId",snapshot)).getAsJsonObject().get("current").getAsBoolean());
            for(var intent:List.of(
                    Map.of("kind","RenameSymbol","target",task("visible"),"name","renamed"),Map.of("kind","DeleteTask","target",task("visible")),
                    Map.of("kind","AddDependency","producer",task("p"),"target",task("visible"),"edgeKind","DEPENDS_ON"),
                    Map.of("kind","RemoveDependency","producer",task("p"),"target",task("visible"),"edgeKind","AFTER"),
                    Map.of("kind","BindResult","producer",task("p"),"target",task("visible"),"field","dataFile","outputField","file"),
                    Map.of("kind","SetTaskOption","target",task("visible"),"field","dependsOn","value",Map.of("kind","List","items",List.of())),
                    Map.of("kind","SetTaskOption","target",task("visible"),"field","after","value",Map.of("kind","List","items",List.of())),
                    Map.of("kind","SetField","target",task("visible"),"field","dataFile","value",Map.of("kind","ResultFieldRef","symbol",task("p"),"field","file")),
                    Map.of("kind","SetDefaults","targets",List.of(task("visible"))),
                    Map.of("kind","ConfigureTask","target",task("visible"),"fields",Map.of("overwrite",Map.of("kind","Boolean","scalar","false")),"options",Map.of("dependsOn",Map.of("kind","List","items",List.of())))
            )){
                var rejected=plan(wire,uri,snapshot,intent);assertEquals("REJECTED",rejected.getAsJsonObject("capability").get("state").getAsString(),intent+": "+rejected);assertTrue(!rejected.has("edit")||rejected.get("edit").isJsonNull(),rejected.toString());assertTrue(rejected.getAsJsonObject("capability").getAsJsonArray("reasons").asList().stream().anyMatch(r->r.getAsJsonObject().get("code").getAsString().equals("EDIT_REFERENCE_COVERAGE")));
            }
            wire.send("textDocument/didChange",Map.of("textDocument",Map.of("uri",uri,"version",2),"contentChanges",List.of(Map.of("text",source.replace("extra.groovy","other.groovy")))),null);
            var changed=graph(wire,uri);assertNotEquals(snapshot,changed.getAsJsonObject("context").get("snapshotId").getAsString());assertFalse(wire.call("gretl/validateEdit",Map.of("protocolVersion",1,"planId",planId,"expectedSnapshotId",snapshot)).getAsJsonObject().get("current").getAsBoolean());
            assertEquals(source,Files.readString(job));assertFalse(Files.exists(marker));wire.stop();
        }
    }
}
