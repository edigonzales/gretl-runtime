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
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Gpkg2Shp task: convert GeoPackage layers to Shapefiles via DuckDB spatial/GDAL. */
public final class Gpkg2ShpOperation {
    public static final String ID = "gretl.gpkg2shp";
    public static final String SYMBOL = "Gpkg2Shp";

    private Gpkg2ShpOperation() {
    }

    public static OperationType<Gpkg2ShpRequest, Gpkg2ShpResult> type() {
        InputField<Gpkg2ShpRequest, Path> dataFile = new InputField<>(
                "dataFile", "path", "GeoPackage file to convert", true,
                Optional.empty(), List.of(), "dataFile = file('data/input.gpkg')",
                Gpkg2ShpOperation::path,
                (builder, value) -> ((Gpkg2ShpBuilder) builder).setDataFile(value));
        InputField<Gpkg2ShpRequest, Path> outputDir = new InputField<>(
                "outputDir", "path", "Shapefile output directory", true,
                Optional.empty(), List.of(), "outputDir = path('build/shp')",
                Gpkg2ShpOperation::path,
                (builder, value) -> ((Gpkg2ShpBuilder) builder).setOutputDir(value));
        OutputField<Gpkg2ShpResult, List<Path>> files = Fields.output(
                "files", "list", "Written Shapefiles", false, Gpkg2ShpResult::files);
        OutputField<Gpkg2ShpResult, Integer> layers = Fields.output(
                "layers", "integer", "Converted layers", false, Gpkg2ShpResult::layers);
        return new OperationType<>(
                ID, SYMBOL, "Convert GeoPackage layers to Shapefiles",
                Gpkg2ShpRequest.class, Gpkg2ShpResult.class,
                Gpkg2ShpBuilder::new, Gpkg2ShpHandler::new,
                List.of(dataFile, outputDir), List.of(files, layers),
                """
                task('convert', Gpkg2Shp) {
                    dataFile = file('data/input.gpkg')
                    outputDir = path('build/shp')
                }
                """);
    }

    public record Gpkg2ShpRequest(Path dataFile, Path outputDir) {
    }

    public record Gpkg2ShpResult(List<Path> files, int layers) {
        public Gpkg2ShpResult {
            files = List.copyOf(files);
        }
    }

    public static final class Gpkg2ShpBuilder
            implements ch.so.agi.gretl.runtime.api.ConfigBuilder<Gpkg2ShpRequest> {
        private Path dataFile;
        private Path outputDir;

        public Gpkg2ShpBuilder setDataFile(Path value) {
            dataFile = value;
            return this;
        }

        public Gpkg2ShpBuilder setOutputDir(Path value) {
            outputDir = value;
            return this;
        }

        @Override
        public Gpkg2ShpRequest build() {
            if (dataFile == null) {
                throw new IllegalArgumentException("dataFile is missing");
            }
            if (outputDir == null) {
                throw new IllegalArgumentException("outputDir is missing");
            }
            return new Gpkg2ShpRequest(dataFile.toAbsolutePath().normalize(),
                    outputDir.toAbsolutePath().normalize());
        }
    }

    static final class Gpkg2ShpHandler
            implements OperationHandler<Gpkg2ShpRequest, Gpkg2ShpResult> {
        @Override
        public Gpkg2ShpResult execute(Gpkg2ShpRequest request, OperationContext context) {
            if (!Files.isRegularFile(request.dataFile())) {
                throw new TabularTaskException("GPKG_FILE",
                        msg(context, "task.geo.file", request.dataFile()), "prepare", null);
            }
            try {
                Files.createDirectories(request.outputDir());
                List<String> layers = GeoFileSupport.gpkgLayers(request.dataFile());
                List<Path> files = new ArrayList<>();
                try (Connection connection = GeoFileSupport.openSpatial(context)) {
                    for (String layer : layers) {
                        String fileName = safeName(layer, context);
                        Path target = request.outputDir().resolve(fileName + ".shp");
                        GeoFileSupport.copyToShapefile(connection, request.dataFile(),
                                layer, target);
                        files.add(target);
                    }
                }
                context.log().log(LogLevel.INFO, "gretl.gpkg2shp.finished",
                        msg(context, "task.gpkg2shp.finished"),
                        Map.of("geo.layers", Integer.toString(files.size())), null);
                return new Gpkg2ShpResult(files, files.size());
            } catch (TabularTaskException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new TabularTaskException("GPKG2SHP",
                        msg(context, "task.gpkg2shp.failed"), "convert", exception);
            }
        }

        private static String safeName(String layer, OperationContext context) {
            if (layer == null || layer.isBlank() || layer.contains("/")
                    || layer.contains("\\") || layer.contains("..")) {
                throw new TabularTaskException("GPKG_LAYER",
                        msg(context, "task.geo.layer", layer), "prepare", null);
            }
            return layer;
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }
    }

    private static Path path(Object value) {
        if (value instanceof Path path) {
            return path.normalize();
        }
        if (value instanceof CharSequence text) {
            return Path.of(text.toString()).normalize();
        }
        throw new IllegalArgumentException("Path expected");
    }
}
