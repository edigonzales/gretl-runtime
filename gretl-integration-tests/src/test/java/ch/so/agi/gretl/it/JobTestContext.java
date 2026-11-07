package ch.so.agi.gretl.it;

import ch.so.agi.gretl.runtime.logging.Json;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Links real process facts to the JUnit invocation that performs the independent assertions. */
public final class JobTestContext implements BeforeEachCallback, AfterEachCallback {
    private record State(String test, Map<Path, Integer> executions) {}
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
    private static final Map<String, String> FAMILIES = JobExecutionEvidence.legacyMatrix() ? families() : Map.of();
    @Override public void beforeEach(ExtensionContext context) {
        if (!JobExecutionEvidence.legacyMatrix()) return;
        String test = context.getRequiredTestClass().getName() + "#" + context.getRequiredTestMethod().getName();
        var matcher = java.util.regex.Pattern.compile("test-template-invocation:#([0-9]+)").matcher(context.getUniqueId());
        if (matcher.find()) test += "[" + matcher.group(1) + "]";
        CURRENT.set(new State(test, new LinkedHashMap<>()));
    }
    @Override public void afterEach(ExtensionContext context) { CURRENT.remove(); }
    static Map<String, Object> identity(Path directory) {
        State state = CURRENT.get();
        if (state == null) return Map.of("testCaseId", "HARNESS", "scenarioId", "HARNESS", "testFamily", "COMPONENT");
        Path root = directory.toAbsolutePath().normalize();
        int ordinal = state.executions().computeIfAbsent(root, ignored -> state.executions().size() + 1);
        String id = state.test() + "/" + String.format(Locale.ROOT, "%02d", ordinal);
        return Map.of("testCaseId", state.test(), "scenarioId", id,
                "testFamily", FAMILIES.getOrDefault(id, "UNCLASSIFIED"), "executionId", root.getFileName().toString());
    }
    private static Map<String, String> families() {
        try (var stream = JobTestContext.class.getResourceAsStream("/qualification/scenarios.json")) {
            if (stream == null) return Map.of();
            var catalog = (Map<?, ?>) Json.parse(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            Map<String, String> result = new LinkedHashMap<>();
            for (Object row : (List<?>) catalog.get("scenarios")) {
                var scenario = (Map<?, ?>) row;
                if (result.put((String) scenario.get("id"), (String) scenario.get("family")) != null)
                    throw new IllegalStateException("Duplicate scenario identity");
            }
            return Map.copyOf(result);
        } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot read scenario catalog", exception); }
    }
}
