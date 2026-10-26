package ch.so.agi.gretl.tasks.interlis.formats;

import ch.so.agi.gretl.internal.formats.FormatTaskSupport;

import ch.so.agi.gretl.internal.InterlisTaskException;
import ch.so.agi.gretl.internal.ioxwkf.Csv2ExcelEngine;
import ch.so.agi.gretl.runtime.api.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** gretl-next Csv2Excel with IOX CSV reader and Excel writer. */
public final class Csv2ExcelOperation {
    public static final String ID = "gretl.csv2excel";
    public static final String SYMBOL = "Csv2Excel";

    private Csv2ExcelOperation() {}

    public static OperationType<Request, Result> type() {
        InputField<Request, Path> csv = field("csvFile", "path", "CSV file to convert", true,
                null, "csvFile = file('data/input.csv')", FormatTaskSupport::path,
                (b, v) -> b.csvFile = v);
        InputField<Request, Path> out = field("outputFile", "path", "XLSX output file", true,
                null, "outputFile = file('build/output.xlsx')", FormatTaskSupport::path,
                (b, v) -> b.outputFile = v);
        InputField<Request, Boolean> header = field("firstLineIsHeader", "boolean",
                "Use first CSV line as header", false, true, "firstLineIsHeader = false",
                FormatTaskSupport::bool, (b, v) -> b.firstLineIsHeader = v);
        InputField<Request, String> delimiter = field("valueDelimiter", "text",
                "CSV quote character", false, null, "valueDelimiter = '\"'",
                FormatTaskSupport::text, (b, v) -> b.valueDelimiter = v);
        InputField<Request, String> separator = field("valueSeparator", "text",
                "CSV field separator", false, null, "valueSeparator = ','",
                FormatTaskSupport::text, (b, v) -> b.valueSeparator = v);
        InputField<Request, String> encoding = field("encoding", "text", "CSV encoding",
                false, null, "encoding = 'UTF-8'", FormatTaskSupport::text,
                (b, v) -> b.encoding = v);
        InputField<Request, String> models = field("models", "text", "INTERLIS model names",
                false, null, "models = 'Model'", FormatTaskSupport::text,
                (b, v) -> b.models = v);
        InputField<Request, String> modeldir = field("modeldir", "text",
                "INTERLIS model directories", false, null, "modeldir = file('models')",
                FormatTaskSupport::text, (b, v) -> b.modeldir = v);
        OutputField<Result, Path> file = Fields.output("file", "path",
                "Written XLSX file", false, Result::file);
        return new OperationType<>(ID, SYMBOL, "Convert CSV to XLSX with IOX",
                Request.class, Result.class, Builder::new, Handler::new,
                List.of(csv, out, header, delimiter, separator, encoding, models, modeldir),
                List.of(file),
                "task('convert', Csv2Excel) {\n    csvFile = file('data/input.csv')\n    outputFile = file('build/output.xlsx')\n}\n");
    }

    private static <T> InputField<Request, T> field(String name, String type, String desc,
            boolean required, T defaultValue, String example,
            java.util.function.Function<Object, T> converter, Setter<T> setter) {
        return new InputField<>(name, type, desc, required,
                defaultValue == null ? Optional.empty() : Optional.of(defaultValue),
                List.of(), example, converter, (builder, value) -> setter.set((Builder) builder, value));
    }

    public record Request(Path csvFile, Path outputFile, boolean firstLineIsHeader,
                          String valueDelimiter, String valueSeparator, String encoding,
                          String models, String modeldir) {}

    public record Result(Path file) {}

    @FunctionalInterface private interface Setter<T> { void set(Builder builder, T value); }

    public static final class Builder implements ConfigBuilder<Request> {
        private Path csvFile;
        private Path outputFile;
        private boolean firstLineIsHeader = true;
        private String valueDelimiter;
        private String valueSeparator;
        private String encoding;
        private String models;
        private String modeldir;

        @Override public Request build() {
            if (csvFile == null) throw new IllegalArgumentException("csvFile is missing");
            if (outputFile == null) throw new IllegalArgumentException("outputFile is missing");
            return new Request(csvFile, outputFile, firstLineIsHeader, valueDelimiter,
                    valueSeparator, encoding, models, modeldir);
        }
    }

    static final class Handler implements OperationHandler<Request, Result> {
        @Override public Result execute(Request request, OperationContext context) {
            try {
                new Csv2ExcelEngine().convert(new Csv2ExcelEngine.Csv2ExcelRequest(
                        request.csvFile(), request.outputFile(), request.firstLineIsHeader(),
                        request.valueDelimiter(), request.valueSeparator(), request.encoding(),
                        request.models(), request.modeldir()));
                return new Result(request.outputFile());
            } catch (Exception exception) {
                throw new InterlisTaskException("CSV2EXCEL", "Csv2Excel failed",
                        "convert", Optional.empty(), exception);
            }
        }
    }
}
