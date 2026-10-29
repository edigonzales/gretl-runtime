package ch.so.agi.gretl.packager;

import ch.so.agi.gretl.runtime.groovy.project.ProjectPath;
import java.util.List;
import java.util.Map;

/** Bytecode and explicit source provenance; contains no instantiated script or runtime values. */
public record CompiledProject(List<Entry> entries, Map<String, byte[]> classes,
                              List<JobCompiler.SourceFile> sources, List<String> defaultTargets,
                              String projectSha256) {
    public CompiledProject {
        entries = List.copyOf(entries); classes = Map.copyOf(classes);
        sources = List.copyOf(sources); defaultTargets = List.copyOf(defaultTargets);
    }
    public record Entry(ProjectPath projectPath, String relativeScriptPath, String virtualGroovyName, String entryClass) { }
}
