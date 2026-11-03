package ch.so.agi.gretl.tooling.workspace;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.source.SourceText;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static ch.so.agi.gretl.tooling.workspace.SourceRepository.*;

/** One bounded acquisition. Overlay values are frozen up front; every read is memoized, including absence. */
public final class SourceView {
    public record SourceVersion(SourceText.SourceStamp stamp,Long bufferIncarnation,String unavailableCode) { }
    public record SnapshotSources(Map<URI,SourceRead> reads,Map<URI,SourceVersion> versions,Map<URI,DocumentStore.OpenDocument> buffers,boolean limited) implements SourceRepository {
        public SnapshotSources { reads=Map.copyOf(reads);versions=Map.copyOf(versions);buffers=Map.copyOf(buffers); }
        @Override public SourceRead read(URI id,ReadPolicy policy,CancellationToken cancellation){
            cancellation.check();id=DocumentStore.identity(id);
            if(!policy.permits(id))return SourceRead.unavailable("SOURCE_OUTSIDE_POLICY",id);
            return reads.getOrDefault(id,SourceRead.unavailable("SOURCE_NOT_CAPTURED",id));
        }
        @Override public SourceListing listProjectEntries(URI root,ScanBudget budget,CancellationToken cancellation){
            cancellation.check();var policy=new ReadPolicy(root,1,false);
            var entries=reads.entrySet().stream().filter(e->e.getValue().source().isPresent()&&policy.permits(e.getKey())).map(Map.Entry::getKey).filter(id->id.getPath()!=null&&(id.getPath().endsWith("/job.groovy")||id.getPath().endsWith("/settings.gretl"))).sorted().toList();
            boolean complete=entries.size()<=budget.maximumEntries();return new SourceListing(entries.stream().limit(budget.maximumEntries()).toList(),complete?List.of():List.of(new SourceIssue("SOURCE_SCAN_LIMIT",root)),complete);
        }
        public Map<URI,SourceText> texts(){var result=new LinkedHashMap<URI,SourceText>();reads.forEach((id,r)->r.source().ifPresent(s->result.put(id,s)));return Collections.unmodifiableMap(result);}
    }
    private final SourceRepository disk;
    private final ReadPolicy policy;
    private final Map<URI,DocumentStore.OpenDocument> buffers;
    private final Map<URI,SourceRead> reads=new LinkedHashMap<>();
    private final Map<URI,SourceVersion> versions=new LinkedHashMap<>();
    private final int maximumSources,maximumTotalUtf16;
    private int usedUtf16;
    private boolean limited;
    public SourceView(SourceRepository disk,ReadPolicy policy,DocumentStore.BufferCapture overlay){this(disk,policy,overlay,512,4_194_304);}
    public SourceView(SourceRepository disk,ReadPolicy policy,DocumentStore.BufferCapture overlay,int maximumSources,int maximumTotalUtf16){
        this.disk=Objects.requireNonNull(disk);this.policy=Objects.requireNonNull(policy);buffers=overlay.documents();
        if(maximumSources<1||maximumSources>4096||maximumTotalUtf16<1||maximumTotalUtf16>33_554_432)throw new IllegalArgumentException("Invalid capture budget");this.maximumSources=maximumSources;this.maximumTotalUtf16=maximumTotalUtf16;
    }
    public SourceRead read(URI id,CancellationToken cancellation){
        cancellation.check();id=DocumentStore.identity(id);var cached=reads.get(id);if(cached!=null)return cached;
        if(reads.size()>=maximumSources){limited=true;return SourceRead.unavailable("SOURCE_CAPTURE_LIMIT",id);}
        int bytes=id.getPath()!=null&&id.getPath().endsWith("/settings.gretl")?Math.min(policy.maximumBytes(),262_144):policy.maximumBytes();
        var perFile=new ReadPolicy(policy.root(),bytes,policy.editable(),policy.excludedRoots(),policy.explicitBuffers());var buffer=buffers.get(id);SourceRead result;
        if(!perFile.permits(id)){result=SourceRead.unavailable("SOURCE_OUTSIDE_POLICY",id);buffer=null;}
        else if(buffer!=null){
            result=buffer.source().text().getBytes(StandardCharsets.UTF_8).length>bytes?SourceRead.unavailable("SOURCE_SIZE_LIMIT",id):SourceRead.found(buffer.source());
        }else result=disk.read(id,perFile,cancellation);
        var text=result.source().orElse(null);var stamp=text==null?(buffer==null?null:buffer.source().stamp()):text.stamp();
        if(text!=null){if((long)usedUtf16+text.text().length()>maximumTotalUtf16){limited=true;result=SourceRead.unavailable("SOURCE_CAPTURE_LIMIT",id);}else usedUtf16+=text.text().length();}
        reads.put(id,result);versions.put(id,new SourceVersion(stamp,buffer==null?null:buffer.incarnation(),result.source().isEmpty()?result.issues().getFirst().code():null));return result;
    }
    public SnapshotSources freeze(){
        var used=new LinkedHashMap<URI,DocumentStore.OpenDocument>();versions.forEach((id,version)->{if(version.bufferIncarnation()!=null)used.put(id,buffers.get(id));});
        return new SnapshotSources(reads,versions,used,limited);
    }
    public ReadPolicy policy(){return policy;}
    SourceVersion version(URI id){return versions.get(DocumentStore.identity(id));}
}
