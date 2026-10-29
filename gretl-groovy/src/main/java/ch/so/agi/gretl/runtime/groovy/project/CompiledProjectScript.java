package ch.so.agi.gretl.runtime.groovy.project;

import java.util.Objects;

/** Versioned-package entry description owned by the Groovy adapter, independent of the packager. */
public record CompiledProjectScript(ProjectPath projectPath, String entryClass,
                                    String relativeScriptPath, String virtualGroovyName) {
    public CompiledProjectScript {
        Objects.requireNonNull(projectPath); Objects.requireNonNull(entryClass);
        Objects.requireNonNull(relativeScriptPath); Objects.requireNonNull(virtualGroovyName);
        String expected = projectPath.relativeDirectory().resolve("job.groovy").toString().replace('\\', '/');
        if (entryClass.isBlank() || !expected.equals(relativeScriptPath)
                || !ProjectSourceNames.virtualGroovyName(projectPath).equals(virtualGroovyName))
            throw new IllegalArgumentException("Invalid compiled project source: " + relativeScriptPath);
    }
}
