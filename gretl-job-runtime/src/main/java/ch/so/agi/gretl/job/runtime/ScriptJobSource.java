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
        return RuntimeInfo.scriptHash(file);
    }

    @Override
    public JobDefinition load(OperationRegistry registry, Path startCwd, UserMessageSink sink) {
        DefinitionEnvironment environment = new DefinitionEnvironment(
                registry, jobRoot(), startCwd, sink, locale);
        return new GroovyJobLoader().load(file, environment);
    }
}
