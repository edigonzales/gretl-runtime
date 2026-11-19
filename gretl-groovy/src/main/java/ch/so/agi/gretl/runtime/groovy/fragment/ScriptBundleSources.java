package ch.so.agi.gretl.runtime.groovy.fragment;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.groovy.GroovyDslException;
import ch.so.agi.gretl.runtime.groovy.SourceLocation;
import ch.so.agi.gretl.runtime.groovy.project.GretlProject;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Captures the definition sources for one compilation. */
public final class ScriptBundleSources {
    private ScriptBundleSources() { }

    public record Snapshot(Path root, FragmentGraphResolver.Bundle bundle) { }

    public static Snapshot single(Path file, Locale locale) {
        Path script = file.toAbsolutePath().normalize();
        Path root = script.getParent();
        var entry = new FragmentGraphResolver.Entry("", script.getFileName().toString());
        try {
            var bundle = new FragmentGraphResolver().resolve(List.of(entry), null,
                    new ContainedFragmentSources(root, locale));
            return new Snapshot(root, bundle);
        } catch (GroovyDslException failure) {
            if (failure.source() != null) throw failure;
            var source = new SourceLocation(script.toString(), 0, 0);
            if (failure.code().equals("APPLY_PATH_UNSAFE")) {
                throw new GroovyDslException("JOB_SOURCE_UNSAFE",
                        Messages.get(locale, "error.groovy.jobSourceUnsafe"), null, null, source, List.of(), failure);
            }
            if (failure.code().equals("APPLY_NOT_FOUND")) {
                boolean missing = failure.getCause() == null || failure.getCause() instanceof NoSuchFileException;
                throw new GroovyDslException(missing ? "JOB_NOT_FOUND" : "JOB_LOAD_FAILED",
                        Messages.format(locale, missing ? "error.groovy.jobNotFound" : "error.groovy.loadFailed",
                                missing ? script : failure.getCause().getMessage()),
                        null, null, source, List.of(), failure);
            }
            throw failure;
        }
    }

    public static Snapshot project(GretlProject project, Locale locale) {
        Path root = project.root();
        var entries = project.entries().stream().map(entry -> new FragmentGraphResolver.Entry(
                entry.projectPath().canonical(),
                root.relativize(entry.scriptFile()).toString().replace('\\', '/'))).toList();
        var settings = new FragmentSource("settings.gretl", project.settingsSource().getBytes(StandardCharsets.UTF_8));
        var bundle = new FragmentGraphResolver().resolve(entries, settings,
                new ContainedFragmentSources(root, locale));
        return new Snapshot(root, bundle);
    }
}
