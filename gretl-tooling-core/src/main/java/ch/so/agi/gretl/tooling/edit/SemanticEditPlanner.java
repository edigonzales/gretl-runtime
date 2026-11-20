package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.SymbolId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Pure entry point: a successful result contains an applied and rebound postimage, never file writes. */
public final class SemanticEditPlanner {
    /** One form confirmation produces one final plan, even when several new fields share an insertion anchor. */
    public EditPlanningResult planConfiguration(AnalysisSnapshot snapshot, SymbolId target,
            Map<String, EditableValue> fields, Set<String> removedFields, Map<String, EditableValue> options,
            EditStyle style, CancellationToken cancellation) {
        cancellation.check();
        int count = fields.size() + removedFields.size() + options.size();
        if (count > 128 || fields.keySet().stream().anyMatch(removedFields::contains)) {
            throw new IllegalArgumentException("EDIT_CONFIGURATION_LIMIT");
        }
        var orderedFields = new TreeMap<>(fields);
        var orderedOptions = new TreeMap<>(options);
        var removals = removedFields.stream().sorted().toList();
        var intents = new ArrayList<SemanticEditIntent>();
        orderedFields.forEach((field, value) -> intents.add(new SemanticEditIntent.SetField(target, field, value)));
        removals.forEach(field -> intents.add(new SemanticEditIntent.RemoveField(target, field)));
        orderedOptions.forEach((option, value) -> intents.add(new SemanticEditIntent.SetTaskOption(target, option, value)));
        // Even an empty form checks the target and host's open-document requirements.
        if (intents.isEmpty()) intents.add(new SemanticEditIntent.SetTaskOption(target, "description", new EditableValue.Literal(null)));
        var capability = new EditabilityAnalyzer().assessAll(snapshot, intents, style, cancellation);
        if (capability.state() != EditCapability.State.ALLOWED) return new EditPlanningResult(capability, Optional.empty());

        try {
            var transaction = new EditTransaction(snapshot, style);
            var fieldEdits = new FieldSourceEdits();
            fieldEdits.set(transaction, target, orderedFields, cancellation);
            for (var field : removals) {
                cancellation.check();
                fieldEdits.remove(transaction, target, field);
            }
            new HeaderSourceEdits().set(transaction, target, orderedOptions, cancellation);
            return new EditPlanningResult(capability, Optional.of(transaction.finish("Configure task", cancellation)));
        } catch (IllegalArgumentException refusal) {
            return rejected(snapshot, capability, refusal);
        }
    }

    public record EditValidation(boolean current, List<String> reasons) {
        public EditValidation { reasons = List.copyOf(reasons); }
    }

    public EditValidation validate(PlannedEdit edit, AnalysisSnapshot current) {
        boolean matches = Objects.requireNonNull(edit).isCurrent(Objects.requireNonNull(current));
        return new EditValidation(matches, matches ? List.of() : List.of("EDIT_STALE_SNAPSHOT"));
    }

    public EditPlanningResult plan(AnalysisSnapshot snapshot, SemanticEditIntent intent,
                                   EditStyle style, CancellationToken cancellation) {
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(intent);
        Objects.requireNonNull(style);
        Objects.requireNonNull(cancellation).check();
        var capability = new EditabilityAnalyzer().assess(snapshot, intent, style);
        if (capability.state() != EditCapability.State.ALLOWED) {
            return new EditPlanningResult(capability, Optional.empty());
        }
        try {
            var transaction = new EditTransaction(snapshot, style);
            switch (intent) {
                case SemanticEditIntent.AddTask i -> new DeclarationSourceEdits().addTask(transaction, i);
                case SemanticEditIntent.AddAction i -> new DeclarationSourceEdits().addAction(transaction, i);
                case SemanticEditIntent.AddParameter i -> new DeclarationSourceEdits().addParameter(transaction, i);
                case SemanticEditIntent.SetField i -> new FieldSourceEdits().set(transaction, i.target(), i.field(), i.value());
                case SemanticEditIntent.RemoveField i -> new FieldSourceEdits().remove(transaction, i.target(), i.field());
                case SemanticEditIntent.SetTaskOption i -> new HeaderSourceEdits().set(transaction, i.target(), i.option(), i.value());
                case SemanticEditIntent.AddDependency i -> new DependencySourceEdits().add(transaction, i.producer(), i.consumer(), i.kind());
                case SemanticEditIntent.RemoveDependency i -> new DependencySourceEdits().remove(transaction, i.producer(), i.consumer(), i.kind());
                case SemanticEditIntent.BindResult i -> new DependencySourceEdits().bind(transaction, i);
                case SemanticEditIntent.RenameSymbol i -> new ReferenceSourceEdits().rename(transaction, i, cancellation);
                case SemanticEditIntent.DeleteTask i -> new ReferenceSourceEdits().delete(transaction, i, cancellation);
                case SemanticEditIntent.SetDefaults i -> new DefaultsSourceEdits().set(transaction, i);
            }
            cancellation.check();
            return new EditPlanningResult(capability, Optional.of(transaction.finish(intent.getClass().getSimpleName(), cancellation)));
        } catch (IllegalArgumentException refusal) {
            return rejected(snapshot, capability, refusal);
        }
    }

    private EditPlanningResult rejected(AnalysisSnapshot snapshot, EditCapability capability, IllegalArgumentException refusal) {
        String code = refusal.getMessage();
        // Only controlled domain refusals become user-visible capabilities.
        if (code == null || !code.matches("EDIT_[A-Z0-9_]+")) throw refusal;
        return new EditPlanningResult(new EditCapability(EditCapability.State.REJECTED,
                List.of(EditabilityAnalyzer.reason(snapshot, code, null)), capability.dependencies(), List.of()), Optional.empty());
    }
}
