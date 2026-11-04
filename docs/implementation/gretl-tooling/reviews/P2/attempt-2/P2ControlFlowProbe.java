package ch.so.agi.gretl.runtime.groovy;

import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.runner.JobOutcome;
import ch.so.agi.gretl.runtime.runner.TaskStatus;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.*;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import ch.so.agi.gretl.tooling.semantic.ProjectAnalyzer;
import ch.so.agi.gretl.tooling.semantic.SemanticModel;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Reviewer-owned counterprobe (new): does a control-flow-guarded inner invocation
 * produce a "proven" static ERROR even though the real runtime never executes it?
 * Also runs the negative control (unguarded) where the same body must fail.
 */
public final class P2ControlFlowProbe {
    static final URI ROOT = URI.create("memory:/fixture");
    static final URI JOB = URI.create("memory:/fixture/job.groovy");

    static CatalogRepository catalog() {
        var codec = new CatalogJsonCodec();
        var operations = new ArrayList<OperationDescriptor>();
        for (OperationType<?, ?> op : List.of(TestGroovyOperations.echo(), TestGroovyOperations.required())) {
            var inputs = op.inputs().stream().map(f -> new InputDescriptor(f.name(), f.description(), f.required(),
                    f.defaultValue().isPresent(), f.defaultValue().map(v -> new DataValue("Text", v.toString(), List.of(), Map.of())).orElse(null),
                    f.allowedValues(), f.example(), ValueShape.fromMetadata(f.valueType()), null, false, Map.<String, String>of())).toList();
            var outputs = op.outputs().stream().map(f -> new OutputDescriptor(f.name(), f.description(),
                    ValueShape.fromMetadata(f.valueType()), f.nullable())).toList();
            operations.add(new OperationDescriptor(op.id(), op.symbol(), op.description(), "fixture", inputs, outputs, op.example(), false));
        }
        var repository = new CatalogRepository();
        var result = repository.loadBundled(codec.encodeCanonical(codec.identify(
                new OperationCatalog(1, "probe", "1", "", List.of("fixture"), operations, "COMPLETE"))), null);
        if (result.result().state() != CatalogJsonCodec.State.AVAILABLE) throw new IllegalStateException(result.result().problems().toString());
        return repository;
    }

    static SemanticModel.Analysis analyze(String text) {
        var store = new DocumentStore();
        store.open(JOB, "gretl", 1, text);
        SourceRepository repository = new SourceRepository() {
            public SourceRead read(URI id, ReadPolicy policy, CancellationToken cancellation) { return SourceRead.unavailable("SOURCE_MISSING", id); }
            public SourceListing listProjectEntries(URI id, ScanBudget budget, CancellationToken cancellation) { throw new AssertionError("no scan"); }
        };
        try (var manager = new WorkspaceManager(store, catalog())) {
            manager.addRoot(ROOT, repository);
            var capture = manager.capture(new ProjectResolver.ProjectRequest(ROOT, JOB), CancellationToken.NONE);
            return new ProjectAnalyzer().analyze(capture, CancellationToken.NONE);
        }
    }

    static void report(String label, String text, JobOutcome outcome, boolean expectRuntimeSuccess) {
        System.out.println("=== " + label + " ===");
        System.out.println("SOURCE: " + text);
        System.out.println("runtime.status=" + outcome.status() + " (expected " + (expectRuntimeSuccess ? "SUCCEEDED" : "FAILED") + ")");
        System.out.println("runtime.counters builderCreations=" + TestGroovyOperations.BUILDER_CREATIONS.get()
                + " handlerCalls=" + TestGroovyOperations.HANDLER_CALLS.get());
        outcome.task("a").ifPresent(t -> System.out.println("runtime.task.a=" + t.status()));
        outcome.task("t").ifPresent(t -> System.out.println("runtime.task.t=" + t.status()));
        var analysis = analyze(text);
        System.out.println("capabilities=" + analysis.capabilities());
        analysis.symbols().forEach((id, symbols) -> symbols.forEach(s -> System.out.println("symbol " + id.qualifiedName() + " coverage=" + s.declaration().coverage())));
        analysis.diagnostics().forEach(d -> System.out.println("static " + d.code() + " " + d.severity() + " span=" + d.anchor().span()));
        long errors = analysis.diagnostics().stream().filter(d -> d.severity() == SemanticModel.Severity.ERROR).count();
        System.out.println("static.errorCount=" + errors);
    }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("p2-control-flow");
        var harness = new GroovyHarness(temp);

        String guardedInner = "action('a') { if (false) { ctx.run(RequiredText) { } } }; defaults 'a'";
        report("guarded-inner", guardedInner, run(harness, "guarded-inner.groovy", guardedInner), true);

        String unguardedInner = "action('a') { ctx.run(RequiredText) { } }; defaults 'a'";
        report("unguarded-control", unguardedInner, run(harness, "unguarded-inner.groovy", unguardedInner), false);

        String guardedResult = "task('p', Echo) { text='x' }; action('a') { if (false) { result('p').missing } }; defaults 'a'";
        report("guarded-result", guardedResult, run(harness, "guarded-result.groovy", guardedResult), true);

        String guardedParameter = "action('a') { if (false) { parameter('unknown') } }; defaults 'a'";
        report("guarded-parameter", guardedParameter, run(harness, "guarded-parameter.groovy", guardedParameter), true);

        String dynamicGuard = "parameter('flag',description:'F',type:'boolean',defaultValue:false); action('a') { if (parameter('flag')) { ctx.run(RequiredText) { } } }; defaults 'a'";
        report("dynamic-guard", dynamicGuard, run(harness, "dynamic-guard.groovy", dynamicGuard), true);

        String guardedTaskInner = "task('p', Echo) { text='x' }; task('t', Echo) { text='x'; if (false) { ctx.run(RequiredText) { } } }; defaults 't'";
        report("guarded-task-inner", guardedTaskInner, run(harness, "guarded-task-inner.groovy", guardedTaskInner), true);
    }

    static JobOutcome run(GroovyHarness harness, String file, String text) throws Exception {
        TestGroovyOperations.reset();
        return harness.run(harness.loadSource(file, text), Map.of());
    }
}
