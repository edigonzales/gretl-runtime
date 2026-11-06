package ch.so.agi.gretl.tooling.workspace;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.source.SourceText;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class InMemoryWorkspaceSources implements SourceRepository {
    static final URI ROOT=URI.create("file:/workspace"), JOB=URI.create("file:/workspace/job.groovy"), SETTINGS=URI.create("file:/workspace/settings.gretl");
    final Map<URI,String> texts=new ConcurrentHashMap<>();
    public SourceRead read(URI id,ReadPolicy policy,CancellationToken cancellation){cancellation.check();String text=texts.get(id);return text==null?SourceRead.unavailable("SOURCE_MISSING",id):SourceRead.found(new SourceText(id,text,null,"DISK",true,text));}
    public SourceListing listProjectEntries(URI root,ScanBudget budget,CancellationToken cancellation){return new SourceListing(List.copyOf(texts.keySet()),List.of(),true);}
}
