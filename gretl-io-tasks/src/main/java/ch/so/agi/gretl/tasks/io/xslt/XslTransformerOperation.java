package ch.so.agi.gretl.tasks.io.xslt;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.CancellationToken;
import ch.so.agi.gretl.runtime.api.ConfigBuilder;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import ch.so.agi.gretl.runtime.api.Progress;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.Configuration;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.lib.StandardURIResolver;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;

/** XSLT task: transform one or more XML files with one stylesheet. */
public final class XslTransformerOperation {
    public static final String ID = "gretl.xsl-transformer";
    public static final String SYMBOL = "XslTransformer";
    public static final String DEFAULT_EXTENSION = "xtf";

    private XslTransformerOperation() {
    }

    public static OperationType<XslTransformerRequest, XslTransformerResult> type() {
        InputField<XslTransformerRequest, List<Path>> xmlFiles = new InputField<>(
                "xmlFiles", "list", "XML input files to transform", true,
                Optional.empty(), List.of(),
                "xmlFiles = [file('data/input.xml')]",
                XslTransformerOperation::paths,
                (builder, value) -> ((XslTransformerBuilder) builder).setXmlFiles(value));
        InputField<XslTransformerRequest, Path> xslFile = new InputField<>(
                "xslFile", "path", "Local XSLT stylesheet; use xslFile or xslResource",
                false, Optional.empty(), List.of(),
                "xslFile = file('xsl/transform.xsl')",
                XslTransformerOperation::path,
                (builder, value) -> ((XslTransformerBuilder) builder).setXslFile(value));
        InputField<XslTransformerRequest, String> xslResource = new InputField<>(
                "xslResource", "text", "Classpath XSLT resource under xslt/",
                false, Optional.empty(), List.of(),
                "xslResource = 'transform.xsl'",
                XslTransformerOperation::text,
                (builder, value) -> ((XslTransformerBuilder) builder).setXslResource(value));
        InputField<XslTransformerRequest, Path> outDirectory = new InputField<>(
                "outDirectory", "path", "Directory receiving transformed files", true,
                Optional.empty(), List.of(),
                "outDirectory = workPath('out')",
                XslTransformerOperation::path,
                (builder, value) -> ((XslTransformerBuilder) builder).setOutDirectory(value));
        InputField<XslTransformerRequest, String> fileExtension = new InputField<>(
                "fileExtension", "text", "Output file extension without a leading dot",
                false, Optional.of(DEFAULT_EXTENSION), List.of(),
                "fileExtension = 'xtf'", XslTransformerOperation::extension,
                (builder, value) -> ((XslTransformerBuilder) builder).setFileExtension(value));
        InputField<XslTransformerRequest, Boolean> overwrite = new InputField<>(
                "overwrite", "boolean", "Replace existing output files",
                false, Optional.of(true), List.of(), "overwrite = false",
                XslTransformerOperation::bool,
                (builder, value) -> ((XslTransformerBuilder) builder).setOverwrite(value));
        OutputField<XslTransformerResult, List<Path>> files = Fields.output(
                "files", "list", "Written output files in input order", false,
                XslTransformerResult::files);
        OutputField<XslTransformerResult, Integer> count = Fields.output(
                "count", "integer", "Number of written output files", false,
                XslTransformerResult::count);
        return new OperationType<>(
                ID, SYMBOL, "Transform one or more XML files with an XSLT stylesheet",
                XslTransformerRequest.class, XslTransformerResult.class,
                XslTransformerBuilder::new, XslTransformerHandler::new,
                List.of(xmlFiles, xslFile, xslResource, outDirectory, fileExtension,
                        overwrite),
                List.of(files, count),
                """
                task('transform', XslTransformer) {
                    xmlFiles = [file('data/input.xml')]
                    xslFile = file('xsl/transform.xsl')
                    outDirectory = workPath('out')
                }
                """);
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new IllegalArgumentException("overwrite must be true or false");
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

    private static String text(Object value) {
        if (!(value instanceof CharSequence text)) {
            throw new IllegalArgumentException("Textwert erwartet");
        }
        return text.toString();
    }

    private static List<Path> paths(Object value) {
        List<Object> values = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(values::add);
        } else if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                values.add(java.lang.reflect.Array.get(value, index));
            }
        } else {
            throw new IllegalArgumentException("xmlFiles must be a list of paths");
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("xmlFiles must not be empty");
        }
        List<Path> result = new ArrayList<>(values.size());
        for (Object element : values) {
            result.add(path(element));
        }
        return List.copyOf(result);
    }

    private static String extension(Object value) {
        String extension = text(value).trim();
        while (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        if (extension.isBlank() || extension.contains("/") || extension.contains("\\")
                || !extension.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("fileExtension is invalid");
        }
        return extension;
    }

    public record XslTransformerRequest(
            List<Path> xmlFiles, Path xslFile, String xslResource,
            Path outDirectory, String fileExtension, boolean overwrite) {
        public XslTransformerRequest {
            xmlFiles = List.copyOf(xmlFiles);
            Objects.requireNonNull(outDirectory, "outDirectory");
            Objects.requireNonNull(fileExtension, "fileExtension");
        }
    }

    public record XslTransformerResult(List<Path> files, int count) {
        public XslTransformerResult {
            files = List.copyOf(files);
        }
    }

    public static final class XslTransformerBuilder
            implements ConfigBuilder<XslTransformerRequest> {
        private List<Path> xmlFiles = List.of();
        private Path xslFile;
        private String xslResource;
        private Path outDirectory;
        private String fileExtension = DEFAULT_EXTENSION;
        private boolean overwrite = true;

        public XslTransformerBuilder setXmlFiles(List<Path> value) { xmlFiles = value; return this; }
        public XslTransformerBuilder setXslFile(Path value) { xslFile = value; return this; }
        public XslTransformerBuilder setXslResource(String value) { xslResource = value; return this; }
        public XslTransformerBuilder setOutDirectory(Path value) { outDirectory = value; return this; }
        public XslTransformerBuilder setFileExtension(String value) { fileExtension = value; return this; }
        public XslTransformerBuilder setOverwrite(Boolean value) { overwrite = Boolean.TRUE.equals(value); return this; }

        @Override
        public XslTransformerRequest build() {
            if (xmlFiles == null || xmlFiles.isEmpty()) {
                throw new IllegalArgumentException("xmlFiles must not be empty");
            }
            boolean hasFile = xslFile != null;
            boolean hasResource = xslResource != null && !xslResource.isBlank();
            if (hasFile == hasResource) {
                throw new IllegalArgumentException(
                        "Configure either xslFile or xslResource");
            }
            if (outDirectory == null) {
                throw new IllegalArgumentException("outDirectory is missing");
            }
            List<Path> inputs = xmlFiles.stream()
                    .map(path -> path.toAbsolutePath().normalize()).toList();
            return new XslTransformerRequest(inputs,
                    hasFile ? xslFile.toAbsolutePath().normalize() : null,
                    hasResource ? xslResource : null,
                    outDirectory.toAbsolutePath().normalize(),
                    extension(fileExtension), overwrite);
        }
    }

    static final class XslTransformerHandler
            implements OperationHandler<XslTransformerRequest, XslTransformerResult> {
        @Override
        public XslTransformerResult execute(XslTransformerRequest request,
                                            OperationContext context) throws Exception {
            CancellationToken cancellation = context.cancellation();
            cancellation.throwIfCancellationRequested();
            Path outputDirectory = request.outDirectory();
            if (Files.exists(outputDirectory, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(outputDirectory)) {
                throw config(context, "task.xslt.outputDirectory", null);
            }
            Files.createDirectories(outputDirectory);

            List<Path> outputs = outputs(request, context);
            Processor processor = processor();
            SecureUriResolver resolver = new SecureUriResolver(
                    processor.getUnderlyingConfiguration());
            XsltExecutable executable = compile(processor, resolver, request, context);

            List<Path> written = new ArrayList<>(outputs.size());
            int index = 0;
            for (Path input : request.xmlFiles()) {
                cancellation.throwIfCancellationRequested();
                if (context.budget().expired()) {
                    throw new XsltTaskException("XSLT_TIMEOUT",
                            msg(context, "task.xslt.timeout"), "transform", null);
                }
                index++;
                Path target = outputs.get(index - 1);
                Path temporary = Files.createTempFile(outputDirectory,
                        "." + target.getFileName() + ".", ".part");
                try {
                    transform(processor, executable, input, temporary, context);
                    moveIntoPlace(temporary, target, request.overwrite());
                    written.add(target);
                    context.progress(new Progress("xslt", index,
                            OptionalLong.of(request.xmlFiles().size()), "files"));
                } catch (XsltTaskException exception) {
                    deleteQuietly(temporary, exception);
                    logFailure(context, request, exception);
                    throw exception;
                } catch (IOException exception) {
                    deleteQuietly(temporary, exception);
                    XsltTaskException wrapped = new XsltTaskException("XSLT_IO",
                            msg(context, "task.xslt.io"), "output", exception);
                    logFailure(context, request, wrapped);
                    throw wrapped;
                } catch (RuntimeException exception) {
                    deleteQuietly(temporary, exception);
                    throw exception;
                }
            }
            context.log().log(LogLevel.INFO, "gretl.xslt.finished",
                    msg(context, "task.xslt.finished"),
                    Map.of("xslt.files", Integer.toString(written.size()),
                            "xslt.output", outputDirectory.getFileName() == null
                                    ? outputDirectory.toString()
                                    : outputDirectory.getFileName().toString()), null);
            return new XslTransformerResult(written, written.size());
        }

        private static List<Path> outputs(XslTransformerRequest request,
                                          OperationContext context) {
            Set<Path> seen = new HashSet<>();
            List<Path> outputs = new ArrayList<>();
            for (Path input : request.xmlFiles()) {
                if (!Files.isRegularFile(input) || !Files.isReadable(input)) {
                    throw new XsltTaskException("XSLT_INPUT",
                            msg(context, "task.xslt.input"), "prepare", null);
                }
                String base = baseName(input);
                Path output = request.outDirectory()
                        .resolve(base + "." + request.fileExtension()).normalize();
                if (!output.startsWith(request.outDirectory())) {
                    throw config(context, "task.xslt.outputInvalid", null);
                }
                if (!seen.add(output)) {
                    throw config(context, "task.xslt.outputCollision", null);
                }
                if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
                    if (!request.overwrite()) {
                        throw new XsltTaskException("XSLT_TARGET_EXISTS",
                                msg(context, "task.xslt.targetExists"), "prepare", null);
                    }
                    if (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)) {
                        throw new XsltTaskException("XSLT_TARGET_UNSAFE",
                                msg(context, "task.xslt.targetUnsafe"), "prepare", null);
                    }
                }
                outputs.add(output);
            }
            return outputs;
        }

        private static XsltExecutable compile(Processor processor, SecureUriResolver resolver,
                                              XslTransformerRequest request,
                                              OperationContext context) {
            XsltCompiler compiler = processor.newXsltCompiler();
            compiler.setURIResolver(resolver);
            try {
                if (request.xslFile() != null) {
                    return compiler.compile(new StreamSource(request.xslFile().toFile()));
                }
                String resourcePath = "xslt/" + request.xslResource();
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                if (loader == null) {
                    loader = XslTransformerOperation.class.getClassLoader();
                }
                InputStream stream = loader.getResourceAsStream(resourcePath);
                if (stream == null) {
                    throw new XsltTaskException("XSLT_RESOURCE",
                            msg(context, "task.xslt.resource"), "compile", null);
                }
                try (stream) {
                    StreamSource source = new StreamSource(stream);
                    source.setSystemId(resourcePath);
                    return compiler.compile(source);
                }
            } catch (XsltTaskException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new XsltTaskException("XSLT_COMPILE",
                        msg(context, "task.xslt.compile"), "compile", exception);
            }
        }

        private static void transform(Processor processor, XsltExecutable executable,
                                      Path input, Path output, OperationContext context) {
            try {
                DocumentBuilder builder = processor.newDocumentBuilder();
                XdmNode source = builder.build(new StreamSource(input.toFile()));
                Serializer serializer = processor.newSerializer(output.toFile());
                XsltTransformer transformer = executable.load();
                try {
                    transformer.setInitialContextNode(source);
                    transformer.setDestination(serializer);
                    transformer.transform();
                } finally {
                    transformer.close();
                }
            } catch (Exception exception) {
                throw new XsltTaskException("XSLT_TRANSFORM",
                        msg(context, "task.xslt.transform"), "transform", exception);
            }
        }

        private static Processor processor() {
            Processor processor = new Processor(false);
            Configuration configuration = processor.getUnderlyingConfiguration();
            configuration.setConfigurationProperty(FeatureKeys.ALLOW_EXTERNAL_FUNCTIONS, false);
            configuration.setConfigurationProperty(FeatureKeys.ALLOWED_PROTOCOLS, "file,jar");
            configuration.setConfigurationProperty(
                    FeatureKeys.XML_PARSER_FEATURE
                            + "http://xml.org/sax/features/external-general-entities", false);
            configuration.setConfigurationProperty(
                    FeatureKeys.XML_PARSER_FEATURE
                            + "http://xml.org/sax/features/external-parameter-entities", false);
            configuration.setConfigurationProperty(
                    FeatureKeys.XML_PARSER_FEATURE
                            + "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false);
            configuration.setConfigurationProperty(
                    FeatureKeys.XML_PARSER_FEATURE
                            + "http://apache.org/xml/features/disallow-doctype-decl",
                    true);
            return processor;
        }

        private static String baseName(Path input) {
            String name = input.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            if (base.isBlank()) {
                throw new IllegalArgumentException("XML file has no usable name: " + input);
            }
            return base;
        }

        private static void moveIntoPlace(Path temporary, Path target, boolean overwrite)
                throws IOException {
            StandardCopyOption[] atomic = overwrite
                    ? new StandardCopyOption[] {StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING}
                    : new StandardCopyOption[] {StandardCopyOption.ATOMIC_MOVE};
            try {
                Files.move(temporary, target, atomic);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                if (overwrite) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temporary, target);
                }
            }
        }

        private static void logFailure(OperationContext context,
                                       XslTransformerRequest request,
                                       XsltTaskException exception) {
            context.log().log(LogLevel.WARN, "gretl.xslt.failed",
                    msg(context, "task.xslt.failed"),
                    Map.of("xslt.phase", exception.phase(),
                            "xslt.files", Integer.toString(request.xmlFiles().size())),
                    exception);
        }

        private static void deleteQuietly(Path path, Throwable primary) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                primary.addSuppressed(exception);
            }
        }

        private static XsltTaskException config(OperationContext context, String key,
                                                Throwable cause) {
            return new XsltTaskException("XSLT_CONFIG", msg(context, key),
                    "configuration", cause);
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }
    }

    private static final class SecureUriResolver implements javax.xml.transform.URIResolver {
        private final Configuration configuration;

        private SecureUriResolver(Configuration configuration) {
            this.configuration = configuration;
        }

        @Override
        public Source resolve(String href, String base) throws TransformerException {
            if (href != null && (href.startsWith("http:") || href.startsWith("https:"))) {
                throw new TransformerException("External URI resolution is disabled");
            }
            return new StandardURIResolver(configuration).resolve(href, base);
        }
    }
}
