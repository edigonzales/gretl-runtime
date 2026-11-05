package ch.so.agi.gretl.lsp;

import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;

public interface GretlLanguageClient extends LanguageClient {
    @JsonNotification("gretl/sourceMembershipChanged") void sourceMembershipChanged(GretlProtocol.SourceMembershipChanged notification);
    @JsonNotification("gretl/analysisReady") void analysisReady(GretlProtocol.AnalysisReady notification);
}
