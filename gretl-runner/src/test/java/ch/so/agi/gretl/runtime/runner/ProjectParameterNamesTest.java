package ch.so.agi.gretl.runtime.runner;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
class ProjectParameterNamesTest {
    @Test void qualifiedNamesAreAdditiveAndStillStrict() {
        for (String name : List.of("old.name", "legacy_name-1", ":root", ":a:root", ":a:b:value"))
            assertEquals(name, ParameterDefinition.text(name, "Value").name());
        for (String name : List.of(":", "a:b", "::a", ":a:", ":a::b", ":a:../b", ":a:1x", ":a:name.dot"))
            assertEquals("INVALID_PARAMETER_DECLARATION", assertThrows(ParameterException.class,
                    () -> ParameterDefinition.text(name, "Value")).code());
    }
}
