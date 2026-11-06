package ch.so.agi.gretl.lsp;

import com.google.gson.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real stdio membership/capture protocol. Client language transitions are additionally covered by host tests. */
class IncludeMembershipServerIT {
    @TempDir Path root;
    @Test void dirtyPreopenedFragmentRequiresConfirmedMembershipAndInvalidatesPlansAndIncarnations()throws Exception{
        var fixture = new IncludeWireFixture(root);
        String source="apply from:'parts/custom.groovy'\ntask('root',Gzip){dataFile=file('input');gzipFile=workPath('x.gz');overwrite=true}\ndefaults 'root'";
        Path job=fixture.write("job.groovy",source),fragment=fixture.write("parts/custom.groovy","task('disk',Gzip){dataFile=file('input');gzipFile=workPath('disk.gz')}");
        String uri=job.toUri().toString(),dirty="// ä😀 unsaved\r\ntask('dirty',Gzip){dataFile=file('input');gzipFile=workPath('dirty.gz')}";
        try(var w=new LspProcessHarness(root,"--stdio")){
            fixture.initialize(w,true);w.opened(uri,1,source);var disk=fixture.graph(w,uri);
            assertTrue(disk.getAsJsonObject("context").getAsJsonArray("notices").asList().stream().anyMatch(v->v.getAsString().equals("INCLUDE_MEMBERSHIP_PENDING")));
            var initial=w.notification("gretl/sourceMembershipChanged",u->!u.getAsJsonArray("members").isEmpty());
            var denied=w.response("gretl/planEdit",fixture.scalar(uri,disk.getAsJsonObject("context").get("snapshotId").getAsString()));assertTrue(denied.has("error"));assertEquals("INCLUDE_MEMBERSHIP_PENDING",denied.getAsJsonObject("error").get("message").getAsString());
            var action=w.response("textDocument/codeAction",Map.of("textDocument",Map.of("uri",uri),"range",Map.of("start",Map.of("line",0,"character",0),"end",Map.of("line",1,"character",0)),"context",Map.of("diagnostics",List.of())));
            assertEquals("INCLUDE_MEMBERSHIP_PENDING",action.getAsJsonObject("error").get("message").getAsString());
            // This buffer was dirty in ordinary Groovy before membership. Only now is it legitimately synchronized.
            w.send("textDocument/didOpen",Map.of("textDocument",Map.of("uri",fragment.toUri().toString(),"languageId","groovy","version",7,"text",dirty)),null);
            var current=fixture.graph(w,uri);var fragmentStamp=fixture.stamp(current,fragment.toUri());assertEquals(7,fragmentStamp.get("version").getAsInt());assertEquals(fixture.hash(fragment.toUri(),dirty),fragmentStamp.get("sha256").getAsString());
            var update=w.notification("gretl/sourceMembershipChanged",u->!u.get("ownerGeneration").equals(initial.get("ownerGeneration"))&&!u.getAsJsonArray("members").isEmpty());
            var ack=w.call("gretl/confirmSourceMembership",Map.of("protocolVersion",1,"includeToolingVersion",1,"documentId",uri,"ownerGeneration",update.get("ownerGeneration").getAsString(),"syncToken",update.get("syncToken").getAsString(),
                    "buffers",List.of(Map.of("documentId",fragment.toUri().toString(),"version",7,"sha256",fixture.hash(fragment.toUri(),dirty))))).getAsJsonObject();assertTrue(ack.get("current").getAsBoolean(),ack.toString());
            current=fixture.graph(w,uri);assertTrue(current.getAsJsonObject("context").getAsJsonArray("notices").isEmpty());String snapshot=current.getAsJsonObject("context").get("snapshotId").getAsString();
            var plan=w.call("gretl/planEdit",fixture.scalar(uri,snapshot)).getAsJsonObject();assertEquals("ALLOWED",plan.getAsJsonObject("capability").get("state").getAsString(),plan.toString());
            String planId=plan.get("planId").getAsString();var before=fixture.contexts(w,fragment.toUri().toString());
            w.send("textDocument/didClose",Map.of("textDocument",Map.of("uri",fragment.toUri().toString())),null);w.opened(fragment.toUri().toString(),7,dirty);
            var stale=w.call("gretl/validateEdit",Map.of("protocolVersion",1,"planId",planId,"expectedSnapshotId",snapshot)).getAsJsonObject();assertFalse(stale.get("current").getAsBoolean());
            var select=new HashMap<String,Object>();select.put("protocolVersion",1);select.put("includeToolingVersion",1);select.put("documentId",fragment.toUri().toString());select.put("contextToken",before.get("selectedToken").getAsString());select.put("incarnation",before.get("incarnation").getAsString());select.put("ownerGeneration",before.get("ownerGeneration").getAsString());
            assertEquals("SOURCE_CONTEXT_STALE",w.response("gretl/selectSourceContext",select).getAsJsonObject("error").get("message").getAsString());
            w.send("textDocument/didClose",Map.of("textDocument",Map.of("uri",fragment.toUri().toString())),null);String saved="task('changedOnDisk',Gzip){dataFile=file('input');gzipFile=workPath('saved.gz')}";Files.writeString(fragment,saved);
            w.send("workspace/didChangeWatchedFiles",Map.of("changes",List.of(Map.of("uri",fragment.toUri().toString(),"type",2))),null);
            assertEquals(fixture.hash(fragment.toUri(),saved),fixture.stamp(w.graphAfterRefresh(uri),fragment.toUri()).get("sha256").getAsString());assertEquals(source,Files.readString(job));assertEquals(saved,Files.readString(fragment));w.stop();
        }
    }
    @Test void unownedSourceIsNotAnImplicitEntryAndFeatureNegotiationIsRequired()throws Exception{
        var fixture = new IncludeWireFixture(root);
        Path job=fixture.write("job.groovy","task('root',Gzip){dataFile=file('input');gzipFile=workPath('x.gz')}"),unowned=fixture.write("unrelated.groovy","task('unowned',Gzip){dataFile=file('input');gzipFile=workPath('x.gz')}");
        try(var w=new LspProcessHarness(root,"--stdio")){
            fixture.initialize(w,true);w.opened(job.toUri().toString(),1,Files.readString(job));w.opened(unowned.toUri().toString(),1,Files.readString(unowned));
            assertTrue(fixture.contexts(w,unowned.toUri().toString()).getAsJsonArray("contexts").isEmpty());var graph=fixture.graph(w,unowned.toUri().toString());assertEquals("PARTIAL",graph.getAsJsonObject("payload").get("status").getAsString());
            assertFalse(graph.getAsJsonObject("payload").getAsJsonArray("nodes").asList().stream().anyMatch(n->n.getAsJsonObject().get("title").getAsString().equals("unowned")));
            var registered=w.call("gretl/sourceContexts",Map.of("protocolVersion",1,"includeToolingVersion",1,"documentId",unowned.toUri().toString(),"registerAsEntry",true)).getAsJsonObject();assertEquals(1,registered.getAsJsonArray("contexts").size());
            assertEquals("COMPLETE",fixture.graph(w,unowned.toUri().toString()).getAsJsonObject("payload").get("status").getAsString());w.stop();
        }
        try(var w=new LspProcessHarness(root,"--stdio")){
            fixture.initialize(w,false);assertEquals("SOURCE_CONTEXT_CAPABILITY_REQUIRED",w.response("gretl/sourceContexts",Map.of("protocolVersion",1,"includeToolingVersion",1,"documentId",job.toUri().toString())).getAsJsonObject("error").get("message").getAsString());w.stop();
        }
    }
    @Test void sharedContextSelectionIsExplicitAndPinnedQueriesDoNotChangeTheEditorSelection()throws Exception{
        var fixture = new IncludeWireFixture(root);
        Path settings=fixture.write("settings.gretl","include 'north','south'");
        for(String scope:List.of("north","south"))fixture.write(scope+"/job.groovy","apply from:'../shared/tasks.groovy'");
        Path fragment=fixture.write("shared/tasks.groovy","task('shared',Gzip){dataFile=file('input');gzipFile=workPath('shared.gz')}");String uri=fragment.toUri().toString();
        try(var w=new LspProcessHarness(root,"--stdio")){
            fixture.initialize(w,true);w.opened(settings.toUri().toString(),1,Files.readString(settings));var choices=fixture.contexts(w,uri);
            assertTrue(choices.get("selectionRequired").getAsBoolean());assertEquals(2,choices.getAsJsonArray("contexts").size());
            var first=choices.getAsJsonArray("contexts").get(0).getAsJsonObject();var second=choices.getAsJsonArray("contexts").get(1).getAsJsonObject();
            assertNotEquals(first.get("resourceBase"),second.get("resourceBase"));
            assertEquals("SOURCE_CONTEXT_SELECTION_REQUIRED",w.response("gretl/graph",Map.of("protocolVersion",1,"documentId",uri)).getAsJsonObject("error").get("message").getAsString());
            w.call("gretl/selectSourceContext",Map.of("protocolVersion",1,"includeToolingVersion",1,"documentId",uri,"contextToken",first.get("contextToken").getAsString(),"ownerGeneration",choices.get("ownerGeneration").getAsString()));
            w.call("gretl/graph",Map.of("protocolVersion",1,"documentId",uri,"contextToken",second.get("contextToken").getAsString()));
            assertEquals(first.get("contextToken"),fixture.contexts(w,uri).get("selectedToken"));
            var standalone=w.call("gretl/sourceContexts",Map.of("protocolVersion",1,"includeToolingVersion",1,"documentId",uri,"registerAsEntry",true)).getAsJsonObject();
            String selected=standalone.get("selectedToken").getAsString();
            var chosen=standalone.getAsJsonArray("contexts").asList().stream().map(JsonElement::getAsJsonObject).filter(c->c.get("contextToken").getAsString().equals(selected)).findFirst().orElseThrow();
            assertEquals("ENTRY",chosen.get("role").getAsString());assertEquals(fragment.toUri(),URI.create(chosen.get("entryDocument").getAsString()));w.stop();
        }
    }

    @Test void settingsAnchorKeepsItsCompleteProjectWithAndWithoutIncludeCapability()throws Exception{
        var fixture = new IncludeWireFixture(root);
        Path settings=fixture.write("settings.gretl","include 'north'\ndefaults ':north:north'");
        fixture.write("north/job.groovy","task('north',Gzip){dataFile=file('input');gzipFile=workPath('north.gz')}");
        for(boolean feature:List.of(false,true))try(var w=new LspProcessHarness(root,"--stdio")){
            fixture.initialize(w,feature);w.opened(settings.toUri().toString(),1,Files.readString(settings));
            var graph=fixture.graph(w,settings.toUri().toString());assertEquals("COMPLETE",graph.getAsJsonObject("payload").get("status").getAsString());
            assertTrue(graph.getAsJsonObject("payload").getAsJsonArray("nodes").asList().stream().anyMatch(n->n.getAsJsonObject().get("title").getAsString().equals(":north:north")));
            if(feature){var contexts=fixture.contexts(w,settings.toUri().toString());assertEquals(1,contexts.getAsJsonArray("contexts").size());
                var context=contexts.getAsJsonArray("contexts").get(0).getAsJsonObject();assertEquals(settings.toUri(),URI.create(context.get("entryDocument").getAsString()));assertEquals("ENTRY",context.get("role").getAsString());}
            w.stop();
        }
    }
    @Test void unrelatedGroovyFileeventsDoNotInvalidateOrParseTheProjectAgain()throws Exception{
        var fixture = new IncludeWireFixture(root);
        Path job=fixture.write("job.groovy","task('root',Gzip){dataFile=file('input');gzipFile=workPath('out.gz')}");
        Path unrelated=fixture.write("unrelated.groovy","this is unrelated invalid Groovy {");
        try(var w=new LspProcessHarness(root,"--stdio")){
            fixture.initialize(w,true);w.opened(job.toUri().toString(),1,Files.readString(job));var before=fixture.graph(w,job.toUri().toString());
            Files.writeString(unrelated,"changed but still unrelated {");
            w.send("workspace/didChangeWatchedFiles",Map.of("changes",List.of(Map.of("uri",unrelated.toUri().toString(),"type",2))),null);
            var after=fixture.graph(w,job.toUri().toString());assertEquals(before.getAsJsonObject("context").get("snapshotId"),after.getAsJsonObject("context").get("snapshotId"));w.stop();
        }
    }

    @Test void explicitlyCreatedUnsavedJobRetainsItsEntryRole()throws Exception{
        var fixture = new IncludeWireFixture(root);
        fixture.write("job.groovy","// existing workspace");String uri="untitled:Draft-1";
        String source="task('draft',Gzip){dataFile=file('input');gzipFile=workPath('draft.gz')}";
        try(var w=new LspProcessHarness(root,"--stdio")){
            fixture.initialize(w,true);w.opened(uri,1,source);var registered=w.call("gretl/sourceContexts",Map.of("protocolVersion",1,"includeToolingVersion",1,"documentId",uri,"registerAsEntry",true)).getAsJsonObject();
            assertEquals(1,registered.getAsJsonArray("contexts").size());assertTrue(fixture.graph(w,uri).getAsJsonObject("payload").getAsJsonArray("nodes").asList().stream().anyMatch(n->n.getAsJsonObject().get("title").getAsString().equals("draft")));w.stop();
        }
    }

}
