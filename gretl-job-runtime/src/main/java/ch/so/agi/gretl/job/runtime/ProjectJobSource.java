package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.groovy.DefinitionEnvironment;
import ch.so.agi.gretl.runtime.groovy.GroovyJobLoader;
import ch.so.agi.gretl.runtime.groovy.SourceLocation;
import ch.so.agi.gretl.runtime.groovy.UserMessageSink;
import ch.so.agi.gretl.runtime.groovy.project.GretlProject;
import ch.so.agi.gretl.runtime.groovy.project.GretlProjectParser;
import ch.so.agi.gretl.runtime.groovy.project.ProjectException;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Explicit project definitions on disk; execution still uses the common runtime and runner. */
public final class ProjectJobSource implements JobSource {
    private final Path directory;
    private final Locale locale;
    private GretlProject parsed;

    public ProjectJobSource(Path directory, Locale locale) {
        this.directory = java.util.Objects.requireNonNull(directory).toAbsolutePath().normalize();
        this.locale = java.util.Objects.requireNonNull(locale);
    }

    ProjectJobSource withLocale(Locale value) { return new ProjectJobSource(directory, value); }

    private GretlProject parse() {
        parsed = new GretlProjectParser(locale).parse(directory);
        return parsed;
    }

    @Override public Path displayFile() { return jobRoot().resolve("settings.gretl"); }
    @Override public Path jobRoot() { return parsed == null ? directory : parsed.root(); }
    @Override public String jobName() { return directory.getFileName() == null ? "project" : directory.getFileName().toString(); }

    @Override public JobDefinition load(OperationRegistry registry, Path startCwd, UserMessageSink sink) {
        GretlProject project = parse();
        return new GroovyJobLoader().loadProject(project,
                new DefinitionEnvironment(registry, project.root(), startCwd, sink, locale));
    }

    @Override public boolean projectMode() { return true; }

    @Override public String sha256() {
        GretlProject project = parse();
        try { return ch.so.agi.gretl.runtime.groovy.project.ProjectSourceHash.sha256(project); }
        catch (IOException failure) {
            Path failed = failure instanceof java.nio.file.FileSystemException fs && fs.getFile() != null
                    ? Path.of(fs.getFile()) : project.settingsFile();
            throw new ProjectException("PROJECT_SOURCE_READ_FAILED", Messages.format(locale,
                    "project.sourceRead", failed), new SourceLocation(failed.toString(), 0, 0), failure);
        }
    }
}
