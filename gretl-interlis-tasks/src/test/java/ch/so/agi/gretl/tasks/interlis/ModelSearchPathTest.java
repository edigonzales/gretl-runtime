package ch.so.agi.gretl.tasks.interlis;

import static org.junit.jupiter.api.Assertions.*;
import ch.so.agi.gretl.internal.formats.FormatTaskSupport;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModelSearchPathTest {
    @ParameterizedTest @ValueSource(strings = {"Csv2Excel", "Av2ch", "Av2geobau"})
    void acceptsLocalPathsAndPreservesSearchText(String symbol) {
        var field = OperationRegistry.load(getClass().getClassLoader()).requireSymbol(symbol)
                .inputs().stream().filter(f -> f.name().equals("modeldir")).findFirst().orElseThrow();
        Path local = Path.of("models", "nested", "..");
        assertEquals(local.toAbsolutePath().normalize().toString(), field.convert().apply(local));
        String search = "./models;https://models.example.invalid/;%XTF_DIR";
        assertEquals(search, field.convert().apply(search));
        for (Object invalid : new Object[]{"", "  ", 42, new Object()}) {
            assertThrows(IllegalArgumentException.class, () -> field.convert().apply(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> field.convert().apply(null));
        assertThrows(IllegalArgumentException.class, () -> FormatTaskSupport.text(local));
    }
}
