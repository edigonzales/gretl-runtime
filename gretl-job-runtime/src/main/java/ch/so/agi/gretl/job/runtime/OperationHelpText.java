package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import ch.so.agi.gretl.runtime.api.SecretValue;
import java.util.Locale;

/**
 * Shared localized text lookup for operation metadata.
 *
 * <p>The text help and the generated AsciiDoc reference must use the same
 * descriptions. This helper keeps that lookup in one place and never reveals
 * a secret default value.</p>
 */
public final class OperationHelpText {
    private static final int MAX_DEFAULT_LENGTH = 200;

    private OperationHelpText() {
    }

    public static String description(OperationType<?, ?> operation, Locale locale) {
        String key = "operation." + operation.id() + ".description";
        return Messages.contains(locale, key)
                ? Messages.get(locale, key) : operation.description();
    }

    public static String fieldDescription(OperationType<?, ?> operation, String field,
                                   Locale locale) {
        String key = "operation." + operation.id() + ".field." + field + ".description";
        if (Messages.contains(locale, key)) {
            return Messages.get(locale, key);
        }
        return operation.inputs().stream()
                .filter(value -> value.name().equals(field))
                .findFirst()
                .map(InputField::description)
                .orElse(field);
    }

    public static String outputDescription(OperationType<?, ?> operation, String field,
                                    Locale locale) {
        String key = "operation." + operation.id() + ".output." + field + ".description";
        if (Messages.contains(locale, key)) {
            return Messages.get(locale, key);
        }
        return operation.outputs().stream()
                .filter(value -> value.name().equals(field))
                .findFirst()
                .map(OutputField::description)
                .orElse(field);
    }

    public static String displayDefault(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof SecretValue) {
            return "[secret]";
        }
        if (value instanceof String text) {
            return "'" + text + "'";
        }
        String text = String.valueOf(value);
        if (text.length() <= MAX_DEFAULT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_DEFAULT_LENGTH - 3) + "...";
    }
}
