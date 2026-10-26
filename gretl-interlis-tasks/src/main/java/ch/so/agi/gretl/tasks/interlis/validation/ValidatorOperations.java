package ch.so.agi.gretl.tasks.interlis.validation;

import ch.so.agi.gretl.internal.formats.FormatTaskSupport;

import ch.so.agi.gretl.internal.InterlisTaskException;
import ch.so.agi.gretl.internal.validation.InterlisValidationRequest;
import ch.so.agi.gretl.internal.validation.InterlisValidationSupport;
import ch.so.agi.gretl.runtime.api.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Operation factories for CsvValidator, JsonValidator, GpkgValidator and ShpValidator. */
public final class ValidatorOperations {
    public enum Kind { CSV, JSON, GPKG, SHP }
    private ValidatorOperations() {}

    public static OperationType<ValidatorRequest, Result> csv() {
        return type(Kind.CSV, "gretl.csv-validator", "CsvValidator",
                "Validate CSV with ilivalidator and the CSV reader", true, false, false);
    }

    public static OperationType<ValidatorRequest, Result> json() {
        return type(Kind.JSON, "gretl.json-validator", "JsonValidator",
                "Validate JSON with ilivalidator and the JSON reader", false, false, false);
    }

    public static OperationType<ValidatorRequest, Result> gpkg() {
        return type(Kind.GPKG, "gretl.gpkg-validator", "GpkgValidator",
                "Validate GeoPackage with ilivalidator and the GeoPackage reader", false, false, true);
    }

    public static OperationType<ValidatorRequest, Result> shp() {
        return type(Kind.SHP, "gretl.shp-validator", "ShpValidator",
                "Validate Shapefile with ilivalidator and the Shapefile reader", false, true, false);
    }

    private static OperationType<ValidatorRequest, Result> type(Kind kind, String id,
            String symbol, String description, boolean csvFields, boolean shpFields,
            boolean tableField) {
        List<InputField<ValidatorRequest, ?>> fields = new ArrayList<>();
        fields.add(field("dataFiles", "list", "Data files to validate", true,
                null, "dataFiles = [file('data/input')]", FormatTaskSupport::paths,
                (b, v) -> b.setDataFiles(v)));
        fields.add(field("modelNames", "list", "INTERLIS model names", false,
                List.<String>of(), "modelNames = ['Model']", FormatTaskSupport::strings,
                (b, v) -> b.setModelNames(v)));
        fields.add(field("modelDirectories", "list", "Model directories or repositories",
                false, List.<String>of(), "modelDirectories = [file('models')]",
                FormatTaskSupport::pathStrings, (b, v) -> b.setModelDirectories(v)));
        fields.add(field("configFile", "path", "Validation configuration file", false,
                null, "configFile = file('validation.toml')", FormatTaskSupport::path,
                (b, v) -> b.setConfigFile(v)));
        fields.add(field("configRepositoryId", "text", "Validation configuration ID",
                false, null, "configRepositoryId = 'ch.example.validation'",
                FormatTaskSupport::text, (b, v) -> b.setConfigRepositoryId(v)));
        fields.add(field("metaConfigFile", "path", "Meta configuration file", false,
                null, "metaConfigFile = file('meta.toml')", FormatTaskSupport::path,
                (b, v) -> b.setMetaConfigFile(v)));
        fields.add(field("metaConfigRepositoryId", "text", "Meta configuration ID",
                false, null, "metaConfigRepositoryId = 'ch.example.meta'",
                FormatTaskSupport::text, (b, v) -> b.setMetaConfigRepositoryId(v)));
        fields.add(field("forceTypeValidation", "boolean", "Force type validation", false,
                false, "forceTypeValidation = true", FormatTaskSupport::bool,
                (b, v) -> b.setForceTypeValidation(v)));
        fields.add(field("disableAreaValidation", "boolean", "Disable area validation", false,
                false, "disableAreaValidation = true", FormatTaskSupport::bool,
                (b, v) -> b.setDisableAreaValidation(v)));
        fields.add(field("multiplicityOff", "boolean", "Disable multiplicity validation", false,
                false, "multiplicityOff = true", FormatTaskSupport::bool,
                (b, v) -> b.setMultiplicityOff(v)));
        fields.add(field("allObjectsAccessible", "boolean", "All objects accessible", false,
                false, "allObjectsAccessible = true", FormatTaskSupport::bool,
                (b, v) -> b.setAllObjectsAccessible(v)));
        fields.add(field("skipPolygonBuilding", "boolean", "Skip polygon building", false,
                false, "skipPolygonBuilding = true", FormatTaskSupport::bool,
                (b, v) -> b.setSkipPolygonBuilding(v)));
        fields.add(field("logFile", "path", "Validation log file", false,
                null, "logFile = file('build/validation.log')", FormatTaskSupport::path,
                (b, v) -> b.setLogFile(v)));
        fields.add(field("xtfLogFile", "path", "IliVErrors log file", false,
                null, "xtfLogFile = file('build/errors.xtf')", FormatTaskSupport::path,
                (b, v) -> b.setXtfLogFile(v)));
        fields.add(field("proxy", "text", "HTTP proxy host", false, null,
                "proxy = 'proxy.example'", FormatTaskSupport::text, (b, v) -> b.setProxy(v)));
        fields.add(field("proxyPort", "integer", "HTTP proxy port", false, null,
                "proxyPort = 8080", FormatTaskSupport::integer, (b, v) -> b.setProxyPort(v)));
        fields.add(field("failOnError", "boolean", "Fail on validation errors", false,
                true, "failOnError = false", FormatTaskSupport::bool, (b, v) -> b.setFailOnError(v)));
        if (csvFields) {
            fields.add(field("firstLineIsHeader", "boolean", "Use first CSV line as header",
                    false, true, "firstLineIsHeader = false", FormatTaskSupport::bool,
                    (b, v) -> b.setFirstLineIsHeader(v)));
            fields.add(field("valueDelimiter", "text", "CSV quote character", false, null,
                    "valueDelimiter = '\"'", FormatTaskSupport::text, (b, v) -> b.setValueDelimiter(v)));
            fields.add(field("valueSeparator", "text", "CSV field separator", false, null,
                    "valueSeparator = ';'", FormatTaskSupport::text, (b, v) -> b.setValueSeparator(v)));
            fields.add(field("encoding", "text", "CSV encoding", false, null,
                    "encoding = 'UTF-8'", FormatTaskSupport::text, (b, v) -> b.setEncoding(v)));
        }
        if (shpFields) {
            fields.add(field("encoding", "text", "Shapefile encoding", false, null,
                    "encoding = 'UTF-8'", FormatTaskSupport::text, (b, v) -> b.setEncoding(v)));
        }
        if (tableField) {
            fields.add(field("tableName", "text", "GeoPackage table name", true, null,
                    "tableName = 'abbaustelle'", FormatTaskSupport::text,
                    (b, v) -> b.setTableName(v)));
        }
        OutputField<Result, Boolean> valid = Fields.output("valid", "boolean",
                "Validation result", false, Result::valid);
        OutputField<Result, Path> log = Fields.output("logFile", "path",
                "Validation log file", false, Result::logFile);
        return new OperationType<>(id, symbol, description, ValidatorRequest.class,
                Result.class, ValidatorBuilder::new, () -> new Handler(kind), fields,
                List.of(valid, log),
                "task('validate', " + symbol + ") {\n    dataFiles = [file('data/input')]\n"
                        + (tableField ? "    tableName = 'abbaustelle'\n" : "") + "}\n");
    }

    @SuppressWarnings("unchecked")
    private static <T> InputField<ValidatorRequest, T> field(String name, String type,
            String desc, boolean required, T defaultValue, String example,
            Function<Object, T> converter, BiConsumer<ValidatorBuilder, T> setter) {
        return new InputField<>(name, type, desc, required,
                defaultValue == null ? Optional.empty() : Optional.of(defaultValue),
                List.of(), example, converter,
                (builder, value) -> setter.accept((ValidatorBuilder) builder, value));
    }

    public record Result(boolean valid, Path logFile) {}

    static final class Handler implements OperationHandler<ValidatorRequest, Result> {
        private final Kind kind;
        Handler(Kind kind) { this.kind = kind; }

        @Override public Result execute(ValidatorRequest request, OperationContext context) {
            validateRequest(request, context);
            InterlisValidationRequest normalized = new InterlisValidationRequest(
                    request.dataFiles(), request.modelNames(), request.modelDirectories(),
                    request.configFile(), request.configRepositoryId(),
                    request.metaConfigFile(), request.metaConfigRepositoryId(),
                    request.forceTypeValidation(), request.disableAreaValidation(),
                    request.multiplicityOff(), request.allObjectsAccessible(),
                    request.skipPolygonBuilding(), request.logFile(), request.xtfLogFile(),
                    request.proxy(), request.proxyPort(), request.tableName(),
                    request.firstLineIsHeader(), request.valueDelimiter(),
                    request.valueSeparator(), request.encoding());
            InterlisValidationSupport support = new InterlisValidationSupport();
            boolean valid;
            try {
                valid = switch (kind) {
                    case CSV -> support.validateCsv(normalized);
                    case JSON -> support.validateJson(normalized);
                    case GPKG -> support.validateGpkg(normalized);
                    case SHP -> support.validateShp(normalized);
                };
            } catch (InterlisTaskException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new InterlisTaskException("VALIDATION_ENGINE",
                        "INTERLIS validation failed", "validation",
                        Optional.ofNullable(request.logFile()), exception);
            }
            if (!valid && request.failOnError()) {
                throw new InterlisTaskException("VALIDATION_FAILED",
                        "INTERLIS validation failed", "validation",
                        Optional.ofNullable(request.logFile()), null);
            }
            return new Result(valid, request.logFile());
        }

        private void validateRequest(ValidatorRequest request, OperationContext context) {
            if (request.dataFiles().isEmpty()) {
                throw new InterlisTaskException("VALIDATION_INPUT",
                        FormatTaskSupport.msg(context, "task.validation.dataFiles"),
                        "configuration", Optional.empty(), null);
            }
            for (Path file : request.dataFiles()) {
                if (!Files.isRegularFile(file)) {
                    throw new InterlisTaskException("VALIDATION_INPUT",
                            FormatTaskSupport.msg(context, "task.validation.file", file),
                            "configuration", Optional.empty(), null);
                }
            }
            if ((kind == Kind.CSV || kind == Kind.SHP) && request.dataFiles().size() != 1) {
                throw new InterlisTaskException("VALIDATION_INPUT",
                        FormatTaskSupport.msg(context, "task.validation.single"),
                        "configuration", Optional.empty(), null);
            }
            if (kind == Kind.GPKG && (request.tableName() == null || request.tableName().isBlank())) {
                throw new InterlisTaskException("VALIDATION_INPUT",
                        FormatTaskSupport.msg(context, "task.validation.table"),
                        "configuration", Optional.empty(), null);
            }
        }
    }
}
