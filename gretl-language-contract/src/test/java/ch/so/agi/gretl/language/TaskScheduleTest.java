package ch.so.agi.gretl.language;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ch.so.agi.gretl.language.PlanSemantics.*;
import static ch.so.agi.gretl.language.TaskSchedule.*;

class TaskScheduleTest {
    private final PlanSemantics semantics = new PlanSemantics();
    private DeclaredGraph graph(String[] ids, Edge... edges) {
        var nodes = new ArrayList<Node>();
        for (int i=0;i<ids.length;i++) nodes.add(new Node(ids[i],i));
        return new DeclaredGraph(nodes,List.of(edges));
    }
    private Edge fin(String a,String f) { return new Edge(a,f,EdgeKind.FINALIZED_BY); }
    private Edge dep(String p,String c) { return new Edge(p,c,EdgeKind.DEPENDS_ON); }
    private Edge after(String p,String c) { return new Edge(p,c,EdgeKind.AFTER); }
    private PlanSelection plan(DeclaredGraph g,String... targets) { return semantics.plan(g,List.of(targets),List.of()); }
    private TaskSchedule schedule(DeclaredGraph g,String... targets) { return new TaskSchedule(plan(g,targets)); }
    private void run(TaskSchedule s,String id,Terminal status) {
        assertEquals(new Execute(id),s.next()); assertEquals(s.next(),s.next());
        s.started(id); s.completed(id,status);
    }
    private void notRun(TaskSchedule s,String id,String reason) {
        assertEquals(new NotRun(id,reason),s.next());s.completed(id,Terminal.NOT_RUN);
    }
    private List<String> drain(TaskSchedule s) {
        var trace=new ArrayList<String>();
        for(int i=0;i<100;i++) {
            var d=s.next();
            if(d instanceof Done) return trace;
            if(d instanceof Execute e) { trace.add(e.taskId());s.started(e.taskId());s.completed(e.taskId(),Terminal.SUCCEEDED); }
            else if(d instanceof NotRun n) s.completed(n.taskId(),Terminal.NOT_RUN);
        }
        fail("No progress");return trace;
    }
    @Test void candidatesAreFixedPointAndOwnerSelectionIsNotReversed() {
        var g=graph(new String[]{"a","unselected","d","f","g"},fin("a","f"),fin("unselected","f"),dep("d","f"),fin("d","g"),fin("f","g"));
        var p=plan(g,"a");assertEquals(List.of("a","d","f","g"),p.orderedIds());
        assertEquals(Set.of("a"),p.normalDemand());assertFalse(p.orderedIds().contains("unselected"));
        assertEquals(Set.of("d"),p.dataPredecessors().get("f"));assertTrue(p.dataPredecessors().get("g").isEmpty());
        assertEquals(List.of("d","f","g"),plan(g,"f").orderedIds());
        assertEquals(List.of("g"),plan(g,"g").orderedIds());
    }
    @Test void sharedFinalizerWaitsForSelectedOwnersAndRunsOnceAfterFailure() {
        var g=graph(new String[]{"a","f","b","outside"},fin("a","f"),fin("b","f"),fin("outside","f"));
        var s=schedule(g,"a","b");run(s,"a",Terminal.FAILED);s.stopNormalWork("FAIL_FAST");
        notRun(s,"b","FAIL_FAST");run(s,"f",Terminal.SUCCEEDED);
        assertEquals(Set.of("a"),s.activatingOwners("f"));assertEquals(new Done(),s.next());
        assertThrows(IllegalStateException.class,()->s.started("f"));
    }
    @Test void notRunOwnerDoesNotActivateButReachedSkippedDoes() {
        var g=graph(new String[]{"p","a","f"},dep("p","a"),fin("a","f"));
        var s=schedule(g,"a");run(s,"p",Terminal.FAILED);s.stopNormalWork("FAIL_FAST");
        notRun(s,"a","FAIL_FAST");notRun(s,"f","FINALIZER_NOT_TRIGGERED");assertEquals(new Done(),s.next());
        assertTrue(s.activatingOwners("f").isEmpty());
        var skipped=schedule(g,"a");run(skipped,"p",Terminal.SUCCEEDED);run(skipped,"a",Terminal.SKIPPED);run(skipped,"f",Terminal.SUCCEEDED);
    }
    @Test void finalizerPrerequisitesAreLazyAndChainCanStillNeedNormalPendingTask() {
        var g=graph(new String[]{"d","a","f","g","ordinary"},fin("a","f"),fin("f","g"),dep("d","g"));
        var s=schedule(g,"a","ordinary");run(s,"a",Terminal.FAILED);s.stopNormalWork("FAIL_FAST");
        // NOT_RUN normal work may be emitted before execution, but D must remain possible.
        var executed=drain(s);assertEquals(List.of("f","d","g"),executed);
        assertEquals(Terminal.NOT_RUN,s.terminals().get("ordinary"));
        assertEquals(Set.of("f"),s.activatingOwners("g"));
    }
    @Test void stoppedNormalPrerequisiteRemainsAvailableForLaterFinalizerChain() {
        var g=graph(new String[]{"a","d","f","g","ordinary"},fin("a","f"),fin("f","g"),dep("d","g"));
        var s=schedule(g,"a","d","ordinary");run(s,"a",Terminal.FAILED);s.stopNormalWork("FAIL_FAST");
        assertEquals(List.of("f","d","g"),drain(s));
        assertEquals(Terminal.SUCCEEDED,s.terminals().get("d"));
        assertEquals(Terminal.NOT_RUN,s.terminals().get("ordinary"));
    }
    @Test void directTargetAndDataPrerequisiteRolesRespectOwnerBarrier() {
        var g=graph(new String[]{"f","a","consumer"},fin("a","f"),dep("f","consumer"));
        assertEquals(List.of("a","f","consumer"),plan(g,"a","consumer","f").orderedIds());
        var s=schedule(g,"a","consumer","f");run(s,"a",Terminal.SUCCEEDED);assertEquals(List.of("f","consumer"),drain(s));
        assertEquals(List.of("f"),plan(g,"f").orderedIds());
    }
    @Test void listOrderAddsNoEdgeAndAfterDoesNotRequireSuccess() {
        var g=graph(new String[]{"a","g","f"},fin("a","f"),fin("a","g"));
        assertEquals(List.of("a","g","f"),plan(g,"a").orderedIds());
        var s=schedule(graph(new String[]{"a","f","g"},fin("a","f"),fin("a","g"),after("f","g")),"a");
        run(s,"a",Terminal.SUCCEEDED);run(s,"f",Terminal.FAILED);s.stopNormalWork("FAIL_FAST");run(s,"g",Terminal.SUCCEEDED);
    }
    @Test void explicitOwnerDataDependencyBlocksFailedFinalizer() {
        var s=schedule(graph(new String[]{"a","f"},fin("a","f"),dep("a","f")),"a");
        run(s,"a",Terminal.FAILED);s.stopNormalWork("FAIL_FAST");notRun(s,"f","DEPENDENCY_FAILED");assertEquals(new Done(),s.next());
    }
    @Test void combinedCyclesAndAcyclicActivationDeadlockAreRejected() {
        var cycle=assertThrows(GraphException.class,()->plan(graph(new String[]{"a","f"},fin("a","f"),after("f","a")),"a"));
        assertEquals("PLAN_CYCLE",cycle.code());assertTrue(cycle.getMessage().contains("FINALIZED_BY"));
        var deadlock=assertThrows(GraphException.class,()->plan(graph(new String[]{"a","d","f"},after("d","a"),fin("a","f"),dep("d","f")),"a"));
        assertEquals("FINALIZER_ACTIVATION_DEADLOCK",deadlock.code());assertEquals(3,deadlock.problem().edges().size());
        // Positive control: independent normal demand for D removes the activation deadlock.
        assertEquals(List.of("d","a","f"),plan(graph(new String[]{"a","d","f"},after("d","a"),fin("a","f"),dep("d","f")),"a","d").orderedIds());
    }
    @Test void executorCanRejectReadyTaskBeforeStartWithoutTriggeringItsFinalizer() {
        var s=schedule(graph(new String[]{"a","f"},fin("a","f")),"a");
        s.preventStart("a","CANCELLED_BEFORE_START");
        notRun(s,"a","CANCELLED_BEFORE_START");notRun(s,"f","FINALIZER_NOT_TRIGGERED");
        assertTrue(s.activatingOwners("f").isEmpty());assertEquals(new Done(),s.next());
    }
    @Test void completionStopTerminalizesWithoutInventedActivation() {
        var s=schedule(graph(new String[]{"a","f","g"},fin("a","f"),fin("f","g")),"a");
        run(s,"a",Terminal.CANCELLED);s.stopNormalWork("CANCELLED_BEFORE_START");s.stopCompletionWork("FINALIZATION_BUDGET_EXHAUSTED");
        notRun(s,"f","FINALIZATION_BUDGET_EXHAUSTED");notRun(s,"g","FINALIZATION_BUDGET_EXHAUSTED");
        assertTrue(s.activatingOwners("g").isEmpty());assertEquals(new Done(),s.next());
    }
    @Test void legacySchedulingPreservesRegistrationTieBreakAndFailFastReasons() {
        var g=graph(new String[]{"z","a","b","c"},dep("a","z"),after("b","c"));
        var p=plan(g,"z","b","c");
        assertEquals(List.of("a","z","b","c"),p.orderedIds());
        assertEquals(p.orderedIds(),drain(new TaskSchedule(p)));
        var s=new TaskSchedule(p);run(s,"a",Terminal.FAILED);s.stopNormalWork("FAIL_FAST");
        notRun(s,"z","FAIL_FAST");notRun(s,"b","FAIL_FAST");notRun(s,"c","FAIL_FAST");
        assertEquals(new Done(),s.next());
    }
    @Test void blockedFutureFinalizerCannotKeepUselessPrerequisitesPending() {
        var g=graph(new String[]{"a","before","d","unused","f","last"},
                fin("a","f"),dep("a","last"),after("before","d"),dep("before","unused"),
                dep("d","f"),dep("unused","last"),fin("f","last"));
        var s=schedule(g,"a","last");run(s,"a",Terminal.FAILED);s.stopNormalWork("FAIL_FAST");
        assertEquals(List.of("d","f"),drain(s));
        assertEquals(Terminal.NOT_RUN,s.terminals().get("before"));
        assertEquals(Terminal.NOT_RUN,s.terminals().get("last"));
        assertEquals(Set.of("f"),s.activatingOwners("last"));
    }
    @Test void boundedGeneratedDagTracesRespectEveryRelationAndStartAtMostOnce() {
        var random=new Random(70931);
        for(int sample=0;sample<150;sample++) {
            var nodes=new ArrayList<Node>();for(int i=0;i<7;i++)nodes.add(new Node("n"+i,i));
            var edges=new ArrayList<Edge>();
            for(int a=0;a<7;a++)for(int b=a+1;b<7;b++)if(random.nextInt(5)==0)edges.add(new Edge("n"+a,"n"+b,EdgeKind.values()[random.nextInt(3)]));
            var graph=new DeclaredGraph(nodes,edges);
            PlanSelection p;
            try { p=plan(graph,"n0","n6"); } catch(GraphException e) { assertEquals("FINALIZER_ACTIVATION_DEADLOCK",e.code());continue; }
            var s=new TaskSchedule(p);var trace=new ArrayList<String>();boolean stopped=false;
            for(int i=0;i<=14;i++) {
                var d=s.next();if(d instanceof Done)break;
                if(d instanceof NotRun n)s.completed(n.taskId(),Terminal.NOT_RUN);
                else if(d instanceof Execute e) {
                    assertFalse(trace.contains(e.taskId()));
                    for(var edge:p.activeEdges())if(edge.toId().equals(e.taskId())) {
                        assertTrue(s.terminals().containsKey(edge.fromId()),edge.toString());
                        if(edge.kind()==EdgeKind.DEPENDS_ON)assertTrue(Set.of(Terminal.SUCCEEDED,Terminal.SKIPPED).contains(s.terminals().get(edge.fromId())));
                    }
                    trace.add(e.taskId());s.started(e.taskId());var status=Terminal.values()[random.nextInt(4)];s.completed(e.taskId(),status);
                    if(!stopped&&(status==Terminal.FAILED||status==Terminal.CANCELLED)) {s.stopNormalWork("FAIL_FAST");stopped=true;}
                }
            }
            assertEquals(new Done(),s.next());assertEquals(new HashSet<>(p.orderedIds()),s.terminals().keySet());
            for(String id:trace) if(!p.normalDemand().contains(id)&&p.activeEdges().stream().anyMatch(e->e.kind()==EdgeKind.FINALIZED_BY&&e.toId().equals(id)))assertFalse(s.activatingOwners(id).isEmpty());
        }
    }
}
