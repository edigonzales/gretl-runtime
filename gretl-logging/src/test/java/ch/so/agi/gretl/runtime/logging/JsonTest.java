package ch.so.agi.gretl.runtime.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonTest {
    @Test
    void parsesObjectsArraysScalarsAndPreservesNumbers() {
        Object value = Json.parse("""
                {"a":1,"b":true,"c":null,"d":[1,2.5,"x\\n\\u00e4"]}
                """);
        Map<?, ?> object = (Map<?, ?>) value;
        assertEquals(1L, object.get("a"));
        assertEquals(true, object.get("b"));
        assertTrue(object.containsKey("c"));
        assertNull(object.get("c"));
        assertNull(Json.parse("null"));
        List<?> list = (List<?>) object.get("d");
        assertEquals(1L, list.get(0));
        assertEquals(new BigDecimal("2.5"), list.get(1));
        assertEquals("x\nä", list.get(2));
        assertEquals(Long.MIN_VALUE, Json.parse(Long.toString(Long.MIN_VALUE)));
        assertEquals(Long.MAX_VALUE, Json.parse(Long.toString(Long.MAX_VALUE)));
        assertEquals(new BigDecimal("1.012345678901234567890123456789"),
                Json.parse("1.012345678901234567890123456789"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("9223372036854775808"));
    }

    @Test
    void rejectsDuplicateKeysTrailingContentAndBadEscapes() {
        assertThrows(IllegalArgumentException.class,
                () -> Json.parse("{\"a\":1,\"a\":2}"));
        assertThrows(IllegalArgumentException.class,
                () -> Json.parse("{\"a\":1} trailing"));
        assertThrows(IllegalArgumentException.class,
                () -> Json.parse("{\"a\":\"\\x\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> Json.parse("{\"a\":01}"));
    }

    @Test
    void rejectsTrailingValuesAndKeepsTokensOutOfDiagnostics() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{} null"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("NaN"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse(""));
        var error = assertThrows(IllegalArgumentException.class,
                () -> Json.parse("{\"a\": sensitiveToken}"));
        assertFalse(error.getMessage().contains("sensitiveToken"));
        assertTrue(error.getMessage().contains("line 1"));
        assertNull(error.getCause());
    }

    @Test
    void enforcesNestingByteLimitAndUtf8(@TempDir Path directory) throws Exception {
        Json.parse("[".repeat(128) + "null" + "]".repeat(128));
        assertThrows(IllegalArgumentException.class,
                () -> Json.parse("[".repeat(129) + "null" + "]".repeat(129)));

        Path file = Files.writeString(directory.resolve("utf8.json"), "\"ä😀\"");
        assertEquals("ä😀", Json.parseFile(file, 8));
        assertThrows(IOException.class, () -> Json.parseFile(file, 7));
        Files.write(file, new byte[] { '"', (byte) 0xc3, '"' });
        assertThrows(IOException.class, () -> Json.parseFile(file, 8));
    }

    @Test
    void rejectsNonFiniteNumbersAndNonJsonValuesOnWrite() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(Map.of("a", List.of(Double.NaN))));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Json.write(Map.of(1, "value")));
        assertThrows(IllegalArgumentException.class, () -> Json.write(new Object()));
    }

    @Test
    void writesEscapedJsonThatCanBeParsedAgain() {
        String json = Json.write(Map.of("message", "a\nb\"c\\dä😀"));
        assertTrue(json.contains("\\n"));
        assertTrue(json.contains("\\\""));
        assertEquals("a\nb\"c\\dä😀",
                ((Map<?, ?>) Json.parse(json)).get("message"));
    }
}
