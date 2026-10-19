package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.csv.CSVRecord;

/** CsvValidator task: structural CSV validation. */
public final class CsvValidatorOperation {
    public static final String ID = "gretl.csv-validator";
    public static final String SYMBOL = "CsvValidator";

    private CsvValidatorOperation() {
    }

    public static OperationType<CsvValidatorRequest, CsvValidatorResult> type() {
        InputField<CsvValidatorRequest, List<Path>> dataFiles = new InputField<>(
                "dataFiles", "list", "CSV files to validate", true,
                Optional.empty(), List.of(), "dataFiles = [file('data/input.csv')]",
                CsvValidatorOperation::paths,
                (builder, value) -> ((CsvValidatorBuilder) builder).setDataFiles(value));
        InputField<CsvValidatorRequest, Boolean> header = new InputField<>(
                "firstLineIsHeader", "boolean", "Use the first CSV line as header", false,
                Optional.of(true), List.of(), "firstLineIsHeader = false",
                CsvValidatorOperation::bool,
                (builder, value) -> ((CsvValidatorBuilder) builder)
                        .setFirstLineIsHeader(value));
        InputField<CsvValidatorRequest, String> delimiter = new InputField<>(
                "valueDelimiter", "text", "CSV field delimiter", false,
                Optional.of(CsvSupport.DEFAULT_DELIMITER), List.of(),
                "valueDelimiter = ','", CsvValidatorOperation::text,
                (builder, value) -> ((CsvValidatorBuilder) builder)
                        .setValueDelimiter(value));
        InputField<CsvValidatorRequest, String> separator = new InputField<>(
                "valueSeparator", "text", "CSV quote character", false,
                Optional.of(CsvSupport.DEFAULT_QUOTE), List.of(),
                "valueSeparator = '\"'", CsvValidatorOperation::text,
                (builder, value) -> ((CsvValidatorBuilder) builder)
                        .setValueSeparator(value));
        InputField<CsvValidatorRequest, String> encoding = new InputField<>(
                "encoding", "text", "CSV encoding", false,
                Optional.of(CsvSupport.DEFAULT_ENCODING), List.of(),
                "encoding = 'UTF-8'", CsvValidatorOperation::text,
                (builder, value) -> ((CsvValidatorBuilder) builder).setEncoding(value));
        InputField<CsvValidatorRequest, Boolean> fail = new InputField<>(
                "failOnError", "boolean", "Fail the task when validation fails", false,
                Optional.of(true), List.of(), "failOnError = false",
                CsvValidatorOperation::bool,
                (builder, value) -> ((CsvValidatorBuilder) builder).setFailOnError(value));
        OutputField<CsvValidatorResult, Boolean> valid = Fields.output(
                "valid", "boolean", "Validation result", false,
                CsvValidatorResult::valid);
        OutputField<CsvValidatorResult, List<String>> errors = Fields.output(
                "errors", "list", "Validation errors", false,
                CsvValidatorResult::errors);
        return new OperationType<>(
                ID, SYMBOL, "Validate CSV structure and header consistency",
                CsvValidatorRequest.class, CsvValidatorResult.class,
                CsvValidatorBuilder::new, CsvValidatorHandler::new,
                List.of(dataFiles, header, delimiter, separator, encoding, fail),
                List.of(valid, errors),
                """
                task('validate', CsvValidator) {
                    dataFiles = [file('data/input.csv')]
                }
                """);
    }

    public record CsvValidatorRequest(
            List<Path> dataFiles, boolean firstLineIsHeader,
            String valueDelimiter, String valueSeparator, String encoding,
            boolean failOnError) {
        public CsvValidatorRequest {
            dataFiles = List.copyOf(dataFiles);
        }
    }

    public record CsvValidatorResult(boolean valid, List<String> errors) {
        public CsvValidatorResult {
            errors = List.copyOf(errors);
        }
    }

    public static final class CsvValidatorBuilder
            implements ch.so.agi.gretl.runtime.api.ConfigBuilder<CsvValidatorRequest> {
        private List<Path> dataFiles = List.of();
        private boolean firstLineIsHeader = true;
        private String valueDelimiter = CsvSupport.DEFAULT_DELIMITER;
        private String valueSeparator = CsvSupport.DEFAULT_QUOTE;
        private String encoding = CsvSupport.DEFAULT_ENCODING;
        private boolean failOnError = true;

        public CsvValidatorBuilder setDataFiles(List<Path> value) {
            dataFiles = value == null ? List.of() : value;
            return this;
        }

        public CsvValidatorBuilder setFirstLineIsHeader(Boolean value) {
            firstLineIsHeader = value == null || value;
            return this;
        }

        public CsvValidatorBuilder setValueDelimiter(String value) {
            valueDelimiter = value;
            return this;
        }

        public CsvValidatorBuilder setValueSeparator(String value) {
            valueSeparator = value;
            return this;
        }

        public CsvValidatorBuilder setEncoding(String value) {
            encoding = value;
            return this;
        }

        public CsvValidatorBuilder setFailOnError(Boolean value) {
            failOnError = value == null || value;
            return this;
        }

        @Override
        public CsvValidatorRequest build() {
            if (dataFiles.isEmpty()) {
                throw new IllegalArgumentException("dataFiles must not be empty");
            }
            return new CsvValidatorRequest(dataFiles, firstLineIsHeader,
                    valueDelimiter, valueSeparator, CsvSupport.encoding(encoding),
                    failOnError);
        }
    }

    static final class CsvValidatorHandler
            implements OperationHandler<CsvValidatorRequest, CsvValidatorResult> {
        @Override
        public CsvValidatorResult execute(CsvValidatorRequest request,
                                          OperationContext context) {
            if (request.dataFiles().size() != 1) {
                throw new TabularTaskException("CSV_FILE",
                        msg(context, "task.csvvalidator.single"), "prepare", null);
            }
            Path file = request.dataFiles().get(0);
            List<String> errors = new ArrayList<>();
            if (!Files.isRegularFile(file)) {
                errors.add(msg(context, "task.csv.file", file));
            } else {
                try {
                    CsvSupport.ParsedCsv csv = CsvSupport.read(file, request.encoding(),
                            request.valueDelimiter(), request.valueSeparator(),
                            request.firstLineIsHeader());
                    if (request.firstLineIsHeader()) {
                        Set<String> names = new HashSet<>();
                        for (String header : csv.headers()) {
                            if (header == null || header.isBlank()) {
                                errors.add(msg(context, "task.csvvalidator.blankHeader"));
                            } else if (!names.add(header.toLowerCase(Locale.ROOT))) {
                                errors.add(msg(context, "task.csvvalidator.duplicateHeader",
                                        header));
                            }
                        }
                    }
                    int expected = csv.headers().isEmpty()
                            ? (csv.records().isEmpty() ? 0 : csv.records().get(0).size())
                            : csv.headers().size();
                    for (CSVRecord record : csv.records()) {
                        if (record.size() != expected) {
                            errors.add(msg(context, "task.csvvalidator.record",
                                    record.getRecordNumber(), expected, record.size()));
                        }
                    }
                } catch (Exception exception) {
                    errors.add(exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage());
                }
            }
            boolean valid = errors.isEmpty();
            if (!valid) {
                context.log().log(LogLevel.WARN, "gretl.csvvalidator.failed",
                        msg(context, "task.csvvalidator.failed"),
                        Map.of("tabular.errors", Integer.toString(errors.size())), null);
                if (request.failOnError()) {
                    throw new TabularTaskException("CSV_VALIDATION",
                            msg(context, "task.csvvalidator.failed"), "validate", null);
                }
            }
            return new CsvValidatorResult(valid, errors);
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }
    }

    private static List<Path> paths(Object value) {
        List<Path> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                result.add(path(element));
            }
        } else if (value instanceof Object[] array) {
            for (Object element : array) {
                result.add(path(element));
            }
        } else {
            throw new IllegalArgumentException("List expected");
        }
        return List.copyOf(result);
    }

    private static Path path(Object value) {
        if (value instanceof Path path) {
            return path.toAbsolutePath().normalize();
        }
        if (value instanceof CharSequence text) {
            return Path.of(text.toString()).toAbsolutePath().normalize();
        }
        throw new IllegalArgumentException("Path expected");
    }

    private static String text(Object value) {
        if (value instanceof CharSequence text) {
            return text.toString();
        }
        throw new IllegalArgumentException("Text expected");
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new IllegalArgumentException("Boolean expected");
    }
}
