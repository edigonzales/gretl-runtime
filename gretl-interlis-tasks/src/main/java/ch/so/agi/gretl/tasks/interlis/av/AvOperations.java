package ch.so.agi.gretl.tasks.interlis.av;

import ch.so.agi.gretl.internal.formats.FormatTaskSupport;

import ch.so.agi.gretl.internal.av.Av2chEngine;
import ch.so.agi.gretl.internal.av.Av2geobauEngine;
import ch.so.agi.gretl.internal.InterlisTaskException;
import ch.so.agi.gretl.internal.ioxwkf.Gpkg2DxfEngine;
import ch.so.agi.gretl.runtime.api.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Av2ch, Av2geobau and Gpkg2Dxf operation definitions. */
public final class AvOperations {
    private AvOperations() {}

    public static OperationType<Av2chRequest, DirectoryResult> av2ch() {
        InputField<Av2chRequest, List<Path>> files = field("inputFiles", "list",
                "ITF files to convert", true, null, "inputFiles = [file('data.itf')]",
                FormatTaskSupport::paths, (b, v) -> ((Av2chBuilder) b).inputFiles = v);
        InputField<Av2chRequest, Path> output = field("outputDirectory", "path",
                "Output directory", true, null, "outputDirectory = parameter('output')",
                FormatTaskSupport::path, (b, v) -> ((Av2chBuilder) b).outputDirectory = v);
        InputField<Av2chRequest, String> modeldir = field("modeldir", "text",
                "Model directory", false, null, "modeldir = file('models')",
                FormatTaskSupport::modelSearchPath, (b, v) -> ((Av2chBuilder) b).modeldir = v);
        InputField<Av2chRequest, String> language = field("language", "text",
                "Language (de or it)", false, "de", "language = 'de'",
                FormatTaskSupport::text, (b, v) -> ((Av2chBuilder) b).language = v);
        InputField<Av2chRequest, Boolean> zip = field("zip", "boolean",
                "Write ZIP files", false, false, "zip = true",
                FormatTaskSupport::bool, (b, v) -> ((Av2chBuilder) b).zip = v);
        return new OperationType<>("gretl.av2ch", "Av2ch",
                "Convert AV ITF files to the federal model", Av2chRequest.class,
                DirectoryResult.class, Av2chBuilder::new, Av2chHandler::new,
                List.of(files, output, modeldir, language, zip),
                List.of(Fields.output("directory", "path", "Output directory", false,
                        DirectoryResult::directory)),
                "parameter('output', type: 'path', required: true, description: 'Ausgabeverzeichnis')\n\ntask('convert', Av2ch) {\n    inputFiles = [file('data.itf')]\n    outputDirectory = parameter('output')\n}\n\ndefaults 'convert'\n");
    }

    public static OperationType<Av2geobauRequest, DirectoryResult> av2geobau() {
        InputField<Av2geobauRequest, List<Path>> files = field("itfFiles", "list",
                "ITF files to convert", true, null, "itfFiles = [file('data.itf')]",
                FormatTaskSupport::paths, (b, v) -> ((Av2geobauBuilder) b).itfFiles = v);
        InputField<Av2geobauRequest, Path> output = field("dxfDirectory", "path",
                "DXF output directory", true, null, "dxfDirectory = parameter('output')",
                FormatTaskSupport::path, (b, v) -> ((Av2geobauBuilder) b).dxfDirectory = v);
        InputField<Av2geobauRequest, String> modeldir = field("modeldir", "text",
                "Model directory", false, null, "modeldir = file('models')",
                FormatTaskSupport::modelSearchPath, (b, v) -> ((Av2geobauBuilder) b).modeldir = v);
        InputField<Av2geobauRequest, Path> log = field("logFile", "path", "Log file", false,
                null, "logFile = workPath('av2geobau.log')", FormatTaskSupport::path,
                (b, v) -> ((Av2geobauBuilder) b).logFile = v);
        InputField<Av2geobauRequest, String> proxy = field("proxy", "text", "HTTP proxy", false,
                null, "proxy = 'proxy.example'", FormatTaskSupport::text, (b, v) -> ((Av2geobauBuilder) b).proxy = v);
        InputField<Av2geobauRequest, Integer> proxyPort = field("proxyPort", "integer",
                "HTTP proxy port", false, null, "proxyPort = 8080", FormatTaskSupport::integer,
                (b, v) -> ((Av2geobauBuilder) b).proxyPort = v);
        InputField<Av2geobauRequest, Boolean> zip = field("zip", "boolean", "Write ZIP files",
                false, false, "zip = true", FormatTaskSupport::bool, (b, v) -> ((Av2geobauBuilder) b).zip = v);
        return new OperationType<>("gretl.av2geobau", "Av2geobau",
                "Convert AV ITF files to GeoBau DXF", Av2geobauRequest.class,
                DirectoryResult.class, Av2geobauBuilder::new, Av2geobauHandler::new,
                List.of(files, output, modeldir, log, proxy, proxyPort, zip),
                List.of(Fields.output("directory", "path", "DXF output directory", false,
                        DirectoryResult::directory)),
                "parameter('output', type: 'path', required: true, description: 'Ausgabeverzeichnis')\n\ntask('convert', Av2geobau) {\n    itfFiles = [file('data.itf')]\n    dxfDirectory = parameter('output')\n}\n\ndefaults 'convert'\n");
    }

    public static OperationType<Gpkg2DxfRequest, DirectoryResult> gpkg2dxf() {
        InputField<Gpkg2DxfRequest, Path> file = field("dataFile", "path",
                "GeoPackage file", true, null, "dataFile = file('data.gpkg')",
                FormatTaskSupport::path, (b, v) -> ((Gpkg2DxfBuilder) b).dataFile = v);
        InputField<Gpkg2DxfRequest, Path> output = field("outputDir", "path",
                "DXF output directory", true, null, "outputDir = parameter('output')",
                FormatTaskSupport::path, (b, v) -> ((Gpkg2DxfBuilder) b).outputDir = v);
        return new OperationType<>("gretl.gpkg2dxf", "Gpkg2Dxf",
                "Convert ili2gpkg GeoPackage tables to DXF", Gpkg2DxfRequest.class,
                DirectoryResult.class, Gpkg2DxfBuilder::new, Gpkg2DxfHandler::new,
                List.of(file, output),
                List.of(Fields.output("directory", "path", "DXF output directory", false,
                        DirectoryResult::directory)),
                "parameter('output', type: 'path', required: true, description: 'Ausgabeverzeichnis')\n\ntask('convert', Gpkg2Dxf) {\n    dataFile = file('data.gpkg')\n    outputDir = parameter('output')\n}\n\ndefaults 'convert'\n");
    }

    private static <T, R> InputField<T, R> field(String name, String type, String desc,
            boolean required, R defaultValue, String example,
            Function<Object, R> converter, BiConsumer<Object, R> setter) {
        return new InputField<T, R>(name, type, desc, required,
                defaultValue == null ? Optional.empty() : Optional.of(defaultValue),
                List.of(), example, converter,
                (builder, value) -> setter.accept(builder, value));
    }

    public record Av2chRequest(List<Path> inputFiles, Path outputDirectory, String modeldir,
                               String language, boolean zip) {
        public Av2chRequest { inputFiles = List.copyOf(inputFiles); }
    }
    public record Av2geobauRequest(List<Path> itfFiles, Path dxfDirectory, String modeldir,
                                   Path logFile, String proxy, Integer proxyPort, boolean zip) {
        public Av2geobauRequest { itfFiles = List.copyOf(itfFiles); }
    }
    public record Gpkg2DxfRequest(Path dataFile, Path outputDir) {}
    public record DirectoryResult(Path directory) {}

    public static final class Av2chBuilder implements ConfigBuilder<Av2chRequest> {
        private List<Path> inputFiles = List.of();
        private Path outputDirectory;
        private String modeldir;
        private String language = "de";
        private boolean zip;
        public void setInputFiles(List<Path> value) { inputFiles = value == null ? List.of() : value; }
        public void setOutputDirectory(Path value) { outputDirectory = value; }
        public void setModeldir(String value) { modeldir = value; }
        public void setLanguage(String value) { language = value; }
        public void setZip(Boolean value) { zip = Boolean.TRUE.equals(value); }

        @Override public Av2chRequest build() {
            if (inputFiles.isEmpty()) throw new IllegalArgumentException("inputFiles is missing");
            if (outputDirectory == null) throw new IllegalArgumentException("outputDirectory is missing");
            return new Av2chRequest(inputFiles, outputDirectory, modeldir, language, zip);
        }
    }
    public static final class Av2geobauBuilder implements ConfigBuilder<Av2geobauRequest> {
        private List<Path> itfFiles = List.of();
        private Path dxfDirectory;
        private String modeldir;
        private Path logFile;
        private String proxy;
        private Integer proxyPort;
        private boolean zip;
        public void setItfFiles(List<Path> value) { itfFiles = value == null ? List.of() : value; }
        public void setDxfDirectory(Path value) { dxfDirectory = value; }
        public void setModeldir(String value) { modeldir = value; }
        public void setLogFile(Path value) { logFile = value; }
        public void setProxy(String value) { proxy = value; }
        public void setProxyPort(Integer value) { proxyPort = value; }
        public void setZip(Boolean value) { zip = Boolean.TRUE.equals(value); }

        @Override public Av2geobauRequest build() {
            if (itfFiles.isEmpty()) throw new IllegalArgumentException("itfFiles is missing");
            if (dxfDirectory == null) throw new IllegalArgumentException("dxfDirectory is missing");
            return new Av2geobauRequest(itfFiles, dxfDirectory, modeldir, logFile, proxy, proxyPort, zip);
        }
    }
    public static final class Gpkg2DxfBuilder implements ConfigBuilder<Gpkg2DxfRequest> {
        private Path dataFile;
        private Path outputDir;
        public void setDataFile(Path value) { dataFile = value; }
        public void setOutputDir(Path value) { outputDir = value; }

        @Override public Gpkg2DxfRequest build() {
            if (dataFile == null) throw new IllegalArgumentException("dataFile is missing");
            if (outputDir == null) throw new IllegalArgumentException("outputDir is missing");
            return new Gpkg2DxfRequest(dataFile, outputDir);
        }
    }

    static final class Av2chHandler implements OperationHandler<Av2chRequest, DirectoryResult> {
        @Override public DirectoryResult execute(Av2chRequest request, OperationContext context) {
            try {
                new Av2chEngine().convert(new Av2chEngine.Av2chRequest(request.inputFiles(),
                        request.outputDirectory(), request.modeldir(), request.language(), request.zip()));
                return new DirectoryResult(request.outputDirectory());
            } catch (InterlisTaskException e) { throw e; }
            catch (Exception e) { throw new InterlisTaskException("AV2CH", "Av2ch failed", "convert", Optional.empty(), e); }
        }
    }
    static final class Av2geobauHandler implements OperationHandler<Av2geobauRequest, DirectoryResult> {
        @Override public DirectoryResult execute(Av2geobauRequest request, OperationContext context) {
            try {
                new Av2geobauEngine().convert(new Av2geobauEngine.Av2geobauRequest(
                        request.itfFiles(), request.dxfDirectory(), request.modeldir(),
                        request.logFile(), request.proxy(), request.proxyPort(), request.zip()));
                return new DirectoryResult(request.dxfDirectory());
            } catch (InterlisTaskException e) { throw e; }
            catch (Exception e) { throw new InterlisTaskException("AV2GEOBAU", "Av2geobau failed", "convert", Optional.empty(), e); }
        }
    }
    static final class Gpkg2DxfHandler implements OperationHandler<Gpkg2DxfRequest, DirectoryResult> {
        @Override public DirectoryResult execute(Gpkg2DxfRequest request, OperationContext context) {
            try {
                new Gpkg2DxfEngine().convert(new Gpkg2DxfEngine.Gpkg2DxfRequest(
                        request.dataFile(), request.outputDir()));
                return new DirectoryResult(request.outputDir());
            } catch (InterlisTaskException e) { throw e; }
            catch (Exception e) { throw new InterlisTaskException("GPKG2DXF", "Gpkg2Dxf failed", "convert", Optional.empty(), e); }
        }
    }
}
