package ch.so.agi.gretl.runtime.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    void parsesObjectsArraysScalarsAndPreservesNumbers() {
        Object value = Json.parse("""
                {"a":1,"b":true,"c":null,"d":[1,2.5,"x\\n\\u00e4"]}
                """);
        Map<?, ?> object = (Map<?, ?>) value;
        assertEquals(1L, object.get("a"));
        assertEquals(true, object.get("b"));
        assertEquals(null, object.get("c"));
        List<?> list = (List<?>) object.get("d");
        assertEquals(1L, list.get(0));
        assertEquals("x\nä", list.get(2));
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
    void writesEscapedJsonThatCanBeParsedAgain() {
        String json = Json.write(Map.of("message", "a\nb\"c\\dä"));
        assertTrue(json.contains("\\n"));
        assertTrue(json.contains("\\\""));
        assertEquals("a\nb\"c\\dä",
                ((Map<?, ?>) Json.parse(json)).get("message"));
    }
}
