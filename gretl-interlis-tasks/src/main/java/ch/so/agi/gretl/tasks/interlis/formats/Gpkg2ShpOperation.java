package ch.so.agi.gretl.tasks.interlis.formats;

import ch.so.agi.gretl.internal.formats.FormatTaskSupport;

import ch.so.agi.gretl.internal.InterlisTaskException;
import ch.so.agi.gretl.internal.ioxwkf.Gpkg2ShpEngine;
import ch.so.agi.gretl.runtime.api.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** gretl-next Gpkg2Shp with native Shapefile writer. */
public final class Gpkg2ShpOperation {
    public static final String ID = "gretl.gpkg2shp";
    public static final String SYMBOL = "Gpkg2Shp";
    private Gpkg2ShpOperation() {}

    public static OperationType<Request, Result> type() {
        InputField<Request, Path> file = new InputField<>("dataFile", "path",
                "GeoPackage file to convert", true, Optional.empty(), List.of(),
                "dataFile = file('data/input.gpkg')", FormatTaskSupport::path,
                (builder, value) -> ((Builder) builder).dataFile = value);
        InputField<Request, Path> output = new InputField<>("outputDir", "path",
                "Shapefile output directory", true, Optional.empty(), List.of(),
                "outputDir = path('build/shp')", FormatTaskSupport::path,
                (builder, value) -> ((Builder) builder).outputDir = value);
        OutputField<Result, Path> directory = Fields.output("directory", "path",
                "Written output directory", false, Result::directory);
        return new OperationType<>(ID, SYMBOL, "Convert GeoPackage to Shapefiles",
                Request.class, Result.class, Builder::new, Handler::new,
                List.of(file, output), List.of(directory),
                "task('convert', Gpkg2Shp) {\n    dataFile = file('data/input.gpkg')\n    outputDir = path('build/shp')\n}\n");
    }

    public record Request(Path dataFile, Path outputDir) {}
    public record Result(Path directory) {}

    public static final class Builder implements ConfigBuilder<Request> {
        private Path dataFile;
        private Path outputDir;
        @Override public Request build() {
            if (dataFile == null) throw new IllegalArgumentException("dataFile is missing");
            if (outputDir == null) throw new IllegalArgumentException("outputDir is missing");
            return new Request(dataFile, outputDir);
        }
    }

    static final class Handler implements OperationHandler<Request, Result> {
        @Override public Result execute(Request request, OperationContext context) {
            try {
                new Gpkg2ShpEngine().convert(new Gpkg2ShpEngine.Gpkg2ShpRequest(
                        request.dataFile(), request.outputDir()));
                return new Result(request.outputDir());
            } catch (InterlisTaskException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new InterlisTaskException("GPKG2SHP", "Gpkg2Shp failed",
                        "convert", Optional.empty(), exception);
            }
        }
    }
}
