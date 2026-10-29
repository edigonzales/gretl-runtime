package ch.so.agi.gretl.packager;

import ch.so.agi.gretl.runtime.groovy.project.GretlProject;
import ch.so.agi.gretl.runtime.groovy.project.ProjectSourceHash;
import ch.so.agi.gretl.runtime.groovy.project.ProjectSourceNames;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compiles all explicit entries together using the same isolated compiler as single jobs. */
public final class ProjectCompiler {
    public CompiledProject compile(GretlProject project, PackageDescriptor descriptor, List<Path> classpath) throws PackageException {
        if (!descriptor.projectPackage()) throw new PackageException("PACKAGE_DESCRIPTOR_SCHEMA", PackagerMessages.get("package.project.compilerSchema"));
        Map<Path, String> names = new LinkedHashMap<>();
        Map<String, CompiledProject.Entry> expected = new LinkedHashMap<>();
        for (var entry : project.entries()) {
            String relative = project.root().relativize(entry.scriptFile()).toString().replace('\\', '/');
            String virtual = ProjectSourceNames.virtualGroovyName(entry.projectPath());
            Path path = descriptor.baseDirectory().resolve(relative).toAbsolutePath().normalize();
            names.put(path, virtual);
            if (expected.putIfAbsent(virtual, new CompiledProject.Entry(entry.projectPath(), relative, virtual,
                    ProjectSourceNames.entryClassName(entry.projectPath()))) != null)
                throw new PackageException("PROJECT_DUPLICATE_SCRIPT_CLASS", PackagerMessages.format("package.project.duplicateSource", relative));
        }
        List<JobCompiler.SourceFile> sources = new JobCompiler().sourceFiles(descriptor, List.copyOf(names.keySet()));
        JobCompiler.Compilation compiled;
        try { compiled = JobCompiler.compileSources(sources, names, classpath); }
        catch (PackageException failure) {
            String message = failure.getMessage();
            for (var entry : expected.values()) message = message.replace(entry.virtualGroovyName(), entry.relativeScriptPath());
            throw new PackageException(failure.code(), message, failure);
        }
        var found = new java.util.HashSet<String>();
        for (var script : compiled.scripts()) {
            var entry = expected.get(script.sourceName());
            if (entry == null) throw new PackageException("PACKAGE_MULTIPLE_SCRIPTS", PackagerMessages.format("package.project.hiddenScript", script.sourceName()));
            JobCompiler.requireBase(script);
            if (!found.add(script.sourceName())
                    || !compiled.classes().containsKey(script.name().replace('.', '/') + ".class"))
                throw new PackageException("PROJECT_DUPLICATE_SCRIPT_CLASS", PackagerMessages.format("package.project.entryClass", script.name()));
            expected.put(script.sourceName(), new CompiledProject.Entry(entry.projectPath(), entry.relativeScriptPath(), entry.virtualGroovyName(), script.name()));
        }
        if (found.size() != expected.size() || found.isEmpty())
            throw new PackageException("PACKAGE_NO_SCRIPT", PackagerMessages.get("package.project.entries"));
        try {
            var provenance = new ArrayList<JobCompiler.SourceFile>();
            provenance.add(new JobCompiler.SourceFile("settings.gretl", project.settingsFile(), Files.readString(project.settingsFile())));
            provenance.addAll(sources);
            return new CompiledProject(List.copyOf(expected.values()), compiled.classes(), provenance,
                    project.defaultTargets(), ProjectSourceHash.sha256(project));
        } catch (IOException failure) { throw new PackageException("PACKAGE_SOURCE_READ", PackagerMessages.get("package.project.sourcesRead"), failure); }
    }
}
