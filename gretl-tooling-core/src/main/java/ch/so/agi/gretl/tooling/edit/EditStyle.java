package ch.so.agi.gretl.tooling.edit;

/** Formatting preference and host transaction boundary, not DSL semantics. */
public record EditStyle(String indentation, boolean requireOpenDocuments) {
    public EditStyle {
        if (indentation == null || indentation.isEmpty() || indentation.length() > 8
                || indentation.chars().anyMatch(c -> c != ' ' && c != '\t')) throw new IllegalArgumentException("EDIT_INDENTATION");
    }
    public static EditStyle defaults() { return new EditStyle("    ", false); }
    public static EditStyle openDocuments() { return new EditStyle("    ", true); }
}
