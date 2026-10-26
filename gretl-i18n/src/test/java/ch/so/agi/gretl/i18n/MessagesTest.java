package ch.so.agi.gretl.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class MessagesTest {
    @Test
    void englishAndGermanCatalogsHaveTheSameKeys() throws Exception {
        ResourceBundle english = directCatalog("");
        ResourceBundle german = directCatalog("_de");

        assertEquals(english.keySet(), german.keySet());
        for (String key : english.keySet()) {
            assertEquals(english.getString(key),
                    Messages.get(Locale.ENGLISH, key));
            assertEquals(german.getString(key), Messages.get(Locale.GERMAN, key));
        }
    }

    private ResourceBundle directCatalog(String suffix) throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/ch/so/agi/gretl/i18n/messages" + suffix + ".properties")) {
            java.util.Objects.requireNonNull(stream);
            return new java.util.PropertyResourceBundle(new java.io.InputStreamReader(
                    stream, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test
    void unsupportedLocaleFallsBackToEnglish() {
        assertEquals("Task failed", Messages.get(Locale.FRENCH,
                "log.title.gretl.task.failed"));
        assertTrue(Messages.contains(Locale.GERMAN, "log.field.code"));
    }
}
