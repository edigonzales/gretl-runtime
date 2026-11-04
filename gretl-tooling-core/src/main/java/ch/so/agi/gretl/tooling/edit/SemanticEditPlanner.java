package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.SymbolId;

/** Pure entry point: a successful result contains an applied and rebound postimage, never file writes. */
public final class SemanticEditPlanner {
    /** One form confirmation produces one final plan, even when several new fields share an insertion anchor. */
    public EditPlanningResult planConfiguration(AnalysisSnapshot snapshot, SymbolId target,
            Map<String,EditableValue> fields, Set<String> removedFields, Map<String,EditableValue> options,
            EditStyle style,CancellationToken cancellation) {
        cancellation.check();
        int count=fields.size()+removedFields.size()+options.size();
        if(count>128||fields.keySet().stream().anyMatch(removedFields::contains))throw new IllegalArgumentException("EDIT_CONFIGURATION_LIMIT");
        var intents=new ArrayList<SemanticEditIntent>();
        fields.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->intents.add(new SemanticEditIntent.SetField(target,e.getKey(),e.getValue())));
        removedFields.stream().sorted().forEach(field->intents.add(new SemanticEditIntent.RemoveField(target,field)));
        options.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->intents.add(new SemanticEditIntent.SetTaskOption(target,e.getKey(),e.getValue())));
        // Even an empty form checks the target and host's open-document requirements.
        var capability=new EditabilityAnalyzer().assess(snapshot,intents.isEmpty()?new SemanticEditIntent.SetTaskOption(target,"description",new EditableValue.Literal(null)):intents.getFirst(),style);
        if(capability.state()!=EditCapability.State.ALLOWED)return new EditPlanningResult(capability,Optional.empty());
        var composition=new LinkedHashMap<java.net.URI,EditComposition>();
        var current=snapshot;
        for(var intent:intents) {
            cancellation.check();
            var result=plan(current,intent,style,cancellation,true);
            if(result.plan().isEmpty())return new EditPlanningResult(new EditCapability(result.capability().state(),result.capability().reasons(),
                    capability.dependencies(),result.capability().requiredOpenDocuments()),Optional.empty());
            var edit=result.plan().orElseThrow();
            for(var entry:edit.changes().entrySet())composition.computeIfAbsent(entry.getKey(),id->new EditComposition(snapshot.source(id).orElseThrow()))
                    .append(entry.getValue(),edit.postimages().get(entry.getKey()),cancellation);
            current=edit.postimage();
        }
        var changes=new LinkedHashMap<java.net.URI,List<SourceReplacement>>();
        composition.forEach((id,edits)->changes.put(id,edits.build(cancellation)));
        var expected=current.analysis();
        try {
            var verified=new EditVerifier().verify(snapshot,changes,"Configure task",after->after.analysis().symbols().equals(expected.symbols())
                    &&after.analysis().references().equals(expected.references())&&after.analysis().diagnostics().equals(expected.diagnostics()),List.of(),cancellation);
            return new EditPlanningResult(capability,Optional.of(verified));
        } catch(IllegalArgumentException refusal) {
            if(refusal.getMessage()==null||!refusal.getMessage().matches("EDIT_[A-Z0-9_]+"))throw refusal;
            return new EditPlanningResult(new EditCapability(EditCapability.State.REJECTED,List.of(EditabilityAnalyzer.reason(snapshot,refusal.getMessage(),null)),
                    capability.dependencies(),List.of()),Optional.empty());
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
        return plan(snapshot,intent,style,cancellation,false);
    }

    private EditPlanningResult plan(AnalysisSnapshot snapshot, SemanticEditIntent intent,
                                   EditStyle style, CancellationToken cancellation,boolean intermediateConfiguration) {
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
            return new EditPlanningResult(capability, Optional.of(transaction.finish(intent.getClass().getSimpleName(), cancellation,intermediateConfiguration)));
        } catch (IllegalArgumentException refusal) {
            String code = refusal.getMessage();
            // Only controlled domain refusals become user-visible capabilities; implementation errors remain errors.
            if (code == null || !code.matches("EDIT_[A-Z0-9_]+")) throw refusal;
            return new EditPlanningResult(new EditCapability(EditCapability.State.REJECTED,
                    List.of(EditabilityAnalyzer.reason(snapshot, code, null)), capability.dependencies(), List.of()), Optional.empty());
        }
    }
}
