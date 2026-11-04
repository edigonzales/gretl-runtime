package ch.so.agi.gretl.tooling.workspace;

import ch.so.agi.gretl.tooling.CancellationToken;
import java.net.URI;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FileSourceRepositoryTest {
    @TempDir Path temp;
    @Test void boundedUtf8ReadsKeepBomCrlfAndDetectSameSizeSameTimeChanges() throws Exception {
        var root=Files.createDirectory(temp.resolve("project"));var path=root.resolve("job.groovy");Files.writeString(path,"\uFEFFabc\r\n");var repository=new FileSourceRepository(root.toUri());var policy=new SourceRepository.ReadPolicy(root.toUri(),1024,true);
        var first=repository.read(path.toUri(),policy,CancellationToken.NONE).source().orElseThrow();assertEquals("\uFEFFabc\r\n",first.text());assertNotNull(first.stamp().diskIdentity());assertNull(first.stamp().bufferVersion());
        var time=Files.getLastModifiedTime(path);Files.writeString(path,"\uFEFFxyz\r\n");Files.setLastModifiedTime(path,time);var next=repository.read(path.toUri(),policy,CancellationToken.NONE).source().orElseThrow();assertNotEquals(first.stamp().diskIdentity(),next.stamp().diskIdentity());
        assertTrue(repository.read(path.toUri(),new SourceRepository.ReadPolicy(root.toUri(),3,true),CancellationToken.NONE).issues().stream().anyMatch(i->i.code().equals("SOURCE_SIZE_LIMIT")));
        Files.write(path,new byte[]{(byte)0xc3,0x28});assertEquals("SOURCE_ENCODING",repository.read(path.toUri(),policy,CancellationToken.NONE).issues().getFirst().code());
    }
    @Test void neitherLeafNorDirectoryLinksOrTraversalCanReadOutsideTheRegisteredRoot() throws Exception {
        var root=Files.createDirectory(temp.resolve("project"));var outside=Files.createDirectory(temp.resolve("external"));var secret=outside.resolve("secret.sql");Files.writeString(secret,"sensitive fixture");
        Files.createSymbolicLink(root.resolve("leaf.sql"),secret);Files.createSymbolicLink(root.resolve("linked"),outside);var repository=new FileSourceRepository(root.toUri());var policy=new SourceRepository.ReadPolicy(root.toUri(),1024,true);
        for(var id:new URI[]{root.resolve("leaf.sql").toUri(),root.resolve("linked/secret.sql").toUri(),root.resolve("../external/secret.sql").toUri(),URI.create("http://127.0.0.1:1/secret.sql")})assertTrue(repository.read(id,policy,CancellationToken.NONE).source().isEmpty(),id.toString());
        var excluded=new SourceRepository.ReadPolicy(root.toUri(),1024,true,java.util.List.of(root.resolve("private").toUri()));Files.createDirectory(root.resolve("private"));Files.writeString(root.resolve("private/x.sql"),"private fixture");assertTrue(repository.read(root.resolve("private/x.sql").toUri(),excluded,CancellationToken.NONE).source().isEmpty());
    }
    @Test void replacingTheRegisteredDirectoryCannotSilentlyChangeSourceOwnership() throws Exception {
        var root=Files.createDirectory(temp.resolve("project"));Files.writeString(root.resolve("job.groovy"),"old");var repository=new FileSourceRepository(root.toUri());Files.move(root,temp.resolve("moved"));Files.createDirectory(root);Files.writeString(root.resolve("job.groovy"),"new owner");
        assertTrue(repository.read(root.resolve("job.groovy").toUri(),new SourceRepository.ReadPolicy(root.toUri(),1024,true),CancellationToken.NONE).source().isEmpty());
    }
    @Test void scanningIsBoundedAndIgnoresBuildCachesWithoutFollowingLinks() throws Exception {
        var root=Files.createDirectory(temp.resolve("project"));Files.writeString(root.resolve("settings.gretl"),"include 'sub'");Files.writeString(root.resolve("job.groovy"),"action('a'){}");var target=Files.createDirectory(root.resolve("target"));Files.writeString(target.resolve("job.groovy"),"ignored");
        var repository=new FileSourceRepository(root.toUri());var all=repository.listProjectEntries(root.toUri(),SourceRepository.ScanBudget.defaults(),CancellationToken.NONE);assertTrue(all.complete(),all.issues().toString());assertEquals(2,all.entries().size());
        var limited=repository.listProjectEntries(root.toUri(),new SourceRepository.ScanBudget(1,10,20),CancellationToken.NONE);assertFalse(limited.complete());assertEquals(1,limited.entries().size());assertEquals("SOURCE_SCAN_LIMIT",limited.issues().getFirst().code());
        assertThrows(java.util.concurrent.CancellationException.class,()->repository.listProjectEntries(root.toUri(),SourceRepository.ScanBudget.defaults(),()->true));
    }
}
