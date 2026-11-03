package ch.so.agi.gretl.tooling.workspace;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.source.SourceText;
import java.net.URI;
import java.util.*;

/** Read-only, explicitly injected host boundary. No analyzer performs hidden disk or network IO. */
public interface SourceRepository {
    record ReadPolicy(URI root,int maximumBytes,boolean editable,List<URI> excludedRoots,Set<URI> explicitBuffers) {
        public ReadPolicy {
            root=SourceUris.directoryIdentity(Objects.requireNonNull(root));if(maximumBytes<1||maximumBytes>16_777_216)throw new IllegalArgumentException("Invalid read budget");excludedRoots=excludedRoots.stream().map(SourceUris::directoryIdentity).toList();explicitBuffers=Set.copyOf(explicitBuffers);
            if(explicitBuffers.size()>64||explicitBuffers.stream().anyMatch(id->!Set.of("untitled","memory").contains(id.getScheme())))throw new IllegalArgumentException("Explicit buffers must be bounded non-file documents");
        }
        public ReadPolicy(URI root,int maximumBytes,boolean editable,List<URI> excludedRoots){this(root,maximumBytes,editable,excludedRoots,Set.of());}
        public ReadPolicy(URI root,int maximumBytes,boolean editable){this(root,maximumBytes,editable,List.of(),Set.of());}
        public boolean permits(URI id){return explicitBuffers.contains(id)||(contained(root,id)&&excludedRoots.stream().noneMatch(r->contained(r,id)));}
        private static boolean contained(URI root,URI id){
            if(root.getScheme()==null||!root.getScheme().equalsIgnoreCase(id.getScheme())||!Objects.equals(root.getAuthority(),id.getAuthority())||root.getPath()==null||id.getPath()==null)return false;
            // Decode URI path segments before dot normalization, without any filesystem lookup.
            try {
                String base=new URI(null,null,root.getPath(),null).normalize().getPath();if(!base.endsWith("/"))base+="/";
                return new URI(null,null,id.getPath(),null).normalize().getPath().startsWith(base);
            }catch(java.net.URISyntaxException invalid){return false;}
        }
    }
    record ScanBudget(int maximumEntries,int maximumDepth,int maximumVisitedEntries) {
        public ScanBudget { if(maximumEntries<1||maximumEntries>4096||maximumDepth<1||maximumDepth>64||maximumVisitedEntries<maximumEntries||maximumVisitedEntries>65536)throw new IllegalArgumentException("Invalid scan budget"); }
        public static ScanBudget defaults(){return new ScanBudget(512,32,4096);}
    }
    record SourceIssue(String code,URI documentId) { }
    record SourceRead(Optional<SourceText> source,List<SourceIssue> issues) {
        public SourceRead { source=Objects.requireNonNull(source);issues=List.copyOf(issues); }
        public static SourceRead found(SourceText source){return new SourceRead(Optional.of(source),List.of());}
        public static SourceRead unavailable(String code,URI id){return new SourceRead(Optional.empty(),List.of(new SourceIssue(code,id)));}
    }
    record SourceListing(List<URI> entries,List<SourceIssue> issues,boolean complete) {
        public SourceListing { entries=List.copyOf(entries);issues=List.copyOf(issues); }
    }
    SourceRead read(URI id,ReadPolicy policy,CancellationToken cancellation);
    SourceListing listProjectEntries(URI root,ScanBudget budget,CancellationToken cancellation);
    default Optional<SourceText.SourceStamp> currentStamp(URI id,ReadPolicy policy,CancellationToken cancellation){return read(id,policy,cancellation).source().map(SourceText::stamp);}
}
