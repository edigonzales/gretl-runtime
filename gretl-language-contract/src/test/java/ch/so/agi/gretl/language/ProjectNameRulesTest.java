package ch.so.agi.gretl.language;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProjectNameRulesTest {
    @Test void logicalScopesAndReferences() {
        var scope = ProjectNameRules.parseInclude("checks:local");
        assertEquals(":checks:local",scope.canonical());
        assertEquals(":checks:local:run",scope.qualifyLocal("run"));
        assertEquals(":prepare:run",scope.resolveReference(":prepare:run"));
        assertEquals("run",scope.renderReference(":checks:local:run"));
        assertEquals(":prepare:run",scope.renderReference(":prepare:run"));
        assertEquals(":run",ProjectNameRules.root().resolveReference("run"));
        assertEquals("run",ProjectNameRules.root().renderReference(":run"));
        assertEquals(ProjectNameRules.parseInclude(":checks:local"),scope);
    }
    @Test void invalidNamesCannotBecomePaths() {
        for (String s : new String[]{null,"",":","a::b","a/b","../a","a:","_a","é",":a/../b"})
            assertThrows(IllegalArgumentException.class,()->ProjectNameRules.parseInclude(s),String.valueOf(s));
        for (String s : new String[]{null,"",":","a:b",":x:"})
            assertThrows(IllegalArgumentException.class,()->ProjectNameRules.root().resolveReference(s),String.valueOf(s));
    }
}
