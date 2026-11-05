package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.runtime.groovy.DefinitionEnvironment;
import ch.so.agi.gretl.runtime.groovy.GroovyJobLoader;
import ch.so.agi.gretl.runtime.groovy.UserMessageSink;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.nio.file.Path;
import java.util.Locale;

/** Normal Groovy script source. */
public final class ScriptJobSource implements JobSource {
    private final Path file;
    private final Locale locale;
    private ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleSources.Snapshot loadedSnapshot;
    private boolean loaded;

    public ScriptJobSource(Path file) {
        this(file, Locale.GERMAN);
    }

    public ScriptJobSource(Path file, Locale locale) {
        this.file = file.toAbsolutePath().normalize();
        this.locale = java.util.Objects.requireNonNull(locale, "locale");
    }

    @Override
    public Path displayFile() {
        return file;
    }

    @Override
    public Path jobRoot() {
        return file.getParent();
    }

    @Override
    public String jobName() {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override
    public String sha256() {
        if(loadedSnapshot!=null)return loadedSnapshot.bundle().sha256();
        if(!loaded)try {
            var current=ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleSources.single(file,locale);
            if(current!=null)return current.bundle().sha256();
        }catch(java.io.IOException failure){throw new StartException(2,"Job file could not be read: "+file,failure);}
        return RuntimeInfo.scriptHash(file);
    }

    @Override
    public JobDefinition load(OperationRegistry registry, Path startCwd, UserMessageSink sink) {
        DefinitionEnvironment environment = new DefinitionEnvironment(
                registry, jobRoot(), startCwd, sink, locale);
        try {
            var snapshot=ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleSources.single(file,locale);
            var loader=new GroovyJobLoader();
            var definition=snapshot==null?loader.load(file,environment):loader.loadBundle(snapshot,environment,java.util.List.of(),java.util.Map.of());
            loadedSnapshot=snapshot;loaded=true;return definition;
        }catch(java.io.IOException failure){
            boolean missing=!java.nio.file.Files.isRegularFile(file);
            throw new ch.so.agi.gretl.runtime.groovy.GroovyDslException(missing?"JOB_NOT_FOUND":"JOB_LOAD_FAILED",
                    ch.so.agi.gretl.i18n.Messages.format(locale,missing?"error.groovy.jobNotFound":"error.groovy.loadFailed",missing?file:failure.getMessage()),
                    null,null,ch.so.agi.gretl.runtime.groovy.SourceLocations.fallback(file),java.util.List.of(),failure);
        }
    }
}
