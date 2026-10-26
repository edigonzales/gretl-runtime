package ch.so.agi.gretl.tasks.ilivalidator;

import ch.ehi.basics.logging.EhiLogger;
import ch.ehi.basics.logging.LogEvent;
import ch.ehi.basics.logging.LogListener;
import ch.ehi.basics.settings.Settings;
import ch.so.agi.gretl.runtime.api.ConfigBuilder;
import ch.so.agi.gretl.runtime.api.Diagnostic;
import ch.so.agi.gretl.runtime.api.DiagnosticArtifact;
import ch.so.agi.gretl.runtime.api.DiagnosticSeverity;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.interlis2.validator.Validator;

/** Local INTERLIS validation with an invocation-scoped protected report. */
public final class IliValidatorOperation {
    public static final String ID = "gretl.ilivalidator";
    public static final String SYMBOL = "IliValidator";

    private IliValidatorOperation() {
    }

    public static OperationType<IliValidatorRequest, ValidationResult> type() {
        InputField<IliValidatorRequest, Path> dataFile = new InputField<>(
                "dataFile", "path", "Existing INTERLIS XTF transfer file", true,
                Optional.empty(), List.of(), "dataFile = file('data.xtf')",
                IliValidatorOperation::path, (builder, value) -> ((IliValidatorBuilder) builder).setDataFile(value));
        InputField<IliValidatorRequest, Path> modelDirectory = new InputField<>(
                "modelDirectory", "path", "Complete local model directory", true,
                Optional.empty(), List.of(), "modelDirectory = file('models')",
                IliValidatorOperation::path, (builder, value) -> ((IliValidatorBuilder) builder).setModelDirectory(value));
        InputField<IliValidatorRequest, Path> validationConfig = new InputField<>(
                "validationConfig", "path", "Optional local validation configuration", false,
                Optional.empty(), List.of(), "validationConfig = file('validation.toml')",
                IliValidatorOperation::path, (builder, value) -> ((IliValidatorBuilder) builder).setValidationConfig(value));
        OutputField<ValidationResult, Path> file = Fields.output(
                "file", "path", "Validated input file", false, ValidationResult::file);
        OutputField<ValidationResult, Path> reportFile = Fields.output(
                "reportFile", "path", "Protected validation report", false,
                ValidationResult::reportFile);
        OutputField<ValidationResult, Long> warnings = Fields.output(
                "warnings", "integer", "Number of distinctly recorded warnings", false,
                ValidationResult::warnings);
        return new OperationType<>(
                ID, SYMBOL, "Validate INTERLIS XTF with local models",
                IliValidatorRequest.class, ValidationResult.class,
                IliValidatorBuilder::new, IliValidatorHandler::new,
                List.of(dataFile, modelDirectory, validationConfig),
                List.of(file, reportFile, warnings),
                """
                task('validate', IliValidator) {
                    dataFile = file('data.xtf')
                    modelDirectory = file('models')
                }
                """);
    }

    private static Path path(Object value) {
        Path path;
        if (value instanceof Path direct) {
            path = direct;
        } else if (value instanceof CharSequence text) {
            path = Path.of(text.toString());
        } else {
            throw new IllegalArgumentException("Path must be a Path or text");
        }
        if (path.toString().isBlank()) {
            throw new IllegalArgumentException("Path must not be blank");
        }
        return path.normalize();
    }

    public record IliValidatorRequest(
            Path dataFile, Path modelDirectory, Path validationConfig) {
        public IliValidatorRequest {
            java.util.Objects.requireNonNull(dataFile, "dataFile");
            java.util.Objects.requireNonNull(modelDirectory, "modelDirectory");
        }
    }

    public static final class IliValidatorBuilder
            implements ConfigBuilder<IliValidatorRequest> {
        private Path dataFile;
        private Path modelDirectory;
        private Path validationConfig;

        public void setDataFile(Path value) { dataFile = value; }
        public void setModelDirectory(Path value) { modelDirectory = value; }
        public void setValidationConfig(Path value) { validationConfig = value; }

        @Override
        public IliValidatorRequest build() {
            if (dataFile == null) {
                throw new IllegalArgumentException("dataFile is missing");
            }
            if (modelDirectory == null) {
                throw new IllegalArgumentException("modelDirectory is missing");
            }
            return new IliValidatorRequest(dataFile, modelDirectory, validationConfig);
        }
    }

    private static String msg(OperationContext context, String key, Object... args) {
        return Messages.format(context.locale(), key, args);
    }

    static final class IliValidatorHandler
            implements ch.so.agi.gretl.runtime.api.OperationHandler<
                    IliValidatorRequest, ValidationResult> {
        @Override
        public ValidationResult execute(IliValidatorRequest request, OperationContext context)
                throws Exception {
            context.cancellation().throwIfCancellationRequested();
            Path data = request.dataFile().toAbsolutePath().normalize();
            Path models = request.modelDirectory().toAbsolutePath().normalize();
            if (!Files.isRegularFile(data)) {
                throw new IliValidatorTaskException("DATA_FILE_NOT_FOUND",
                        msg(context, "task.ili.dataNotFound"), "prepare", 0, 0,
                        Optional.empty(), null);
            }
            if (!Files.isDirectory(models)) {
                throw new IliValidatorTaskException("MODEL_DIRECTORY_NOT_FOUND",
                        msg(context, "task.ili.modelsNotFound"), "prepare", 0, 0,
                        Optional.empty(), null);
            }
            if (request.validationConfig() != null
                    && !Files.isRegularFile(request.validationConfig().toAbsolutePath()
                    .normalize())) {
                throw new IliValidatorTaskException("CONFIG_NOT_FOUND",
                        msg(context, "task.ili.configNotFound"), "prepare", 0, 0,
                        Optional.empty(), null);
            }
            Path report = context.workPath("validation-report.txt");
            Settings settings = new Settings();
            settings.setValue(Validator.SETTING_ILIDIRS, models.toString());
            settings.setValue(Validator.SETTING_DISABLE_STD_LOGGER, Validator.TRUE);
            if (request.validationConfig() != null) {
                settings.setValue(Validator.SETTING_CONFIGFILE,
                        request.validationConfig().toAbsolutePath().normalize().toString());
            }
            ValidationListener listener = new ValidationListener();
            EhiLogger logger = EhiLogger.getInstance();
            logger.addListener(listener);
            java.io.ByteArrayOutputStream console = new java.io.ByteArrayOutputStream();
            java.io.PrintStream originalOut = System.out;
            java.io.PrintStream originalErr = System.err;
            System.setOut(new java.io.PrintStream(console, true, StandardCharsets.UTF_8));
            System.setErr(new java.io.PrintStream(console, true, StandardCharsets.UTF_8));
            boolean valid;
            try {
                settings.setTransientObject(ch.interlis.iox_j.validator.Validator.CONFIG_CUSTOM_FUNCTIONS,
                        selectedFunctions());
                valid = Validator.runValidation(data.toString(), settings);
                if (listener.events().stream().anyMatch(event -> event.message().contains("Function")
                        && event.message().contains("is not yet implemented"))) {
                    throw new IllegalArgumentException("Required INTERLIS function has no implementation; see validation report");
                }
            } catch (RuntimeException exception) {
                listener.addSynthetic(exception);
                writeReport(report, listener, data, models, console.toString(StandardCharsets.UTF_8));
                DiagnosticArtifact artifact = artifact(report);
                context.report(new Diagnostic(DiagnosticSeverity.ERROR,
                        "VALIDATION_ENGINE_FAILED", msg(context, "task.ili.engineFailed"),
                        Map.of("errors", Integer.toString(listener.errors()),
                                "warnings", Long.toString(listener.warnings())),
                        List.of(artifact)));
                throw new IliValidatorTaskException("VALIDATION_ENGINE_FAILED",
                        msg(context, "task.ili.engineFailed"), "validation",
                        listener.errors(), listener.warnings(), Optional.of(report), exception);
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
                logger.removeListener(listener);
            }
            try {
                writeReport(report, listener, data, models, console.toString(StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new IliValidatorTaskException("REPORT_WRITE_FAILED",
                        msg(context, "task.ili.reportWriteFailed"),
                        "report", listener.errors(), listener.warnings(),
                        Optional.of(report), exception);
            }
            DiagnosticArtifact artifact = artifact(report);
            if (!valid) {
                context.log().log(LogLevel.WARN, "gretl.ilivalidator.failed",
                        msg(context, "task.ili.failed"),
                        Map.of("ilivalidator.errors", Integer.toString(listener.errors()),
                                "ilivalidator.warnings", Long.toString(listener.warnings())),
                        null);
                context.report(new Diagnostic(DiagnosticSeverity.ERROR,
                        "VALIDATION_FAILED", msg(context, "task.ili.invalid"),
                        Map.of("errors", Integer.toString(listener.errors()),
                                "warnings", Long.toString(listener.warnings())),
                        List.of(artifact)));
                throw new IliValidatorTaskException("VALIDATION_FAILED",
                        msg(context, "task.ili.invalid"), "validation",
                        listener.errors(), listener.warnings(), Optional.of(report), null);
            }
            context.report(new Diagnostic(DiagnosticSeverity.INFO,
                    "VALIDATION_REPORT", msg(context, "task.ili.report"),
                    Map.of("warnings", Long.toString(listener.warnings())),
                    List.of(artifact)));
            context.log().log(LogLevel.INFO, "gretl.ilivalidator.finished",
                    msg(context, "task.ili.finished"),
                    Map.of("ilivalidator.warnings", Long.toString(listener.warnings())),
                    null);
            return new ValidationResult(data, report, listener.warnings());
        }

        /** Use IOX's existing startup selection and Java services without
         * importing the format-task module or adding DSL options. */
        private static Map<String, Class<?>> selectedFunctions() {
            var plugins = new java.util.ArrayList<ch.interlis.iox_j.plugins.IoxPlugin>();
            String selected = System.getProperty("ch.ehi.iox-ili.pluginClasses");
            if (selected != null) {
                for (String name : selected.split(",", -1)) {
                    if (name.isBlank()) continue;
                    try {
                        plugins.add(Class.forName(name.strip(), true, IliValidatorOperation.class.getClassLoader())
                                .asSubclass(ch.interlis.iox_j.plugins.IoxPlugin.class).getDeclaredConstructor().newInstance());
                    } catch (ReflectiveOperationException | LinkageError exception) {
                        throw new IllegalArgumentException("Cannot load selected IOX plugin " + name, exception);
                    }
                }
            } else {
                try {
                    java.util.ServiceLoader.load(ch.interlis.iox_j.plugins.IoxPlugin.class,
                            IliValidatorOperation.class.getClassLoader()).forEach(plugins::add);
                } catch (java.util.ServiceConfigurationError | LinkageError exception) {
                    throw new IllegalArgumentException("Cannot load selected IOX service: " + exception.getMessage(), exception);
                }
            }
            Map<String, Class<?>> functions = new java.util.LinkedHashMap<>();
            for (var plugin : plugins) {
                if (!(plugin instanceof ch.interlis.iox_j.validator.InterlisFunction function)) continue;
                String name = function.getQualifiedIliName();
                if (name == null || name.isBlank()) throw new IllegalArgumentException("Selected IOX function has no qualified name: " + plugin.getClass().getName());
                Class<?> previous = functions.putIfAbsent(name, plugin.getClass());
                if (previous != null) throw new IllegalArgumentException("Duplicate qualified INTERLIS function " + name
                        + ": " + previous.getName() + " and " + plugin.getClass().getName());
            }
            return java.util.Collections.unmodifiableMap(functions);
        }

        private static DiagnosticArtifact artifact(Path report) {
            return new DiagnosticArtifact("validation-report", report,
                    "text/plain", true);
        }

        private static void writeReport(Path report, ValidationListener listener,
                                        Path data, Path models, String consoleOutput) throws IOException {
            Files.createDirectories(report.getParent());
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(report,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING))) {
                writer.println("GRETL INTERLIS validation report");
                writer.println("data=" + data.getFileName());
                writer.println("models=" + models.getFileName());
                writer.println("events=" + listener.events().size());
                for (CapturedEvent event : listener.events()) {
                    writer.println(event.kind() + "\t" + event.message().replace('\n', ' ')
                            + "\torigin=" + event.origin()
                            + (event.exception() == null ? "" : "\texception=" + event.exception()));
                }
                for (String line : consoleOutput.lines().toList()) {
                    writer.println("console\t" + line.replace('\n', ' '));
                }
            }
            try {
                Files.setPosixFilePermissions(report, java.util.EnumSet.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException | IOException ignored) {
            }
        }
    }

    record CapturedEvent(int kind, String message, String origin, String exception) {
    }

    static final class ValidationListener implements LogListener {
        private final List<CapturedEvent> events = new ArrayList<>();

        @Override
        public synchronized void logEvent(LogEvent event) {
            if (event == null) {
                return;
            }
            String message = event.getEventMsg() == null ? "" : event.getEventMsg();
            if (event instanceof ch.interlis.iox_j.logging.LogEventImpl iox && iox.getSourceObjectXtfId() != null) {
                message = "tid " + iox.getSourceObjectXtfId() + ": " + message;
            }
            events.add(new CapturedEvent(event.getEventKind(), message,
                    event.getOrigin() == null ? "" : event.getOrigin().toString(),
                    event.getException() == null ? null
                            : event.getException().getClass().getName() + ": "
                            + event.getException().getMessage()));
        }

        synchronized void addSynthetic(Throwable exception) {
            events.add(new CapturedEvent(LogEvent.ERROR,
                    "validator engine threw " + exception.getClass().getName(),
                    "", exception.getClass().getName() + ": " + exception.getMessage()));
        }

        synchronized List<CapturedEvent> events() {
            return List.copyOf(events);
        }

        synchronized int errors() {
            return (int) events.stream().filter(event -> event.kind() == LogEvent.ERROR).count();
        }

        synchronized long warnings() {
            return events.stream().filter(event -> event.kind() == LogEvent.ADAPTION).count();
        }
    }
}
