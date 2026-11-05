package ch.so.agi.gretl.lsp;

import ch.so.agi.gretl.tooling.catalog.OperationCatalog;
import ch.so.agi.gretl.tooling.edit.*;
import ch.so.agi.gretl.tooling.graph.JobGraph;
import ch.so.agi.gretl.tooling.semantic.AnalysisSnapshot;
import ch.so.agi.gretl.tooling.semantic.SemanticModel.*;
import java.net.URI;
import java.util.*;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.WorkspaceEdit;

/** Version one wire data. Revisions and integer/decimal scalars use strings to avoid JS precision loss. */
public final class GretlProtocol {
    private GretlProtocol() { }
    public static final int VERSION=1;
    public record SourceStamp(String documentId,String sha256,Integer version,String incarnation,boolean editable,String unavailableCode) { }
    public record Context(int protocolVersion,int schemaVersion,String serverInstanceId,String projectId,String workspaceGeneration,
                          String snapshotId,String catalogId,List<SourceStamp> sourceStamps,Capabilities completeness,List<String> notices,
                          String ownerGeneration,String contextGeneration,String contextDocumentId,String contextToken) {
        public Context(int protocolVersion,int schemaVersion,String serverInstanceId,String projectId,String workspaceGeneration,String snapshotId,String catalogId,List<SourceStamp> sourceStamps,Capabilities completeness,List<String> notices){this(protocolVersion,schemaVersion,serverInstanceId,projectId,workspaceGeneration,snapshotId,catalogId,sourceStamps,completeness,notices,null,null,null,null);}
    }
    public record AnalysisReady(Context context) { }
    public record CatalogRequest(int protocolVersion,int catalogSchemaVersion) { }
    public record CatalogResponse(int protocolVersion,int schemaVersion,String serverInstanceId,String state,OperationCatalog catalog,List<String> notices) { }
    public record SourceContextsRequest(int protocolVersion,int includeToolingVersion,String documentId,boolean registerAsEntry) { }
    public record SelectSourceContextRequest(int protocolVersion,int includeToolingVersion,String documentId,String contextToken,String incarnation,String ownerGeneration) { }
    public record SourceContextInfo(String contextToken,String projectId,String entryDocument,String ownerScope,String resourceBase,String role) { }
    public record SourceContextsResponse(int protocolVersion,int includeToolingVersion,String serverInstanceId,String documentId,
            String ownerGeneration,String contextGeneration,String incarnation,boolean ownersComplete,boolean selectionRequired,String selectedToken,List<SourceContextInfo> contexts) { }
    public record MemberInfo(String documentId,int contextCount) { }
    public record SourceMembershipChanged(int protocolVersion,int includeToolingVersion,String serverInstanceId,String workspaceRoot,String anchorDocument,
            String ownerGeneration,String syncToken,boolean ownersComplete,List<MemberInfo> members) { }
    public record MemberBuffer(String documentId,Integer version,String sha256) { }
    public record ConfirmSourceMembershipRequest(int protocolVersion,int includeToolingVersion,String documentId,String ownerGeneration,String syncToken,List<MemberBuffer> buffers) { }
    public record ConfirmSourceMembershipResponse(int protocolVersion,int includeToolingVersion,boolean current,String ownerGeneration,List<String> reasons) { }
    public record GraphRequest(int protocolVersion,String documentId,String expectedSnapshotId,boolean includeInnerInvocations,List<String> requestedSources,List<String> previewTargets,String contextToken) {
        public GraphRequest(int protocolVersion,String documentId,String expectedSnapshotId,boolean includeInnerInvocations,List<String> requestedSources){this(protocolVersion,documentId,expectedSnapshotId,includeInnerInvocations,requestedSources,null,null);}
        public GraphRequest(int protocolVersion,String documentId,String expectedSnapshotId,boolean includeInnerInvocations,List<String> requestedSources,List<String> previewTargets){this(protocolVersion,documentId,expectedSnapshotId,includeInnerInvocations,requestedSources,previewTargets,null);}
    }
    public record GraphPayload(JobGraph.Status status,List<JobGraph.Node> nodes,List<JobGraph.Edge> edges,List<JobGraph.Node> parameters,List<JobGraph.Node> cleanup,List<Diagnostic> diagnostics,ch.so.agi.gretl.tooling.graph.TargetPreviewProjector.Preview preview,ch.so.agi.gretl.tooling.graph.GraphFormProjector.Projection forms) {
        public GraphPayload(JobGraph.Status status,List<JobGraph.Node> nodes,List<JobGraph.Edge> edges,List<JobGraph.Node> parameters,List<JobGraph.Node> cleanup,List<Diagnostic> diagnostics){this(status,nodes,edges,parameters,cleanup,diagnostics,null,null);}
        public GraphPayload(JobGraph.Status status,List<JobGraph.Node> nodes,List<JobGraph.Edge> edges,List<JobGraph.Node> parameters,List<JobGraph.Node> cleanup,List<Diagnostic> diagnostics,ch.so.agi.gretl.tooling.graph.TargetPreviewProjector.Preview preview){this(status,nodes,edges,parameters,cleanup,diagnostics,preview,null);}
    }
    public record GraphResponse(Context context,GraphPayload payload) { }
    public record SymbolKey(String namespace,String qualifiedName) {
        SymbolId core(){return new SymbolId(Namespace.valueOf(required(namespace,32)),required(qualifiedName,65_536));}
    }
    public record Value(String kind,String scalar,List<Value> items,Map<String,Value> entries,SymbolKey symbol,String field) {
        EditableValue core(){return core(0,new int[]{0});}
        private EditableValue core(int depth,int[] count) {
            if(depth>16||++count[0]>4096)throw new IllegalArgumentException("EDIT_VALUE_LIMIT");
            return switch(required(kind,32)) {
                case "Null"->new EditableValue.Literal(null);
                case "Text"->new EditableValue.Literal(required(scalar,65_536));
                case "Boolean"->{if(!Set.of("true","false").contains(scalar))throw new IllegalArgumentException("EDIT_BOOLEAN");yield new EditableValue.Literal(Boolean.valueOf(scalar));}
                case "Integer"->new EditableValue.Literal(Integer.valueOf(required(scalar,4096)));
                case "Long"->new EditableValue.Literal(Long.valueOf(required(scalar,4096)));
                case "BigInteger"->new EditableValue.Literal(new java.math.BigInteger(required(scalar,4096)));
                case "Decimal"->new EditableValue.Literal(new java.math.BigDecimal(required(scalar,4096)));
                case "List"->{if(items==null||items.size()>1024)throw new IllegalArgumentException("EDIT_VALUE_LIMIT");yield new EditableValue.Sequence(items.stream().map(v->v.core(depth+1,count)).toList());}
                case "Map"->{if(entries==null||entries.size()>1024)throw new IllegalArgumentException("EDIT_VALUE_LIMIT");var values=new LinkedHashMap<String,EditableValue>();entries.forEach((key,value)->values.put(required(key,65_536),value.core(depth+1,count)));yield new EditableValue.Mapping(values);}
                case "ParameterRef"->new EditableValue.ParameterRef(Objects.requireNonNull(symbol).core());
                case "ResultFieldRef"->new EditableValue.ResultFieldRef(Objects.requireNonNull(symbol).core(),required(field,1024));
                case "JobFileRef"->new EditableValue.JobFileRef(required(scalar,4096));
                case "WorkPathRef"->new EditableValue.WorkPathRef(required(scalar,4096));
                case "SecretAliasRef"->new EditableValue.SecretAliasRef(required(scalar,1024));
                default->throw new IllegalArgumentException("EDIT_VALUE_KIND");
            };
        }
    }
    public record Parameter(String name,String description,String type,boolean required,Value defaultValue) { }
    public record Intent(String kind,SymbolKey target,SymbolKey producer,String field,String outputField,String edgeKind,
                         String name,String operationId,Integer insertionOffset,Value value,Map<String,Value> fields,
                         List<SymbolKey> targets,Parameter parameter,Set<String> removedFields,Map<String,Value> options) {
        SemanticEditIntent core(URI document) {
            return switch(required(kind,32)) {
                case "AddTask"->new SemanticEditIntent.AddTask(document,Objects.requireNonNull(insertionOffset),required(name,65_536),required(operationId,1024),values(fields));
                case "AddAction"->new SemanticEditIntent.AddAction(document,Objects.requireNonNull(insertionOffset),required(name,65_536));
                case "AddParameter"->{Objects.requireNonNull(parameter);yield new SemanticEditIntent.AddParameter(document,Objects.requireNonNull(insertionOffset),new SemanticEditIntent.ParameterDeclaration(required(parameter.name(),65_536),parameter.description(),parameter.type(),parameter.required(),parameter.defaultValue()==null?null:parameter.defaultValue().core()));}
                case "SetField"->new SemanticEditIntent.SetField(Objects.requireNonNull(target).core(),required(field,1024),Objects.requireNonNull(value).core());
                case "RemoveField"->new SemanticEditIntent.RemoveField(Objects.requireNonNull(target).core(),required(field,1024));
                case "SetTaskOption"->new SemanticEditIntent.SetTaskOption(Objects.requireNonNull(target).core(),required(field,1024),Objects.requireNonNull(value).core());
                case "AddDependency"->new SemanticEditIntent.AddDependency(Objects.requireNonNull(producer).core(),Objects.requireNonNull(target).core(),ch.so.agi.gretl.language.PlanSemantics.EdgeKind.valueOf(required(edgeKind,32)));
                case "RemoveDependency"->new SemanticEditIntent.RemoveDependency(Objects.requireNonNull(producer).core(),Objects.requireNonNull(target).core(),ch.so.agi.gretl.language.PlanSemantics.EdgeKind.valueOf(required(edgeKind,32)));
                case "BindResult"->new SemanticEditIntent.BindResult(Objects.requireNonNull(producer).core(),required(outputField,1024),Objects.requireNonNull(target).core(),required(field,1024));
                case "RenameSymbol"->new SemanticEditIntent.RenameSymbol(Objects.requireNonNull(target).core(),required(name,65_536));
                case "DeleteTask"->new SemanticEditIntent.DeleteTask(Objects.requireNonNull(target).core());
                case "SetDefaults"->{if(targets==null||targets.size()>1024)throw new IllegalArgumentException("EDIT_TARGET_LIMIT");yield new SemanticEditIntent.SetDefaults(document,targets.stream().map(SymbolKey::core).toList());}
                default->throw new IllegalArgumentException("EDIT_INTENT_KIND");
            };
        }
    }
    public record EditRequest(int protocolVersion,String documentId,String expectedSnapshotId,Intent intent,boolean requireOpenDocuments,String contextToken) {
        public EditRequest(int protocolVersion,String documentId,String expectedSnapshotId,Intent intent,boolean requireOpenDocuments){this(protocolVersion,documentId,expectedSnapshotId,intent,requireOpenDocuments,null);}
    }
    public record EditResponse(Context context,EditCapability capability,String planId,WorkspaceEdit edit,List<String> notices) { }
    public record ValidationRequest(int protocolVersion,String planId,String expectedSnapshotId) { }
    public record ValidationResponse(int protocolVersion,boolean current,List<String> reasons) { }
    public record NavigationRequest(int protocolVersion,String documentId,String expectedSnapshotId,String elementId,Integer offsetUtf16,String contextToken) {
        public NavigationRequest(int protocolVersion,String documentId,String expectedSnapshotId,String elementId,Integer offsetUtf16){this(protocolVersion,documentId,expectedSnapshotId,elementId,offsetUtf16,null);}
    }
    public record NavigationResponse(Context context,Location location,List<String> elements,SourceContextInfo sourceContext) {
        public NavigationResponse(Context context,Location location,List<String> elements){this(context,location,elements,null);}
    }
    static Context context(AnalysisSnapshot snapshot) {
        var capture=snapshot.analysis().capture();
        var stamps=capture.project().sources().versions().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->{var stamp=e.getValue().stamp();return new SourceStamp(e.getKey().toString(),stamp==null?null:stamp.contentSha256(),stamp==null?null:stamp.bufferVersion(),e.getValue().bufferIncarnation()==null?null:e.getValue().bufferIncarnation().toString(),stamp!=null&&stamp.editable(),e.getValue().unavailableCode());}).toList();
        return new Context(VERSION,1,capture.serverInstance().toString(),capture.project().id().toString(),Long.toString(capture.generation()),snapshot.id().toString(),capture.catalog().result().catalog().map(c->c.catalogId()).orElse(null),stamps,snapshot.analysis().capabilities(),List.of(),Long.toString(capture.ownership().generation()),snapshot.contextSelection()==null?null:Long.toString(snapshot.contextSelection().generation()),snapshot.contextSelection()==null?null:snapshot.contextSelection().application().sourceDocument().toString(),snapshot.contextSelection()==null?null:snapshot.contextSelection().token());
    }
    static Map<String,EditableValue> values(Map<String,Value> fields) {
        if(fields==null)return Map.of();if(fields.size()>128)throw new IllegalArgumentException("EDIT_FIELD_LIMIT");
        var result=new LinkedHashMap<String,EditableValue>();fields.forEach((name,value)->result.put(required(name,1024),value.core()));return Map.copyOf(result);
    }
    static String required(String value,int max){if(value==null||value.length()>max)throw new IllegalArgumentException("PROTOCOL_FIELD_LIMIT");return value;}
    static void version(int version){if(version!=VERSION)throw new IllegalArgumentException("PROTOCOL_VERSION_MISMATCH");}
}
