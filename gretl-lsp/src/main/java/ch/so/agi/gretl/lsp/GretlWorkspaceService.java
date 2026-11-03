package ch.so.agi.gretl.lsp;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.WorkspaceService;

final class GretlWorkspaceService implements WorkspaceService {
    private final GretlLanguageServer server;
    GretlWorkspaceService(GretlLanguageServer server){this.server=server;}
    public void didChangeConfiguration(DidChangeConfigurationParams params){server.notification(()-> {
        var previous=server.workspace().configuration;
        var next=ServerConfiguration.decode(new Gson().toJsonTree(params.getSettings()),previous);
        server.workspace().configure(next);
    });}
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params){server.notification(()-> {
        if(params.getChanges().size()>512)throw new IllegalArgumentException("WATCHED_FILES_LIMIT");
        server.workspace().sourcesChanged(params.getChanges().stream().map(event->server.workspace().document(event.getUri())).collect(Collectors.toSet()));
    });}
    public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params){server.notification(()-> {
        try {
            server.workspace().updateRoots(params.getEvent().getAdded().stream().map(folder->URI.create(folder.getUri())).toList(),
                    params.getEvent().getRemoved().stream().map(folder->URI.create(folder.getUri())).toList());
        }catch(IOException invalid){throw new IllegalArgumentException("WORKSPACE_ROOT_UNSAFE");}
    });}
}
