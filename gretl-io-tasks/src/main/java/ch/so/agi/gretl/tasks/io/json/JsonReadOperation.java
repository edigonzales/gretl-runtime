package ch.so.agi.gretl.tasks.io.json;

import ch.so.agi.gretl.runtime.api.ConfigBuilder;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Read one JSON value from a bounded local file. */
public final class JsonReadOperation {
    private static final int DEFAULT_MAX_BYTES = 1024 * 1024;
    private static final int MAX_BYTES = 16 * DEFAULT_MAX_BYTES;
    private static final Pattern INVALID_ESCAPE = Pattern.compile("~(?![01])");

    private JsonReadOperation() {}

    public static OperationType<Request, Result> type() {
        return new OperationType<>("gretl.json-read", "JsonRead", "Read a value from a JSON file",
                Request.class, Result.class, Builder::new, Handler::new,
                List.of(
                        Fields.input("sourceFile", "path", "Local JSON file", true, Optional.empty(),
                                List.of(), "sourceFile = file('response.json')", Builder.class,
                                Path.class::cast, (builder, value) -> builder.sourceFile = value),
                        Fields.input("pointer", "text", "RFC 6901 JSON Pointer; empty selects the root",
                                true, Optional.empty(), List.of(), "pointer = '/name'", Builder.class,
                                JsonReadOperation::text, (builder, value) -> builder.pointer = value),
                        Fields.input("required", "boolean", "Fail when the pointer has no value",
                                false, Optional.of(true), List.of(), "required = false", Builder.class,
                                Boolean.class::cast, (builder, value) -> builder.required = value),
                        Fields.input("maxBytes", "integer", "File size limit in bytes (1 to 16777216)",
                                false, Optional.of(DEFAULT_MAX_BYTES), List.of(), "maxBytes = 65536",
                                Builder.class, JsonReadOperation::byteLimit, (builder, value) -> builder.maxBytes = value)),
                List.of(
                        Fields.output("found", "boolean", "Whether the pointer resolved", false, Result::found),
                        Fields.output("kind", "text", "JSON value kind or missing", false, Result::kind),
                        Fields.output("value", "text", "Extracted value as text, or null", true, Result::value)),
                """
                task('readName', JsonRead) {
                    sourceFile = file('response.json')
                    pointer = '/name'
                }
                """);
    }

    private static String text(Object value) {
        if (value instanceof CharSequence text) {
            return text.toString();
        }
        throw new IllegalArgumentException("pointer must be text");
    }

    private static int byteLimit(Object value) {
        return switch (value) {
            case Byte n -> n.intValue();
            case Short n -> n.intValue();
            case Integer n -> n;
            case Long n -> Math.toIntExact(n);
            case BigInteger n -> n.intValueExact();
            default -> throw new IllegalArgumentException("maxBytes must be an integer");
        };
    }

    public record Request(Path sourceFile, String pointer, boolean required, int maxBytes) {
        public Request {
            Objects.requireNonNull(sourceFile, "sourceFile");
            Objects.requireNonNull(pointer, "pointer");
            if (sourceFile.toString().isBlank()) {
                throw new IllegalArgumentException("sourceFile must not be blank");
            }
            if ((!pointer.isEmpty() && !pointer.startsWith("/")) || INVALID_ESCAPE.matcher(pointer).find()) {
                throw new IllegalArgumentException("pointer must be an RFC 6901 JSON Pointer");
            }
            if (maxBytes < 1 || maxBytes > MAX_BYTES) {
                throw new IllegalArgumentException("maxBytes must be between 1 and " + MAX_BYTES);
            }
        }
    }

    public record Result(boolean found, String kind, String value) {}

    public static final class Builder implements ConfigBuilder<Request> {
        private Path sourceFile;
        private String pointer;
        private boolean required = true;
        private int maxBytes = DEFAULT_MAX_BYTES;

        @Override public Request build() {
            return new Request(sourceFile, pointer, required, maxBytes);
        }
    }

    static final class Handler implements OperationHandler<Request, Result> {
        private static final JsonMapper JSON = JsonMapper.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(JsonNodeFeature.WRITE_PROPERTIES_SORTED)
                .build();

        @Override public Result execute(Request request, OperationContext context) {
            context.cancellation().throwIfCancellationRequested();
            byte[] bytes = readFile(request);
            context.cancellation().throwIfCancellationRequested();

            try {
                JsonNode root = JSON.readTree(bytes);
                if (root == null || root.isMissingNode()) {
                    throw new JsonReadException("JSON_READ_INVALID", "Expected a JSON document", "parse");
                }
                JsonNode node = root.at(JsonPointer.compile(request.pointer()));
                if (node.isMissingNode()) {
                    if (request.required()) {
                        throw new JsonReadException("JSON_READ_MISSING", "JSON Pointer has no value", "select");
                    }
                    return new Result(false, "missing", null);
                }
                String kind = node.getNodeType().name().toLowerCase(Locale.ROOT);
                String value = switch (node.getNodeType()) {
                    case NULL -> null;
                    case STRING -> node.textValue();
                    default -> JSON.writeValueAsString(node);
                };
                return new Result(true, kind, value);
            } catch (IOException e) {
                // Parser messages may contain member names or fragments of the payload.
                throw new JsonReadException("JSON_READ_INVALID", "Invalid or unsupported JSON document", "parse");
            }
        }

        private static byte[] readFile(Request request) {
            if (!Files.isRegularFile(request.sourceFile(), LinkOption.NOFOLLOW_LINKS)) {
                throw new JsonReadException("JSON_READ_INPUT", "sourceFile must be a regular file", "read");
            }
            try (var input = Files.newInputStream(request.sourceFile(), LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(request.maxBytes() + 1);
                if (bytes.length > request.maxBytes()) {
                    throw new JsonReadException("JSON_READ_SIZE", "JSON file exceeds maxBytes", "read");
                }
                return bytes;
            } catch (IOException e) {
                throw new JsonReadException("JSON_READ_INPUT", "Could not read sourceFile", "read");
            }
        }
    }
}
