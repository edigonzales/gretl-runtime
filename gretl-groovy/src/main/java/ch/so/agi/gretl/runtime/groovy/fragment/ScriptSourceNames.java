package ch.so.agi.gretl.runtime.groovy.fragment;

/** Compatibility facade for injective source names used by bundle compilation. */
final class ScriptSourceNames {
    private ScriptSourceNames() { }
    static String virtualGroovyName(String sourceId) {
        return ch.so.agi.gretl.syntax.ScriptSourceNames.virtualGroovyName(sourceId);
    }
}
