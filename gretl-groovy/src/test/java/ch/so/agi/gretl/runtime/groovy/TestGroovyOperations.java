package ch.so.agi.gretl.runtime.groovy;

import ch.so.agi.gretl.runtime.api.ConfigBuilder;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationProvider;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Local-only fixture operations for the M2 Groovy harness. */
final class TestGroovyOperations {
    static final AtomicInteger BUILDER_CREATIONS = new AtomicInteger();
    static final AtomicInteger HANDLER_CREATIONS = new AtomicInteger();
    static final AtomicInteger HANDLER_CALLS = new AtomicInteger();

    private TestGroovyOperations() {
    }

    static void reset() {
        BUILDER_CREATIONS.set(0);
        HANDLER_CREATIONS.set(0);
        HANDLER_CALLS.set(0);
    }

    record EchoRequest(String text) {
    }

    record EchoResult(String result) {
    }

    public static final class EchoBuilder implements ConfigBuilder<EchoRequest> {
        private String text;
        public void setText(String value) { text = value; }
        @Override public EchoRequest build() { return new EchoRequest(text); }
    }

    record RequiredRequest(String text) {
    }

    public static final class RequiredBuilder implements ConfigBuilder<RequiredRequest> {
        private String text;
        public void setText(String value) { text = value; }
        @Override public RequiredRequest build() {
            if (text == null) throw new IllegalArgumentException("text fehlt");
            return new RequiredRequest(text);
        }
    }

    record FileSizeRequest(Path input) {
    }

    record FileSizeResult(long bytes) {
    }

    public static final class FileSizeBuilder implements ConfigBuilder<FileSizeRequest> {
        private Path input;
        public void setInput(Path value) { input = value; }
        @Override public FileSizeRequest build() {
            if (input == null) throw new IllegalArgumentException("input fehlt");
            return new FileSizeRequest(input);
        }
    }

    record NullableResult(String maybe) {
    }

    static OperationType<EchoRequest, EchoResult> echo() {
        InputField<EchoRequest, String> text = Fields.input(
                "text", "text", "Text", false, Optional.of("default"), List.of(),
                "text = 'value'", EchoBuilder.class,
                value -> {
                    if (!(value instanceof CharSequence chars)) {
                        throw new IllegalArgumentException("text erwartet Zeichen");
                    }
                    return chars.toString();
                }, EchoBuilder::setText);
        OutputField<EchoResult, String> result = Fields.output(
                "result", "text", "Ergebnis", false, EchoResult::result);
        return new OperationType<>("test.echo", "Echo", "Echo der Eingabe",
                EchoRequest.class, EchoResult.class,
                () -> {
                    BUILDER_CREATIONS.incrementAndGet();
                    return new EchoBuilder();
                },
                () -> {
                    HANDLER_CREATIONS.incrementAndGet();
                    return (request, context) -> {
                        HANDLER_CALLS.incrementAndGet();
                        return new EchoResult(request.text());
                    };
                },
                List.of(text), List.of(result),
                "task('echo', Echo) { text = 'value' }");
    }

    static OperationType<RequiredRequest, EchoResult> required() {
        InputField<RequiredRequest, String> text = Fields.input(
                "text", "text", "Pflichttext", true, Optional.empty(), List.of(),
                "text = 'value'", RequiredBuilder.class,
                value -> {
                    if (!(value instanceof CharSequence chars)) {
                        throw new IllegalArgumentException("text erwartet Zeichen");
                    }
                    return chars.toString();
                }, RequiredBuilder::setText);
        OutputField<EchoResult, String> result = Fields.output(
                "result", "text", "Ergebnis", false, EchoResult::result);
        return new OperationType<>("test.required", "RequiredText", "Pflichttext",
                RequiredRequest.class, EchoResult.class,
                () -> {
                    BUILDER_CREATIONS.incrementAndGet();
                    return new RequiredBuilder();
                },
                () -> {
                    HANDLER_CREATIONS.incrementAndGet();
                    return (request, context) -> {
                        HANDLER_CALLS.incrementAndGet();
                        return new EchoResult(request.text());
                    };
                },
                List.of(text), List.of(result),
                "task('required', RequiredText) { text = 'value' }");
    }

    static OperationType<FileSizeRequest, FileSizeResult> fileSize() {
        InputField<FileSizeRequest, Path> input = Fields.requiredPath(
                "input", "Datei", FileSizeBuilder.class, FileSizeBuilder::setInput);
        OutputField<FileSizeResult, Long> bytes = Fields.output(
                "bytes", "integer", "Bytes", false, FileSizeResult::bytes);
        return new OperationType<>("test.file-size", "FileSize", "Dateigroesse",
                FileSizeRequest.class, FileSizeResult.class,
                () -> {
                    BUILDER_CREATIONS.incrementAndGet();
                    return new FileSizeBuilder();
                },
                () -> {
                    HANDLER_CREATIONS.incrementAndGet();
                    return (request, context) -> {
                        HANDLER_CALLS.incrementAndGet();
                        return new FileSizeResult(Files.size(request.input()));
                    };
                },
                List.of(input), List.of(bytes),
                "task('messen', FileSize) { input = file('input.xtf') }");
    }

    static OperationType<String, NullableResult> nullable() {
        OutputField<NullableResult, String> maybe = Fields.output(
                "maybe", "text", "Darf null sein", true, NullableResult::maybe);
        return new OperationType<>("test.nullable", "Nullable", "Nullwert",
                String.class, NullableResult.class, () -> () -> "request",
                () -> (request, context) -> {
                    HANDLER_CALLS.incrementAndGet();
                    return new NullableResult(null);
                },
                List.of(), List.of(maybe), "action('x') { }");
    }

    static OperationType<String, Void> voidOperation() {
        return new OperationType<>("test.void", "VoidProbe", "Ohne Wert",
                String.class, Void.class, () -> () -> "request",
                () -> (request, context) -> {
                    HANDLER_CALLS.incrementAndGet();
                    return null;
                },
                List.of(), List.of(), "action('x') { }");
    }

    static OperationType<String, EchoResult> failing() {
        return new OperationType<>("test.fail", "Fail", "Fehlschlag",
                String.class, EchoResult.class, () -> () -> "request",
                () -> (request, context) -> {
                    HANDLER_CALLS.incrementAndGet();
                    throw new IllegalStateException("fixture failure");
                },
                List.of(), List.of(), "action('x') { }");
    }

    static Collection<OperationType<?, ?>> operations() {
        return List.of(echo(), required(), fileSize(), nullable(), voidOperation(), failing());
    }

    static final class Provider implements OperationProvider {
        @Override
        public Collection<OperationType<?, ?>> operations() {
            return TestGroovyOperations.operations();
        }
    }
}
