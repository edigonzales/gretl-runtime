package ch.so.agi.gretl.lsp;

import ch.so.agi.gretl.tooling.workspace.ProjectResolver.ProjectId;
import java.net.URI;
import java.util.*;
import org.eclipse.lsp4j.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticPublicationStateTest {
    ProjectId project(String name){return new ProjectId(URI.create("file:/root/"),URI.create("file:/root/"+name+"/job.groovy"),false);}
    PublishDiagnosticsParams error(String uri,String code){var diagnostic=new Diagnostic(new Range(new Position(0,0),new Position(0,2)),code);diagnostic.setCode(code);var params=new PublishDiagnosticsParams(uri,List.of(diagnostic));params.setVersion(3);return params;}
    @Test void fixedOrRemovedExternalSourceClearsItsPreviousDiagnosticWithoutAnInventedRange(){
        var state=new DiagnosticPublicationState(64);String sql="file:/root/query.sql";var id=project("one");
        assertEquals(1,state.replace(id,1,List.of(error(sql,"SQL_PARAMETER_MISSING"))).changes().getFirst().getDiagnostics().size());
        var cleared=state.replace(id,2,List.of(new PublishDiagnosticsParams("file:/root/one/job.groovy",List.of()))).changes();
        assertEquals(1,cleared.size());assertEquals(sql,cleared.getFirst().getUri());assertTrue(cleared.getFirst().getDiagnostics().isEmpty());
        assertTrue(state.replace(id,2,List.of()).changes().isEmpty());
    }
    @Test void sharedResourceContributionsSurviveInvalidationOfAnotherProject(){
        var state=new DiagnosticPublicationState(64);String sql="file:/root/shared.sql";var first=project("one");var second=project("two");
        state.replace(first,1,List.of(error(sql,"FIRST")));
        var combined=state.replace(second,2,List.of(error(sql,"SECOND"))).changes().getFirst();assertEquals(2,combined.getDiagnostics().size());assertEquals(3,combined.getVersion());
        var remaining=state.invalidate(Set.of(first),(id,generation)->false).changes().getFirst();assertEquals(1,remaining.getDiagnostics().size());assertEquals("SECOND",remaining.getDiagnostics().getFirst().getMessage().getLeft());
        var cleared=state.invalidate(Set.of(second),(id,generation)->false).changes().getFirst();assertTrue(cleared.getDiagnostics().isEmpty());
    }
    @Test void aLateInvalidationDoesNotDiscardAnAlreadyPublishedCurrentGeneration(){
        var state=new DiagnosticPublicationState(64);var id=project("one");state.replace(id,9,List.of(error("file:/root/query.sql","CURRENT")));
        assertTrue(state.invalidate(Set.of(id),(project,generation)->generation==9).changes().isEmpty());
        assertTrue(state.replace(id,9,List.of(error("file:/root/query.sql","CURRENT"))).changes().isEmpty());
    }
    @Test void identicalSharedDiagnosticsRemainUntilTheirLastCurrentOwnerIsRemoved(){
        var state=new DiagnosticPublicationState(64);String sql="file:/root/shared.sql";var first=project("one");var second=project("two");
        state.replace(first,1,List.of(error(sql,"SHARED")));
        assertTrue(state.replace(second,2,List.of(error(sql,"SHARED"))).changes().isEmpty());
        assertTrue(state.invalidate(Set.of(first),(id,generation)->false).changes().isEmpty());
        var cleared=state.invalidate(Set.of(second),(id,generation)->false).changes();assertEquals(1,cleared.size());assertTrue(cleared.getFirst().getDiagnostics().isEmpty());
    }
    @Test void diagnosticDtoRetentionIsBoundedAndEvictionClearsOnlyTheEvictedContribution(){
        var state=new DiagnosticPublicationState(2);state.replace(project("one"),1,List.of(error("file:/root/one.sql","ONE")));state.replace(project("two"),2,List.of(error("file:/root/two.sql","TWO")));
        var batch=state.replace(project("three"),3,List.of(error("file:/root/three.sql","THREE")));assertTrue(batch.limited());assertEquals(2,batch.changes().size());
        assertTrue(batch.changes().stream().anyMatch(value->value.getUri().endsWith("one.sql")&&value.getDiagnostics().isEmpty()));
        assertTrue(batch.changes().stream().noneMatch(value->value.getUri().endsWith("two.sql")));
    }
}
