package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Csv2Excel task: convert a CSV file into an XLSX workbook. */
public final class Csv2ExcelOperation {
    public static final String ID = "gretl.csv2excel";
    public static final String SYMBOL = "Csv2Excel";

    private Csv2ExcelOperation() {
    }

    public static OperationType<Csv2ExcelRequest, Csv2ExcelResult> type() {
        InputField<Csv2ExcelRequest, Path> csv = new InputField<>(
                "csvFile", "path", "CSV file to convert", true,
                Optional.empty(), List.of(), "csvFile = file('data/input.csv')",
                Csv2ExcelOperation::path,
                (builder, value) -> ((Csv2ExcelBuilder) builder).setCsvFile(value));
        InputField<Csv2ExcelRequest, Path> output = new InputField<>(
                "outputFile", "path", "XLSX output file", true,
                Optional.empty(), List.of(), "outputFile = file('build/output.xlsx')",
                Csv2ExcelOperation::path,
                (builder, value) -> ((Csv2ExcelBuilder) builder).setOutputFile(value));
        InputField<Csv2ExcelRequest, Boolean> header = new InputField<>(
                "firstLineIsHeader", "boolean", "Use the first CSV line as header", false,
                Optional.of(true), List.of(), "firstLineIsHeader = false",
                Csv2ExcelOperation::bool,
                (builder, value) -> ((Csv2ExcelBuilder) builder).setFirstLineIsHeader(value));
        InputField<Csv2ExcelRequest, String> delimiter = new InputField<>(
                "valueDelimiter", "text", "CSV field delimiter", false,
                Optional.of(CsvSupport.DEFAULT_DELIMITER), List.of(),
                "valueDelimiter = ','", Csv2ExcelOperation::text,
                (builder, value) -> ((Csv2ExcelBuilder) builder).setValueDelimiter(value));
        InputField<Csv2ExcelRequest, String> separator = new InputField<>(
                "valueSeparator", "text", "CSV quote character", false,
                Optional.of(CsvSupport.DEFAULT_QUOTE), List.of(),
                "valueSeparator = '\"'", Csv2ExcelOperation::text,
                (builder, value) -> ((Csv2ExcelBuilder) builder).setValueSeparator(value));
        InputField<Csv2ExcelRequest, String> encoding = new InputField<>(
                "encoding", "text", "CSV encoding", false,
                Optional.of(CsvSupport.DEFAULT_ENCODING), List.of(),
                "encoding = 'UTF-8'", Csv2ExcelOperation::text,
                (builder, value) -> ((Csv2ExcelBuilder) builder).setEncoding(value));
        OutputField<Csv2ExcelResult, Path> file = Fields.output(
                "file", "path", "Written XLSX file", false, Csv2ExcelResult::file);
        OutputField<Csv2ExcelResult, Integer> rows = Fields.output(
                "rows", "integer", "Number of data rows", false, Csv2ExcelResult::rows);
        return new OperationType<>(
                ID, SYMBOL, "Convert a CSV file into an XLSX workbook",
                Csv2ExcelRequest.class, Csv2ExcelResult.class,
                Csv2ExcelBuilder::new, Csv2ExcelHandler::new,
                List.of(csv, output, header, delimiter, separator, encoding),
                List.of(file, rows),
                """
                task('convert', Csv2Excel) {
                    csvFile = file('data/input.csv')
                    outputFile = file('build/output.xlsx')
                }
                """);
    }

    public record Csv2ExcelRequest(
            Path csvFile, Path outputFile, boolean firstLineIsHeader,
            String valueDelimiter, String valueSeparator, String encoding) {
    }

    public record Csv2ExcelResult(Path file, int rows) {
    }

    public static final class Csv2ExcelBuilder
            implements ch.so.agi.gretl.runtime.api.ConfigBuilder<Csv2ExcelRequest> {
        private Path csvFile;
        private Path outputFile;
        private boolean firstLineIsHeader = true;
        private String valueDelimiter = CsvSupport.DEFAULT_DELIMITER;
        private String valueSeparator = CsvSupport.DEFAULT_QUOTE;
        private String encoding = CsvSupport.DEFAULT_ENCODING;

        public Csv2ExcelBuilder setCsvFile(Path value) {
            csvFile = value;
            return this;
        }

        public Csv2ExcelBuilder setOutputFile(Path value) {
            outputFile = value;
            return this;
        }

        public Csv2ExcelBuilder setFirstLineIsHeader(Boolean value) {
            firstLineIsHeader = value == null || value;
            return this;
        }

        public Csv2ExcelBuilder setValueDelimiter(String value) {
            valueDelimiter = value;
            return this;
        }

        public Csv2ExcelBuilder setValueSeparator(String value) {
            valueSeparator = value;
            return this;
        }

        public Csv2ExcelBuilder setEncoding(String value) {
            encoding = value;
            return this;
        }

        @Override
        public Csv2ExcelRequest build() {
            if (csvFile == null) {
                throw new IllegalArgumentException("csvFile is missing");
            }
            if (outputFile == null) {
                throw new IllegalArgumentException("outputFile is missing");
            }
            return new Csv2ExcelRequest(csvFile.toAbsolutePath().normalize(),
                    outputFile.toAbsolutePath().normalize(), firstLineIsHeader,
                    valueDelimiter, valueSeparator, CsvSupport.encoding(encoding));
        }
    }

    static final class Csv2ExcelHandler
            implements OperationHandler<Csv2ExcelRequest, Csv2ExcelResult> {
        @Override
        public Csv2ExcelResult execute(Csv2ExcelRequest request, OperationContext context) {
            if (!Files.isRegularFile(request.csvFile())) {
                throw new TabularTaskException("CSV_FILE",
                        msg(context, "task.csv.file", request.csvFile()), "prepare", null);
            }
            Path temporary = null;
            try {
                CsvSupport.ParsedCsv csv = CsvSupport.read(request.csvFile(),
                        request.encoding(), request.valueDelimiter(),
                        request.valueSeparator(), request.firstLineIsHeader());
                Files.createDirectories(request.outputFile().getParent());
                temporary = Files.createTempFile(request.outputFile().getParent(),
                        "." + request.outputFile().getFileName() + ".", ".xlsx");
                try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                    Sheet sheet = workbook.createSheet("Sheet1");
                    int rowIndex = 0;
                    if (request.firstLineIsHeader() && !csv.headers().isEmpty()) {
                        Row row = sheet.createRow(rowIndex++);
                        for (int column = 0; column < csv.headers().size(); column++) {
                            row.createCell(column).setCellValue(csv.headers().get(column));
                        }
                    }
                    for (CSVRecord record : csv.records()) {
                        Row row = sheet.createRow(rowIndex++);
                        for (int column = 0; column < record.size(); column++) {
                            row.createCell(column).setCellValue(CsvSupport.stripBom(
                                    record.get(column)));
                        }
                    }
                    try (OutputStream output = Files.newOutputStream(temporary)) {
                        workbook.write(output);
                    }
                }
                move(temporary, request.outputFile());
                context.log().log(LogLevel.INFO, "gretl.csv2excel.finished",
                        msg(context, "task.csv2excel.finished"),
                        Map.of("tabular.file", request.outputFile().toString(),
                                "tabular.rows", Integer.toString(csv.records().size())), null);
                return new Csv2ExcelResult(request.outputFile(), csv.records().size());
            } catch (IOException | RuntimeException exception) {
                deleteQuietly(temporary);
                if (exception instanceof TabularTaskException tabular) {
                    throw tabular;
                }
                throw new TabularTaskException("CSV2EXCEL",
                        msg(context, "task.csv2excel.failed"), "convert", exception);
            }
        }

        private static void move(Path temporary, Path target) throws IOException {
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        private static void deleteQuietly(Path path) {
            if (path == null) {
                return;
            }
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Preserve primary failure.
            }
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

    private static Boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new IllegalArgumentException("Boolean expected");
    }

    private static String text(Object value) {
        if (value instanceof CharSequence text) {
            return text.toString();
        }
        throw new IllegalArgumentException("Text expected");
    }
}
