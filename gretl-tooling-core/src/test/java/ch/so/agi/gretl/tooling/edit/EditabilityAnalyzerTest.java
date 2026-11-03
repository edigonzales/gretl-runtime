package ch.so.agi.gretl.tooling.edit;

import java.util.List;
import org.junit.jupiter.api.Test;
import static ch.so.agi.gretl.tooling.edit.EditVerifierTest.*;
import static org.junit.jupiter.api.Assertions.*;

class EditabilityAnalyzerTest {
    private final EditabilityAnalyzer analyzer = new EditabilityAnalyzer();
    private final SemanticEditIntent.SetField set = new SemanticEditIntent.SetField(TASK, "input", new EditableValue.Literal("new"));

    @Test void modelerRequestsOpeningClosedTargetsThenAllowsSynchronizedBuffers() {
        try (var fixture = new Fixture()) {
            var closed = fixture.capture();
            var capability = analyzer.assess(closed, set, EditStyle.openDocuments());
            assertEquals(EditCapability.State.NEEDS_OPEN_DOCUMENTS, capability.state());
            assertEquals(List.of(JOB), capability.requiredOpenDocuments());
            assertTrue(capability.dependencies().contains(SETTINGS));
            fixture.buffers.open(JOB, "gretl", 1, fixture.files.get(JOB));
            assertEquals(EditCapability.State.ALLOWED, analyzer.assess(fixture.capture(), set, EditStyle.openDocuments()).state());
        }
    }

    @Test void duplicateAssignmentsAndDuplicateOptionsAreNotArbitrarilyResolved() {
        try (var fixture = new Fixture()) {
            fixture.files.put(JOB, "task('a', Echo) { input='first'; input='last' }");
            var duplicateField = analyzer.assess(fixture.capture(), set, EditStyle.defaults());
            assertEquals(EditCapability.State.REJECTED, duplicateField.state());
            assertEquals("EDIT_MULTIPLE_ASSIGNMENTS", duplicateField.reasons().getFirst().code());
            fixture.files.put(JOB, "task([description:'first', description:'last'], 'a', Echo) { input='x' }");
            var duplicateOption = analyzer.assess(fixture.capture(), new SemanticEditIntent.SetTaskOption(TASK,"description",new EditableValue.Literal("new")), EditStyle.defaults());
            assertEquals(EditCapability.State.REJECTED, duplicateOption.state());
            assertEquals("EDIT_MULTIPLE_OPTIONS", duplicateOption.reasons().getFirst().code());
        }
    }

    @Test void aSeparateStaticFieldCanRemainEditableWhileOpaqueExpressionsAreRetained() {
        try (var fixture = new Fixture()) {
            fixture.files.put(JOB, "task('a', Echo) { input='old'; other=loadDynamicValue() }");
            assertEquals(EditCapability.State.ALLOWED, analyzer.assess(fixture.capture(), set, EditStyle.defaults()).state());
            fixture.files.put(JOB, "task('a', Echo) { input=loadDynamicValue() }");
            var opaqueTarget = analyzer.assess(fixture.capture(), set, EditStyle.defaults());
            assertEquals(EditCapability.State.REJECTED, opaqueTarget.state());
            assertEquals("EDIT_UNSAFE_REGION", opaqueTarget.reasons().getFirst().code());
        }
    }

    @Test void deletionDoesNotCascadeThroughKnownDefaultsAndDynamicReferencesBlockRename() {
        try (var fixture = new Fixture()) {
            var referenced = analyzer.assess(fixture.capture(), new SemanticEditIntent.DeleteTask(TASK), EditStyle.defaults());
            assertEquals(EditCapability.State.REJECTED, referenced.state());
            assertEquals("EDIT_REFERENCED_TARGET", referenced.reasons().getFirst().code());
            fixture.files.put(JOB, "task('a', Echo) { input='x' }; action('use') { result(taskName()) }");
            var dynamic = analyzer.assess(fixture.capture(), new SemanticEditIntent.RenameSymbol(TASK,"b"), EditStyle.defaults());
            assertEquals(EditCapability.State.REJECTED, dynamic.state());
            assertEquals("EDIT_REFERENCE_COVERAGE", dynamic.reasons().getFirst().code());
        }
    }
}
