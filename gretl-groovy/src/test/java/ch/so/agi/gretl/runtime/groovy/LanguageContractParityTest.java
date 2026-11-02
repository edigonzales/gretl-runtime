package ch.so.agi.gretl.runtime.groovy;

import ch.so.agi.gretl.language.DslVocabulary;
import ch.so.agi.gretl.language.ProjectNameRules;
import ch.so.agi.gretl.runtime.groovy.project.ProjectPath;
import ch.so.agi.gretl.runtime.runner.ParameterValueType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LanguageContractParityTest {
    @Test void parameterTypesAndScopeHelpersMatchRuntime() {
        assertEquals(DslVocabulary.parameterTypes(),Arrays.stream(ParameterValueType.values()).map(v->v.name().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet()));
        for(var scope:DslVocabulary.ScopeKind.values()){
            Class<?> type=switch(scope){case TOPLEVEL->GretlScript.class;case CLEANUP->CleanupScope.class;case ACTION,WHEN->ActionScope.class;default->TaskConfigurationScope.class;};
            Set<String> methods=Arrays.stream(type.getMethods()).map(java.lang.reflect.Method::getName).collect(java.util.stream.Collectors.toSet());
            assertTrue(methods.containsAll(DslVocabulary.scopeCapabilities(scope).helpers()),scope+": "+methods);
        }
        assertFalse(DslVocabulary.scopeCapabilities(DslVocabulary.ScopeKind.CLEANUP).resultAccess());
        assertFalse(DslVocabulary.scopeCapabilities(DslVocabulary.ScopeKind.CLEANUP).innerInvocation());
    }
    @Test void publicNameAdapterPreservesExpectedNamesAndFailures() {
        for(String scope:List.of("a",":a:b","Task_2-x"))for(String local:List.of("run","a_2","A-z")){
            String expected=(scope.startsWith(":")?scope:":"+scope)+":"+local;
            assertEquals(expected,ProjectPath.parseInclude(scope).qualifyLocal(local));
            assertEquals(expected,ProjectNameRules.parseInclude(scope).resolveReference(local));
        }
        for(String name:List.of("a::b","../a","a/b",":a:","_x")){
            String rejectedSegment = Map.of("a::b", "", "../a", "../a", "a/b", "a/b", ":a:", "", "_x", "_x").get(name);
            var old=assertThrows(IllegalArgumentException.class,()->ProjectPath.parseInclude(name));
            assertEquals("Invalid project/name path: "+rejectedSegment,old.getMessage());
        }
    }
}
