package ch.so.agi.gretl.lsp;

import ch.so.agi.gretl.tooling.source.SourceText;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source and membership operations used by the packaged include process tests. */
final class IncludeWireFixture {
    private final Path root;

    IncludeWireFixture(Path root) {
        this.root = root;
    }

    void initialize(LspProcessHarness wire,boolean feature)throws Exception{
        var capabilities=new HashMap<String,Object>();capabilities.put("workspace",Map.of("workspaceEdit",Map.of("documentChanges",true)));
        if(feature)capabilities.put("experimental",Map.of("gretlRuntime",Map.of("includeToolingVersion",1)));
        var result=wire.call("initialize",Map.of("rootUri",root.toUri().toString(),"initializationOptions",Map.of("trusted",true),"capabilities",capabilities));
        assertEquals(1,result.getAsJsonObject().getAsJsonObject("capabilities").getAsJsonObject("experimental").getAsJsonObject("gretlRuntime").get("includeToolingVersion").getAsInt());wire.send("initialized",Map.of(),null);
    }
    Path write(String name,String text)throws Exception{Path p=root.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,text);return p;}
    JsonObject graph(LspProcessHarness w,String uri)throws Exception{return w.call("gretl/graph",Map.of("protocolVersion",1,"documentId",uri)).getAsJsonObject();}
    JsonObject contexts(LspProcessHarness w,String uri)throws Exception{return w.call("gretl/sourceContexts",Map.of("protocolVersion",1,"includeToolingVersion",1,"documentId",uri)).getAsJsonObject();}
    JsonObject stamp(JsonObject graph,URI uri){return graph.getAsJsonObject("context").getAsJsonArray("sourceStamps").asList().stream().map(JsonElement::getAsJsonObject).filter(s->URI.create(s.get("documentId").getAsString()).equals(uri)).findFirst().orElseThrow();}
    String hash(URI id,String text){return new SourceText(id,text,null,"TEST",false).stamp().contentSha256();}
    Map<String,Object> scalar(String uri,String snapshot){return Map.of("protocolVersion",1,"documentId",uri,"expectedSnapshotId",snapshot,"requireOpenDocuments",true,
            "intent",Map.of("kind","SetField","target",Map.of("namespace","TASK","qualifiedName","root"),"field","overwrite","value",Map.of("kind","Boolean","scalar","false")));}
    void initializeForEdits(LspProcessHarness w)throws Exception{
        w.call("initialize",Map.of("rootUri",root.toUri().toString(),"initializationOptions",Map.of("trusted",true),"capabilities",Map.of("experimental",Map.of("gretlRuntime",Map.of("includeToolingVersion",1)),"workspace",Map.of("workspaceEdit",Map.of("documentChanges",true)),"textDocument",Map.of("codeAction",Map.of("codeActionLiteralSupport",Map.of("codeActionKind",Map.of("valueSet",List.of("quickfix"))))))));w.send("initialized",Map.of(),null);
    }
    Map<String,Object> symbol(String name){return Map.of("namespace","TASK","qualifiedName",name);}
    Map<String,Object> position(String uri,int line,int column){return Map.of("textDocument",Map.of("uri",uri),"position",Map.of("line",line,"character",column));}
    JsonObject ready(LspProcessHarness w,String uri)throws Exception{
        var graph=graph(w,uri);String generation=graph.getAsJsonObject("context").get("ownerGeneration").getAsString();
        var notice=w.notification("gretl/sourceMembershipChanged",n->n.get("ownerGeneration").getAsString().equals(generation));
        var buffers=new ArrayList<Map<String,Object>>();
        for(var member:notice.getAsJsonArray("members")){
            String id=member.getAsJsonObject().get("documentId").getAsString();var stamp=stamp(graph,URI.create(id));var buffer=new HashMap<String,Object>();buffer.put("documentId",id);
            buffer.put("version",stamp.has("version")?stamp.get("version").getAsInt():null);buffer.put("sha256",stamp.has("version")?stamp.get("sha256").getAsString():null);buffers.add(buffer);
        }
        assertTrue(w.call("gretl/confirmSourceMembership",Map.of("protocolVersion",1,"includeToolingVersion",1,"documentId",uri,"ownerGeneration",generation,"syncToken",notice.get("syncToken").getAsString(),"buffers",buffers)).getAsJsonObject().get("current").getAsBoolean());return graph(w,uri);
    }
    JsonObject plan(LspProcessHarness w,String uri,JsonObject graph,Map<String,Object> intent)throws Exception{return w.call("gretl/planEdit",Map.of("protocolVersion",1,"documentId",uri,"expectedSnapshotId",graph.getAsJsonObject("context").get("snapshotId").getAsString(),"intent",intent,"requireOpenDocuments",false)).getAsJsonObject();}
}
