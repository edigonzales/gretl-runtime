package ch.so.agi.gretl.job.runtime;

import java.util.Locale;
import java.util.Optional;

/** Text formats supported by the plan graph exporter. */
public enum PlanGraphFormat {
    MERMAID("mermaid"),
    PLANTUML("plantuml");

    private final String optionValue;

    PlanGraphFormat(String optionValue) {
        this.optionValue = optionValue;
    }

    public String optionValue() {
        return optionValue;
    }

    public static Optional<PlanGraphFormat> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        for (PlanGraphFormat format : values()) {
            if (format.optionValue.equals(normalized)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }
}
