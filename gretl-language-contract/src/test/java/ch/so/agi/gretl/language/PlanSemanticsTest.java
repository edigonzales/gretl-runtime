package ch.so.agi.gretl.language;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static ch.so.agi.gretl.language.PlanSemantics.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanSemanticsTest {
    private final PlanSemantics semantics=new PlanSemantics();
    private DeclaredGraph graph(List<Edge> edges,String... ids){var nodes=new ArrayList<Node>();for(int i=0;i<ids.length;i++)nodes.add(new Node(ids[i],i));return new DeclaredGraph(nodes,edges);}
    @Test void afterOrdersOnlySelectedNodesAndNeverGrantsDataAccess(){
        var graph=graph(List.of(new Edge("a","c",EdgeKind.DEPENDS_ON),new Edge("b","c",EdgeKind.AFTER)),"a","b","c");
        var selected=semantics.plan(graph,List.of(),List.of("c"));assertEquals(List.of("a","c"),selected.orderedIds());
        assertEquals(Set.of("a"),semantics.dataPredecessors(graph,"c"));assertEquals(1,selected.activeEdges().size());
        var all=semantics.plan(graph,List.of("c","b","c"),List.of());assertEquals(List.of("c","b"),all.requestedTargets());
        assertEquals(List.of("a","b","c"),all.orderedIds());assertEquals(Set.of("a"),all.dataPredecessors().get("c"));
        assertThrows(UnsupportedOperationException.class,()->all.dataPredecessors().get("c").add("b"));
        assertThrows(UnsupportedOperationException.class,()->all.activeEdges().clear());
    }
    @Test void mixedCycleDependsOnTheRequestedSelection(){
        var graph=graph(List.of(new Edge("b","a",EdgeKind.DEPENDS_ON),new Edge("a","b",EdgeKind.AFTER)),"a","b");
        assertTrue(semantics.validateDeclaredDependencies(graph).isEmpty());assertEquals(List.of("b"),semantics.plan(graph,null,List.of("b")).orderedIds());
        var cycle=assertThrows(GraphException.class,()->semantics.plan(graph,List.of("a"),List.of("b")));
        assertEquals("PLAN_CYCLE",cycle.code());assertEquals(2,cycle.problem().edges().size());
        assertEquals("Zyklus im ausgewaehlten Plan: a --AFTER--> b -> b --DEPENDS_ON--> a",cycle.getMessage());
        assertTrue(semantics.dataPredecessors(graph,"b").isEmpty());
    }
    @Test void completeDataCycleIsValidatedEvenOutsideTargetsAndBeforeMissingTarget(){
        var graph=graph(List.of(new Edge("b","a",EdgeKind.DEPENDS_ON),new Edge("a","b",EdgeKind.DEPENDS_ON)),"a","b","target");
        assertEquals("DEPENDENCY_CYCLE",semantics.validateDeclaredDependencies(graph).getFirst().code());
        for(var targets:List.of(List.of("target"),List.<String>of()))assertEquals("DEPENDENCY_CYCLE",assertThrows(GraphException.class,()->semantics.plan(graph,targets,List.of())).code());
    }
    @Test void unknownAfterIsInvalidEvenForAnUnselectedNode(){
        var graph=graph(List.of(new Edge("missing","b",EdgeKind.AFTER)),"a","b");
        var error=assertThrows(GraphException.class,()->semantics.plan(graph,List.of("a"),List.of()));assertEquals("UNKNOWN_TASK_REFERENCE",error.code());assertEquals("b",error.problem().taskId());
        assertEquals("MISSING_TARGET",assertThrows(GraphException.class,()->semantics.plan(graph(List.of(),"a"),List.of(),List.of())).code());
        assertEquals("INVALID_TARGET",assertThrows(GraphException.class,()->semantics.plan(graph(List.of(),"a"),Arrays.asList((String)null),List.of())).code());
    }
    @Test void thousandNodeChainUsesBoundedCallStackAndPreservesTransitiveDataHull(){
        var nodes=new ArrayList<Node>();var edges=new ArrayList<Edge>();for(int i=0;i<1000;i++){nodes.add(new Node("n"+i,i));if(i>0)edges.add(new Edge("n"+(i-1),"n"+i,EdgeKind.DEPENDS_ON));}
        var graph=new DeclaredGraph(nodes,edges);var selected=assertTimeout(Duration.ofSeconds(5),()->semantics.plan(graph,List.of("n999"),List.of()));
        assertEquals(1000,selected.orderedIds().size());assertEquals("n0",selected.orderedIds().getFirst());assertEquals("n999",selected.orderedIds().getLast());assertEquals(999,selected.dataPredecessors().get("n999").size());
    }
}
