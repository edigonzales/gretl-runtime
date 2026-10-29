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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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

    @Override public String sha256() {
        GretlProject project = parse();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("gretl-project-source-v1\0".getBytes(StandardCharsets.UTF_8));
            hashFile(digest, project, project.settingsFile());
            for (var entry : project.entries()) hashFile(digest, project, entry.scriptFile());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private void hashFile(MessageDigest digest, GretlProject project, Path file) {
        String relative = java.util.stream.StreamSupport.stream(project.root().relativize(file).spliterator(), false)
                .map(Path::toString).collect(java.util.stream.Collectors.joining("/"));
        byte[] path = relative.getBytes(StandardCharsets.UTF_8);
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readAllBytes();
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(path.length).array());
            digest.update(path);
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());
            digest.update(bytes);
        } catch (IOException failure) {
            throw new ProjectException("PROJECT_SOURCE_READ_FAILED", Messages.format(locale,
                    "project.sourceRead", file), new SourceLocation(file.toString(), 0, 0), failure);
        }
    }
}
