package ch.so.agi.gretl.runtime.runner;

import java.nio.file.Path;
import java.util.Optional;

/** Frozen public job-parameter declaration. */
public record ParameterDefinition(
        String name, ParameterValueType type, String description,
        boolean required, Optional<Object> defaultValue) {

    public ParameterDefinition {
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_.-]*")) {
            throw new ParameterException("INVALID_PARAMETER_DECLARATION", name == null ? "?" : name,
                    "Ungueltiger Parametername: " + name);
        }
        java.util.Objects.requireNonNull(type, "type");
        if (description == null || description.isBlank()) {
            throw new ParameterException("INVALID_PARAMETER_DECLARATION", name,
                    "Der Parameter " + name + " benoetigt eine Beschreibung");
        }
        java.util.Objects.requireNonNull(defaultValue, "defaultValue");
        if (required && defaultValue.isPresent()) {
            throw new ParameterException("INVALID_PARAMETER_DECLARATION", name,
                    "Der Parameter " + name + " darf nicht required und defaultValue kombinieren");
        }
        defaultValue.ifPresent(value -> validateDefault(name, type, value));
    }

    public static ParameterDefinition text(String name, String description) {
        return new ParameterDefinition(name, ParameterValueType.TEXT, description, false, Optional.empty());
    }

    private static void validateDefault(String name, ParameterValueType type, Object value) {
        boolean valid = switch (type) {
            case TEXT -> value instanceof CharSequence;
            case BOOLEAN -> value instanceof Boolean
                    || value instanceof CharSequence text
                    && ("true".equals(text.toString()) || "false".equals(text.toString()));
            case INTEGER -> value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long
                    || value instanceof CharSequence text && text.toString().matches("[+-]?[0-9]+");
            case PATH -> value instanceof CharSequence || value instanceof Path;
        };
        if (!valid) {
            throw new ParameterException("INVALID_PARAMETER_DECLARATION", name,
                    "Defaultwert passt nicht zum Typ " + type + ": " + value);
        }
    }
}
