package ch.so.agi.gretl.tasks.io.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.so.agi.gretl.tasks.io.TestOperationContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class JsonReadOperationTest {
    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource(value = {
            "'', object, '{\"\":true,\"a/b\":{\"~1\":[null,2.5,\"Grüezi\"]}}'",
            "'/', boolean, true",
            "/a~1b/~01, array, '[null,2.5,\"Grüezi\"]'",
            "/a~1b/~01/0, 'null',",
            "/a~1b/~01/1, number, 2.5",
            "/a~1b/~01/2, string, Grüezi"
    })
    void extractsRootMembersEscapesAndArrayElements(String pointer, String kind, String value) throws Exception {
        Path file = Files.writeString(directory.resolve("input.json"),
                "{\"a/b\":{\"~1\":[null,2.50,\"Grüezi\"]},\"\":true}");
        assertEquals(new JsonReadOperation.Result(true, kind, value), read(file, pointer, true, 1024));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/absent", "/array/01", "/array/-", "/array/2147483648", "/number/x"})
    void missingIsDistinctFromNullAndCanBeRequired(String pointer) throws Exception {
        Path file = Files.writeString(directory.resolve("input.json"), "{\"array\":[0,1],\"number\":3}");
        assertEquals(new JsonReadOperation.Result(false, "missing", null), read(file, pointer, false, 1024));
        assertEquals("JSON_READ_MISSING", assertThrows(JsonReadException.class,
                () -> read(file, pointer, true, 1024)).diagnostic().code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"#", "name", "/wrong~", "/wrong~2"})
    void invalidPointerSyntaxFailsEvenWhenOptional(String pointer) {
        assertThrows(IllegalArgumentException.class,
                () -> new JsonReadOperation.Request(directory.resolve("input.json"), pointer, false, 1024));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "{} {}", "{\"key\":1,\"key\":2}", "private_invalid_payload"})
    void invalidJsonFailsWithoutIncludingParserDetails(String json) throws Exception {
        Path file = Files.writeString(directory.resolve("input.json"), json);
        JsonReadException error = assertThrows(JsonReadException.class, () -> read(file, "", false, 1024));
        assertEquals("JSON_READ_INVALID", error.diagnostic().code());
        assertNull(error.getCause());
    }

    @Test
    void enforcesByteLimitAndRegularFileInput() throws Exception {
        Path file = Files.writeString(directory.resolve("input.json"), "\"é\"");
        assertEquals(new JsonReadOperation.Result(true, "string", "é"), read(file, "", true, 4));
        assertEquals("JSON_READ_SIZE", assertThrows(JsonReadException.class,
                () -> read(file, "", true, 3)).diagnostic().code());
        assertEquals("JSON_READ_INPUT", assertThrows(JsonReadException.class,
                () -> read(directory, "", true, 1024)).diagnostic().code());
        assertThrows(IllegalArgumentException.class, () -> read(file, "", true, 0));
        assertThrows(IllegalArgumentException.class, () -> read(file, "", true, 16 * 1024 * 1024 + 1));
    }

    private JsonReadOperation.Result read(Path file, String pointer, boolean required, int maxBytes) throws Exception {
        var request = new JsonReadOperation.Request(file, pointer, required, maxBytes);
        return JsonReadOperation.type().handlerFactory().get().execute(request,
                new TestOperationContext(directory, Map.of(), Map.of(), Map.of()));
    }
}
