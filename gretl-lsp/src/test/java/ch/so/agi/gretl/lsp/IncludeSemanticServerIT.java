package ch.so.agi.gretl.lsp;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Standard LSP requests and custom graph/navigation against the real packaged stdio server. */
class IncludeSemanticServerIT {
    @TempDir Path root;
    IncludeMembershipServerIT helper(){var helper=new IncludeMembershipServerIT();helper.root=root;return helper;}
    Map<String,Object> position(String uri,int line,int column){return Map.of("textDocument",Map.of("uri",uri),"position",Map.of("line",line,"character",column));}
    void confirm(ServerProcessIT.Wire w,IncludeMembershipServerIT h,String entry)throws Exception{
        var update=w.notification("gretl/sourceMembershipChanged",u->!u.getAsJsonArray("members").isEmpty());
        var buffers=new ArrayList<Map<String,Object>>();
        for(var member:update.getAsJsonArray("members")){var data=new HashMap<String,Object>();data.put("documentId",member.getAsJsonObject().get("documentId").getAsString());data.put("version",null);data.put("sha256",null);buffers.add(data);}
        assertTrue(w.call("gretl/confirmSourceMembership",Map.of("protocolVersion",1,"includeToolingVersion",1,"documentId",entry,"ownerGeneration",update.get("ownerGeneration").getAsString(),"syncToken",update.get("syncToken").getAsString(),"buffers",buffers)).getAsJsonObject().get("current").getAsBoolean());
    }
    @Test void typedGzipPipelineHasCompleteGraphPreviewOriginalNavigationAndStandardQueries()throws Exception{
        var h=helper();String entry="apply from:'tasks/prepare.groovy'\napply from:'tasks/finish.groovy'\ndefaults 'finish'";
        var job=h.write("job.groovy",entry);String prepare="parameter('input',description:'Input',type:'path',defaultValue:'data/input.txt')\ntask('prepare',Gzip){dataFile=parameter('input');gzipFile=workPath('input.gz');overwrite=true}";
        var source=h.write("tasks/prepare.groovy",prepare);String finish="task('finish',Gzip,dependsOn:'prepare'){dataFile=result('prepare').file;gzipFile=workPath('final.gz');overwrite=true}";
        var target=h.write("tasks/finish.groovy",finish);h.write("data/input.txt","content");String uri=job.toUri().toString(),fragment=target.toUri().toString();
        try(var w=new ServerProcessIT.Wire(root,"--stdio")){
            h.initialize(w,true);w.opened(uri,1,entry);h.graph(w,uri);confirm(w,h,uri);
            var graph=w.call("gretl/graph",Map.of("protocolVersion",1,"documentId",uri,"previewTargets",List.of())).getAsJsonObject();var payload=graph.getAsJsonObject("payload");
            assertEquals("COMPLETE",payload.get("status").getAsString(),payload.toString());assertEquals(2,payload.getAsJsonArray("nodes").size());
            assertEquals(List.of("prepare","finish"),payload.getAsJsonObject("preview").getAsJsonArray("orderedSymbols").asList().stream().map(JsonElement::getAsString).toList());
            assertEquals(2,payload.getAsJsonArray("edges").size());
            var definition=w.call("textDocument/definition",position(fragment,0,finish.indexOf("'prepare'")+2)).getAsJsonArray();assertEquals(1,definition.size());assertEquals(source.toUri(),java.net.URI.create(definition.get(0).getAsJsonObject().get("uri").getAsString()));
            assertFalse(w.call("textDocument/hover",position(fragment,0,finish.indexOf("'prepare'")+2)).isJsonNull());
            var params=new HashMap<String,Object>(position(fragment,0,finish.indexOf("'prepare'")+2));params.put("context",Map.of("includeDeclaration",true));assertEquals(3,w.call("textDocument/references",params).getAsJsonArray().size());
            var completions=w.call("textDocument/completion",position(fragment,0,finish.indexOf("'prepare'")+2)).getAsJsonArray();assertTrue(completions.asList().stream().anyMatch(p->p.getAsJsonObject().get("label").getAsString().equals("prepare")));
            assertEquals(0,w.call("textDocument/documentSymbol",Map.of("textDocument",Map.of("uri",uri))).getAsJsonArray().size());assertEquals(1,w.call("textDocument/documentSymbol",Map.of("textDocument",Map.of("uri",fragment))).getAsJsonArray().size());
            var links=w.call("textDocument/documentLink",Map.of("textDocument",Map.of("uri",uri))).getAsJsonArray();assertEquals(2,links.size());
            var literalDef=w.call("textDocument/definition",position(uri,0,entry.indexOf("prepare")+1)).getAsJsonArray();assertEquals(source.toUri(),java.net.URI.create(literalDef.get(0).getAsJsonObject().get("uri").getAsString()));
            var node=payload.getAsJsonArray("nodes").asList().stream().map(JsonElement::getAsJsonObject).filter(n->n.get("title").getAsString().equals("finish")).findFirst().orElseThrow();
            var nav=w.call("gretl/navigation",Map.of("protocolVersion",1,"documentId",uri,"expectedSnapshotId",graph.getAsJsonObject("context").get("snapshotId").getAsString(),"elementId",node.get("id").getAsString())).getAsJsonObject();assertEquals(target.toUri(),java.net.URI.create(nav.getAsJsonObject("location").get("uri").getAsString()));assertEquals(job.toUri(),java.net.URI.create(nav.getAsJsonObject("sourceContext").get("entryDocument").getAsString()));
            assertEquals("NEEDS_OPEN_DOCUMENTS",node.getAsJsonObject("editCapabilities").getAsJsonObject("rename").get("state").getAsString());w.stop();
        }
    }
    @Test void sharedStandardQueriesRequireSelectionAndNavigationDoesNotMergeApplications()throws Exception{
        var h=helper();var settings=h.write("settings.gretl","include 'north','south'");
        for(String side:List.of("north","south"))h.write(side+"/job.groovy","apply from:'../shared/tasks.groovy'");
        String text="task('shared',Gzip){dataFile=file('input');gzipFile=workPath('shared.gz')}";var shared=h.write("shared/tasks.groovy",text);String uri=shared.toUri().toString();
        try(var w=new ServerProcessIT.Wire(root,"--stdio")){
            h.initialize(w,true);w.opened(settings.toUri().toString(),1,Files.readString(settings));var contexts=h.contexts(w,uri);
            assertEquals(1,w.call("textDocument/documentSymbol",Map.of("textDocument",Map.of("uri",uri))).getAsJsonArray().size());
            assertEquals("SOURCE_CONTEXT_SELECTION_REQUIRED",w.response("textDocument/hover",position(uri,0,8)).getAsJsonObject("error").get("message").getAsString());
            confirm(w,h,settings.toUri().toString());
            for(var choice:contexts.getAsJsonArray("contexts")){
                var c=choice.getAsJsonObject();w.call("gretl/selectSourceContext",Map.of("protocolVersion",1,"includeToolingVersion",1,"documentId",uri,"contextToken",c.get("contextToken").getAsString(),"ownerGeneration",contexts.get("ownerGeneration").getAsString()));
                assertTrue(w.call("textDocument/hover",position(uri,0,8)).toString().contains(c.get("ownerScope").getAsString()+":shared"));
                var graph=h.graph(w,uri);assertEquals("COMPLETE",graph.getAsJsonObject("payload").get("status").getAsString());
                var nav=w.call("gretl/navigation",Map.of("protocolVersion",1,"documentId",uri,"contextToken",c.get("contextToken").getAsString(),"expectedSnapshotId",graph.getAsJsonObject("context").get("snapshotId").getAsString(),"offsetUtf16",8)).getAsJsonObject();assertEquals(1,nav.getAsJsonArray("elements").size());
            }
            w.stop();
        }
    }
    @Test void dirtySharedBufferUpdatesBothOwnerGraphsAndInvalidatesTheirOldTokens()throws Exception{
        var h=helper();var settings=h.write("settings.gretl","include 'north','south'");
        for(String side:List.of("north","south"))h.write(side+"/job.groovy","apply from:'../shared/tasks.groovy'");
        String original="task('shared',Gzip){dataFile=file('input');gzipFile=workPath('shared.gz')}";
        var file=h.write("shared/tasks.groovy",original);String uri=file.toUri().toString();
        try(var w=new ServerProcessIT.Wire(root,"--stdio")){
            h.initialize(w,true);w.opened(settings.toUri().toString(),1,Files.readString(settings));var old=h.contexts(w,uri);confirm(w,h,settings.toUri().toString());
            String dirty=original.replace("'shared'","'dirty'");w.opened(uri,7,dirty);var current=h.contexts(w,uri);
            assertNotEquals(old.get("ownerGeneration"),current.get("ownerGeneration"));
            var oldContext=old.getAsJsonArray("contexts").get(0).getAsJsonObject();
            assertEquals("SOURCE_CONTEXT_STALE",w.response("gretl/graph",Map.of("protocolVersion",1,"documentId",uri,"contextToken",oldContext.get("contextToken").getAsString())).getAsJsonObject("error").get("message").getAsString());
            var update=w.notification("gretl/sourceMembershipChanged",u->u.get("ownerGeneration").equals(current.get("ownerGeneration")));
            assertTrue(w.call("gretl/confirmSourceMembership",Map.of("protocolVersion",1,"includeToolingVersion",1,"documentId",settings.toUri().toString(),"ownerGeneration",update.get("ownerGeneration").getAsString(),"syncToken",update.get("syncToken").getAsString(),"buffers",List.of(Map.of("documentId",uri,"version",7,"sha256",h.hash(file.toUri(),dirty))))).getAsJsonObject().get("current").getAsBoolean());
            for(var c:current.getAsJsonArray("contexts")){
                var graph=w.call("gretl/graph",Map.of("protocolVersion",1,"documentId",uri,"contextToken",c.getAsJsonObject().get("contextToken").getAsString())).getAsJsonObject();
                assertEquals("COMPLETE",graph.getAsJsonObject("payload").get("status").getAsString());
                var names=graph.getAsJsonObject("payload").getAsJsonArray("nodes").asList().stream().map(JsonElement::getAsJsonObject).filter(n->n.has("symbol")).map(n->n.get("title").getAsString()).toList();
                assertEquals(Set.of(":north:dirty",":south:dirty"),new HashSet<>(names));assertEquals(7,h.stamp(graph,file.toUri()).get("version").getAsInt());
            }
            assertEquals(original,Files.readString(file));w.stop();
        }
    }
    @Test void unfinishedIncludeHasSafePathCompletionWithoutACompleteDefinition()throws Exception{
        var h=helper();String text="apply from:'tasks/ne";var job=h.write("job.groovy",text);h.write("tasks/new.groovy","// fragment");h.write("tasks/new.txt","other");
        Files.createSymbolicLink(root.resolve("tasks/next.groovy"),root.resolve("tasks/new.groovy"));String uri=job.toUri().toString();
        try(var w=new ServerProcessIT.Wire(root,"--stdio")){
            h.initialize(w,true);w.opened(uri,1,text);var result=w.call("textDocument/completion",position(uri,0,text.length())).getAsJsonArray();
            assertEquals(List.of("new.groovy"),result.asList().stream().map(v->v.getAsJsonObject().get("label").getAsString()).toList());
            assertNotEquals("COMPLETE",h.graph(w,uri).getAsJsonObject("payload").get("status").getAsString());w.stop();
        }
    }
}
