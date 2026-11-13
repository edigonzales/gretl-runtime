package ch.so.agi.gretl.it;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Records positive SCRIPT operations from real reports after the test assertions pass. */
public final class ScriptOperationCoverage implements BeforeEachCallback, AfterEachCallback {
    private static final String COVERAGE_FILE = System.getProperty("gretl.test.operationCoverageFile");
    private static final ThreadLocal<Set<String>> OPERATIONS = new ThreadLocal<>();

    @Override public void beforeEach(ExtensionContext context) {
        if (COVERAGE_FILE != null) OPERATIONS.set(new TreeSet<>());
    }

    @Override public void afterEach(ExtensionContext context) throws IOException {
        var operations = OPERATIONS.get();
        OPERATIONS.remove();
        if (operations != null && !operations.isEmpty() && context.getExecutionException().isEmpty())
            append(operations);
    }

    private static synchronized void append(Set<String> operations) throws IOException {
        Files.write(Path.of(COVERAGE_FILE), operations, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static void record(ArtifactJobRunner.Invocation invocation, ArtifactJobRunner.Result result) throws IOException {
        var operations = OPERATIONS.get();
        if (operations == null || !result.mode().equals("SCRIPT") || result.code() != 0
                || result.timedOut() || result.aborted() || result.forciblyKilled()
                || invocation.verb() != ArtifactJobRunner.Verb.RUN || invocation.help()) return;
        var execution = JobTestJson.execution(JobTestJson.read(result.report()));
        if ("NO_DATA".equals(execution.get("status"))) return;
        if (!"SUCCEEDED".equals(execution.get("status")))
            throw new IOException("Successful SCRIPT process did not report SUCCEEDED: " + result.report());
        for (Object item : (List<?>) execution.get("invocations")) {
            var operation = (Map<?, ?>) item;
            if ("SUCCEEDED".equals(operation.get("status"))) operations.add((String) operation.get("operationId"));
        }
    }

    public static void main(String[] args) throws IOException {
        var baseline = (List<?>) Json.parseFile(Path.of(args[0]), 1L << 20);
        var expected = new TreeSet<String>();
        for (Object item : baseline) expected.add((String) ((Map<?, ?>) item).get("id"));
        if (expected.size() != 50 || baseline.size() != 50)
            throw new IllegalStateException("Expected 50 unique standard operations");
        Path observations = Path.of(args[1]);
        if (Files.isRegularFile(observations)) expected.removeAll(Files.readAllLines(observations));
        if (!expected.isEmpty()) throw new IllegalStateException("Missing successful SCRIPT operations: " + expected);
        System.out.println("SCRIPT coverage: 50/50 standard operations succeeded");
    }
}
