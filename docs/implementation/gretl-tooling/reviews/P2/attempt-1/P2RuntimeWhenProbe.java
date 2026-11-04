package ch.so.agi.gretl.runtime.groovy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Reviewer-owned runtime probe: is a task with when:{false} and a missing required field valid? */
public final class P2RuntimeWhenProbe {
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("p2-when-probe");
        var harness = new GroovyHarness(temp);
        var definition = harness.loadSource("when-false.groovy",
                "task('t', RequiredText, when: { false }) { }; defaults 't'");
        var outcome = harness.run(definition, Map.of());
        System.out.println("runtime.status=" + outcome.status());
        outcome.task("t").ifPresentOrElse(
                task -> System.out.println("task.present status=" + task.status() + " failure=" + task.failure().map(Object::toString).orElse("none")),
                () -> System.out.println("task.present=false"));
        System.out.println("builderCreations=" + TestGroovyOperations.BUILDER_CREATIONS.get()
                + " handlerCreations=" + TestGroovyOperations.HANDLER_CREATIONS.get()
                + " handlerCalls=" + TestGroovyOperations.HANDLER_CALLS.get());
        // Negative control: the identical task without a false condition must fail.
        var failing = harness.loadSource("no-when.groovy", "task('t', RequiredText) { }; defaults 't'");
        var failed = harness.run(failing, Map.of());
        System.out.println("control.status=" + failed.status());
        failed.task("t").flatMap(t -> t.failure()).flatMap(f -> f.diagnostic()).ifPresent(d -> System.out.println("control.code=" + d.code()));
        // Literal null assignment to the required field.
        var nullAssignment = harness.loadSource("null-field.groovy", "task('t', RequiredText) { text = null }; defaults 't'");
        var nullOutcome = harness.run(nullAssignment, Map.of());
        System.out.println("nullAssignment.status=" + nullOutcome.status());
        nullOutcome.task("t").flatMap(t -> t.failure()).flatMap(f -> f.diagnostic()).ifPresent(d -> System.out.println("nullAssignment.code=" + d.code()));
        // Parameter-driven dynamic condition using its declared default.
        var dynamic = harness.loadSource("dynamic-when.groovy",
                "parameter('flag', description: 'F', type: 'boolean', defaultValue: false); task('t', RequiredText, when: { parameter('flag') }) { }; defaults 't'");
        var dynamicOutcome = harness.run(dynamic, Map.of());
        System.out.println("dynamicWhen.status=" + dynamicOutcome.status());
        dynamicOutcome.task("t").ifPresent(t -> System.out.println("dynamicWhen.task=" + t.status()));
    }
}
