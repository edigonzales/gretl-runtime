package ch.so.agi.gretl.lsp;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import static ch.so.agi.gretl.lsp.GretlProtocol.*;

public interface GretlLanguageServerApi extends LanguageServer {
    @JsonRequest("gretl/sourceContexts") CompletableFuture<SourceContextsResponse> sourceContexts(SourceContextsRequest request);
    @JsonRequest("gretl/selectSourceContext") CompletableFuture<SourceContextsResponse> selectSourceContext(SelectSourceContextRequest request);
    @JsonRequest("gretl/confirmSourceMembership") CompletableFuture<ConfirmSourceMembershipResponse> confirmSourceMembership(ConfirmSourceMembershipRequest request);
    @JsonRequest("gretl/catalog") CompletableFuture<CatalogResponse> catalog(CatalogRequest request);
    @JsonRequest("gretl/graph") CompletableFuture<GraphResponse> graph(GraphRequest request);
    @JsonRequest("gretl/planEdit") CompletableFuture<EditResponse> planEdit(EditRequest request);
    @JsonRequest("gretl/validateEdit") CompletableFuture<ValidationResponse> validateEdit(ValidationRequest request);
    @JsonRequest("gretl/navigation") CompletableFuture<NavigationResponse> navigate(NavigationRequest request);
}
