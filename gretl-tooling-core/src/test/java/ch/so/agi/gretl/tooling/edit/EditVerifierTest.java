package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.*;
import ch.so.agi.gretl.tooling.semantic.*;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.LiteralValue;
import ch.so.agi.gretl.tooling.workspace.*;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import static ch.so.agi.gretl.tooling.workspace.SourceRepository.*;
import static org.junit.jupiter.api.Assertions.*;

class EditVerifierTest {
    static final URI ROOT = URI.create("file:/work"), JOB = URI.create("file:/work/job.groovy"), SETTINGS = URI.create("file:/work/settings.gretl");
    static final SymbolId TASK = new SymbolId(Namespace.TASK, "a");

    static final class Fixture implements AutoCloseable {
        final Map<URI, String> files = new HashMap<>();
        final CatalogRepository catalog = new CatalogRepository();
        final DocumentStore buffers = new DocumentStore();
        final WorkspaceManager manager = new WorkspaceManager(buffers, catalog);
        boolean prohibitReads;
        Fixture() {
            files.put(JOB, "// keep\r\ntask('a', Echo) { input = 'old' }\r\ndefaults 'a'\r\n");
            loadCatalog();
            manager.addRoot(ROOT, new SourceRepository() {
                public SourceRead read(URI id, ReadPolicy policy, CancellationToken cancellation) {
                    assertFalse(prohibitReads, "Planning must not reacquire external sources");
                    String text = files.get(id);
                    return text == null ? SourceRead.unavailable("SOURCE_MISSING", id)
                            : SourceRead.found(new SourceText(id, text, null, "DISK", true, text));
                }
                public SourceListing listProjectEntries(URI root, ScanBudget budget, CancellationToken cancellation) {
                    // P2 explicitly captures the owner inventory. Planning/postimages must reuse it without IO.
                    assertFalse(prohibitReads,"Planning must not reacquire the owner inventory");
                    assertEquals(SourceUris.directoryIdentity(ROOT),SourceUris.directoryIdentity(root));
                    var entries=files.keySet().stream().filter(policyId->SourceUris.contains(root,policyId))
                            .filter(id->id.getPath().endsWith("/job.groovy")||id.getPath().endsWith("/settings.gretl")).sorted().toList();
                    assertTrue(entries.size()<=budget.maximumEntries());return new SourceListing(entries,List.of(),true);
                }
            });
        }
        void loadCatalog() {
            var input = new InputDescriptor("input", "Input", true, false, null, List.of(), "input='x'",
                    ValueShape.fromMetadata("text"), null, false, Map.of());
            var mode = new InputDescriptor("mode", "Mode", false, false, null, List.of("FAST","SLOW"), "mode='FAST'", ValueShape.fromMetadata("text"), null, false, Map.of());
            var operation = new OperationDescriptor("test.echo", "Echo", "Test", "fixture", List.of(input, mode), List.of(), "example", true);
            var codec = new CatalogJsonCodec();
            catalog.loadBundled(codec.encodeCanonical(codec.identify(new OperationCatalog(1,"test","test","",List.of("fixture"),List.of(operation),"COMPLETE"))), null);
        }
        AnalysisSnapshot capture() {
            return capture(List.of());
        }
        AnalysisSnapshot capture(List<URI> additional) {
            prohibitReads = false;
            var captured = manager.capture(new ProjectResolver.ProjectRequest(ROOT, JOB, false, List.of(), additional), CancellationToken.NONE);
            prohibitReads = true;
            return AnalysisSnapshot.of(new ProjectAnalyzer().analyze(captured, CancellationToken.NONE));
        }
        @Override public void close() { manager.close(); }
    }

    @Test void appliesAndRebindsActualPostimageWithoutIoOrChangingOriginal() {
        try (var fixture = new Fixture()) {
            var before = fixture.capture();
            var value = before.analysis().unique(TASK).orElseThrow().declaration().fields().getFirst().value();
            var edit = new SourceReplacement(value.span(), "'old'", "'new'");
            var planned = new EditVerifier().verify(before, Map.of(JOB, List.of(edit)), "Change input",
                    post -> post.analysis().unique(TASK).map(s -> s.declaration().fields().getFirst().value())
                            .filter(v -> v instanceof LiteralValue l && "new".equals(l.value())).isPresent(),
                    List.of(), CancellationToken.NONE);
            assertEquals("// keep\r\ntask('a', Echo) { input = 'new' }\r\ndefaults 'a'\r\n", planned.postimages().get(JOB));
            assertEquals("old", ((LiteralValue) before.analysis().unique(TASK).orElseThrow().declaration().fields().getFirst().value()).value());
            assertTrue(planned.isCurrent(before));
            assertFalse(planned.isCurrent(planned.postimage()));
            assertEquals(0, planned.postimage().analysis().diagnostics().stream().filter(d -> d.severity() == Severity.ERROR).count());
            assertFalse(planned.noOp());
        }
    }

    @Test void rejectsBrokenPostimagesFalseSemanticDeltaAndElidesNoOp() {
        try (var fixture = new Fixture()) {
            var before = fixture.capture();
            var span = before.analysis().unique(TASK).orElseThrow().declaration().fields().getFirst().value().span();
            assertThrows(IllegalArgumentException.class, () -> new EditVerifier().verify(before,
                    Map.of(JOB, List.of(new SourceReplacement(span, "'old'", "["))), "Broken", post -> true, List.of(), CancellationToken.NONE));
            assertThrows(IllegalArgumentException.class, () -> new EditVerifier().verify(before,
                    Map.of(JOB, List.of(new SourceReplacement(span, "'old'", "'new'"))), "Wrong expectation", post -> false, List.of(), CancellationToken.NONE));
            var noOp = new EditVerifier().verify(before, Map.of(JOB, List.of(new SourceReplacement(span,"'old'","'old'"))),
                    "No change", post -> true, List.of(), CancellationToken.NONE);
            assertTrue(noOp.noOp());
            assertTrue(noOp.postimages().isEmpty());
            assertSame(before, noOp.postimage());
        }
    }

    @Test void settingsCatalogAndBufferChangesAreNotHiddenByAnUnchangedJob() {
        try (var fixture = new Fixture()) {
            var before = fixture.capture();
            var preconditions = EditPreconditions.capture(before);
            fixture.files.put(SETTINGS, "// retained\r\ndefaults ':a'\r\n");
            fixture.manager.onSourcesChanged(Set.of(SETTINGS));
            var settingsChanged = fixture.capture();
            var settings = settingsChanged.analysis().capture().project().settings();
            assertNotNull(settings);
            assertEquals("defaults ':a'", settings.source().slice(settings.directives().get("defaults")));
            assertTrue(settings.tokens().all().stream().anyMatch(t -> t.text().equals("// retained")));
            assertSame(settings.tokens(), new ch.so.agi.gretl.tooling.query.QueryContext(settingsChanged, SETTINGS, 1).tokens().orElseThrow());
            assertFalse(preconditions.matches(new AnalysisSnapshot(before.id(), settingsChanged.analysis())));
            var settingsPreconditions = EditPreconditions.capture(settingsChanged);
            fixture.loadCatalog();
            fixture.manager.onCatalogChanged();
            var catalogChanged = fixture.capture();
            assertFalse(settingsPreconditions.matches(new AnalysisSnapshot(settingsChanged.id(), catalogChanged.analysis())));
            var catalogPreconditions = EditPreconditions.capture(catalogChanged);
            fixture.buffers.open(JOB, "gretl", 1, fixture.files.get(JOB));
            var bufferChanged = fixture.capture();
            assertFalse(catalogPreconditions.matches(new AnalysisSnapshot(catalogChanged.id(), bufferChanged.analysis())));
        }
    }

    @Test void referenceIndexUsesBoundAliasesAndLiteralReferencesWithoutSearchingComments() {
        try (var fixture = new Fixture()) {
            String job = "def source=task('a', Echo) { input='a' }\n"
                    + "action('use', dependsOn:source, after:'a') { result(source); resultOptional('a') }\n"
                    + "defaults 'a','use'\n// untouched 'a'\ndef ordinary='a'\n";
            fixture.files.put(JOB, job);
            var snapshot = fixture.capture();
            var index = snapshot.references();
            assertEquals(5, index.referencesTo(TASK).uses().size());
            assertTrue(index.referencesTo(TASK).complete());
            int resultAlias = job.indexOf("result(source)") + "result(".length();
            assertEquals(List.of(snapshot.analysis().unique(TASK).orElseThrow().anchor()), index.definitionsAt(JOB, resultAlias));
            assertTrue(index.referencesAt(JOB, job.indexOf("untouched 'a'") + "untouched '".length()).isEmpty());
            assertTrue(index.definitionsAt(JOB, job.indexOf("ordinary='a'") + "ordinary='".length()).isEmpty());
            assertTrue(index.definitionsAt(JOB, job.indexOf("input='a'") + "input='".length()).isEmpty());
        }
    }
}
