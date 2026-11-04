package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.language.PlanSemantics.EdgeKind;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.SymbolId;
import java.net.URI;
import java.util.List;
import java.util.Map;

/** Shared intent contract for queries, quick fixes and the later modeler host. */
public sealed interface SemanticEditIntent {
    record AddTask(URI document, int insertionOffset, String localName, String operationId,
                   Map<String, EditableValue> initialFields) implements SemanticEditIntent {
        public AddTask { initialFields = Map.copyOf(initialFields); }
    }
    record AddAction(URI document, int insertionOffset, String localName) implements SemanticEditIntent { }
    record ParameterDeclaration(String localName, String description, String type, boolean required,
                                EditableValue defaultValue) { }
    record AddParameter(URI document, int insertionOffset, ParameterDeclaration declaration) implements SemanticEditIntent { }
    record SetField(SymbolId target, String field, EditableValue value) implements SemanticEditIntent { }
    record RemoveField(SymbolId target, String field) implements SemanticEditIntent { }
    record SetTaskOption(SymbolId target, String option, EditableValue value) implements SemanticEditIntent { }
    record AddDependency(SymbolId producer, SymbolId consumer, EdgeKind kind) implements SemanticEditIntent { }
    record RemoveDependency(SymbolId producer, SymbolId consumer, EdgeKind kind) implements SemanticEditIntent { }
    record BindResult(SymbolId producer, String outputField, SymbolId consumer, String inputField) implements SemanticEditIntent { }
    record RenameSymbol(SymbolId target, String newLocalName) implements SemanticEditIntent { }
    record DeleteTask(SymbolId target) implements SemanticEditIntent { }
    record SetDefaults(URI scopeDocument, List<SymbolId> targets) implements SemanticEditIntent {
        public SetDefaults { targets = List.copyOf(targets); }
    }
}
