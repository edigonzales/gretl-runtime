package ch.so.agi.gretl.lsp;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.workspace.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;

/** Secure bounded core file reader plus host editability metadata; no writes or provider discovery. */
final class LspHostSourceRepository implements SourceRepository {
    private final FileSourceRepository files;
    private final URI root;
    LspHostSourceRepository(URI root) throws IOException {this.root=root;files=new FileSourceRepository(root);}
    public SourceRead read(URI id,ReadPolicy policy,CancellationToken cancellation) {
        var read=files.read(id,policy,cancellation);
        if(read.source().isEmpty())return read;
        var source=read.source().orElseThrow();
        return SourceRead.found(new SourceText(id,source.text(),source.stamp().bufferVersion(),source.stamp().origin(),source.stamp().editable()&&writable(id,root),source.stamp().diskIdentity()));
    }
    public java.util.List<SourceIssue> checkIncludeTraversal(IncludeTraversal traversal,ReadPolicy policy,CancellationToken cancellation){return files.checkIncludeTraversal(traversal,policy,cancellation);}
    public PathListing listIncludePaths(URI directory,ReadPolicy policy,CancellationToken cancellation){return files.listIncludePaths(directory,policy,cancellation);}
    public SourceListing listProjectEntries(URI root,ScanBudget budget,CancellationToken cancellation){return files.listProjectEntries(root,budget,cancellation);}
    public SourceListing listProjectEntries(URI root,ScanBudget budget,ReadPolicy policy,CancellationToken cancellation){return files.listProjectEntries(root,budget,policy,cancellation);}
    static boolean writable(URI id,URI root) {
        if(!"file".equals(id.getScheme()))return "untitled".equals(id.getScheme());
        try {
            Path path=Path.of(id);
            Path boundary=Path.of(root).toAbsolutePath().normalize();
            if(!path.startsWith(boundary))return false;
            for(Path ancestor=path;ancestor!=null&&!ancestor.equals(boundary);ancestor=ancestor.getParent())if(Files.isSymbolicLink(ancestor))return false;
            if(Files.isSymbolicLink(path))return false;
            if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return Files.isWritable(path.getParent());
            if(!Files.isWritable(path))return false;
            try {var permissions=Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS);return permissions.contains(PosixFilePermission.OWNER_WRITE)||permissions.contains(PosixFilePermission.GROUP_WRITE)||permissions.contains(PosixFilePermission.OTHERS_WRITE);}
            catch(UnsupportedOperationException otherPlatform){return true;}
        }catch(IOException|IllegalArgumentException denied){return false;}
    }
}
