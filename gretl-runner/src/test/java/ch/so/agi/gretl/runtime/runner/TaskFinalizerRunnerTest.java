package ch.so.agi.gretl.runtime.runner;

import ch.so.agi.gretl.runtime.api.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TaskFinalizerRunnerTest {
    @TempDir Path root;
    final FakeMonotonicClock clock=new FakeMonotonicClock();
    RunControl control=new RunControl(clock,Optional.empty());
    final RecordingEventSink events=new RecordingEventSink();
    RunEventSink eventSink=events;
    final List<String> trace=new ArrayList<>();
    TaskRegistration.Builder<String> task(String id) {
        return TaskRegistration.builder(id,String.class).body(ctx->{trace.add(id);return id;});
    }
    JobOutcome run(Consumer<JobDefinitionBuilder> setup) {
        var b=new JobDefinitionBuilder(root);setup.accept(b);b.cleanup("global",ctx->trace.add("cleanup"));b.defaults("a");
        var d=b.freeze();var out=new SequentialJobRunner().run(d,new ExecutionPlanner().plan(d,List.of()),
                RunnerTestSupport.input(root,clock,eventSink).runControl(control).secretResolver(alias->"synthetic-finalizer-secret").build());
        assertTrue(out.tasks().stream().noneMatch(t->t.status()==TaskStatus.PENDING||t.status()==TaskStatus.RUNNING));return out;
    }
    void status(JobOutcome out,String id,TaskStatus expected) { assertEquals(expected,out.task(id).orElseThrow().status(),id); }
    OperationType<String,String> operation(String id,OperationHandler<String,String> handler) {
        return new OperationType<>("probe."+id,id,id,String.class,String.class,
                ()->new ConfigBuilder<>(){public String build(){return "request";}},()->handler,List.of(),List.of(),"fixture");
    }
    @Test void successRunsLazyPrerequisiteFinalizerAndCleanupAfterResourceClose() {
        var held=new AtomicReference<TaskContext>();
        var out=run(b->{b.register(task("a").finalizedBy("f").body(ctx->{held.set(ctx);ctx.resources().manage(()->trace.add("owner-close"));trace.add("a");return "value";}).build());
            b.register(task("d").build());b.register(task("f").dependsOn("d").body(ctx->{assertThrows(ContextAccessException.class,()->held.get().workPath("closed"));trace.add("f");return "done";}).build());});
        assertEquals(List.of("a","owner-close","d","f","cleanup"),trace);assertEquals(RunStatus.SUCCEEDED,out.status());
    }
    @Test void originalFailureAndAllFinalizerFailuresRemainVisibleWithoutSuppressingSiblings() {
        var out=run(b->{b.register(task("a").finalizedBy("f","g").body(ctx->{throw new IOException("owner-first");}).build());
            b.register(task("normal").build());b.defaults("normal");
            b.register(task("f").body(ctx->{trace.add("f");throw new IOException("finalizer-second");}).build());
            b.register(task("g").after("f").build());});
        assertEquals(RunStatus.FAILED,out.status());assertEquals("owner-first",out.primaryFailure().orElseThrow().message());
        assertTrue(out.secondaryFailures().stream().anyMatch(f->f.message().equals("finalizer-second")));
        assertEquals(List.of("f","g","cleanup"),trace);status(out,"normal",TaskStatus.NOT_RUN);
    }
    @Test void falseOrFailingPredicatesStillActivateFinalizers() {
        for(boolean failure:List.of(false,true)) {
            trace.clear();var out=run(b->{b.register(task("a").when(ctx->{if(failure)throw new IOException("predicate");return false;}).finalizedBy("f").build());b.register(task("f").build());});
            status(out,"a",failure?TaskStatus.FAILED:TaskStatus.SKIPPED);assertEquals(List.of("f","cleanup"),trace);
        }
    }
    @Test void unsuccessfulPrerequisitePreventsOwnerAndDoesNotTriggerFinalizer() {
        var out=run(b->{b.register(task("p").body(ctx->{throw new IOException("p");}).build());b.register(task("a").dependsOn("p").finalizedBy("f").build());b.register(task("f").build());});
        status(out,"a",TaskStatus.NOT_RUN);status(out,"f",TaskStatus.NOT_RUN);assertEquals("FINALIZER_NOT_TRIGGERED",out.task("f").orElseThrow().reason());assertEquals(List.of("cleanup"),trace);
    }
    @Test void sharedFinalizerWaitsForOwnerNotRunAndStartsExactlyOnce() {
        var out=run(b->{b.register(task("a").finalizedBy("f").body(ctx->{trace.add("a");throw new IOException("a");}).build());
            b.register(task("f").build());b.register(task("b").finalizedBy("f").build());b.defaults("b");});
        assertEquals(List.of("a","f","cleanup"),trace);status(out,"b",TaskStatus.NOT_RUN);
        assertEquals(2,events.count("gretl.task.started"));assertEquals(1,trace.stream().filter("f"::equals).count());
        assertEquals(1,events.count("gretl.finalizer.activated"));
        var started=events.events().stream().filter(e->e.eventName().equals("gretl.task.started")&&e.taskId().orElse("").equals("f")).findFirst().orElseThrow();
        assertEquals("FINALIZER",started.attributes().get("task.roles"));assertEquals("a",started.attributes().get("finalizer.active_owners"));
    }
    @Test void finalizerFailureActivatesChainAndKeepsLaterNormalPrerequisiteAvailable() {
        var out=run(b->{b.register(task("a").finalizedBy("f").body(ctx->{throw new IOException("a");}).build());
            b.register(task("d").build());b.defaults("d");b.register(task("f").finalizedBy("g").body(ctx->{trace.add("f");throw new IOException("f");}).build());b.register(task("g").dependsOn("d").build());});
        assertEquals(List.of("f","d","g","cleanup"),trace);assertEquals(RunStatus.FAILED,out.status());
        var dStart=events.events().stream().filter(e->e.eventName().equals("gretl.task.started")&&e.taskId().orElse("").equals("d")).findFirst().orElseThrow();
        assertEquals("FINALIZER_PREREQUISITE",dStart.attributes().get("task.roles"));
    }
    @Test void explicitDataDependencyBlocksFinalizerButAfterDoesNot() {
        var out=run(b->{b.register(task("a").finalizedBy("f","g").body(ctx->{throw new IOException("a");}).build());b.register(task("f").dependsOn("a").build());b.register(task("g").after("a").build());});
        status(out,"f",TaskStatus.NOT_RUN);status(out,"g",TaskStatus.SUCCEEDED);assertEquals(List.of("g","cleanup"),trace);
    }
    @Test void caughtFinishStillStopsNormalWorkAndAllowsTypedAndInnerOperations() {
        var inner=operation("inner",(r,c)->{c.cancellation().throwIfCancellationRequested();c.resources().manage(()->trace.add("invocation-close"));Files.writeString(c.workPath("marker"),r);trace.add("operation");return r;});
        var out=run(b->{b.register(task("a").finalizedBy("f").body(ctx->{try{ctx.finishJob("empty");}catch(RuntimeException ignored){}return "caught";}).build());
            b.register(task("normal").build());b.defaults("normal");
            b.register(task("f").operation(inner).body(new OperationTaskBody<>(inner,frame->{assertFalse(frame.context().cancellation().isCancellationRequested());return "typed";})).finalizedBy("g").build());
            b.register(task("g").body(ctx->ctx.call(inner,"inner")).build());});
        assertEquals(RunStatus.NO_DATA,out.status());assertEquals(Optional.of("empty"),out.finishReason());
        status(out,"a",TaskStatus.SKIPPED);status(out,"f",TaskStatus.SUCCEEDED);status(out,"g",TaskStatus.SUCCEEDED);status(out,"normal",TaskStatus.NOT_RUN);
        assertEquals(2,out.invocations().size());assertTrue(out.invocations().stream().allMatch(i->i.status()==InvocationStatus.SUCCEEDED));
        assertEquals(List.of("operation","invocation-close","operation","invocation-close","cleanup"),trace);
    }
    @Test void cancellationDuringOwnerAllowsTypedOperationWithoutChangingOriginalRunReason() {
        var op=operation("cancelFinalizer",(r,c)->{assertFalse(c.cancellation().isCancellationRequested());trace.add("typed");c.resources().manage(()->trace.add("close"));return r;});
        var out=run(b->{b.register(task("a").finalizedBy("f","g").body(ctx->{control.requestCancel(CancelReason.REQUESTED);ctx.cancellation().throwIfCancellationRequested();return null;}).build());
            b.register(task("f").operation(op).body(new OperationTaskBody<>(op,frame->"request")).build());
            b.register(task("g").body(ctx->ctx.call(op,"inner")).build());});
        assertEquals(RunStatus.CANCELLED,out.status());assertEquals(Optional.of(CancelReason.REQUESTED),out.cancelReason());status(out,"a",TaskStatus.CANCELLED);status(out,"f",TaskStatus.SUCCEEDED);
        assertEquals(InvocationStatus.SUCCEEDED,out.invocations().getFirst().status());assertEquals(2,out.invocations().size());status(out,"g",TaskStatus.SUCCEEDED);
        assertEquals(List.of("typed","close","typed","close","cleanup"),trace);
    }
    @Test void cancellationBeforeOwnerStartTriggersNothing() {
        control.requestCancel(CancelReason.REQUESTED);
        var out=run(b->{b.register(task("a").finalizedBy("f").build());b.register(task("f").build());});
        status(out,"a",TaskStatus.NOT_RUN);status(out,"f",TaskStatus.NOT_RUN);assertEquals(List.of("cleanup"),trace);assertEquals(RunStatus.CANCELLED,out.status());
    }
    @Test void localCompletionTimeoutDoesNotCancelSiblingBudget() {
        var out=run(b->{b.register(task("a").finalizedBy("f","g").body(ctx->{ctx.finishJob("empty");return null;}).build());
            b.register(task("f").timeout(Duration.ofSeconds(1)).body(ctx->{clock.advance(Duration.ofSeconds(1));ctx.cancellation().throwIfCancellationRequested();return null;}).build());b.register(task("g").build());});
        assertEquals(RunStatus.CANCELLED,out.status());status(out,"f",TaskStatus.CANCELLED);status(out,"g",TaskStatus.SUCCEEDED);assertEquals(List.of("g","cleanup"),trace);
    }
    @Test void oneCompletionBudgetIsSharedWithFinalizersAndGlobalCleanup() {
        var out=run(b->{b.register(task("a").finalizedBy("f","g").body(ctx->{ctx.finishJob("empty");return null;}).build());
            b.register(task("f").body(ctx->{clock.advance(Duration.ofSeconds(30));return "late";}).build());b.register(task("g").build());});
        assertEquals(RunStatus.CANCELLED,out.status());status(out,"f",TaskStatus.CANCELLED);status(out,"g",TaskStatus.NOT_RUN);assertEquals("FINALIZATION_BUDGET_EXHAUSTED",out.task("g").orElseThrow().reason());
        assertEquals(List.of(),trace);assertEquals(1,events.count("gretl.cleanup.started"));assertEquals(1,events.count("gretl.cleanup.failed"));
    }
    @Test void businessFailureAloneDoesNotImposeThirtySecondCompletionDeadline() {
        var out=run(b->{b.register(task("a").finalizedBy("f").body(ctx->{throw new IOException("business");}).build());b.register(task("f").body(ctx->{clock.advance(Duration.ofMinutes(1));trace.add("f");return "done";}).build());});
        assertEquals(RunStatus.FAILED,out.status());status(out,"f",TaskStatus.SUCCEEDED);assertEquals(List.of("f","cleanup"),trace);
    }
    @Test void finalizerFinishDoesNotSuppressSiblingAndLaterFailureOverridesNoData() {
        var out=run(b->{b.register(task("a").finalizedBy("f","g").build());b.register(task("f").body(ctx->{ctx.finishJob("stop");return null;}).build());b.register(task("g").body(ctx->{trace.add("g");throw new IOException("after-finish");}).build());});
        status(out,"f",TaskStatus.SKIPPED);status(out,"g",TaskStatus.FAILED);assertEquals(RunStatus.FAILED,out.status());assertEquals("after-finish",out.primaryFailure().orElseThrow().message());assertTrue(trace.contains("cleanup"));
    }
    @Test void errorAfterCancellationIsSecondaryAndOriginalErrorBeforeCancellationStaysPrimary() {
        for(boolean firstFailure:List.of(false,true)) {
            control=new RunControl(clock,Optional.empty());trace.clear();var out=run(b->{b.register(task("a").finalizedBy("f","g").body(ctx->{if(firstFailure)throw new IOException("earlier");control.requestCancel(CancelReason.REQUESTED);ctx.cancellation().throwIfCancellationRequested();return null;}).build());
                b.register(task("f").body(ctx->{if(firstFailure){control.requestCancel(CancelReason.REQUESTED);ctx.cancellation().throwIfCancellationRequested();}throw new IOException("later");}).build());b.register(task("g").build());});
            assertEquals(firstFailure?RunStatus.FAILED:RunStatus.CANCELLED,out.status());
            if(firstFailure)assertEquals("earlier",out.primaryFailure().orElseThrow().message());else assertTrue(out.secondaryFailures().stream().anyMatch(f->f.message().equals("later")));
            status(out,"g",TaskStatus.SUCCEEDED);
        }
    }
    @Test void interruptFromOwnerIsRestoredAfterFinalizersAndNewInterruptCancelsOnlyItsTask() {
        try {
            var out=run(b->{b.register(task("a").finalizedBy("f","g").body(ctx->{throw new InterruptedException("owner-interrupt");}).build());b.register(task("f").body(ctx->{assertFalse(Thread.currentThread().isInterrupted());throw new InterruptedException("new-interrupt");}).build());b.register(task("g").build());});
            assertTrue(Thread.currentThread().isInterrupted());assertEquals(RunStatus.CANCELLED,out.status());status(out,"f",TaskStatus.CANCELLED);status(out,"g",TaskStatus.SUCCEEDED);
        } finally {Thread.interrupted();}
    }
    @Test void skippedFinalizerStillActivatesItsChain() {
        var out=run(b->{b.register(task("a").finalizedBy("f").build());b.register(task("f").when(ctx->false).finalizedBy("g").build());b.register(task("g").build());});
        status(out,"f",TaskStatus.SKIPPED);status(out,"g",TaskStatus.SUCCEEDED);assertEquals(List.of("a","g","cleanup"),trace);
    }
    @Test void configurationFailureTriggersFinalizerAndRetainsInvocationPhase() {
        var op=operation("configuration",(r,c)->{throw new AssertionError("handler must not run");});
        var out=run(b->{b.register(task("a").operation(op).body(new OperationTaskBody<>(op,frame->{throw new IOException("configuration-failed");})).finalizedBy("f").build());b.register(task("f").build());});
        status(out,"a",TaskStatus.FAILED);status(out,"f",TaskStatus.SUCCEEDED);assertEquals("configuration",out.invocations().getFirst().phase());assertEquals(List.of("f","cleanup"),trace);
    }
    @Test void closeFailureTerminalizesOwnerBeforeFinalizerAndFinalizerCloseFailureDoesNotSuppressChain() {
        var out=run(b->{b.register(task("a").finalizedBy("f").body(ctx->{ctx.resources().manage(()->{trace.add("owner-close");throw new IOException("owner-close-failure");});return "a";}).build());
            b.register(task("f").finalizedBy("g").body(ctx->{assertEquals(List.of("owner-close"),trace);ctx.resources().manage(()->{trace.add("f-close");throw new IOException("f-close-failure");});return "f";}).build());b.register(task("g").build());});
        assertEquals("owner-close-failure",out.primaryFailure().orElseThrow().message());assertTrue(out.secondaryFailures().stream().anyMatch(f->f.message().equals("f-close-failure")));assertEquals(List.of("owner-close","f-close","g","cleanup"),trace);
    }
    @Test void finalizerHasOnlyItsOwnDataRightsAndClosedContextsKeepCancellationSnapshot() {
        var owner=new AtomicReference<TaskRef<String>>();var producer=new AtomicReference<TaskRef<String>>();var closed=new AtomicReference<CancellationToken>();
        var out=run(b->{producer.set(b.register(task("d").build()));owner.set(b.register(task("a").dependsOn(producer.get()).finalizedBy("f").body(ctx->{closed.set(ctx.cancellation());ctx.finishJob("done");return null;}).build()));
            b.register(task("f").dependsOn(producer.get()).body(ctx->{assertEquals("d",ctx.result(producer.get()));assertThrows(ResultAccessException.class,()->ctx.result(owner.get()));assertThrows(ResultAccessException.class,()->ctx.resultOptional(owner.get()));
                assertFalse(closed.get().isCancellationRequested());assertThrows(ContextAccessException.class,()->closed.get().throwIfCancellationRequested());trace.add("f");return "f";}).build());});
        assertEquals(RunStatus.NO_DATA,out.status());status(out,"f",TaskStatus.SUCCEEDED);assertEquals(List.of("d","f","cleanup"),trace);
    }
    @Test void severalFinalizersConsumeOneWindowAndDoNotResetIt() {
        var out=run(b->{b.register(task("a").finalizedBy("f","g","h").body(ctx->{ctx.finishJob("empty");return null;}).build());
            b.register(task("f").body(ctx->{clock.advance(Duration.ofSeconds(20));trace.add("f");return "f";}).build());
            b.register(task("g").after("f").body(ctx->{assertEquals(Optional.of(Duration.ofSeconds(10)),ctx.budget().remaining());clock.advance(Duration.ofSeconds(11));return "g";}).build());b.register(task("h").after("g").build());});
        status(out,"f",TaskStatus.SUCCEEDED);status(out,"g",TaskStatus.CANCELLED);status(out,"h",TaskStatus.NOT_RUN);assertEquals(RunStatus.CANCELLED,out.status());assertEquals(List.of("f"),trace);
    }
    @Test void runtimeoutAllowsFreshFinalizerTaskButKeepsOriginalDeadlineReason() {
        control=new RunControl(clock,Optional.of(Duration.ofSeconds(1)));
        var out=run(b->{b.register(task("a").finalizedBy("f").body(ctx->{clock.advance(Duration.ofSeconds(1));return "late";}).build());b.register(task("f").timeout(Duration.ofSeconds(2)).build());});
        assertEquals(RunStatus.CANCELLED,out.status());assertEquals(Optional.of(CancelReason.TIMEOUT),out.cancelReason());status(out,"a",TaskStatus.CANCELLED);status(out,"f",TaskStatus.SUCCEEDED);assertEquals(List.of("f","cleanup"),trace);
    }
    @Test void completionWindowStartsAtObservedFinishBeforeOwnerCloses() {
        var out=run(b->{b.register(task("a").finalizedBy("f").body(ctx->{ctx.resources().manage(()->clock.advance(Duration.ofSeconds(30)));ctx.finishJob("empty");return null;}).build());b.register(task("f").build());});
        status(out,"f",TaskStatus.NOT_RUN);assertEquals("FINALIZATION_BUDGET_EXHAUSTED",out.task("f").orElseThrow().reason());assertEquals(RunStatus.CANCELLED,out.status());assertEquals(List.of(),trace);
    }
    @Test void cleanupTimeoutAfterFinishIsCancellationRatherThanSuccessOrNoData() {
        var out=run(b->{b.register(task("a").finalizedBy("f").body(ctx->{ctx.finishJob("empty");return null;}).build());b.register(task("f").build());b.cleanup("consume",ctx->clock.advance(Duration.ofSeconds(30)));});
        assertEquals(RunStatus.CANCELLED,out.status());assertEquals(Optional.of(CancelReason.TIMEOUT),out.cancelReason());assertEquals(List.of("f","cleanup"),trace);
    }
    @Test void earlierCleanupFailureRemainsPrimaryBeforeLaterBudgetCancellation() {
        var out=run(b->{b.register(task("a").finalizedBy("f").body(ctx->{ctx.finishJob("empty");return null;}).build());b.register(task("f").build());
            b.cleanup("expire",ctx->clock.advance(Duration.ofSeconds(30)));b.cleanup("error",ctx->{throw new IOException("cleanup-first");});});
        assertEquals(RunStatus.FAILED,out.status());assertEquals("cleanup-first",out.primaryFailure().orElseThrow().message());
    }
    @Test void finalizerSecretsAndResourceScopesRemainClosedAfterRun() {
        var secret=new AtomicReference<SecretValue>();var context=new AtomicReference<TaskContext>();
        var out=run(b->{b.register(task("a").finalizedBy("f").body(ctx->{ctx.finishJob("empty");return null;}).build());
            b.register(task("f").body(ctx->{context.set(ctx);secret.set(ctx.secret("fixture"));
                assertEquals("[REDACTED]",secret.get().toString());
                ctx.resources().manage(()->{assertEquals("synthetic-finalizer-secret",secret.get().reveal());trace.add("closed");});return "f";}).build());});
        status(out,"f",TaskStatus.SUCCEEDED);assertEquals(List.of("closed","cleanup"),trace);
        assertThrows(ContextAccessException.class,()->secret.get().reveal());assertThrows(ContextAccessException.class,()->context.get().resources().manage(()->{}));
    }
    @Test void directTargetAndFinalizerAreOneTaskWithBothReportedRoles() {
        var out=run(b->{b.register(task("a").finalizedBy("f").build());b.register(task("f").build());b.defaults("f");});
        assertEquals(RunStatus.SUCCEEDED,out.status());assertEquals(List.of("a","f","cleanup"),trace);
        var started=events.events().stream().filter(e->e.eventName().equals("gretl.task.started")&&e.taskId().orElse("").equals("f")).findFirst().orElseThrow();
        assertEquals("NORMAL,FINALIZER",started.attributes().get("task.roles"));assertEquals(1,events.count("gretl.finalizer.activated"));
    }
    @Test void runDeadlineStillLimitsBusinessFailureDrainBeforeCompletionMode() {
        control=new RunControl(clock,Optional.of(Duration.ofSeconds(10)));
        var out=run(b->{b.register(task("a").finalizedBy("f","g").body(ctx->{throw new IOException("business-first");}).build());
            b.register(task("f").body(ctx->{assertEquals(Optional.of(Duration.ofSeconds(10)),ctx.budget().remaining());clock.advance(Duration.ofSeconds(11));return "late";}).build());b.register(task("g").build());});
        assertEquals(RunStatus.FAILED,out.status());assertEquals("business-first",out.primaryFailure().orElseThrow().message());status(out,"f",TaskStatus.CANCELLED);status(out,"g",TaskStatus.SUCCEEDED);
    }
    @Test void fatalFinalizerErrorEscapesAndStillRestoresPreviouslyObservedInterrupt() {
        try {
            var fatal=assertThrows(InternalError.class,()->run(b->{
                b.register(task("a").finalizedBy("f").body(ctx->{throw new InterruptedException("owner");}).build());
                b.register(task("f").body(ctx->{throw new InternalError("fatal-fixture");}).build());}));
            assertEquals("fatal-fixture",fatal.getMessage());assertTrue(Thread.currentThread().isInterrupted());assertFalse(trace.contains("cleanup"));
        } finally {Thread.interrupted();}
    }
    @Test void freshInterruptBetweenTasksCannotBeClearedAsAnOldCancellation() {
        eventSink=event->{events.emit(event);if(event.eventName().equals("gretl.finalizer.activated")&&event.attributes().get("finalizer.task").equals("f"))Thread.currentThread().interrupt();};
        try {
            var out=run(b->{b.register(task("a").finalizedBy("f","g").body(ctx->{control.requestCancel(CancelReason.REQUESTED);ctx.cancellation().throwIfCancellationRequested();return null;}).build());
                b.register(task("f").finalizedBy("h").build());b.register(task("g").build());b.register(task("h").build());});
            status(out,"f",TaskStatus.NOT_RUN);assertEquals("CANCELLED_BEFORE_START",out.task("f").orElseThrow().reason());status(out,"h",TaskStatus.NOT_RUN);assertEquals("FINALIZER_NOT_TRIGGERED",out.task("h").orElseThrow().reason());status(out,"g",TaskStatus.SUCCEEDED);
            assertEquals(Optional.of(CancelReason.REQUESTED),out.cancelReason());assertTrue(Thread.currentThread().isInterrupted());assertEquals(List.of("g","cleanup"),trace);
        }finally{Thread.interrupted();}
    }
    @Test void cancellationInTaskScopeSetupBeforeRunningDoesNotReachOwner() {
        var b=new JobDefinitionBuilder(root);b.register(task("a").timeout(Duration.ofSeconds(1)).finalizedBy("f").build());b.register(task("f").build());b.defaults("a");var d=b.freeze();
        RunEventSink sink=new RunEventSink(){
            public void emit(RunEvent event){events.emit(event);}
            public Scope openScope(ExecutionIdentity identity,Optional<String> operation){
                if(identity.taskId().orElse("").equals("a"))control.requestCancel(CancelReason.REQUESTED);
                return ()->{};
            }
        };
        var out=new SequentialJobRunner().run(d,new ExecutionPlanner().plan(d,List.of()),RunnerTestSupport.input(root,clock,sink).runControl(control).build());
        status(out,"a",TaskStatus.NOT_RUN);status(out,"f",TaskStatus.NOT_RUN);assertEquals(0,events.count("gretl.task.started"));assertTrue(trace.isEmpty());
    }
    @Test void finishInTaskScopeSetupBeforeRunningDoesNotReachOwner() {
        var b=new JobDefinitionBuilder(root);b.register(task("a").finalizedBy("f").build());b.register(task("f").build());b.defaults("a");var d=b.freeze();
        RunEventSink sink=new RunEventSink(){
            public void emit(RunEvent event){events.emit(event);}
            public Scope openScope(ExecutionIdentity identity,Optional<String> operation){
                if(identity.taskId().orElse("").equals("a"))control.requestFinish("before-start");
                return ()->{};
            }
        };
        var out=new SequentialJobRunner().run(d,new ExecutionPlanner().plan(d,List.of()),RunnerTestSupport.input(root,clock,sink).runControl(control).build());
        status(out,"a",TaskStatus.NOT_RUN);status(out,"f",TaskStatus.NOT_RUN);assertEquals(RunStatus.NO_DATA,out.status());assertEquals(Optional.of("before-start"),out.finishReason());assertEquals(0,events.count("gretl.task.started"));
    }
    @Test void localTimeoutBeforeFinalizerStartDoesNotActivateChainOrConsumeSiblingWindow() {
        var b=new JobDefinitionBuilder(root);
        b.register(task("a").finalizedBy("f","g").body(ctx->{ctx.finishJob("empty");return null;}).build());
        b.register(task("f").timeout(Duration.ofSeconds(1)).finalizedBy("h").build());b.register(task("g").build());b.register(task("h").build());b.defaults("a");var d=b.freeze();
        RunEventSink sink=new RunEventSink(){
            public void emit(RunEvent event){events.emit(event);}
            public Scope openScope(ExecutionIdentity identity,Optional<String> operation){
                if(identity.taskId().orElse("").equals("f"))clock.advance(Duration.ofSeconds(1));
                return ()->{};
            }
        };
        var out=new SequentialJobRunner().run(d,new ExecutionPlanner().plan(d,List.of()),RunnerTestSupport.input(root,clock,sink).runControl(control).build());
        status(out,"f",TaskStatus.NOT_RUN);assertEquals("TASK_TIMEOUT_BEFORE_START",out.task("f").orElseThrow().reason());status(out,"g",TaskStatus.SUCCEEDED);status(out,"h",TaskStatus.NOT_RUN);
        assertEquals(RunStatus.CANCELLED,out.status());assertEquals(Optional.of(CancelReason.TIMEOUT),out.cancelReason());assertEquals(List.of("g"),trace);
    }
    @Test void taskSetupFailureAfterStartActivatesFinalizerAndClosesNormally() {
        var out=run(b->{b.register(task("p").body(ctx->{Files.writeString(ctx.workPath("anchor").getParent().getParent().resolve("task-000001"),"blocked");return "p";}).build());
            b.register(task("a").dependsOn("p").finalizedBy("f").build());b.register(task("f").build());});
        status(out,"a",TaskStatus.FAILED);assertEquals("task-setup",out.task("a").orElseThrow().failurePhase());status(out,"f",TaskStatus.SUCCEEDED);
        assertEquals(3,events.count("gretl.task.started"));assertEquals(List.of("f","cleanup"),trace);
    }
}
