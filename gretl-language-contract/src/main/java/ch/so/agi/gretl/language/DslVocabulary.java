package ch.so.agi.gretl.language;

import java.util.Set;

/** Pure vocabulary shared with the executable DSL; no callbacks or reflection. */
public final class DslVocabulary {
    private DslVocabulary() { }
    public static Set<String> taskOptions() { return Set.of("dependsOn", "after", "description", "when", "timeout"); }
    public static Set<String> parameterOptions() { return Set.of("type", "required", "description", "defaultValue"); }
    public static Set<String> parameterTypes() { return Set.of("text", "boolean", "integer", "path"); }
    public enum ScopeKind { TOPLEVEL, CONFIGURATION, ACTION, WHEN, INNER_CONFIGURATION, CLEANUP }
    public record ScopeCapabilities(Set<String> helpers, boolean fieldAssignments, boolean resultAccess, boolean innerInvocation) {
        public ScopeCapabilities { helpers = Set.copyOf(helpers); }
    }
    public static ScopeCapabilities scopeCapabilities(ScopeKind scope) {
        if (scope == ScopeKind.TOPLEVEL) return new ScopeCapabilities(Set.of("task", "action", "parameter", "defaults", "cleanup", "println"), false, false, false);
        if (scope == ScopeKind.CLEANUP) return new ScopeCapabilities(Set.of("parameter", "file", "path", "workPath", "config", "log", "report", "resources", "budget", "println"), false, false, false);
        return new ScopeCapabilities(Set.of("parameter", "file", "path", "workPath", "config", "secret", "sensitive", "log", "report", "progress", "cancellation", "budget", "resources", "println", "result", "resultOptional", "finishJob"), scope == ScopeKind.CONFIGURATION || scope == ScopeKind.INNER_CONFIGURATION, true, true);
    }
}
