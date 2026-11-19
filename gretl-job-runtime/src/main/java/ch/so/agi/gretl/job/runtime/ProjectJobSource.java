package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.runtime.groovy.DefinitionEnvironment;
import ch.so.agi.gretl.runtime.groovy.GroovyJobLoader;
import ch.so.agi.gretl.runtime.groovy.UserMessageSink;
import ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleSources;
import ch.so.agi.gretl.runtime.groovy.project.GretlProjectParser;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.nio.file.Path;
import java.util.Locale;

/** Explicit project definitions on disk; execution still uses the common runtime and runner. */
public final class ProjectJobSource implements JobSource {
    private final Path directory;

    public ProjectJobSource(Path directory) {
        this.directory = java.util.Objects.requireNonNull(directory).toAbsolutePath().normalize();
    }

    @Override public Path displayFile() { return directory.resolve("settings.gretl"); }
    @Override public String jobName() { return directory.getFileName() == null ? "project" : directory.getFileName().toString(); }

    @Override public boolean projectMode() { return true; }

    @Override
    public Loaded load(OperationRegistry registry, Path startCwd, UserMessageSink sink, Locale locale) {
        var project = new GretlProjectParser(locale).parse(directory);
        var snapshot = ScriptBundleSources.project(project, locale);
        var environment = new DefinitionEnvironment(registry, snapshot.root(), startCwd, sink, locale);
        var definition = new GroovyJobLoader().loadBundle(snapshot, environment,
                project.defaultTargets(), project.defaultTargetSources());
        return new Loaded(definition, snapshot.bundle().sha256());
    }
}
