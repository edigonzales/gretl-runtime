package ch.so.agi.gretl.runtime.groovy.project;

import static org.junit.jupiter.api.Assertions.*;

import ch.so.agi.gretl.runtime.groovy.CompilerSettings;
import groovy.lang.GroovyClassLoader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.Phases;
import org.junit.jupiter.api.Test;

class ProjectSourceNamesTest {
    @Test
    void prj005NamesUseTheWholeCanonicalIdentityAndProduceDifferentRealGroovyClasses() throws Exception {
        var paths = List.of(ProjectPath.root(), ProjectPath.parseInclude("a:b"),
                ProjectPath.parseInclude("a_b"));
        var configuration = CompilerSettings.create();
        try (var loader = new GroovyClassLoader(getClass().getClassLoader(), configuration)) {
            var unit = new CompilationUnit(configuration, null, loader);
            for (int index = 0; index < paths.size(); index++) {
                ProjectPath path = paths.get(index);
                String entry = ProjectSourceNames.entryClassName(path);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(path.canonical().getBytes(StandardCharsets.UTF_8)));
                assertTrue(entry.matches("[A-Za-z_$][A-Za-z0-9_$]*"));
                assertTrue(entry.endsWith(hash), entry);
                assertEquals(entry + ".groovy", ProjectSourceNames.virtualGroovyName(path));
                assertEquals(entry, ProjectSourceNames.entryClassName(path));
                unit.addSource(ProjectSourceNames.virtualGroovyName(path),
                        "def ownValue = 'project-" + index + "'\nreturn ownValue\n");
            }
            unit.compile(Phases.CLASS_GENERATION);
            var generated = unit.getClasses().stream().map(c -> c.getName()).toList();
            assertEquals(3, generated.size());
            assertEquals(3, generated.stream().distinct().count());
            for (ProjectPath path : paths) {
                assertTrue(generated.contains(ProjectSourceNames.entryClassName(path)), generated.toString());
            }
        }
    }
}
