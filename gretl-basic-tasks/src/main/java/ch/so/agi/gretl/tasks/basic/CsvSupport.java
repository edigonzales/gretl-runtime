package ch.so.agi.gretl.tasks.basic;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

/** Common CSV parsing and writing helpers for the tabular task family. */
final class CsvSupport {
    static final String DEFAULT_DELIMITER = ";";
    static final String DEFAULT_QUOTE = "\"";
    static final String DEFAULT_ENCODING = "UTF-8";

    private CsvSupport() {
    }

    static ParsedCsv read(Path file, String encoding, String delimiter, String quote,
                          boolean header) throws IOException {
        Charset charset = Charset.forName(encoding == null ? DEFAULT_ENCODING : encoding);
        char delimiterChar = singleCharacter(delimiter, DEFAULT_DELIMITER, "valueDelimiter");
        char quoteChar = singleCharacter(quote, DEFAULT_QUOTE, "valueSeparator");
        CSVFormat.Builder builder = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiterChar)
                .setQuote(quoteChar)
                .setIgnoreEmptyLines(false)
                .setTrim(false);
        if (header) {
            builder.setHeader().setSkipHeaderRecord(true);
        }
        CSVFormat format = builder.build();
        try (Reader reader = Files.newBufferedReader(file, charset);
             CSVParser parser = format.parse(reader)) {
            List<String> headers = new ArrayList<>();
            if (header) {
                for (String name : parser.getHeaderNames()) {
                    headers.add(stripBom(name));
                }
            }
            List<CSVRecord> records = new ArrayList<>();
            for (CSVRecord record : parser) {
                records.add(record);
            }
            if (!header && !records.isEmpty()) {
                List<String> values = new ArrayList<>();
                for (String value : records.get(0).values()) {
                    values.add(stripBom(value));
                }
                // The first record is data; BOM handling is only meaningful for
                // the first cell, which remains available through values().
            }
            return new ParsedCsv(headers, records);
        }
    }

    static CSVPrinter printer(Writer writer, String delimiter, String quote)
            throws IOException {
        char delimiterChar = singleCharacter(delimiter, DEFAULT_DELIMITER, "valueDelimiter");
        char quoteChar = singleCharacter(quote, DEFAULT_QUOTE, "valueSeparator");
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiterChar)
                .setQuote(quoteChar)
                .setRecordSeparator("\r\n")
                .setIgnoreEmptyLines(false)
                .build();
        return new CSVPrinter(writer, format);
    }

    static String stripBom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\ufeff') {
            return value.substring(1);
        }
        return value;
    }

    static char singleCharacter(String value, String fallback, String field) {
        String actual = value == null || value.isEmpty() ? fallback : value;
        if (actual.length() != 1) {
            throw new IllegalArgumentException(field + " must be a single character");
        }
        return actual.charAt(0);
    }

    static String encoding(String value) {
        String actual = value == null || value.isBlank() ? DEFAULT_ENCODING : value;
        Charset.forName(actual);
        return actual;
    }

    record ParsedCsv(List<String> headers, List<CSVRecord> records) {
        ParsedCsv {
            headers = List.copyOf(headers);
            records = List.copyOf(records);
        }
    }
}
