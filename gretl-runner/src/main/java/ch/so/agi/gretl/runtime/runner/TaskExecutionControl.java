package ch.so.agi.gretl.runtime.runner;

import ch.so.agi.gretl.runtime.api.*;
import java.time.Duration;
import java.util.Optional;

/** Effective task/invocation control. Completion tasks retain run intent while
 * observing only their own cancellation/finish and the shared completion deadline.
 * A local timeout never cancels the shared budget object.
 */
final class TaskExecutionControl {
    private final RunControl run;
    private final RunControl effective;
    private final TimeBudget budget;
    private final boolean finalizersPresent;

    static TaskExecutionControl create(RunEnvironment environment, TaskDefinition<?> task, boolean completion) {
        RunControl run=environment.control();
        RunControl effective=completion ? new RunControl(environment.monotonicClock(),Optional.empty()) : run;
        TimeBudget parent=completion ? run.completionBudget() : run;
        TimeBudget budget=parent;
        if(task.timeout()!=null) {
            TimeBudget local=effective.childBudget(task.timeout());
            budget=new TimeBudget() {
                public Optional<Duration> remaining() {
                    var a=parent.remaining();var b=local.remaining();
                    if(a.isEmpty())return b;if(b.isEmpty())return a;
                    return Optional.of(a.get().compareTo(b.get())<=0?a.get():b.get());
                }
                public boolean expired() {return parent.expired()||local.expired();}
            };
        }
        return new TaskExecutionControl(run,effective,budget,environment.plan().edges().stream()
                .anyMatch(e->e.type()==PlanEdge.EdgeType.FINALIZED_BY));
    }
    static TaskExecutionControl normal(RunControl run,TimeBudget budget) {
        return new TaskExecutionControl(run,run,budget,false);
    }
    private TaskExecutionControl(RunControl run,RunControl effective,TimeBudget budget,boolean finalizersPresent) {
        this.run=run;this.effective=effective;this.budget=budget;this.finalizersPresent=finalizersPresent;
    }
    TimeBudget budget(){return budget;}
    void checkBeforeWork(){
        try {effective.checkBeforeWork(budget);}catch(TaskCancelledException cancel){observeCancel(cancel.reason());throw cancel;}
    }
    void checkCancellation(){
        try {effective.checkCancellation(budget);}catch(TaskCancelledException cancel){observeCancel(cancel.reason());throw cancel;}
    }
    void observeCancel(CancelReason reason){
        effective.observeCancel(reason);
        if(effective!=run)run.observeCancel(reason);
        if(finalizersPresent)run.completionBudget();
    }
    void requestFinish(String reason){
        run.requestFinish(reason);effective.requestFinish(reason);
        if(finalizersPresent)run.completionBudget();
    }
    boolean interrupted(){return effective.interrupted();}
    boolean cancellationObserved(){return effective.cancellationObserved();}
    Optional<CancelReason> reason(){
        var reason=effective.reason();
        return reason.isPresent()?reason:budget.expired()?Optional.of(CancelReason.TIMEOUT):Optional.empty();
    }
    ControlSnapshot snapshot(){
        var snapshot=effective.snapshot();
        return new ControlSnapshot(snapshot.cancellationReason(),snapshot.finishRequested(),snapshot.finishReason(),budget.expired());
    }
}
