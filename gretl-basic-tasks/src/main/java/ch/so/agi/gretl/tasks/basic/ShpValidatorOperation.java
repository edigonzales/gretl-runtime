package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** ShpValidator task: structural Shapefile validation. */
public final class ShpValidatorOperation {
    public static final String ID = "gretl.shp-validator";
    public static final String SYMBOL = "ShpValidator";

    private ShpValidatorOperation() {
    }

    public static OperationType<ShpValidatorRequest, ShpValidatorResult> type() {
        InputField<ShpValidatorRequest, List<Path>> dataFiles = new InputField<>(
                "dataFiles", "list", "Shapefile .shp paths to validate", true,
                Optional.empty(), List.of(), "dataFiles = [file('data/input.shp')]",
                ShpValidatorOperation::paths,
                (builder, value) -> ((ShpValidatorBuilder) builder).setDataFiles(value));
        InputField<ShpValidatorRequest, String> encoding = new InputField<>(
                "encoding", "text", "Optional Shapefile encoding", false,
                Optional.empty(), List.of(), "encoding = 'UTF-8'",
                ShpValidatorOperation::text,
                (builder, value) -> ((ShpValidatorBuilder) builder).setEncoding(value));
        InputField<ShpValidatorRequest, Boolean> fail = new InputField<>(
                "failOnError", "boolean", "Fail the task when validation fails", false,
                Optional.of(true), List.of(), "failOnError = false",
                ShpValidatorOperation::bool,
                (builder, value) -> ((ShpValidatorBuilder) builder).setFailOnError(value));
        OutputField<ShpValidatorResult, Boolean> valid = Fields.output(
                "valid", "boolean", "Validation result", false,
                ShpValidatorResult::valid);
        OutputField<ShpValidatorResult, List<String>> errors = Fields.output(
                "errors", "list", "Validation errors", false,
                ShpValidatorResult::errors);
        return new OperationType<>(
                ID, SYMBOL, "Validate a Shapefile structure",
                ShpValidatorRequest.class, ShpValidatorResult.class,
                ShpValidatorBuilder::new, ShpValidatorHandler::new,
                List.of(dataFiles, encoding, fail), List.of(valid, errors),
                """
                task('validate', ShpValidator) {
                    dataFiles = [file('data/input.shp')]
                }
                """);
    }

    public record ShpValidatorRequest(
            List<Path> dataFiles, String encoding, boolean failOnError) {
        public ShpValidatorRequest {
            dataFiles = List.copyOf(dataFiles);
        }
    }

    public record ShpValidatorResult(boolean valid, List<String> errors) {
        public ShpValidatorResult {
            errors = List.copyOf(errors);
        }
    }

    public static final class ShpValidatorBuilder
            implements ch.so.agi.gretl.runtime.api.ConfigBuilder<ShpValidatorRequest> {
        private List<Path> dataFiles = List.of();
        private String encoding;
        private boolean failOnError = true;

        public ShpValidatorBuilder setDataFiles(List<Path> value) {
            dataFiles = value == null ? List.of() : value;
            return this;
        }

        public ShpValidatorBuilder setEncoding(String value) {
            encoding = value;
            return this;
        }

        public ShpValidatorBuilder setFailOnError(Boolean value) {
            failOnError = value == null || value;
            return this;
        }

        @Override
        public ShpValidatorRequest build() {
            if (dataFiles.isEmpty()) {
                throw new IllegalArgumentException("dataFiles must not be empty");
            }
            return new ShpValidatorRequest(dataFiles, encoding, failOnError);
        }
    }

    static final class ShpValidatorHandler
            implements OperationHandler<ShpValidatorRequest, ShpValidatorResult> {
        @Override
        public ShpValidatorResult execute(ShpValidatorRequest request,
                                          OperationContext context) {
            if (request.dataFiles().size() != 1) {
                throw new TabularTaskException("SHP_FILE",
                        msg(context, "task.geo.validator.single"), "prepare", null);
            }
            Path file = request.dataFiles().get(0);
            List<String> errors = new ArrayList<>();
            for (String extension : List.of(".shp", ".shx", ".dbf")) {
                Path sidecar = sibling(file, extension);
                if (!Files.isRegularFile(sidecar)) {
                    errors.add(msg(context, "task.geo.sidecar", sidecar));
                }
            }
            if (errors.isEmpty()) {
                try (Connection connection = GeoFileSupport.openSpatial(context)) {
                    GeoFileSupport.count(connection, file, null);
                } catch (Exception exception) {
                    errors.add(exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage());
                }
            }
            boolean valid = errors.isEmpty();
            if (!valid && request.failOnError()) {
                throw new TabularTaskException("SHP_VALIDATION",
                        msg(context, "task.shpvalidator.failed"), "validate", null);
            }
            return new ShpValidatorResult(valid, errors);
        }

        private static Path sibling(Path file, String extension) {
            String name = file.getFileName().toString();
            int dot = name.lastIndexOf('.');
            return file.getParent().resolve((dot < 0 ? name : name.substring(0, dot))
                    + extension);
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
