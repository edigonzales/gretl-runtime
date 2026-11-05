package ch.so.agi.gretl.tooling.semantic;

import ch.so.agi.gretl.language.PlanSemantics;
import ch.so.agi.gretl.tooling.catalog.OperationCatalog.OperationDescriptor;
import ch.so.agi.gretl.tooling.source.TextSpan;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.*;
import ch.so.agi.gretl.tooling.workspace.WorkspaceManager.WorkspaceCapture;
import java.net.URI;
import ch.so.agi.gretl.tooling.workspace.IncludeModel.*;
import java.util.*;

/** Immutable bound project. IDs distinguish namespaces; duplicate definitions remain visible. */
public final class SemanticModel {
    private SemanticModel() { }
    public enum Namespace { TASK, PARAMETER, CLEANUP }
    public record SymbolId(Namespace namespace,String qualifiedName) { }
    public record Anchor(URI documentId,TextSpan span) { }
    public record Symbol(SymbolId id,String localName,String scope,Anchor anchor,Declaration declaration,OperationDescriptor operation,SourceApplication application) {
        public Symbol(SymbolId id,String localName,String scope,Anchor anchor,Declaration declaration,OperationDescriptor operation){this(id,localName,scope,anchor,declaration,operation,null);}
    }
    public enum Resolution { RESOLVED, UNRESOLVED, AMBIGUOUS, DYNAMIC }
    public enum ReferenceKind { DEPENDS_ON, AFTER, DEFAULT, RESULT, RESULT_OPTIONAL, PARAMETER }
    public record Reference(ReferenceKind kind,Anchor anchor,SymbolId owner,String spelling,Resolution resolution,List<SymbolId> candidates,ApplicationId application) {
        public Reference(ReferenceKind kind,Anchor anchor,SymbolId owner,String spelling,Resolution resolution,List<SymbolId> candidates){this(kind,anchor,owner,spelling,resolution,candidates,null);}
        public Reference { candidates=List.copyOf(candidates); }
        public Optional<SymbolId> target(){return resolution==Resolution.RESOLVED?Optional.of(candidates.getFirst()):Optional.empty();}
    }
    public enum PathKind { JOB_RESOURCE, WORK, EXTERNAL_PARAMETER, DYNAMIC }
    public record PathReference(Anchor anchor,PathKind kind,URI resourceId,SymbolId parameter,ApplicationId application) {
        public PathReference(Anchor anchor,PathKind kind,URI resourceId,SymbolId parameter){this(anchor,kind,resourceId,parameter,null);}
    }
    public enum Severity { ERROR, WARNING, INFORMATION }
    public record Diagnostic(String code,Severity severity,String message,Anchor anchor,List<Anchor> related,ApplicationId application) {
        public Diagnostic(String code,Severity severity,String message,Anchor anchor,List<Anchor> related){this(code,severity,message,anchor,related,null);}
        public Diagnostic { related=List.copyOf(related); }
    }
    public record Capabilities(boolean declarationsComplete,boolean referencesComplete,boolean graphComplete,boolean catalogAvailable,boolean bounded,boolean fieldAssignmentsComplete,boolean executionComplete) {
        public Capabilities(boolean declarationsComplete,boolean referencesComplete,boolean graphComplete,boolean catalogAvailable,boolean bounded){this(declarationsComplete,referencesComplete,graphComplete,catalogAvailable,bounded,false,false);}
    }
    public record Analysis(WorkspaceCapture capture,Map<SymbolId,List<Symbol>> symbols,List<Reference> references,List<PathReference> paths,List<Diagnostic> diagnostics,PlanSemantics.DeclaredGraph graph,List<String> defaultTargets,Capabilities capabilities) {
        public Analysis {var copy=new LinkedHashMap<SymbolId,List<Symbol>>();symbols.forEach((k,v)->copy.put(k,List.copyOf(v)));symbols=Collections.unmodifiableMap(copy);references=List.copyOf(references);paths=List.copyOf(paths);diagnostics=List.copyOf(diagnostics);defaultTargets=List.copyOf(defaultTargets);}
        public Optional<Symbol> unique(SymbolId id){var found=symbols.getOrDefault(id,List.of());return found.size()==1?Optional.of(found.getFirst()):Optional.empty();}
    }
}
