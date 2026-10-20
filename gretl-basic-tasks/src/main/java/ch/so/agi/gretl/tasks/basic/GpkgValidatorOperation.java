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

/** GpkgValidator task: structural GeoPackage layer validation. */
public final class GpkgValidatorOperation {
    public static final String ID = "gretl.gpkg-validator";
    public static final String SYMBOL = "GpkgValidator";

    private GpkgValidatorOperation() {
    }

    public static OperationType<GpkgValidatorRequest, GpkgValidatorResult> type() {
        InputField<GpkgValidatorRequest, List<Path>> dataFiles = new InputField<>(
                "dataFiles", "list", "GeoPackage files to validate", true,
                Optional.empty(), List.of(), "dataFiles = [file('data/input.gpkg')]",
                GpkgValidatorOperation::paths,
                (builder, value) -> ((GpkgValidatorBuilder) builder).setDataFiles(value));
        InputField<GpkgValidatorRequest, String> tableName = new InputField<>(
                "tableName", "text", "GeoPackage layer to validate", true,
                Optional.empty(), List.of(), "tableName = 'abbaustelle'",
                GpkgValidatorOperation::text,
                (builder, value) -> ((GpkgValidatorBuilder) builder).setTableName(value));
        InputField<GpkgValidatorRequest, Boolean> fail = new InputField<>(
                "failOnError", "boolean", "Fail the task when validation fails", false,
                Optional.of(true), List.of(), "failOnError = false",
                GpkgValidatorOperation::bool,
                (builder, value) -> ((GpkgValidatorBuilder) builder).setFailOnError(value));
        OutputField<GpkgValidatorResult, Boolean> valid = Fields.output(
                "valid", "boolean", "Validation result", false,
                GpkgValidatorResult::valid);
        OutputField<GpkgValidatorResult, List<String>> errors = Fields.output(
                "errors", "list", "Validation errors", false,
                GpkgValidatorResult::errors);
        return new OperationType<>(
                ID, SYMBOL, "Validate a GeoPackage layer structure",
                GpkgValidatorRequest.class, GpkgValidatorResult.class,
                GpkgValidatorBuilder::new, GpkgValidatorHandler::new,
                List.of(dataFiles, tableName, fail), List.of(valid, errors),
                """
                task('validate', GpkgValidator) {
                    dataFiles = [file('data/input.gpkg')]
                    tableName = 'abbaustelle'
                }
                """);
    }

    public record GpkgValidatorRequest(
            List<Path> dataFiles, String tableName, boolean failOnError) {
        public GpkgValidatorRequest {
            dataFiles = List.copyOf(dataFiles);
        }
    }

    public record GpkgValidatorResult(boolean valid, List<String> errors) {
        public GpkgValidatorResult {
            errors = List.copyOf(errors);
        }
    }

    public static final class GpkgValidatorBuilder
            implements ch.so.agi.gretl.runtime.api.ConfigBuilder<GpkgValidatorRequest> {
        private List<Path> dataFiles = List.of();
        private String tableName;
        private boolean failOnError = true;

        public GpkgValidatorBuilder setDataFiles(List<Path> value) {
            dataFiles = value == null ? List.of() : value;
            return this;
        }

        public GpkgValidatorBuilder setTableName(String value) {
            tableName = value;
            return this;
        }

        public GpkgValidatorBuilder setFailOnError(Boolean value) {
            failOnError = value == null || value;
            return this;
        }

        @Override
        public GpkgValidatorRequest build() {
            if (dataFiles.isEmpty()) {
                throw new IllegalArgumentException("dataFiles must not be empty");
            }
            if (tableName == null || tableName.isBlank()) {
                throw new IllegalArgumentException("tableName is missing");
            }
            return new GpkgValidatorRequest(dataFiles, tableName, failOnError);
        }
    }

    static final class GpkgValidatorHandler
            implements OperationHandler<GpkgValidatorRequest, GpkgValidatorResult> {
        @Override
        public GpkgValidatorResult execute(GpkgValidatorRequest request,
                                           OperationContext context) {
            if (request.dataFiles().size() != 1) {
                throw new TabularTaskException("GPKG_FILE",
                        msg(context, "task.geo.validator.single"), "prepare", null);
            }
            Path file = request.dataFiles().get(0);
            List<String> errors = new ArrayList<>();
            if (!Files.isRegularFile(file)) {
                errors.add(msg(context, "task.geo.file", file));
            } else {
                try (Connection connection = GeoFileSupport.openSpatial(context)) {
                    long count = GeoFileSupport.count(connection, file, request.tableName());
                    if (count < 0) {
                        errors.add(msg(context, "task.geo.validator.layer",
                                request.tableName()));
                    }
                } catch (Exception exception) {
                    errors.add(exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage());
                }
            }
            boolean valid = errors.isEmpty();
            if (!valid && request.failOnError()) {
                throw new TabularTaskException("GPKG_VALIDATION",
                        msg(context, "task.gpkgvalidator.failed"), "validate", null);
            }
            return new GpkgValidatorResult(valid, errors);
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
        if (value instanceof CharSequence text && !text.toString().isBlank()) {
            return text.toString();
        }
        throw new IllegalArgumentException("Non-blank text expected");
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new IllegalArgumentException("Boolean expected");
    }
}
