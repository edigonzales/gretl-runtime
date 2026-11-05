package ch.so.agi.gretl.packager;

import ch.so.agi.gretl.runtime.groovy.fragment.*;
import java.nio.file.Path;
import java.util.*;

/** Exact script allowlist plus explicit helper sources, compiled from captured definition bytes. */
final class BundleCompiler {
    record Result(Map<String,byte[]> classes,List<JobCompiler.SourceFile> sources,CompiledScriptBundle bundle) { }
    static Result compile(ScriptBundleSources.Snapshot snapshot,PackageDescriptor descriptor,List<Path> classpath)throws PackageException{
        var bundle=snapshot.bundle();var captured=new LinkedHashMap<Path,JobCompiler.SourceFile>();
        var names=new LinkedHashMap<Path,String>();var ids=new LinkedHashMap<String,String>();
        for(var source:bundle.sources().values()){
            Path path=snapshot.root().resolve(source.sourceId()).toAbsolutePath().normalize();
            captured.put(path,new JobCompiler.SourceFile(source.sourceId(),path,source.text()));
            if(!source.sourceId().equals(bundle.settingsId())){names.put(path,bundle.virtualName(source.sourceId()));ids.put(bundle.virtualName(source.sourceId()),source.sourceId());}
        }
        var sources=new JobCompiler().sourceFiles(descriptor,List.of(),captured);
        var compilationSources=sources.stream().filter(s->!s.relativePath().equals(bundle.settingsId())).toList();
        JobCompiler.Compilation compiled;
        try{compiled=JobCompiler.compileSources(compilationSources,names,classpath,ids.keySet());}
        catch(PackageException failure){String message=failure.getMessage();for(var e:ids.entrySet())message=message.replace(e.getKey(),e.getValue());throw new PackageException(failure.code(),message,failure);}
        var classes=new LinkedHashMap<String,String>();
        for(var script:compiled.scripts()){
            String id=ids.get(script.sourceName());
            if(id==null)throw new PackageException("PACKAGE_MULTIPLE_SCRIPTS",PackagerMessages.format("package.project.hiddenScript",script.sourceName()));
            JobCompiler.requireBase(script);
            if(classes.putIfAbsent(id,script.name())!=null||!compiled.classes().containsKey(script.name().replace('.','/')+".class"))
                throw new PackageException("PACKAGE_ENTRY_BYTECODE_MISSING","Missing or duplicate bundle script: "+id);
        }
        if(classes.size()!=ids.size())throw new PackageException("PACKAGE_NO_SCRIPT",PackagerMessages.get("package.noScript"));
        return new Result(compiled.classes(),sources,CompiledScriptBundle.from(bundle,classes));
    }
}
