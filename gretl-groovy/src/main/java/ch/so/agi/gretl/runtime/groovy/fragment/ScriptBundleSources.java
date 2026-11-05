package ch.so.agi.gretl.runtime.groovy.fragment;

import ch.so.agi.gretl.runtime.groovy.GroovyDslException;
import ch.so.agi.gretl.runtime.groovy.SourceLocation;
import ch.so.agi.gretl.runtime.groovy.project.GretlProject;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Per-definition capture; never a global cache. A null snapshot selects the untouched legacy loader. */
public final class ScriptBundleSources {
    private ScriptBundleSources() { }
    public record Snapshot(Path root,FragmentGraphResolver.Bundle bundle) { }
    public static Snapshot single(Path file,Locale locale)throws IOException{
        file=file.toAbsolutePath().normalize();Path root=file.getParent();String id=file.getFileName().toString();
        byte[] bytes=Files.readAllBytes(file);
        var result=new ApplyDirectiveReader().read(id,new String(bytes,StandardCharsets.UTF_8));
        if(!result.applySeen())return null;
        result.requireValid(locale);
        return resolve(root,List.of(new FragmentGraphResolver.Entry("",id)),false,null,Map.of(id,source(id,bytes,locale)),locale);
    }
    public static Snapshot project(GretlProject project,Locale locale)throws IOException{
        var entries=project.entries().stream().map(e->new FragmentGraphResolver.Entry(e.projectPath().canonical(),
                project.root().relativize(e.scriptFile()).toString().replace('\\','/'))).toList();
        Map<String,byte[]> raw=new LinkedHashMap<>();boolean includes=false;
        for(var entry:entries){
            byte[] bytes=Files.readAllBytes(project.root().resolve(entry.sourceId()));raw.put(entry.sourceId(),bytes);
            var result=new ApplyDirectiveReader().read(entry.sourceId(),new String(bytes,StandardCharsets.UTF_8));
            if(result.applySeen()){includes=true;result.requireValid(locale);}
        }
        if(!includes)return null;
        Map<String,FragmentSource> captured=new LinkedHashMap<>();raw.forEach((id,bytes)->captured.put(id,source(id,bytes,locale)));
        String settingsId=project.root().relativize(project.settingsFile()).toString().replace('\\','/');
        byte[] settings=project.settingsSource()==null?Files.readAllBytes(project.settingsFile()):project.settingsSource().getBytes(StandardCharsets.UTF_8);
        captured.put(settingsId,source(settingsId,settings,locale));
        return resolve(project.root(),entries,true,settingsId,captured,locale);
    }
    private static Snapshot resolve(Path root,List<FragmentGraphResolver.Entry> entries,boolean project,String settings,
                                    Map<String,FragmentSource> captured,Locale locale){
        var disk=new ContainedFragmentSources(root,locale);
        FragmentSourceAccess access=new FragmentSourceAccess(){
            @Override public Locale locale(){return locale;}
            @Override public String canonicalId(String id)throws IOException{return disk.canonicalId(id);}
            @Override public String resolve(String caller,String from)throws IOException{return disk.resolve(caller,from);}
            @Override public FragmentSource read(String id,boolean fragment)throws IOException{
                return captured.containsKey(id)?captured.get(id):disk.read(id,fragment);
            }
        };
        return new Snapshot(root,new FragmentGraphResolver().resolve(entries,project,settings,access));
    }
    private static FragmentSource source(String id,byte[] bytes,Locale locale){
        try{return new FragmentSource(id,bytes);}
        catch(IllegalArgumentException failure){throw new GroovyDslException("APPLY_SYNTAX",
                ch.so.agi.gretl.i18n.Messages.format(locale,"apply.APPLY_SYNTAX",id),null,null,new SourceLocation(id,0,0),List.of(),failure);}
    }
}
