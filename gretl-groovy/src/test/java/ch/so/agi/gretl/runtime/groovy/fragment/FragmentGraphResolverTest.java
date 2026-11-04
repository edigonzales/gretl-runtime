package ch.so.agi.gretl.runtime.groovy.fragment;

import ch.so.agi.gretl.runtime.groovy.GroovyDslException;
import ch.so.agi.gretl.runtime.groovy.project.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class FragmentGraphResolverTest {
    @TempDir Path root;
    void write(String id,String text)throws Exception{Path p=root.resolve(id);Files.createDirectories(p.getParent());Files.writeString(p,text);}
    FragmentGraphResolver.Bundle single(){return new FragmentGraphResolver().resolve(List.of(new FragmentGraphResolver.Entry("","job.groovy")),false,null,new ContainedFragmentSources(root));}
    void code(String expected){assertEquals(expected,assertThrows(GroovyDslException.class,this::single).code());}
    @Test void nestedSourcesHaveDistinctNamesConsistentBytesAndHash()throws Exception{
        write("job.groovy","apply from:'tasks/a.groovy'\n");write("tasks/a.groovy","apply from:'../shared/a.groovy'\n");write("shared/a.groovy","// original\n");
        var first=single();assertEquals(3,first.sources().size());assertEquals(List.of("tasks/a.groovy","shared/a.groovy"),first.edges().stream().map(FragmentGraphResolver.Edge::targetId).toList());
        assertNotEquals(first.virtualName("tasks/a.groovy"),first.virtualName("shared/a.groovy"));
        write("shared/a.groovy","// changed\n");assertNotEquals(first.sha256(),single().sha256());assertEquals("// original\n",first.sources().get("shared/a.groovy").text());
        byte[] exposed=first.sources().get("shared/a.groovy").bytes();exposed[0]=0;assertEquals("// original\n",first.sources().get("shared/a.groovy").text());
        Path copy=Files.createTempDirectory(root.getParent(),"relocated-");
        try {for(var e:single().sources().values()){Path p=copy.resolve(e.sourceId());Files.createDirectories(p.getParent());Files.write(p,e.bytes());}
            var moved=new FragmentGraphResolver().resolve(List.of(new FragmentGraphResolver.Entry("","job.groovy")),false,null,new ContainedFragmentSources(copy));assertEquals(single().sha256(),moved.sha256());
        }finally{try(var paths=Files.walk(copy)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
    @Test void snapshotReadsEveryPhysicalSourceOnceAcrossScopes()throws Exception{
        write("a/job.groovy","apply from:'../shared.groovy'");write("b/job.groovy","apply from:'../shared.groovy'");write("shared.groovy","// shared");
        Map<String,Integer> reads=new HashMap<>();var disk=new ContainedFragmentSources(root);
        FragmentSourceAccess access=new FragmentSourceAccess(){public FragmentSource read(String id,boolean fragment)throws java.io.IOException{reads.merge(id,1,Integer::sum);return disk.read(id,fragment);}public String resolve(String caller,String from)throws java.io.IOException{return disk.resolve(caller,from);}};
        var bundle=new FragmentGraphResolver().resolve(List.of(new FragmentGraphResolver.Entry(":a","a/job.groovy"),new FragmentGraphResolver.Entry(":b","b/job.groovy")),true,null,access);
        assertEquals(2,bundle.edges().size());assertEquals(Map.of("a/job.groovy",1,"b/job.groovy",1,"shared.groovy",1),reads);
    }
    @Test void duplicateAndCycleHaveSeparateCodesAndIncludeChains()throws Exception{
        write("job.groovy","apply from:'a.groovy'\napply from:'b.groovy'");write("a.groovy","apply from:'shared.groovy'");write("b.groovy","apply from:'shared.groovy'");write("shared.groovy","// shared");
        var failure=assertThrows(GroovyDslException.class,this::single);assertEquals("APPLY_DUPLICATE",failure.code());assertTrue(failure.getMessage().contains("a.groovy"));assertTrue(failure.getMessage().contains("b.groovy"));
        write("shared.groovy","apply from:'a.groovy'");code("APPLY_CYCLE");
        write("job.groovy","apply from:'job.groovy'");code("APPLY_CYCLE");
    }
    @ParameterizedTest @ValueSource(strings={"../escape.groovy","/absolute.groovy","https://host/a.groovy","C:/a.groovy","//host/a.groovy","a\\\\b.groovy","*.groovy","x.txt","bad\\n.groovy"})
    void rejectsUnsafePaths(String path)throws Exception{write("job.groovy","apply from:'"+path+"'");code("APPLY_PATH_UNSAFE");}
    @Test void missingDirectoryAndSymlinksAreRejected()throws Exception{
        write("job.groovy","apply from:'missing.groovy'");code("APPLY_NOT_FOUND");Files.createDirectory(root.resolve("missing.groovy"));code("APPLY_NOT_FOUND");
        write("real/a.groovy","// real");Files.createSymbolicLink(root.resolve("link"),Path.of("real"));write("job.groovy","apply from:'link/a.groovy'");code("APPLY_PATH_UNSAFE");
        write("job.groovy","apply from:'link/../real/a.groovy'");code("APPLY_PATH_UNSAFE");
        Files.createSymbolicLink(root.resolve("file.groovy"),Path.of("real/a.groovy"));write("job.groovy","apply from:'file.groovy'");code("APPLY_PATH_UNSAFE");
    }
    @Test void allowsUnicodeSpacesAndPreservesOldHashes()throws Exception{
        write("job.groovy","// old\n");assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve("job.groovy")))),single().sha256());
        write("settings.gretl","include 'a'\n");write("a/job.groovy","// sub\n");var project=new GretlProjectParser().parse(root);
        var bundle=new FragmentGraphResolver().resolve(project.entries().stream().map(e->new FragmentGraphResolver.Entry(e.projectPath().canonical(),project.root().relativize(e.scriptFile()).toString().replace('\\','/'))).toList(),true,"settings.gretl",new ContainedFragmentSources(root));
        assertEquals(ProjectSourceHash.sha256(project),bundle.sha256());
        write("job.groovy","apply from:'ä space.groovy'");write("ä space.groovy","// okay");assertTrue(single().hasIncludes());
    }
    @Test void rejectsImportingRealProjectEntryAsFragment()throws Exception{
        write("job.groovy","apply from:'a/job.groovy'");write("a/job.groovy","// entry");
        var ex=assertThrows(GroovyDslException.class,()->new FragmentGraphResolver().resolve(List.of(new FragmentGraphResolver.Entry(":","job.groovy"),new FragmentGraphResolver.Entry(":a","a/job.groovy")),true,null,new ContainedFragmentSources(root)));assertEquals("APPLY_CONTEXT",ex.code());
    }
    @Test void canonicalEntryAndSettingsSpellingsHaveSameIdentity()throws Exception{
        write("settings.gretl","// settings");write("job.groovy","apply from:'fragment.groovy'");write("fragment.groovy","// fragment");Files.createDirectory(root.resolve("sub"));
        var resolver=new FragmentGraphResolver();var access=new ContainedFragmentSources(root);
        var canonical=resolver.resolve(List.of(new FragmentGraphResolver.Entry(":","job.groovy")),true,"settings.gretl",access);
        var spelled=resolver.resolve(List.of(new FragmentGraphResolver.Entry(":","./sub/../job.groovy")),true,"./settings.gretl",access);
        assertEquals(canonical.sha256(),spelled.sha256());assertEquals(canonical.sources().keySet(),spelled.sources().keySet());assertEquals(canonical.entries(),spelled.entries());assertEquals(canonical.edges(),spelled.edges());
    }
    @Test void canonicalRootIdsRejectAbsoluteEscapeAndHiddenSymlink()throws Exception{
        write("job.groovy","// entry");write("settings.gretl","// settings");Files.createDirectory(root.resolve("real"));Files.createSymbolicLink(root.resolve("link"),Path.of("real"));
        for(String id:List.of(root.resolve("job.groovy").toString(),"../job.groovy","link/../job.groovy")){
            var failure=assertThrows(GroovyDslException.class,()->new FragmentGraphResolver().resolve(List.of(new FragmentGraphResolver.Entry("",id)),false,null,new ContainedFragmentSources(root)));
            assertEquals("APPLY_PATH_UNSAFE",failure.code(),id);
        }
        var failure=assertThrows(GroovyDslException.class,()->new FragmentGraphResolver().resolve(List.of(new FragmentGraphResolver.Entry(":","job.groovy")),true,root.resolve("settings.gretl").toString(),new ContainedFragmentSources(root)));
        assertEquals("APPLY_PATH_UNSAFE",failure.code());
    }
    @Test void boundedReadAndDepthLimitAreEffective()throws Exception{
        write("job.groovy","apply from:'large.groovy'");Files.write(root.resolve("large.groovy"),new byte[ContainedFragmentSources.MAX_FRAGMENT_BYTES+1]);code("APPLY_LIMIT");
        for(int i=0;i<=FragmentGraphResolver.MAX_DEPTH;i++)write(i==0?"job.groovy":("f"+i+".groovy"),"apply from:'f"+(i+1)+".groovy'");write("f65.groovy","// end");code("APPLY_LIMIT");
    }
}
