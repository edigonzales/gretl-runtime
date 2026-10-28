package ch.so.agi.gretl.runtime.groovy.project;

import java.nio.file.Path;
import java.util.Objects;

/** One explicitly selected definition source, without a loaded job or runtime state. */
public record ProjectEntry(ProjectPath projectPath, Path scriptFile) {
    public ProjectEntry {
        Objects.requireNonNull(projectPath, "projectPath");
        scriptFile = Objects.requireNonNull(scriptFile, "scriptFile").toAbsolutePath().normalize();
    }
}
