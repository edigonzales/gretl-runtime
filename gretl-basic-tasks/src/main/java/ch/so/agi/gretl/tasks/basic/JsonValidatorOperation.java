package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** JsonValidator task: structural JSON validation. */
public final class JsonValidatorOperation {
    public static final String ID = "gretl.json-validator";
    public static final String SYMBOL = "JsonValidator";

    private JsonValidatorOperation() {
    }

    public static OperationType<JsonValidatorRequest, JsonValidatorResult> type() {
        InputField<JsonValidatorRequest, List<Path>> dataFiles = new InputField<>(
                "dataFiles", "list", "JSON files to validate", true,
                Optional.empty(), List.of(), "dataFiles = [file('data/input.json')]",
                JsonValidatorOperation::paths,
                (builder, value) -> ((JsonValidatorBuilder) builder).setDataFiles(value));
        InputField<JsonValidatorRequest, Boolean> fail = new InputField<>(
                "failOnError", "boolean", "Fail the task when validation fails", false,
                Optional.of(true), List.of(), "failOnError = false",
                JsonValidatorOperation::bool,
                (builder, value) -> ((JsonValidatorBuilder) builder).setFailOnError(value));
        OutputField<JsonValidatorResult, Boolean> valid = Fields.output(
                "valid", "boolean", "Validation result", false,
                JsonValidatorResult::valid);
        OutputField<JsonValidatorResult, List<String>> errors = Fields.output(
                "errors", "list", "Validation errors", false,
                JsonValidatorResult::errors);
        return new OperationType<>(
                ID, SYMBOL, "Validate JSON syntax",
                JsonValidatorRequest.class, JsonValidatorResult.class,
                JsonValidatorBuilder::new, JsonValidatorHandler::new,
                List.of(dataFiles, fail), List.of(valid, errors),
                """
                task('validate', JsonValidator) {
                    dataFiles = [file('data/input.json')]
                }
                """);
    }

    public record JsonValidatorRequest(
            List<Path> dataFiles, boolean failOnError) {
        public JsonValidatorRequest {
            dataFiles = List.copyOf(dataFiles);
        }
    }

    public record JsonValidatorResult(boolean valid, List<String> errors) {
        public JsonValidatorResult {
            errors = List.copyOf(errors);
        }
    }

    public static final class JsonValidatorBuilder
            implements ch.so.agi.gretl.runtime.api.ConfigBuilder<JsonValidatorRequest> {
        private List<Path> dataFiles = List.of();
        private boolean failOnError = true;

        public JsonValidatorBuilder setDataFiles(List<Path> value) {
            dataFiles = value == null ? List.of() : value;
            return this;
        }

        public JsonValidatorBuilder setFailOnError(Boolean value) {
            failOnError = value == null || value;
            return this;
        }

        @Override
        public JsonValidatorRequest build() {
            if (dataFiles.isEmpty()) {
                throw new IllegalArgumentException("dataFiles must not be empty");
            }
            return new JsonValidatorRequest(dataFiles, failOnError);
        }
    }

    static final class JsonValidatorHandler
            implements OperationHandler<JsonValidatorRequest, JsonValidatorResult> {
        @Override
        public JsonValidatorResult execute(JsonValidatorRequest request,
                                           OperationContext context) {
            ObjectMapper mapper = new ObjectMapper();
            List<String> errors = new ArrayList<>();
            for (Path file : request.dataFiles()) {
                if (!Files.isRegularFile(file)) {
                    errors.add(msg(context, "task.json.file", file));
                    continue;
                }
                try {
                    mapper.readTree(Files.readString(file));
                } catch (Exception exception) {
                    errors.add(file.getFileName() + ": " + (exception.getMessage() == null
                            ? exception.getClass().getSimpleName() : exception.getMessage()));
                }
            }
            boolean valid = errors.isEmpty();
            if (!valid) {
                context.log().log(LogLevel.WARN, "gretl.jsonvalidator.failed",
                        msg(context, "task.jsonvalidator.failed"),
                        Map.of("tabular.errors", Integer.toString(errors.size())), null);
                if (request.failOnError()) {
                    throw new TabularTaskException("JSON_VALIDATION",
                            msg(context, "task.jsonvalidator.failed"), "validate", null);
                }
            }
            return new JsonValidatorResult(valid, errors);
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

    private static Boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new IllegalArgumentException("Boolean expected");
    }
}
