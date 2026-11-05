package ch.so.agi.gretl.lsp;

import com.google.gson.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual stdio edit routes: positive cross-file native/custom plans and closed-owner counterexamples. */
class IncludeEditServerIT {
    @TempDir Path root;
    IncludeMembershipServerIT helper(){var h=new IncludeMembershipServerIT();h.root=root;return h;}
    void initialize(ServerProcessIT.Wire w)throws Exception{
        w.call("initialize",Map.of("rootUri",root.toUri().toString(),"initializationOptions",Map.of("trusted",true),"capabilities",Map.of("experimental",Map.of("gretlRuntime",Map.of("includeToolingVersion",1)),"workspace",Map.of("workspaceEdit",Map.of("documentChanges",true)),"textDocument",Map.of("codeAction",Map.of("codeActionLiteralSupport",Map.of("codeActionKind",Map.of("valueSet",List.of("quickfix"))))))));w.send("initialized",Map.of(),null);
    }
    Map<String,Object> symbol(String name){return Map.of("namespace","TASK","qualifiedName",name);}
    Map<String,Object> position(String uri,int line,int column){return Map.of("textDocument",Map.of("uri",uri),"position",Map.of("line",line,"character",column));}
    JsonObject ready(ServerProcessIT.Wire w,IncludeMembershipServerIT h,String uri)throws Exception{
        var graph=h.graph(w,uri);String generation=graph.getAsJsonObject("context").get("ownerGeneration").getAsString();
        var notice=w.notification("gretl/sourceMembershipChanged",n->n.get("ownerGeneration").getAsString().equals(generation));
        var buffers=new ArrayList<Map<String,Object>>();
        for(var member:notice.getAsJsonArray("members")){
            String id=member.getAsJsonObject().get("documentId").getAsString();var stamp=h.stamp(graph,URI.create(id));var buffer=new HashMap<String,Object>();buffer.put("documentId",id);
            buffer.put("version",stamp.has("version")?stamp.get("version").getAsInt():null);buffer.put("sha256",stamp.has("version")?stamp.get("sha256").getAsString():null);buffers.add(buffer);
        }
        assertTrue(w.call("gretl/confirmSourceMembership",Map.of("protocolVersion",1,"includeToolingVersion",1,"documentId",uri,"ownerGeneration",generation,"syncToken",notice.get("syncToken").getAsString(),"buffers",buffers)).getAsJsonObject().get("current").getAsBoolean());return h.graph(w,uri);
    }
    JsonObject plan(ServerProcessIT.Wire w,String uri,JsonObject graph,Map<String,Object> intent)throws Exception{return w.call("gretl/planEdit",Map.of("protocolVersion",1,"documentId",uri,"expectedSnapshotId",graph.getAsJsonObject("context").get("snapshotId").getAsString(),"intent",intent,"requireOpenDocuments",false)).getAsJsonObject();}
    @Test void nativeTaskParameterRenameAndCustomFragmentDefaultsAndBindingAreActuallyPositive()throws Exception{
        var h=helper();String entry="// entry 😀\r\napply from:'tasks/source.groovy'\r\napply from:'tasks/use.groovy'\r\ndefaults 'source','use'\r\n";
        String producer="parameter('path',description:'Path',type:'path',defaultValue:'data/input.txt')\r\ntask('source',Gzip){dataFile=parameter('path');gzipFile=workPath('source.gz')}\r\n";
        String consumer="// source remains comment\r\ntask('use',Gzip,dependsOn:'source',after:'source'){dataFile=result('source').file;gzipFile=workPath('use.gz')}\r\n";
        var job=h.write("job.groovy",entry);var source=h.write("tasks/source.groovy",producer);var use=h.write("tasks/use.groovy",consumer);String uri=job.toUri().toString();
        try(var w=new ServerProcessIT.Wire(root,"--stdio")){
            initialize(w);w.opened(uri,1,entry);w.opened(source.toUri().toString(),3,producer);w.opened(use.toUri().toString(),5,consumer);var graph=ready(w,h,uri);assertEquals("COMPLETE",graph.getAsJsonObject("payload").get("status").getAsString());
            var rename=new HashMap<String,Object>(position(source.toUri().toString(),1,8));rename.put("newName","renamedSource");var edit=w.call("textDocument/rename",rename).getAsJsonObject();
            assertEquals(3,edit.getAsJsonArray("documentChanges").size());assertEquals(Set.of(job.toUri(),source.toUri(),use.toUri()),edit.getAsJsonArray("documentChanges").asList().stream().map(v->URI.create(v.getAsJsonObject().getAsJsonObject("textDocument").get("uri").getAsString())).collect(java.util.stream.Collectors.toSet()));
            assertTrue(edit.getAsJsonArray("documentChanges").asList().stream().allMatch(v->v.getAsJsonObject().getAsJsonObject("textDocument").has("version")));
            var parameter=new HashMap<String,Object>(position(source.toUri().toString(),0,13));parameter.put("newName","inputPath");assertFalse(w.call("textDocument/rename",parameter).getAsJsonObject().getAsJsonArray("documentChanges").isEmpty());
            var binding=plan(w,uri,graph,Map.of("kind","BindResult","producer",symbol("source"),"target",symbol("use"),"outputField","file","field","dataFile"));assertEquals("ALLOWED",binding.getAsJsonObject("capability").get("state").getAsString());
            var fragmentGraph=h.graph(w,use.toUri().toString());var defaults=plan(w,use.toUri().toString(),fragmentGraph,Map.of("kind","SetDefaults","targets",List.of(symbol("use"))));assertEquals("ALLOWED",defaults.getAsJsonObject("capability").get("state").getAsString(),defaults.toString());assertEquals(job.toUri(),URI.create(defaults.getAsJsonObject("edit").getAsJsonArray("documentChanges").get(0).getAsJsonObject().getAsJsonObject("textDocument").get("uri").getAsString()));
            var form=plan(w,uri,graph,Map.of("kind","ConfigureTask","target",symbol("use"),"fields",Map.of("overwrite",Map.of("kind","Boolean","scalar","false")),"options",Map.of("description",Map.of("kind","Text","scalar","Changed"))));assertEquals("ALLOWED",form.getAsJsonObject("capability").get("state").getAsString());
            assertTrue(w.call("gretl/validateEdit",Map.of("protocolVersion",1,"planId",form.get("planId").getAsString(),"expectedSnapshotId",graph.getAsJsonObject("context").get("snapshotId").getAsString())).getAsJsonObject().get("current").getAsBoolean());
            assertEquals(entry,Files.readString(job));assertEquals(producer,Files.readString(source));assertEquals(consumer,Files.readString(use));w.stop();
        }
    }
    @Test void newUnopenedOwnerInvalidatesAnExistingPlanDespiteUnchangedTargetHash()throws Exception{
        var h=helper();var job=h.write("job.groovy","apply from:'child/shared.groovy'");var shared=h.write("child/shared.groovy","task('shared',Gzip){dataFile=file('input');gzipFile=workPath('shared.gz')}");String uri=job.toUri().toString();
        try(var w=new ServerProcessIT.Wire(root,"--stdio")){
            initialize(w);w.opened(uri,1,Files.readString(job));var before=ready(w,h,uri);String hash=h.stamp(before,shared.toUri()).get("sha256").getAsString();
            var planned=plan(w,uri,before,Map.of("kind","SetField","target",symbol("shared"),"field","overwrite","value",Map.of("kind","Boolean","scalar","false")));assertEquals("ALLOWED",planned.getAsJsonObject("capability").get("state").getAsString());
            h.write("child/job.groovy","apply from:'shared.groovy'");
            assertFalse(w.call("gretl/validateEdit",Map.of("protocolVersion",1,"planId",planned.get("planId").getAsString(),"expectedSnapshotId",before.getAsJsonObject("context").get("snapshotId").getAsString())).getAsJsonObject().get("current").getAsBoolean());
            var current=ready(w,h,uri);assertEquals(hash,h.stamp(current,shared.toUri()).get("sha256").getAsString());assertNotEquals(before.getAsJsonObject("context").get("ownerGeneration"),current.getAsJsonObject("context").get("ownerGeneration"));
            var denied=plan(w,uri,current,Map.of("kind","SetField","target",symbol("shared"),"field","overwrite","value",Map.of("kind","Boolean","scalar","false")));assertEquals("REJECTED",denied.getAsJsonObject("capability").get("state").getAsString());var reason=denied.getAsJsonObject("capability").getAsJsonArray("reasons").get(0).getAsJsonObject();assertEquals("EDIT_SHARED_SOURCE",reason.get("code").getAsString());assertEquals(2,reason.getAsJsonArray("owners").size());w.stop();
        }
    }
    @Test void selectedSharedWritesAndSharedUseFootprintsCannotBypassNativeOrFormRoutes()throws Exception{
        var h=helper();var settings=h.write("settings.gretl","include 'north','south'");var job=h.write("job.groovy","task('unique',Gzip){dataFile=file('input');gzipFile=workPath('unique.gz')}");
        for(String side:List.of("north","south"))h.write(side+"/job.groovy","apply from:'../shared/tasks.groovy'\ntask('local',Gzip){dataFile=file('input');gzipFile=workPath('local.gz')}");
        var shared=h.write("shared/tasks.groovy","task('shared',Gzip,dependsOn:':unique'){dataFile=result(':unique').file;gzipFile=workPath('shared.gz')}");String uri=job.toUri().toString();
        try(var w=new ServerProcessIT.Wire(root,"--stdio")){
            initialize(w);w.opened(uri,1,Files.readString(job));var graph=ready(w,h,uri);assertEquals("COMPLETE",graph.getAsJsonObject("payload").get("status").getAsString());
            var rename=new HashMap<String,Object>(position(uri,0,8));rename.put("newName","renamed");assertEquals("EDIT_SHARED_SOURCE",w.response("textDocument/rename",rename).getAsJsonObject("error").get("message").getAsString());
            for(var intent:List.of(Map.<String,Object>of("kind","SetField","target",symbol(":north:shared"),"field","overwrite","value",Map.of("kind","Boolean","scalar","false")),Map.<String,Object>of("kind","ConfigureTask","target",symbol(":south:shared"),"fields",Map.of("overwrite",Map.of("kind","Boolean","scalar","false")),"options",Map.of()))){var denied=plan(w,uri,graph,intent);assertEquals("REJECTED",denied.getAsJsonObject("capability").get("state").getAsString());}
            var independent=plan(w,uri,graph,Map.of("kind","SetField","target",symbol(":unique"),"field","overwrite","value",Map.of("kind","Boolean","scalar","false")));assertEquals("ALLOWED",independent.getAsJsonObject("capability").get("state").getAsString());
            var reading=plan(w,uri,graph,Map.of("kind","BindResult","producer",symbol(":north:shared"),"target",symbol(":north:local"),"outputField","file","field","dataFile"));assertEquals("ALLOWED",reading.getAsJsonObject("capability").get("state").getAsString());
            var choices=h.contexts(w,shared.toUri().toString());var first=choices.getAsJsonArray("contexts").get(0).getAsJsonObject();var second=choices.getAsJsonArray("contexts").get(1).getAsJsonObject();
            w.call("gretl/selectSourceContext",Map.of("protocolVersion",1,"includeToolingVersion",1,"documentId",shared.toUri().toString(),"contextToken",first.get("contextToken").getAsString(),"ownerGeneration",choices.get("ownerGeneration").getAsString()));
            var firstGraph=h.graph(w,shared.toUri().toString());var contextual=plan(w,shared.toUri().toString(),firstGraph,Map.of("kind","SetField","target",symbol(":unique"),"field","overwrite","value",Map.of("kind","Boolean","scalar","false")));assertEquals("ALLOWED",contextual.getAsJsonObject("capability").get("state").getAsString());
            w.call("gretl/selectSourceContext",Map.of("protocolVersion",1,"includeToolingVersion",1,"documentId",shared.toUri().toString(),"contextToken",second.get("contextToken").getAsString(),"ownerGeneration",choices.get("ownerGeneration").getAsString()));
            var secondGraph=h.graph(w,shared.toUri().toString());assertEquals(firstGraph.getAsJsonObject("context").get("ownerGeneration"),secondGraph.getAsJsonObject("context").get("ownerGeneration"));assertEquals(firstGraph.getAsJsonObject("context").get("sourceStamps"),secondGraph.getAsJsonObject("context").get("sourceStamps"));assertNotEquals(firstGraph.getAsJsonObject("context").get("contextGeneration"),secondGraph.getAsJsonObject("context").get("contextGeneration"));
            assertFalse(w.call("gretl/validateEdit",Map.of("protocolVersion",1,"planId",contextual.get("planId").getAsString(),"expectedSnapshotId",firstGraph.getAsJsonObject("context").get("snapshotId").getAsString())).getAsJsonObject().get("current").getAsBoolean());
            assertEquals(Files.readString(shared),"task('shared',Gzip,dependsOn:':unique'){dataFile=result(':unique').file;gzipFile=workPath('shared.gz')}");w.stop();
        }
    }
    @Test void nativeQuickFixWorksOnAUniqueFragmentAndDoesNotOfferASharedWrite()throws Exception{
        var h=helper();var job=h.write("job.groovy","apply from:'parts/producer.groovy'\napply from:'parts/use.groovy'");
        h.write("parts/producer.groovy","task('producer',Gzip){dataFile=file('input');gzipFile=workPath('producer.gz')}");
        String consumer="task('consumer',Gzip){dataFile=result('producer').file;gzipFile=workPath('consumer.gz')}";var use=h.write("parts/use.groovy",consumer);String uri=job.toUri().toString();
        try(var w=new ServerProcessIT.Wire(root,"--stdio")){
            initialize(w);w.opened(uri,1,Files.readString(job));w.opened(use.toUri().toString(),2,consumer);var graph=ready(w,h,uri);
            assertTrue(graph.getAsJsonObject("payload").getAsJsonArray("diagnostics").asList().stream().anyMatch(d->d.getAsJsonObject().get("code").getAsString().equals("RESULT_DEPENDENCY_REQUIRED")));
            var actions=w.call("textDocument/codeAction",Map.of("textDocument",Map.of("uri",use.toUri().toString()),"range",Map.of("start",Map.of("line",0,"character",0),"end",Map.of("line",0,"character",consumer.length())),"context",Map.of("diagnostics",List.of()))).getAsJsonArray();assertEquals(1,actions.size());
            var change=actions.get(0).getAsJsonObject().getAsJsonObject("edit").getAsJsonArray("documentChanges").get(0).getAsJsonObject();assertEquals(use.toUri(),URI.create(change.getAsJsonObject("textDocument").get("uri").getAsString()));assertEquals(2,change.getAsJsonObject("textDocument").get("version").getAsInt());w.stop();
        }
        h.write("settings.gretl","include 'north','south'");h.write("job.groovy","task('producer',Gzip){dataFile=file('input');gzipFile=workPath('producer.gz')}");
        for(String side:List.of("north","south"))h.write(side+"/job.groovy","apply from:'../shared/tasks.groovy'");
        var shared=h.write("shared/tasks.groovy",consumer.replace("'producer'","':producer'"));
        try(var w=new ServerProcessIT.Wire(root,"--stdio")){
            initialize(w);w.opened(uri,1,Files.readString(job));var graph=ready(w,h,uri);assertTrue(graph.getAsJsonObject("payload").getAsJsonArray("diagnostics").asList().stream().anyMatch(d->d.getAsJsonObject().get("code").getAsString().equals("RESULT_DEPENDENCY_REQUIRED")));
            var contexts=h.contexts(w,shared.toUri().toString());var choice=contexts.getAsJsonArray("contexts").get(0).getAsJsonObject();w.call("gretl/selectSourceContext",Map.of("protocolVersion",1,"includeToolingVersion",1,"documentId",shared.toUri().toString(),"contextToken",choice.get("contextToken").getAsString(),"ownerGeneration",contexts.get("ownerGeneration").getAsString()));
            assertEquals(0,w.call("textDocument/codeAction",Map.of("textDocument",Map.of("uri",shared.toUri().toString()),"range",Map.of("start",Map.of("line",0,"character",0),"end",Map.of("line",0,"character",consumer.length())),"context",Map.of("diagnostics",List.of()))).getAsJsonArray().size());w.stop();
        }
    }
}
