package ch.so.agi.gretl.runtime.groovy.project;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectPathTest {
    @Test
    void rootAndCanonicalIncludesHaveStableValueSemantics() {
        ProjectPath root = ProjectPath.root();
        assertTrue(root.isRoot());
        assertEquals(":", root.canonical());
        assertEquals(Path.of(""), root.relativeDirectory());
        assertEquals(root, ProjectPath.parseQualified(":"));
        assertEquals(":run", root.qualifyLocal("run"));

        ProjectPath nested = ProjectPath.parseInclude("etl:Transform_2-x");
        assertFalse(nested.isRoot());
        assertEquals(":etl:Transform_2-x", nested.canonical());
        assertEquals(Path.of("etl", "Transform_2-x"), nested.relativeDirectory());
        assertEquals(nested, ProjectPath.parseInclude(":etl:Transform_2-x"));
        assertEquals(nested.hashCode(), ProjectPath.parseQualified(nested.canonical()).hashCode());
        assertNotEquals(ProjectPath.parseInclude(":a:b"), ProjectPath.parseInclude(":a_b"));
    }

    @Test
    void referencesAreStrictlyLocalOrAbsoluteWithoutFilesystemSyntax() {
        ProjectPath scope = ProjectPath.parseInclude("etl:import");
        assertEquals(":etl:import:run", scope.qualifyLocal("run"));
        assertEquals(":etl:import:prepare", scope.resolveReference("prepare"));
        assertEquals(":download:fetch", scope.resolveReference(":download:fetch"));
        assertEquals(":rootTask", scope.resolveReference(":rootTask"));
        assertThrows(IllegalArgumentException.class, () -> scope.resolveReference(":"));
        assertThrows(IllegalArgumentException.class, () -> ProjectPath.parseQualified("local"));
        for (String name : List.of("", ":a", "a:b", "a/b", "a\\b", "..", "two words", "_x", "1x")) {
            assertThrows(IllegalArgumentException.class, () -> scope.qualifyLocal(name), name);
            if (!name.startsWith(":")) {
                assertThrows(IllegalArgumentException.class, () -> scope.resolveReference(name), name);
            }
        }
    }

    @Test
    void malformedIncludesCannotBeNormalizedIntoDifferentProjects() {
        for (String name : List.of("", ":", ".", "..", "../a", "a/..", "a/b", "a\\b",
                "a::b", "::a", "a:", ":a:", "a b", " a", "a ", "1a", "_a", "ä", "a\n")) {
            assertThrows(IllegalArgumentException.class, () -> ProjectPath.parseInclude(name), name);
        }
        assertThrows(IllegalArgumentException.class, () -> ProjectPath.parseInclude(null));
        assertThrows(IllegalArgumentException.class, () -> ProjectPath.parseQualified(null));
        assertThrows(IllegalArgumentException.class, () -> ProjectPath.root().qualifyLocal(null));
    }
}
