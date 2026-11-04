package ch.so.agi.gretl.runtime.groovy.project;

import ch.so.agi.gretl.language.ProjectNameRules;
import java.nio.file.Path;

/** Compatible filesystem adapter for the shared pure logical-name contract. */
public final class ProjectPath {
    private static final ProjectPath ROOT = new ProjectPath(ProjectNameRules.root());
    private final ProjectNameRules names;
    private ProjectPath(ProjectNameRules names) { this.names = names; }
    public static ProjectPath root() { return ROOT; }
    public static ProjectPath parseInclude(String text) { return new ProjectPath(ProjectNameRules.parseInclude(text)); }
    public static ProjectPath parseQualified(String text) { return new ProjectPath(ProjectNameRules.parseQualified(text)); }
    public String canonical() { return names.canonical(); }
    public Path relativeDirectory() {
        Path result = Path.of("");
        for (String segment : names.segments()) result = result.resolve(segment);
        return result;
    }
    public String qualifyLocal(String name) { return names.qualifyLocal(name); }
    public String resolveReference(String name) { return names.resolveReference(name); }
    public boolean isRoot() { return names.isRoot(); }
    @Override public boolean equals(Object other) { return other instanceof ProjectPath path && names.equals(path.names); }
    @Override public int hashCode() { return names.hashCode(); }
    @Override public String toString() { return canonical(); }
}
