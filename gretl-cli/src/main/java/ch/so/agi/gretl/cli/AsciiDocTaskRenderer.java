package ch.so.agi.gretl.cli;

import ch.so.agi.gretl.job.runtime.OperationHelpText;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Renders the operation catalog as property tables for the editorial AsciiDoc
 * reference.
 *
 * <p>Only the input-field table is generated. Task descriptions, examples,
 * results and the main reference document are written by hand. The source of
 * truth is the same {@link OperationType} metadata that drives
 * {@code gretl operations} and the job runtime. No annotations, Javadoc or
 * doclet are involved.</p>
 */
final class AsciiDocTaskRenderer {
    private final AsciiDocTextRenderer textRenderer = new AsciiDocTextRenderer();

    void render(OperationRegistry registry, Path outputDirectory, Locale locale)
            throws IOException {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(locale, "locale");
        Files.createDirectories(outputDirectory);
        removeGeneratedTaskFiles(outputDirectory);
        for (OperationType<?, ?> operation : registry.all()) {
            write(outputDirectory.resolve(fileName(operation)),
                    renderTaskTable(operation, locale));
        }
    }

    void writeTask(OperationType<?, ?> operation, Path outputDirectory, Locale locale)
            throws IOException {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(locale, "locale");
        Files.createDirectories(outputDirectory);
        write(outputDirectory.resolve(fileName(operation)),
                renderTaskTable(operation, locale));
    }

    String renderTaskTable(OperationType<?, ?> operation, Locale locale) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(locale, "locale");
        StringBuilder out = new StringBuilder();
        out.append("[cols=\"4,6,1\", options=\"header\"]\n");
        out.append("|===\n");
        out.append("| ").append(text(locale, "doc.field"))
                .append(" | ").append(text(locale, "doc.description"))
                .append(" | ").append(text(locale, "doc.required")).append("\n\n");
        for (InputField<?, ?> field : operation.inputs()) {
            out.append("| ").append(fieldSignature(field)).append("\n");
            appendCell(out, inputDescription(operation, field, locale));
            out.append("| ").append(field.required()
                            ? "[.required]#" + text(locale, "doc.yes") + "#"
                            : "[.optional]#" + text(locale, "doc.no") + "#")
                    .append("\n\n");
        }
        out.append("|===\n");
        return out.toString();
    }

    String fileName(OperationType<?, ?> operation) {
        return anchor(operation) + ".adoc";
    }

    String anchor(OperationType<?, ?> operation) {
        return "task-" + operation.symbol()
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-|-$", "")
                .toLowerCase(Locale.ROOT);
    }

    private String fieldSignature(InputField<?, ?> field) {
        return "[.dsl-signature]#*" + field.name() + "* = " + field.valueType() + "#";
    }

    private String inputDescription(OperationType<?, ?> operation, InputField<?, ?> field,
                                    Locale locale) {
        String description = OperationHelpText.fieldDescription(
                operation, field.name(), locale);
        return escapeCell(textRenderer.render(description));
    }

    private static void appendCell(StringBuilder out, String value) {
        if (value == null || value.isBlank()) {
            out.append("|\n");
            return;
        }
        out.append("| ").append(value).append("\n");
    }

    private static String escapeCell(String value) {
        return value.replace("|", "\\|")
                .replace("\r\n", "\n")
                .replace("\n", " +\n");
    }

    private static void write(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void removeGeneratedTaskFiles(Path outputDirectory) throws IOException {
        try (Stream<Path> files = Files.list(outputDirectory)) {
            List<Path> stale = new ArrayList<>();
            files.filter(path -> path.getFileName().toString().startsWith("task-"))
                    .filter(path -> path.getFileName().toString().endsWith(".adoc"))
                    .forEach(stale::add);
            for (Path path : stale) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String text(Locale locale, String key) {
        return Messages.get(locale, key);
    }
}
