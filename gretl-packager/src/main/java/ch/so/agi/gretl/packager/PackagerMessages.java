package ch.so.agi.gretl.packager;

import ch.so.agi.gretl.i18n.Messages;
import java.util.Locale;

/** Locale context for one packager invocation. */
final class PackagerMessages {
    private static final ThreadLocal<Locale> LOCALE =
            ThreadLocal.withInitial(() -> Locale.GERMAN);

    private PackagerMessages() {
    }

    static void use(Locale locale) {
        LOCALE.set(locale == null ? Locale.GERMAN : locale);
    }

    static String get(String key) {
        return Messages.get(LOCALE.get(), key);
    }

    static String format(String key, Object... arguments) {
        return Messages.format(LOCALE.get(), key, arguments);
    }
}
