package ch.so.agi.gretl.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.api.ConfigBuilder;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationProvider;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import ch.so.agi.gretl.runtime.api.SecretValue;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AsciiDocTaskRendererTest {
    @TempDir
    Path tempDir;

    @Test
    void rendersOnlyTheFieldTable() {
        AsciiDocTaskRenderer renderer = new AsciiDocTaskRenderer();
        String table = renderer.renderTaskTable(testOperation(), Locale.GERMAN);

        assertTrue(table.startsWith("[cols=\"4,6,1\", options=\"header\"]"), table);
        assertTrue(table.contains("| Feld | Beschreibung | Erforderlich"), table);
        assertTrue(table.contains("[.dsl-signature]#*url* = uri#"), table);
        assertTrue(table.contains("[.required]#ja#"), table);
        assertTrue(table.contains("[.optional]#nein#"), table);
        assertTrue(table.contains("A \\| B +\nC"), table);
        assertTrue(table.endsWith("|===\n"), table);
        assertFalse(table.contains("Vorgabe:"), table);
        assertFalse(table.contains("Erlaubt:"), table);
        assertFalse(table.contains(".Ergebnisse"), table);
        assertFalse(table.contains(".Beispiel"), table);
        assertFalse(table.contains("[source,groovy]"), table);
        assertFalse(table.contains("task('demo'"), table);
    }

    @Test
    void hidesSecretDefaults() {
        assertEquals("[secret]", OperationHelpText.displayDefault(
                SecretValue.deferred(() -> "do-not-print")));
    }

    @Test
    void rendersEnglishLabels() {
        String table = new AsciiDocTaskRenderer()
                .renderTaskTable(testOperation(), Locale.ENGLISH);
        assertTrue(table.contains("| Field | Description | Required"), table);
        assertTrue(table.contains("[.required]#yes#"), table);
        assertFalse(table.contains("Default:"), table);
        assertFalse(table.contains("Results"), table);
    }

    @Test
    void writesOneFilePerTaskAndNoIndex() throws Exception {
        OperationRegistry registry = OperationRegistry.fromProviders(
                List.of((OperationProvider) () -> List.of(testOperation())));

        new AsciiDocTaskRenderer().render(registry, tempDir, Locale.GERMAN);

        Path table = tempDir.resolve("task-testoperation.adoc");
        assertTrue(Files.isRegularFile(table));
        assertFalse(Files.exists(tempDir.resolve("task-reference.adoc")));
        String content = Files.readString(table, StandardCharsets.UTF_8);
        assertTrue(content.startsWith("[cols=\"4,6,1\", options=\"header\"]"));
        assertTrue(content.contains("*url* = uri"));
        assertFalse(content.contains(".Beispiel"));
    }

    @Test
    void removesStaleGeneratedTaskFilesButKeepsMasterFiles() throws Exception {
        Files.writeString(tempDir.resolve("task-stale.adoc"), "stale");
        Files.writeString(tempDir.resolve("task-reference.adoc"), "old index");
        Files.writeString(tempDir.resolve("reference.adoc"), "master");
        OperationRegistry registry = OperationRegistry.fromProviders(
                List.of((OperationProvider) () -> List.of(testOperation())));

        new AsciiDocTaskRenderer().render(registry, tempDir, Locale.GERMAN);

        assertFalse(Files.exists(tempDir.resolve("task-stale.adoc")));
        assertFalse(Files.exists(tempDir.resolve("task-reference.adoc")));
        assertEquals("master", Files.readString(tempDir.resolve("reference.adoc")));
    }

    @Test
    void anchorUsesTheSameStableShapeAsGretlNext() {
        AsciiDocTaskRenderer renderer = new AsciiDocTaskRenderer();
        assertEquals("task-s3bucket2bucket",
                renderer.anchor(operation("S3Bucket2Bucket")));
        assertEquals("task-ili2pgimportschema",
                renderer.anchor(operation("Ili2pgImportSchema")));
        assertEquals("task-httpdownload",
                renderer.anchor(operation("HttpDownload")));
    }

    private static OperationType<TestRequest, TestResult> testOperation() {
        InputField<TestRequest, String> url = new InputField<>(
                "url", "uri", "A | B\nC", true, Optional.empty(), List.of(),
                "url = 'https://example.invalid'",
                Object::toString, (builder, value) -> {});
        InputField<TestRequest, String> method = new InputField<>(
                "method", "text", "HTTP method", false,
                Optional.of("https://example.invalid"), List.of("GET", "POST"),
                "method = 'GET'", Object::toString, (builder, value) -> {});
        OutputField<TestResult, Path> file = Fields.output(
                "file", "path", "Completed file", true, TestResult::file);
        return new OperationType<>(
                "gretl.test-operation", "TestOperation", "A test operation",
                TestRequest.class, TestResult.class,
                TestBuilder::new, TestHandler::new, List.of(url, method),
                List.of(file),
                """
                task('demo', TestOperation) {
                    url = 'https://example.invalid'
                }
                """);
    }

    private static OperationType<TestRequest, TestResult> operation(String symbol) {
        return new OperationType<>(
                "gretl." + symbol.toLowerCase(Locale.ROOT), symbol, "Test",
                TestRequest.class, TestResult.class, TestBuilder::new,
                TestHandler::new, List.of(), List.of(), "task('x', " + symbol + ") {}");
    }

    record TestRequest(String url, String method) {
    }

    record TestResult(Path file) {
    }

    static final class TestBuilder implements ConfigBuilder<TestRequest> {
        @Override
        public TestRequest build() {
            return new TestRequest("https://example.invalid", "GET");
        }
    }

    static final class TestHandler implements OperationHandler<TestRequest, TestResult> {
        @Override
        public TestResult execute(TestRequest request, OperationContext context) {
            return new TestResult(Path.of("file"));
        }
    }
}
