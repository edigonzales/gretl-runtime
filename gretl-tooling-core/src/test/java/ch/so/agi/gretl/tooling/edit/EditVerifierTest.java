package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace;
import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.*;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.LiteralValue;
import java.util.*;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.testsupport.EditTestWorkspace.*;
import static org.junit.jupiter.api.Assertions.*;

class EditVerifierTest {
    @Test void appliesAndRebindsActualPostimageWithoutIoOrChangingOriginal() {
        try (var fixture = new EditTestWorkspace()) {
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
        try (var fixture = new EditTestWorkspace()) {
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
        try (var fixture = new EditTestWorkspace()) {
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
        try (var fixture = new EditTestWorkspace()) {
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
