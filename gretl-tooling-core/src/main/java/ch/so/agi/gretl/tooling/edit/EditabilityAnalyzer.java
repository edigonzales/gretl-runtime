package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.language.DslVocabulary;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import ch.so.agi.gretl.tooling.syntax.ExecutionRegions;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Local editability and closed-reference requirements are separate decisions. */
public final class EditabilityAnalyzer {
    public EditCapability assess(AnalysisSnapshot snapshot, SemanticEditIntent intent, EditStyle style) {
        var analysis = snapshot.analysis();
        var dependencies = analysis.capture().project().sources().versions().keySet().stream().sorted().toList();
        if (!analysis.capabilities().bounded() || analysis.capture().project().sources().limited()) return rejected(snapshot, dependencies, "EDIT_ANALYSIS_LIMIT", null);
        var documents = new LinkedHashSet<URI>();
        SymbolId target = target(intent);
        if (target != null) {
            if (!(intent instanceof SemanticEditIntent.RenameSymbol) && target.namespace() != Namespace.TASK) {
                return rejected(snapshot, dependencies, "EDIT_TARGET_KIND", null);
            }
            var symbol = analysis.unique(target).orElse(null);
            if (symbol == null) return rejected(snapshot, dependencies, "EDIT_AMBIGUOUS_TARGET", null);
            documents.add(symbol.anchor().documentId());
            var targetImpact=new EditImpactAnalyzer().assessSources(snapshot,documents);
            if(!targetImpact.allowed())return impactRejected(snapshot,dependencies,targetImpact);
            var declaration = symbol.declaration();
            if (declaration.nameLiteral() == null || declaration.recovered() || declaration.coverage().syntax() != Completeness.COMPLETE) {
                return rejected(snapshot, dependencies, "EDIT_UNSAFE_REGION", symbol.anchor());
            }
            String field = intent instanceof SemanticEditIntent.SetField set ? set.field()
                    : intent instanceof SemanticEditIntent.RemoveField remove ? remove.field()
                    : intent instanceof SemanticEditIntent.BindResult bind ? bind.inputField() : null;
            if (field != null) {
                if (declaration.kind() != DeclarationKind.TASK || symbol.operation() == null) return rejected(snapshot, dependencies, "EDIT_UNKNOWN_SCHEMA", symbol.anchor());
                if (declaration.body() == null) return rejected(snapshot, dependencies, "EDIT_UNSAFE_REGION", symbol.anchor());
                if (DslVocabulary.reservedConfigurationFields().contains(field)
                        || symbol.operation().inputs().stream().noneMatch(i -> i.name().equals(field))) {
                    return rejected(snapshot, dependencies, "EDIT_UNKNOWN_FIELD", symbol.anchor());
                }
                var assignments = declaration.fields().stream().filter(f -> f.name().equals(field)).toList();
                if (assignments.size() > 1) return rejected(snapshot, dependencies, "EDIT_MULTIPLE_ASSIGNMENTS", symbol.anchor());
                if (assignments.isEmpty() && declaration.coverage().fieldAssignments() != Completeness.COMPLETE) {
                    return rejected(snapshot, dependencies, "EDIT_FIELD_COVERAGE", symbol.anchor());
                }
                if (!assignments.isEmpty() && (!structured(assignments.getFirst().value())
                        || ExecutionRegions.contains(declaration.guardedExecution(), assignments.getFirst().span()))) {
                    return rejected(snapshot, dependencies, "EDIT_UNSAFE_REGION", new Anchor(symbol.anchor().documentId(), assignments.getFirst().span()));
                }
            }
            if (intent instanceof SemanticEditIntent.SetTaskOption option) {
                if (declaration.options().stream().anyMatch(e -> !(e.key() instanceof LiteralValue l && l.value() instanceof String))) {
                    return rejected(snapshot, dependencies, "EDIT_OPTION_COVERAGE", symbol.anchor());
                }
                var entries = declaration.options().stream().filter(e -> e.key() instanceof LiteralValue l && option.option().equals(l.value())).toList();
                if (entries.size() > 1) return rejected(snapshot, dependencies, "EDIT_MULTIPLE_OPTIONS", symbol.anchor());
                if (!entries.isEmpty() && !structured(entries.getFirst().value())) return rejected(snapshot, dependencies, "EDIT_UNSAFE_REGION", symbol.anchor());
            }
        }
        // Generic setters and form batches must obey the same coverage rule as graph intents.
        boolean graphChange = intent instanceof SemanticEditIntent.AddDependency || intent instanceof SemanticEditIntent.RemoveDependency
                || intent instanceof SemanticEditIntent.BindResult || intent instanceof SemanticEditIntent.SetDefaults
                || intent instanceof SemanticEditIntent.SetTaskOption option && List.of("dependsOn","after").contains(option.option())
                || intent instanceof SemanticEditIntent.SetField field && references(field.value(),0);
        boolean closedReferences = graphChange || intent instanceof SemanticEditIntent.RenameSymbol || intent instanceof SemanticEditIntent.DeleteTask;
        if (closedReferences && (!analysis.capabilities().declarationsComplete() || !analysis.capabilities().referencesComplete())) {
            return rejected(snapshot, dependencies, "EDIT_REFERENCE_COVERAGE", null);
        }
        if (graphChange && !analysis.capabilities().graphComplete()) {
            return rejected(snapshot, dependencies, "EDIT_REFERENCE_COVERAGE", null);
        }
        if (intent instanceof SemanticEditIntent.RenameSymbol rename) {
            if(!snapshot.references().referencesTo(rename.target()).complete())return rejected(snapshot,dependencies,"EDIT_REFERENCE_COVERAGE",null);
            try{new AliasUseGuard().check(snapshot,analysis.unique(rename.target()).orElseThrow(),false);}
            catch(IllegalArgumentException escaped){return rejected(snapshot,dependencies,"EDIT_ALIAS_ESCAPE",analysis.unique(rename.target()).orElseThrow().anchor());}
            for (var reference : snapshot.references().referencesTo(rename.target()).uses()) documents.add(reference.anchor().documentId());
        } else if (intent instanceof SemanticEditIntent.DeleteTask delete) {
            if(!snapshot.references().referencesTo(delete.target()).complete())return rejected(snapshot,dependencies,"EDIT_REFERENCE_COVERAGE",null);
            var uses = snapshot.references().referencesTo(delete.target()).uses();
            if (!uses.isEmpty()) return rejected(snapshot, dependencies, "EDIT_REFERENCED_TARGET", uses.getFirst().anchor());
            try{new AliasUseGuard().check(snapshot,analysis.unique(delete.target()).orElseThrow(),true);}
            catch(IllegalArgumentException escaped){return rejected(snapshot,dependencies,"EDIT_ALIAS_ESCAPE",analysis.unique(delete.target()).orElseThrow().anchor());}
        } else if (intent instanceof SemanticEditIntent.AddTask add) {
            documents.add(add.document());
            if (!analysis.capabilities().declarationsComplete()) return rejected(snapshot, dependencies, "EDIT_REFERENCE_COVERAGE", null);
        } else if (intent instanceof SemanticEditIntent.AddAction add) {
            documents.add(add.document());
            if (!analysis.capabilities().declarationsComplete()) return rejected(snapshot, dependencies, "EDIT_REFERENCE_COVERAGE", null);
        } else if (intent instanceof SemanticEditIntent.AddParameter add) {
            documents.add(add.document());
            if (!analysis.capabilities().declarationsComplete()) return rejected(snapshot, dependencies, "EDIT_REFERENCE_COVERAGE", null);
        } else if (intent instanceof SemanticEditIntent.SetDefaults defaults) documents.add(defaults.scopeDocument());
        var impact=new EditImpactAnalyzer().assessSources(snapshot,documents);
        if(!impact.allowed())return impactRejected(snapshot,dependencies,impact);
        var open = new ArrayList<URI>();
        for (var document : documents) {
            var source = snapshot.source(document).orElse(null);
            if (source == null || !source.stamp().editable()) return rejected(snapshot, dependencies, "EDIT_READONLY", new Anchor(document, null));
            if (style.requireOpenDocuments() && source.stamp().bufferVersion() == null) open.add(document);
        }
        return new EditCapability(open.isEmpty() ? EditCapability.State.ALLOWED : EditCapability.State.NEEDS_OPEN_DOCUMENTS,
                open.isEmpty() ? List.of() : List.of(reason(snapshot, "EDIT_OPEN_DOCUMENTS", null)), dependencies, open);
    }

    private boolean references(EditableValue value,int depth) {
        // Beyond the existing value-depth contract, safety cannot be established either.
        if(depth>16)return true;
        return switch(value) {
            case EditableValue.ResultFieldRef ignored -> true;
            case EditableValue.ParameterRef ignored -> true;
            case EditableValue.Sequence sequence -> sequence.items().stream().anyMatch(item->references(item,depth+1));
            case EditableValue.Mapping mapping -> mapping.entries().values().stream().anyMatch(item->references(item,depth+1));
            default -> false;
        };
    }

    private EditCapability impactRejected(AnalysisSnapshot snapshot,List<URI> dependencies,EditImpactAnalyzer.EditImpact impact){
        return new EditCapability(EditCapability.State.REJECTED,List.of(new EditCapability.Reason("EDIT_SHARED_SOURCE",
                reason(snapshot,"EDIT_SHARED_SOURCE",null).message(),impact.sources(),impact.sources().stream()
                .flatMap(a->snapshot.analysis().capture().ownership().contexts(a.documentId()).stream()).map(a->a.id()).distinct().toList())),dependencies,List.of());
    }
    private SymbolId target(SemanticEditIntent intent) {
        return switch (intent) {
            case SemanticEditIntent.SetField i -> i.target();
            case SemanticEditIntent.RemoveField i -> i.target();
            case SemanticEditIntent.SetTaskOption i -> i.target();
            case SemanticEditIntent.AddDependency i -> i.consumer();
            case SemanticEditIntent.RemoveDependency i -> i.consumer();
            case SemanticEditIntent.BindResult i -> i.consumer();
            case SemanticEditIntent.RenameSymbol i -> i.target();
            case SemanticEditIntent.DeleteTask i -> i.target();
            default -> null;
        };
    }

    private boolean structured(ValueExpression value) {
        return switch (value) {
            case LiteralValue ignored -> true;
            case ListValue list -> list.items().stream().allMatch(this::structured);
            case MapValue map -> map.entries().stream().allMatch(e -> e.key() instanceof LiteralValue l && l.value() instanceof String && structured(e.value()))
                    && map.entries().stream().map(e -> ((LiteralValue) e.key()).value()).distinct().count() == map.entries().size();
            case CallValue call -> List.of("file", "workPath", "parameter", "result", "secret").contains(call.name())
                    && call.arguments().stream().allMatch(v -> v instanceof LiteralValue);
            case PropertyValue property -> property.receiver() instanceof CallValue call && call.name().equals("result") && structured(call);
            default -> false;
        };
    }

    private EditCapability rejected(AnalysisSnapshot snapshot, List<URI> dependencies, String code, Anchor anchor) {
        return new EditCapability(EditCapability.State.REJECTED, List.of(reason(snapshot, code, anchor)), dependencies, List.of());
    }

    static EditCapability.Reason reason(AnalysisSnapshot snapshot, String code, Anchor anchor) {
        boolean de = snapshot.analysis().capture().configuration().language().toLowerCase(java.util.Locale.ROOT).startsWith("de");
        String message = switch (code) {
            case "EDIT_ANALYSIS_LIMIT" -> de ? "Die Analysegrenze ist erreicht. Bitte im Quelltext bearbeiten." : "Analysis limit reached. Edit in source.";
            case "EDIT_AMBIGUOUS_TARGET" -> de ? "Das Ziel fehlt oder ist mehrdeutig." : "The target is missing or ambiguous.";
            case "EDIT_UNSAFE_REGION" -> de ? "Dieser Codebereich ist nicht sicher strukturiert bearbeitbar." : "This code region cannot be edited structurally with confidence.";
            case "EDIT_UNKNOWN_SCHEMA" -> de ? "Die Operation hat kein eindeutiges Konfigurationsschema." : "The operation has no unambiguous configuration schema.";
            case "EDIT_TARGET_KIND" -> de ? "Diese Aktion ist für diesen Symboltyp nicht vorgesehen." : "This action does not apply to this symbol kind.";
            case "EDIT_UNKNOWN_FIELD" -> de ? "Das Feld fehlt im Schema oder ist reserviert." : "The field is absent from the schema or reserved.";
            case "EDIT_MULTIPLE_ASSIGNMENTS" -> de ? "Das Feld wird mehrfach zugewiesen. Bitte im Quelltext bearbeiten." : "The field is assigned more than once. Edit in source.";
            case "EDIT_FIELD_COVERAGE" -> de ? "Weitere Feldzuweisungen sind statisch nicht ausgeschlossen." : "Additional field assignments cannot be ruled out statically.";
            case "EDIT_OPTION_COVERAGE" -> de ? "Weitere Optionen sind statisch nicht ausgeschlossen." : "Additional options cannot be ruled out statically.";
            case "EDIT_MULTIPLE_OPTIONS" -> de ? "Diese Option wird mehrfach angegeben. Bitte im Quelltext bearbeiten." : "This option occurs more than once. Edit in source.";
            case "EDIT_INCLUDE_PHASE_GUARD" -> de ? "Strukturelle Include-Edits sind bis zur Footprintprüfung gesperrt." : "Structural include edits are locked pending footprint verification.";
            case "EDIT_SHARED_SOURCE" -> de ? "Die Quelle hat mehrere oder nicht vollständig bekannte Besitzer." : "Source ownership is shared or incomplete.";
            case "EDIT_REFERENCE_COVERAGE" -> de ? "Nicht alle relevanten Deklarationen und Referenzen sind bekannt." : "Some relevant declarations or references are not known.";
            case "EDIT_REFERENCED_TARGET" -> de ? "Das Ziel wird noch referenziert. Es gibt keine automatische Kaskadenlöschung." : "The target is still referenced. Deletion does not cascade automatically.";
            case "EDIT_ALIAS_ESCAPE" -> de ? "Die TaskRef-Variable wird ausserhalb der belegten GRETL-Referenzen verwendet. Bitte im Quelltext bearbeiten." : "The TaskRef variable is used outside proven GRETL references. Edit in source.";
            case "EDIT_OPEN_DOCUMENTS" -> de ? "Die Zieldateien müssen zuerst geöffnet und synchronisiert werden." : "Open and synchronize the target documents first.";
            case "EDIT_READONLY" -> de ? "Die Zieldatei ist nicht als bearbeitbare Quelle erfasst." : "The target is not captured as an editable source.";
            default -> de ? "Die Änderung ist unter diesen Voraussetzungen nicht sicher möglich." : "The change is not safe under these preconditions.";
        };
        return new EditCapability.Reason(code, message, anchor == null ? List.of() : List.of(anchor));
    }
}
