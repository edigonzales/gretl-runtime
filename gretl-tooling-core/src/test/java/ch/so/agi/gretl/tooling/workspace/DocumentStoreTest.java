package ch.so.agi.gretl.tooling.workspace;

import ch.so.agi.gretl.tooling.source.*;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentStoreTest {
    @org.junit.jupiter.api.Test void readonlyBufferMetadataSurvivesIncrementalAndFullChanges() {
        var store=new DocumentStore();var id=java.net.URI.create("file:/readonly/job.groovy");
        store.open(id,"gretl",1,"action('a') {}",false);
        store.applyChanges(id,2,java.util.List.of(DocumentStore.TextChange.full("action('b') {}")));
        org.junit.jupiter.api.Assertions.assertFalse(store.get(id).orElseThrow().source().stamp().editable());
    }
    private final URI id=URI.create("memory:/job.groovy");
    @Test void allIncrementalChangesUseThePrecedingChangeAndOriginalUtf16Coordinates(){
        var store=new DocumentStore();store.open(id,"gretl",1,"😀a\r\nb");var before=store.capture();
        store.applyChanges(id,2,List.of(new DocumentStore.TextChange(new TextPosition(0,2),new TextPosition(0,3),"xy"),new DocumentStore.TextChange(new TextPosition(0,3),new TextPosition(1,1),"Z")));
        assertEquals("😀xZ",store.get(id).orElseThrow().source().text());assertEquals("😀a\r\nb",before.documents().get(id).source().text());
        assertEquals(2,store.get(id).orElseThrow().source().stamp().bufferVersion());assertEquals(1,before.documents().get(id).source().stamp().bufferVersion());
        assertThrows(UnsupportedOperationException.class,()->before.documents().clear());
    }
    @Test void invalidLaterChangeRollsBackTheWholeNotificationIncludingVersion(){
        var store=new DocumentStore();store.open(id,"gretl",5,"old");var before=store.capture();
        assertThrows(IllegalArgumentException.class,()->store.applyChanges(id,6,List.of(DocumentStore.TextChange.full("new"),new DocumentStore.TextChange(new TextPosition(9,0),new TextPosition(9,1),"bad"))));
        assertEquals(before,store.capture());assertThrows(IllegalArgumentException.class,()->store.applyChanges(id,5,List.of(DocumentStore.TextChange.full("stale"))));
        assertThrows(IllegalArgumentException.class,()->store.applyChanges(id,6,List.of(new DocumentStore.TextChange(new TextPosition(-1,0),new TextPosition(0,1),"bad"))));
        assertEquals(before,store.capture());
    }
    @Test void reopenIsANewIncarnationEvenWithTheSameVersionAndContent(){
        var store=new DocumentStore();store.open(id,"gretl",1,"same");var old=store.capture().documents().get(id);store.close(id);assertTrue(store.get(id).isEmpty());
        store.open(id,"gretl",1,"same");var next=store.get(id).orElseThrow();assertNotEquals(old.incarnation(),next.incarnation());assertEquals(old.source().stamp(),next.source().stamp());
        assertFalse(store.ifUnchanged(Map.of(id,old),uri->true,()->fail("stale publish")));
    }
    @Test void independentRootChangesDoNotInvalidateAnUnchangedCapturedProject(){
        var store=new DocumentStore();URI other=URI.create("memory:/other/job.groovy");store.open(id,"gretl",1,"a");store.open(other,"gretl",1,"b");var expected=Map.of(id,store.get(id).orElseThrow());
        store.applyChanges(other,2,List.of(DocumentStore.TextChange.full("changed")));assertTrue(store.ifUnchanged(expected,uri->uri.equals(id),()->{}));
    }
    @Test void textAndAggregateBudgetsRejectWithoutLosingPriorState(){
        var store=new DocumentStore(3,10,12);store.open(id,"gretl",1,"12345678");var before=store.capture();
        assertThrows(IllegalStateException.class,()->store.open(URI.create("memory:/second"),"gretl",1,"12345"));assertEquals(before,store.capture());
        assertThrows(IllegalArgumentException.class,()->store.applyChanges(id,2,List.of(DocumentStore.TextChange.full("x".repeat(11)))));assertEquals(before,store.capture());
        URI other=URI.create("memory:/second");store.open(other,"gretl",1,"1234");assertThrows(IllegalStateException.class,()->store.applyChanges(other,2,List.of(DocumentStore.TextChange.full("12345"))));
        assertEquals("1234",store.get(other).orElseThrow().source().text());
    }
    @Test void fileUriIdentityCanonicalizesEncodedDotSegmentsWithoutDiskReads(){
        assertEquals(URI.create("file:/root/outside.sql"),DocumentStore.identity(URI.create("file:/root/job/%2e%2e/outside.sql")));
        var policy=new SourceRepository.ReadPolicy(URI.create("file:/root/job/"),100,true);assertFalse(policy.permits(URI.create("file:/root/job/%2e%2e/outside.sql")));
    }
}
