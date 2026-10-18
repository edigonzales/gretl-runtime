package ch.so.agi.gretl.runtime.runner;

/** Non-controlling sink for lifecycle and task log events. */
@FunctionalInterface
public interface RunEventSink {
    void emit(RunEvent event);

    RunEventSink NO_OP = event -> {
    };
}
