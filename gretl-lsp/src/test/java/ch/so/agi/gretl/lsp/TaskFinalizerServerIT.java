package ch.so.agi.gretl.lsp;

import com.google.gson.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real stdio transport, original fragment ranges, owner edits and stale/shared rejection. */
class TaskFinalizerServerIT {
    @TempDir Path root;
    IncludeMembershipServerIT files(){var h=new IncludeMembershipServerIT();h.root=root;return h;}
    IncludeEditServerIT edits(){var h=new IncludeEditServerIT();h.root=root;return h;}
    Map<String,Object> symbol(String name){return Map.of("namespace","TASK","qualifiedName",name);}
    Map<String,Object> position(String uri,int line,int column){return Map.of("textDocument",Map.of("uri",uri),"position",Map.of("line",line,"character",column));}
    void initialize(ServerProcessIT.Wire w)throws Exception{
        var result=w.call("initialize",Map.of("rootUri",root.toUri().toString(),"initializationOptions",Map.of("trusted",true),"capabilities",Map.of("experimental",Map.of("gretlRuntime",Map.of("includeToolingVersion",1)),"workspace",Map.of("workspaceEdit",Map.of("documentChanges",true))))).getAsJsonObject();
        var features=result.getAsJsonObject("capabilities").getAsJsonObject("experimental").getAsJsonObject("gretlRuntime");assertEquals(1,features.get("protocolVersion").getAsInt());assertEquals(1,features.get("catalogSchemaVersion").getAsInt());assertEquals(2,features.get("graphSchemaVersion").getAsInt());assertEquals(1,features.get("taskFinalizersVersion").getAsInt());w.send("initialized",Map.of(),null);
    }
    void changed(ServerProcessIT.Wire w,String uri,int version,String text)throws Exception{w.send("textDocument/didChange",Map.of("textDocument",Map.of("uri",uri,"version",version),"contentChanges",List.of(Map.of("text",text))),null);}
    @Test void completeIncludeGraphPreviewNavigationQueriesAndRenameUseFinalizerReferences()throws Exception{
        var h=files();var e=edits();String source="// ä😀\r\napply from:'parts/end.groovy'\r\naction('owner',finalizedBy:'release'){}\r\ndefaults 'owner'\r\n";
        String finish="task('release',Gzip){dataFile=file('input');gzipFile=workPath('end.gz')}\r\n";
        var job=h.write("job.groovy",source);var fragment=h.write("parts/end.groovy",finish);String uri=job.toUri().toString(),furi=fragment.toUri().toString();
        try(var w=new ServerProcessIT.Wire(root,"--stdio")){
            initialize(w);w.opened(uri,1,source);w.opened(furi,2,finish);var graph=e.ready(w,h,uri);assertEquals(2,graph.getAsJsonObject("context").get("schemaVersion").getAsInt());var payload=graph.getAsJsonObject("payload");assertEquals("COMPLETE",payload.get("status").getAsString());
            var edge=payload.getAsJsonArray("edges").get(0).getAsJsonObject();assertEquals("FINALIZED_BY",edge.get("kind").getAsString());
            var nodes=payload.getAsJsonArray("nodes").asList().stream().map(JsonElement::getAsJsonObject).toList();var owner=nodes.stream().filter(n->n.get("title").getAsString().equals("owner")).findFirst().orElseThrow();var release=nodes.stream().filter(n->n.get("title").getAsString().equals("release")).findFirst().orElseThrow();assertEquals(owner.get("id"),edge.get("source"));assertEquals(release.get("id"),edge.get("target"));
            var preview=w.call("gretl/graph",Map.of("protocolVersion",1,"documentId",uri,"previewTargets",List.of("owner"))).getAsJsonObject().getAsJsonObject("payload").getAsJsonObject("preview");assertEquals(List.of("owner","release"),preview.getAsJsonArray("orderedSymbols").asList().stream().map(JsonElement::getAsString).toList());assertEquals("release",preview.getAsJsonArray("conditionalSymbols").get(0).getAsString());
            int column="action('owner',finalizedBy:'".length()+1;var pos=position(uri,2,column);
            var definitions=w.call("textDocument/definition",pos).getAsJsonArray();assertEquals(fragment.toUri(),URI.create(definitions.get(0).getAsJsonObject().get("uri").getAsString()));assertFalse(w.call("textDocument/hover",pos).isJsonNull());
            var refs=new HashMap<String,Object>(pos);refs.put("context",Map.of("includeDeclaration",true));assertEquals(2,w.call("textDocument/references",refs).getAsJsonArray().size());
            assertTrue(w.call("textDocument/completion",pos).getAsJsonArray().asList().stream().anyMatch(p->p.getAsJsonObject().get("label").getAsString().equals("release")));
            var rename=new HashMap<String,Object>(pos);rename.put("newName","closed");var edit=w.call("textDocument/rename",rename).getAsJsonObject();assertEquals(Set.of(job.toUri(),fragment.toUri()),edit.getAsJsonArray("documentChanges").asList().stream().map(v->URI.create(v.getAsJsonObject().getAsJsonObject("textDocument").get("uri").getAsString())).collect(java.util.stream.Collectors.toSet()));
            var nav=w.call("gretl/navigation",Map.of("protocolVersion",1,"documentId",uri,"expectedSnapshotId",graph.getAsJsonObject("context").get("snapshotId").getAsString(),"elementId",release.get("id").getAsString())).getAsJsonObject();assertEquals(fragment.toUri(),URI.create(nav.getAsJsonObject("location").get("uri").getAsString()));
            assertEquals(source,Files.readString(job));assertEquals(finish,Files.readString(fragment));w.stop();
        }
    }
    @Test void addRemoveAndOwnerDetailsAreNativeEditsAndLiteralChangesInvalidatePlans()throws Exception{
        var h=files();var e=edits();String owner="action('owner'){}\ndefaults 'owner'\n";String end="action('release'){}\n";
        var job=h.write("job.groovy","apply from:'end.groovy'\n"+owner);var fragment=h.write("end.groovy",end);String uri=job.toUri().toString();
        try(var w=new ServerProcessIT.Wire(root,"--stdio")){
            initialize(w);w.opened(uri,1,Files.readString(job));w.opened(fragment.toUri().toString(),2,end);var graph=e.ready(w,h,uri);
            var added=e.plan(w,uri,graph,Map.of("kind","AddDependency","producer",symbol("owner"),"target",symbol("release"),"edgeKind","FINALIZED_BY"));assertEquals("ALLOWED",added.getAsJsonObject("capability").get("state").getAsString());
            var changes=added.getAsJsonObject("edit").getAsJsonArray("documentChanges");assertEquals(1,changes.size());assertEquals(job.toUri(),URI.create(changes.get(0).getAsJsonObject().getAsJsonObject("textDocument").get("uri").getAsString()));assertTrue(changes.toString().contains("finalizedBy"));assertFalse(changes.toString().contains("after"));
            String updated="apply from:'end.groovy'\naction('owner',finalizedBy:'release'){}\ndefaults 'owner'\n";changed(w,uri,3,updated);
            assertFalse(w.call("gretl/validateEdit",Map.of("protocolVersion",1,"planId",added.get("planId").getAsString(),"expectedSnapshotId",graph.getAsJsonObject("context").get("snapshotId").getAsString())).getAsJsonObject().get("current").getAsBoolean());
            graph=e.ready(w,h,uri);var removed=e.plan(w,uri,graph,Map.of("kind","RemoveDependency","producer",symbol("owner"),"target",symbol("release"),"edgeKind","FINALIZED_BY"));assertEquals("ALLOWED",removed.getAsJsonObject("capability").get("state").getAsString());assertEquals(job.toUri(),URI.create(removed.getAsJsonObject("edit").getAsJsonArray("documentChanges").get(0).getAsJsonObject().getAsJsonObject("textDocument").get("uri").getAsString()));
            var batch=e.plan(w,uri,graph,Map.of("kind","ConfigureTask","target",symbol("owner"),"fields",Map.of(),"options",Map.of("finalizedBy",Map.of("kind","List","items",List.of()))));assertEquals("ALLOWED",batch.getAsJsonObject("capability").get("state").getAsString());
            changed(w,fragment.toUri().toString(),4,end+"// changed fragment\n");assertFalse(w.call("gretl/validateEdit",Map.of("protocolVersion",1,"planId",batch.get("planId").getAsString(),"expectedSnapshotId",graph.getAsJsonObject("context").get("snapshotId").getAsString())).getAsJsonObject().get("current").getAsBoolean());assertEquals(end,Files.readString(fragment));w.stop();
        }
    }
    @Test void sharedFinalizerOwnerCannotBeMutatedThroughGraphSetterOrBatch()throws Exception{
        var h=files();var e=edits();var settings=h.write("settings.gretl","include 'north','south'\ndefaults ':north:local'");
        for(String side:List.of("north","south"))h.write(side+"/job.groovy","apply from:'../shared.groovy'\naction('local'){}");h.write("shared.groovy","action('owner',finalizedBy:'release'){}\naction('release'){}");String uri=settings.toUri().toString();
        try(var w=new ServerProcessIT.Wire(root,"--stdio")){
            initialize(w);w.opened(uri,1,Files.readString(settings));var graph=e.ready(w,h,uri);
            for(var intent:List.of(Map.of("kind","RemoveDependency","producer",symbol(":north:owner"),"target",symbol(":north:release"),"edgeKind","FINALIZED_BY"),Map.of("kind","SetTaskOption","target",symbol(":north:owner"),"field","finalizedBy","value",Map.of("kind","Null")),Map.of("kind","ConfigureTask","target",symbol(":north:owner"),"fields",Map.of(),"options",Map.of("finalizedBy",Map.of("kind","List","items",List.of()))))){
                var denied=e.plan(w,uri,graph,intent);assertEquals("REJECTED",denied.getAsJsonObject("capability").get("state").getAsString());assertEquals("EDIT_SHARED_SOURCE",denied.getAsJsonObject("capability").getAsJsonArray("reasons").get(0).getAsJsonObject().get("code").getAsString());assertFalse(denied.has("edit"));
            }w.stop();
        }
    }
    @Test void activationErrorsPointToTheFinalizerLiteralAndDynamicLinksStayPartial()throws Exception{
        var h=files();var e=edits();String text="action('owner',finalizedBy:'release',after:'prepare'){}\naction('prepare'){}\naction('release',dependsOn:'prepare'){}\ndefaults 'owner'\n";var job=h.write("job.groovy",text);String uri=job.toUri().toString();
        try(var w=new ServerProcessIT.Wire(root,"--stdio")){
            initialize(w);w.opened(uri,1,text);var graph=e.ready(w,h,uri);assertEquals("INVALID",graph.getAsJsonObject("payload").get("status").getAsString());var d=graph.getAsJsonObject("payload").getAsJsonArray("diagnostics").asList().stream().map(JsonElement::getAsJsonObject).filter(v->v.get("code").getAsString().equals("FINALIZER_ACTIVATION_DEADLOCK")).findFirst().orElseThrow();assertEquals(text.indexOf("release"),d.getAsJsonObject("anchor").getAsJsonObject("span").get("startUtf16").getAsInt());
            changed(w,uri,2,"def name=System.getProperty('name'); action('owner',finalizedBy:name){}\n");assertEquals("PARTIAL",e.ready(w,h,uri).getAsJsonObject("payload").get("status").getAsString());w.stop();
        }
    }
}
