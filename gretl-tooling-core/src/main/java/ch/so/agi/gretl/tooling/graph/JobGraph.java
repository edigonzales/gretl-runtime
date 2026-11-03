package ch.so.agi.gretl.tooling.graph;

import ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;
import ch.so.agi.gretl.tooling.edit.EditCapability;
import ch.so.agi.gretl.tooling.edit.EditPreconditions;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Static declared graph, independent of CLI selection and runtime status. */
public record JobGraph(UUID snapshotId,EditPreconditions preconditions,Status status,List<Node> nodes,
                       List<Edge> edges,List<Node> parameters,List<Node> cleanup,List<Diagnostic> diagnostics) {
    public enum Status { COMPLETE, PARTIAL, INVALID }
    public enum NodeKind { TASK, ACTION, DYNAMIC_CODE, PROJECT, INNER_INVOCATION, PARAMETER, CLEANUP }
    public enum EdgeKind { DEPENDS_ON, AFTER, RESULT_BINDING }
    public record Node(String id,String presentationKey,SymbolId symbol,Anchor anchor,NodeKind kind,String title,
                       String operationId,String parentId,Map<String,EditCapability> editCapabilities,
                       List<InputDescriptor> inputs,List<OutputDescriptor> outputs,List<Diagnostic> diagnostics) {
        public Node {editCapabilities=Map.copyOf(editCapabilities);inputs=List.copyOf(inputs);outputs=List.copyOf(outputs);diagnostics=List.copyOf(diagnostics);}
    }
    public record Edge(String id,String source,String target,EdgeKind kind,Anchor anchor,
                       String outputField,String inputField,boolean schemaKnown) { }
    public JobGraph {nodes=List.copyOf(nodes);edges=List.copyOf(edges);parameters=List.copyOf(parameters);cleanup=List.copyOf(cleanup);diagnostics=List.copyOf(diagnostics);}
}
