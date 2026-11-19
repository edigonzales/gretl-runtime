package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.runtime.groovy.DefinitionEnvironment;
import ch.so.agi.gretl.runtime.groovy.GroovyJobLoader;
import ch.so.agi.gretl.runtime.groovy.UserMessageSink;
import ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleSources;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Normal Groovy script source. */
public final class ScriptJobSource implements JobSource {
    private final Path file;

    public ScriptJobSource(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    @Override
    public Path displayFile() {
        return file;
    }

    @Override
    public String jobName() {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override
    public Loaded load(OperationRegistry registry, Path startCwd, UserMessageSink sink, Locale locale) {
        var snapshot = ScriptBundleSources.single(file, locale);
        var environment = new DefinitionEnvironment(registry, snapshot.root(), startCwd, sink, locale);
        var definition = new GroovyJobLoader().loadBundle(snapshot, environment, List.of(), Map.of());
        return new Loaded(definition, snapshot.bundle().sha256());
    }
}
