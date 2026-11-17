package ch.so.agi.gretl.runtime.logging;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Strict JSON for runtime configuration and diagnostic data. */
public final class Json {
    private static final int MAX_DEPTH = 128;
    private static final JsonMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(MAX_DEPTH).build())
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_LONG_FOR_INTS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private Json() {
    }

    public static Object parse(String text) {
        try {
            return MAPPER.readValue(text, Object.class);
        } catch (JsonProcessingException failure) {
            // Jackson's original diagnostic can quote tokens from a secret-bearing file.
            var location = failure.getLocation();
            String position = location == null ? "" : " at line " + location.getLineNr()
                    + ", column " + location.getColumnNr();
            throw new IllegalArgumentException("Invalid JSON" + position);
        }
    }

    public static Object parseFile(Path file, long maxBytes) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return parse(input, Math.toIntExact(maxBytes));
        }
    }

    public static Object parse(InputStream input, int maxBytes) throws IOException {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        byte[] bytes = input.readNBytes(Math.addExact(maxBytes, 1));
        if (bytes.length > maxBytes) {
            throw new IOException("JSON input exceeds limit of " + maxBytes + " bytes");
        }
        return parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
    }

    public static String write(Object value) {
        validate(value, 0);
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Cannot serialize JSON", failure);
        }
    }

    private static void validate(Object value, int depth) {
        switch (value) {
            case null -> { }
            case String _, Boolean _, Byte _, Short _, Integer _, Long _, BigInteger _, BigDecimal _ -> { }
            case Float number when Float.isFinite(number) -> { }
            case Double number when Double.isFinite(number) -> { }
            case Map<?, ?> map when depth < MAX_DEPTH -> {
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String)) {
                        throw new IllegalArgumentException("JSON object keys must be strings");
                    }
                    validate(entry.getValue(), depth + 1);
                }
            }
            case List<?> list when depth < MAX_DEPTH -> {
                for (Object element : list) {
                    validate(element, depth + 1);
                }
            }
            case Float ignored -> throw new IllegalArgumentException("JSON numbers must be finite");
            case Double ignored -> throw new IllegalArgumentException("JSON numbers must be finite");
            case Map<?, ?> ignored -> throw new IllegalArgumentException("JSON nesting exceeds " + MAX_DEPTH);
            case List<?> ignored -> throw new IllegalArgumentException("JSON nesting exceeds " + MAX_DEPTH);
            default -> throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
        }
    }
}
