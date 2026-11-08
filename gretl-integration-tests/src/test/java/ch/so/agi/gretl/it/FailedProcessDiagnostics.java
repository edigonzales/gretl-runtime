package ch.so.agi.gretl.it;

import ch.so.agi.gretl.runtime.logging.Json;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opentest4j.TestAbortedException;

/** Associates the artifact harness's redirected logs with the JUnit test that owns them. */
public final class FailedProcessDiagnostics implements BeforeEachCallback, AfterEachCallback {
    private static final int MAX_ATTEMPTS = 4;
    private static final int MAX_CASES = 20;
    private static final int MAX_BYTES = 64 * 1024;
    private static final AtomicInteger CASES = new AtomicInteger();
    private static final ThreadLocal<Case> CURRENT = new ThreadLocal<>();

    private record Attempt(ArtifactJobRunner.Request request, ArtifactJobRunner.Mode mode) {}

    private static final class Case {
        final Map<Path, Attempt> attempts = new LinkedHashMap<>();
        boolean omitted;
    }

    @Override public void beforeEach(ExtensionContext context) {
        CURRENT.set(new Case());
    }

    static void register(ArtifactJobRunner.Request request, ArtifactJobRunner.Mode mode) {
        Case active = CURRENT.get();
        if (active == null) return;
        if (active.attempts.size() < MAX_ATTEMPTS || active.attempts.containsKey(request.directory())) {
            active.attempts.put(request.directory(), new Attempt(request, mode));
        } else {
            active.omitted = true;
        }
    }

    @Override public void afterEach(ExtensionContext context) {
        Case active = CURRENT.get();
        CURRENT.remove();
        if (active == null || active.attempts.isEmpty()
                || context.getExecutionException().filter(error -> !(error instanceof TestAbortedException)).isEmpty()) {
            return;
        }
        int caseNumber = CASES.incrementAndGet();
        if (caseNumber > MAX_CASES) {
            if (caseNumber == MAX_CASES + 1) {
                System.err.println("Process diagnostics staging case limit reached");
            }
            return;
        }
        try {
            stage(context.getRequiredTestClass().getName() + "#" + context.getRequiredTestMethod().getName(), active);
        } catch (IOException | RuntimeException failure) {
            System.err.println("Could not stage process diagnostics: " + failure.getClass().getSimpleName());
        }
    }

    private static void stage(String test, Case active) throws IOException {
        String runId = System.getProperty("gretl.test.runId", "development");
        if (!runId.matches("[A-Za-z0-9_-]{1,64}")) return;
        Path root = Files.createDirectories(Path.of("target", "failed-process-diagnostics", runId));
        Path output = Files.createTempDirectory(root, "case-");
        var logs = new ArrayList<Map<String, Object>>();
        int omitted = active.omitted ? 1 : 0;
        int index = 0;
        for (Attempt attempt : active.attempts.values()) {
            index++;
            try {
                var redactor = ArtifactJobRunner.redactor(attempt.request());
                for (String stream : java.util.List.of("stdout", "stderr")) {
                    Path source = attempt.request().directory().resolve("job." + stream + ".log");
                    if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                        omitted++;
                        continue;
                    }
                    String name = index + "-" + attempt.mode().name() + "-" + stream + ".log";
                    try (var input = Files.newByteChannel(source, StandardOpenOption.READ,
                            LinkOption.NOFOLLOW_LINKS)) {
                        long offset = Math.max(0, input.size() - MAX_BYTES);
                        if (offset > 0 && attempt.request().config().get("secrets") instanceof Map<?, ?> secrets
                                && !secrets.isEmpty()) {
                            // A clipped source can begin inside a multiline secret.
                            omitted++;
                            continue;
                        }
                        input.position(offset);
                        ByteBuffer buffer = ByteBuffer.allocate(MAX_BYTES);
                        while (buffer.hasRemaining() && input.read(buffer) > 0) {
                            // Read at most one tail.
                        }
                        String text = new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
                        text = redactor.redact(text);
                        if (offset > 0) {
                            int lineBreak = text.indexOf('\n');
                            text = lineBreak < 0 ? "" : text.substring(lineBreak + 1);
                        }
                        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                        boolean trimmed = offset > 0 || bytes.length > MAX_BYTES;
                        Files.write(output.resolve(name), bytes.length > MAX_BYTES
                                ? Arrays.copyOf(bytes, MAX_BYTES) : bytes, StandardOpenOption.CREATE_NEW);
                        logs.add(Map.of("name", name, "trimmed", trimmed));
                    }
                }
            } catch (IOException | RuntimeException failure) {
                omitted++;
            }
        }
        Files.writeString(output.resolve("case.json"), Json.write(Map.of(
                "test", test, "logs", logs, "omitted", omitted)), StandardOpenOption.CREATE_NEW);
    }
}
