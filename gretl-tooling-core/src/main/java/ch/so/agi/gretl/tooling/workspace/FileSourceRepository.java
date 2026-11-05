package ch.so.agi.gretl.tooling.workspace;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.catalog.CatalogJsonCodec;
import ch.so.agi.gretl.tooling.source.SourceText;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Explicit opt-in host adapter, pinned to one registered root. Secure relative handles never follow links. */
public final class FileSourceRepository implements SourceRepository {
    private final URI rootId;
    private final Path logicalRoot,physicalRoot;
    private final Object rootKey;
    public FileSourceRepository(URI root) throws IOException {
        rootId=SourceUris.directoryIdentity(root);logicalRoot=localPath(rootId);
        if(Files.isSymbolicLink(logicalRoot)||!Files.isDirectory(logicalRoot,LinkOption.NOFOLLOW_LINKS))throw new IOException("SOURCE_ROOT_UNSAFE");
        physicalRoot=logicalRoot.toRealPath();if(physicalRoot.getNameCount()>256)throw new IOException("SOURCE_ROOT_DEPTH_LIMIT");rootKey=Files.readAttributes(physicalRoot,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();
        if(rootKey==null)throw new IOException("SOURCE_ROOT_IDENTITY_UNAVAILABLE");
    }
    @Override public SourceRead read(URI id,ReadPolicy policy,CancellationToken cancellation){
        try{
            id=DocumentStore.identity(id);cancellation.check();Path file=localPath(id);
            if(!SourceUris.directoryIdentity(policy.root()).equals(rootId)||!policy.permits(id)||!file.startsWith(logicalRoot))return SourceRead.unavailable("SOURCE_OUTSIDE_POLICY",id);
            Path relative=logicalRoot.relativize(file);if(relative.toString().isEmpty())return SourceRead.unavailable("SOURCE_NOT_REGULAR",id);
            if(relative.getNameCount()>256)return SourceRead.unavailable("SOURCE_PATH_LIMIT",id);
            try(var root=openPinnedRoot()){
                SecureDirectoryStream<Path> current=root;boolean ownsCurrent=false;
                try{
                    for(int i=0;i<relative.getNameCount()-1;i++){
                        cancellation.check();var next=current.newDirectoryStream(relative.getName(i),LinkOption.NOFOLLOW_LINKS);
                        if(ownsCurrent)current.close();current=next;ownsCurrent=true;
                    }
                    Path name=relative.getFileName();var attributes=current.getFileAttributeView(name,BasicFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
                    var before=attributes.readAttributes();if(!before.isRegularFile()||before.isSymbolicLink())return SourceRead.unavailable("SOURCE_NOT_REGULAR",id);
                    if(before.size()>policy.maximumBytes())return SourceRead.unavailable("SOURCE_SIZE_LIMIT",id);
                    byte[] bytes;
                    try(var input=Channels.newInputStream(current.newByteChannel(name,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)))){bytes=input.readNBytes(policy.maximumBytes()+1);}
                    cancellation.check();if(bytes.length>policy.maximumBytes())return SourceRead.unavailable("SOURCE_SIZE_LIMIT",id);
                    var after=attributes.readAttributes();
                    if(!Objects.equals(before.fileKey(),after.fileKey())||before.size()!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())||!after.isRegularFile())return SourceRead.unavailable("SOURCE_CHANGED_DURING_READ",id);
                    String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                    String disk=after.fileKey()+"|"+after.size()+"|"+after.lastModifiedTime()+"|"+CatalogJsonCodec.hash(bytes);
                    return SourceRead.found(new SourceText(id,text,null,"DISK",policy.editable(),disk));
                }finally{if(ownsCurrent)current.close();}
            }
        }catch(java.util.concurrent.CancellationException cancelled){throw cancelled;}
        catch(CharacterCodingException invalid){return SourceRead.unavailable("SOURCE_ENCODING",id);}
        catch(NoSuchFileException missing){return SourceRead.unavailable("SOURCE_MISSING",id);}
        catch(UnsupportedOperationException unsupported){return SourceRead.unavailable("SOURCE_READ_POLICY_UNSUPPORTED",id);}
        catch(IOException|IllegalArgumentException failure){return SourceRead.unavailable("SOURCE_UNSAFE_OR_UNREADABLE",id);}
    }
    @Override public List<SourceIssue> checkIncludeTraversal(IncludeTraversal traversal,ReadPolicy policy,CancellationToken cancellation) {
        var opened=new ArrayList<SecureDirectoryStream<Path>>();
        try {
            if(!policy.permits(traversal.caller())||!policy.permits(traversal.definitionRoot())
                    &&!SourceUris.directoryIdentity(policy.root()).equals(traversal.definitionRoot()))
                return List.of(new SourceIssue("SOURCE_OUTSIDE_POLICY",traversal.caller()));
            Path caller=localPath(traversal.caller());
            Path relative=logicalRoot.relativize(caller.getParent()).resolve(traversal.literal()); // deliberately not normalized
            opened.add(openPinnedRoot());
            for(int i=0;i<relative.getNameCount()-1;i++) {
                cancellation.check();Path segment=relative.getName(i);String name=segment.toString();
                if(name.equals("."))continue;
                if(name.equals("..")) {
                    if(opened.size()==1)throw new IOException("Outside pinned root");
                    opened.remove(opened.size()-1).close();
                } else opened.add(opened.getLast().newDirectoryStream(segment,LinkOption.NOFOLLOW_LINKS));
            }
            return List.of();
        } catch(java.util.concurrent.CancellationException cancelled){throw cancelled;}
        catch(NoSuchFileException missing){return List.of(new SourceIssue("SOURCE_MISSING",traversal.caller()));}
        catch(IOException|UnsupportedOperationException|IllegalArgumentException unsafe){return List.of(new SourceIssue("SOURCE_UNSAFE_OR_UNREADABLE",traversal.caller()));}
        finally {for(int i=opened.size()-1;i>=0;i--)try{opened.get(i).close();}catch(IOException ignored){}}
    }
    @Override public SourceListing listProjectEntries(URI root,ScanBudget budget,CancellationToken cancellation){
        return listProjectEntries(root,budget,new ReadPolicy(root,1,false),cancellation);
    }
    @Override public SourceListing listProjectEntries(URI root,ScanBudget budget,ReadPolicy policy,CancellationToken cancellation){
        var entries=new ArrayList<URI>();var issues=new ArrayList<SourceIssue>();int[] visited={0};boolean[] limited={false};
        try{
            if(!SourceUris.directoryIdentity(root).equals(rootId)||!SourceUris.directoryIdentity(policy.root()).equals(rootId))return new SourceListing(List.of(),List.of(new SourceIssue("SOURCE_OUTSIDE_POLICY",root)),false);
            try(var directory=openPinnedRoot()){scan(directory,logicalRoot,0,budget,policy,cancellation,entries,issues,visited,limited);}
        }catch(java.util.concurrent.CancellationException cancelled){throw cancelled;}
        catch(IOException|UnsupportedOperationException failure){issues.add(new SourceIssue("SOURCE_SCAN_UNAVAILABLE",root));}
        return new SourceListing(entries,issues,!limited[0]&&issues.isEmpty());
    }
    private void scan(SecureDirectoryStream<Path> directory,Path logical,int depth,ScanBudget budget,ReadPolicy policy,CancellationToken cancellation,List<URI> entries,List<SourceIssue> issues,int[] visited,boolean[] limited) throws IOException {
        var names=new ArrayList<Path>();for(Path entry:directory){cancellation.check();if(++visited[0]>budget.maximumVisitedEntries()){limit(issues,limited,logical);return;}names.add(entry.getFileName());}
        names.sort(Comparator.comparing(Path::toString));
        for(Path name:names){
            cancellation.check();String text=name.toString();if(text.startsWith(".")||Set.of("target","node_modules","dist").contains(text))continue;
            Path child=logical.resolve(name);URI childId=DocumentStore.fileUri(child);
            if(policy.excludedRoots().stream().anyMatch(excluded->SourceUris.directoryIdentity(excluded).equals(SourceUris.directoryIdentity(childId))||SourceUris.contains(excluded,childId)))continue;
            var attributes=directory.getFileAttributeView(name,BasicFileAttributeView.class,LinkOption.NOFOLLOW_LINKS).readAttributes();
            if(attributes.isSymbolicLink()){
                if(text.equals("job.groovy")||text.equals("settings.gretl"))issues.add(new SourceIssue("SOURCE_UNSAFE_OR_UNREADABLE",DocumentStore.fileUri(child)));continue;
            }
            if(attributes.isDirectory()){
                if(depth>=budget.maximumDepth()){limit(issues,limited,child);return;}
                try(var nested=directory.newDirectoryStream(name,LinkOption.NOFOLLOW_LINKS)){scan(nested,child,depth+1,budget,policy,cancellation,entries,issues,visited,limited);}
                if(limited[0])return;
            }else if(attributes.isRegularFile()&&(text.equals("job.groovy")||text.equals("settings.gretl"))){
                if(entries.size()>=budget.maximumEntries()){limit(issues,limited,child);return;}entries.add(DocumentStore.fileUri(child));
            }
        }
    }
    private static void limit(List<SourceIssue> issues,boolean[] limited,Path at){limited[0]=true;issues.add(new SourceIssue("SOURCE_SCAN_LIMIT",DocumentStore.fileUri(at)));}
    /** Open every canonical parent with NOFOLLOW, including the root itself; no check/open symlink gap. */
    private SecureDirectoryStream<Path> openPinnedRoot() throws IOException {
        if(Files.isSymbolicLink(logicalRoot)||!logicalRoot.toRealPath().equals(physicalRoot))throw new IOException("SOURCE_ROOT_CHANGED");
        var opened=Files.newDirectoryStream(physicalRoot.getRoot());
        if(!(opened instanceof SecureDirectoryStream<Path> secure)){opened.close();throw new UnsupportedOperationException("Secure directory handles required");}
        SecureDirectoryStream<Path> current=secure;
        try{
            for(Path segment:physicalRoot){var next=current.newDirectoryStream(segment,LinkOption.NOFOLLOW_LINKS);current.close();current=next;}
            if(!Objects.equals(rootKey,current.getFileAttributeView(BasicFileAttributeView.class).readAttributes().fileKey()))throw new IOException("SOURCE_ROOT_CHANGED");
            return current;
        }catch(IOException|RuntimeException failure){current.close();throw failure;}
    }
    private static Path localPath(URI id){if(!"file".equalsIgnoreCase(id.getScheme())||id.getAuthority()!=null||id.getQuery()!=null)throw new IllegalArgumentException("No network source URI");return Path.of(id).toAbsolutePath().normalize();}
}
