package ch.so.agi.gretl.tooling.query;

import ch.so.agi.gretl.tooling.semantic.SemanticModel.Anchor;
import ch.so.agi.gretl.tooling.source.TextSpan;
import java.net.URI;
import java.util.List;
import ch.so.agi.gretl.tooling.edit.EditCapability;
import ch.so.agi.gretl.tooling.edit.SemanticEditIntent;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.SymbolId;

/** Protocol-neutral immutable editor results. LSP types remain in the future host adapter. */
public final class QueryResults {
    private QueryResults() { }
    public record CompletionProposal(String label, String kind, String detail, TextSpan replacement, String newText) { }
    public record HoverInfo(Anchor anchor, String title, String documentation) { }
    public record SignatureInfo(Anchor anchor, String label, List<String> parameters, int activeParameter, String documentation) {
        public SignatureInfo { parameters = List.copyOf(parameters); }
    }
    public record ReferenceResult(List<Anchor> locations, boolean complete) {
        public ReferenceResult { locations = List.copyOf(locations); }
    }
    public record OutlineNode(String name, String kind, Anchor range, Anchor selection, List<OutlineNode> children) {
        public OutlineNode { children = List.copyOf(children); }
    }
    public record FileLink(Anchor anchor, URI target) { }
    public record FoldingRegion(Anchor anchor, String kind) { }
    public record RenamePreparation(Anchor anchor, String placeholder, SymbolId target, EditCapability capability) { }
    public record DiagnosticId(String code, Anchor anchor) { }
    public record CodeFixProposal(String title, DiagnosticId diagnostic, SemanticEditIntent intent) { }
}
