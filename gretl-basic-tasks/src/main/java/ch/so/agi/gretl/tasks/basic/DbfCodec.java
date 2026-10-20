package ch.so.agi.gretl.tasks.basic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Minimal DBF reader/writer implementing the documented GRETL/Hop NULL and encoding rules. */
final class DbfCodec {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final byte[] NULL_DATE = "00000000".getBytes(StandardCharsets.US_ASCII);

    private DbfCodec() {
    }

    static Table read(Path dbf, String explicitEncoding) throws IOException {
        byte[] bytes = Files.readAllBytes(dbf);
        if (bytes.length < 32) {
            throw new IOException("DBF header is truncated");
        }
        int count = littleInt(bytes, 4);
        int headerLength = littleShort(bytes, 8);
        int recordLength = littleShort(bytes, 10);
        Charset charset = charset(dbf, explicitEncoding, bytes[29] & 0xff);
        List<Field> fields = new ArrayList<>();
        int offset = 32;
        while (offset + 32 <= headerLength && (bytes[offset] & 0xff) != 0x0d) {
            String name = ascii(bytes, offset, 11);
            char type = (char) (bytes[offset + 11] & 0xff);
            int length = bytes[offset + 16] & 0xff;
            int decimal = bytes[offset + 17] & 0xff;
            fields.add(new Field(name, type, length, decimal));
            offset += 32;
        }
        List<Row> rows = new ArrayList<>(count);
        int recordOffset = headerLength;
        for (int rowIndex = 0; rowIndex < count; rowIndex++) {
            if (recordOffset + recordLength > bytes.length) {
                throw new IOException("DBF record " + (rowIndex + 1) + " is truncated");
            }
            if ((bytes[recordOffset] & 0xff) == 0x2a) {
                recordOffset += recordLength;
                continue;
            }
            int fieldOffset = recordOffset + 1;
            Map<String, Object> values = new LinkedHashMap<>();
            for (Field field : fields) {
                byte[] raw = Arrays.copyOfRange(bytes, fieldOffset,
                        fieldOffset + field.length());
                values.put(field.name(), decode(field, raw, charset));
                fieldOffset += field.length();
            }
            rows.add(new Row(values));
            recordOffset += recordLength;
        }
        return new Table(List.copyOf(fields), List.copyOf(rows), charset);
    }

    static void write(Path dbf, List<Field> fields, List<Row> rows, Charset charset)
            throws IOException {
        List<Field> adjusted = adjustLengths(fields, rows, charset);
        int headerLength = 32 + adjusted.size() * 32 + 1;
        int recordLength = 1 + adjusted.stream().mapToInt(Field::length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(headerLength
                + recordLength * rows.size() + 1).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 0x03);
        LocalDate now = LocalDate.now();
        buffer.put((byte) (now.getYear() - 1900));
        buffer.put((byte) now.getMonthValue());
        buffer.put((byte) now.getDayOfMonth());
        buffer.putInt(rows.size());
        buffer.putShort((short) headerLength);
        buffer.putShort((short) recordLength);
        for (int index = 0; index < 20; index++) {
            buffer.put((byte) 0);
        }
        for (Field field : adjusted) {
            byte[] name = field.name().getBytes(StandardCharsets.US_ASCII);
            byte[] nameField = new byte[11];
            System.arraycopy(name, 0, nameField, 0, Math.min(11, name.length));
            buffer.put(nameField);
            buffer.put((byte) field.type());
            buffer.putInt(0);
            buffer.put((byte) field.length());
            buffer.put((byte) field.decimal());
            for (int index = 0; index < 14; index++) {
                buffer.put((byte) 0);
            }
        }
        buffer.put((byte) 0x0d);
        for (Row row : rows) {
            buffer.put((byte) 0x20);
            for (Field field : adjusted) {
                byte[] encoded = encode(field, row.values().get(field.name()), charset);
                buffer.put(encoded);
            }
        }
        buffer.put((byte) 0x1a);
        Files.write(dbf, buffer.array());
    }

    static Charset charsetFor(String name) throws IOException {
        return charset(name);
    }

    static Charset transcode(Path dbf, String targetEncoding) throws IOException {
        Table table = read(dbf, "ISO-8859-1");
        Charset target = charset(null, targetEncoding, 0);
        write(dbf, table.fields(), table.rows(), target);
        return target;
    }

    private static Charset charset(Path dbf, String explicit, int ldid) throws IOException {
        String name = explicit;
        if (name == null || name.isBlank()) {
            Path cpg = dbf == null ? null : dbf.resolveSibling(
                    dbf.getFileName().toString().replaceAll("\\.[^.]+$", "") + ".cpg");
            if (cpg != null && Files.isRegularFile(cpg)) {
                name = Files.readString(cpg).trim();
            }
        }
        if (name == null || name.isBlank()) {
            name = ldidName(ldid);
        }
        if (name == null || name.isBlank()) {
            name = "ISO-8859-1";
        }
        return charset(name);
    }

    private static Charset charset(String name) throws IOException {
        String normalized = name.trim();
        if ("Latin-1".equalsIgnoreCase(normalized)) {
            throw new IOException("Invalid encoding alias: " + name);
        }
        if ("Latin1".equalsIgnoreCase(normalized)) {
            normalized = "ISO-8859-1";
        }
        try {
            return Charset.forName(normalized);
        } catch (RuntimeException exception) {
            throw new IOException("Unknown encoding: " + name, exception);
        }
    }

    private static String ldidName(int ldid) {
        return switch (ldid) {
            case 0x01 -> "IBM437";
            case 0x02 -> "IBM850";
            case 0x03, 0x57, 0x58, 0x59 -> "windows-1252";
            case 0x13 -> "Shift_JIS";
            default -> null;
        };
    }

    private static Object decode(Field field, byte[] raw, Charset charset) throws IOException {
        if (field.type() == 'C') {
            if (all(raw, (byte) 0)) {
                return null;
            }
            String value = decodeStrict(raw, charset);
            int end = value.length();
            while (end > 0 && value.charAt(end - 1) == ' ') {
                end--;
            }
            return value.substring(0, end);
        }
        String text = new String(raw, StandardCharsets.US_ASCII).trim();
        if (field.type() == 'N' || field.type() == 'F') {
            if (text.isEmpty() || text.chars().allMatch(c -> c == '*')) {
                return null;
            }
            if (text.indexOf('*') >= 0) {
                throw new IOException("Partially filled numeric NULL in field " + field.name());
            }
            return new BigDecimal(text);
        }
        if (field.type() == 'D') {
            if (text.isEmpty() || text.equals("?") || text.equals("0")
                    || text.equals("00000000")) {
                return null;
            }
            try {
                return LocalDate.parse(text, DATE);
            } catch (DateTimeParseException exception) {
                throw new IOException("Invalid DBF date in field " + field.name() + ": " + text,
                        exception);
            }
        }
        if (field.type() == 'L') {
            if (text.isEmpty() || text.equals("?")) {
                return null;
            }
            return switch (text.toUpperCase(Locale.ROOT)) {
                case "T", "Y" -> Boolean.TRUE;
                case "F", "N" -> Boolean.FALSE;
                default -> throw new IOException("Invalid DBF logical in field " + field.name());
            };
        }
        return new String(raw, charset).trim();
    }

    private static byte[] encode(Field field, Object value, Charset charset) throws IOException {
        byte[] result = new byte[field.length()];
        Arrays.fill(result, (byte) ' ');
        if (value == null) {
            if (field.type() == 'C') {
                Arrays.fill(result, (byte) 0);
            } else if (field.type() == 'D') {
                System.arraycopy(NULL_DATE, 0, result, 0, Math.min(result.length, 8));
            } else if (field.type() == 'N' || field.type() == 'F') {
                Arrays.fill(result, (byte) '*');
            }
            return result;
        }
        String text;
        if (value instanceof LocalDate date) {
            text = DATE.format(date);
        } else if (value instanceof BigDecimal decimal) {
            text = decimal.toPlainString();
        } else {
            text = value.toString();
        }
        byte[] encoded = text.getBytes(charset);
        int offset = field.type() == 'C' ? 0 : Math.max(0, result.length - encoded.length);
        if (offset + encoded.length > result.length) {
            throw new IOException("Value does not fit DBF field " + field.name());
        }
        System.arraycopy(encoded, 0, result, offset, encoded.length);
        return result;
    }

    private static List<Field> adjustLengths(List<Field> fields, List<Row> rows,
                                             Charset charset) throws IOException {
        List<Field> adjusted = new ArrayList<>();
        for (Field field : fields) {
            int length = field.length();
            if (field.type() == 'C') {
                for (Row row : rows) {
                    Object value = row.values().get(field.name());
                    if (value != null) {
                        length = Math.max(length, value.toString().getBytes(charset).length);
                    }
                }
            }
            adjusted.add(new Field(field.name(), field.type(), length, field.decimal()));
        }
        return adjusted;
    }

    private static String decodeStrict(byte[] raw, Charset charset) throws IOException {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw)).toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("Invalid byte sequence for encoding " + charset.name(),
                    exception);
        }
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        int end = offset;
        while (end < offset + length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.US_ASCII).trim();
    }

    private static boolean all(byte[] value, byte expected) {
        return value.length > 0 && Arrays.stream(toInts(value)).allMatch(v -> v == expected);
    }

    private static int[] toInts(byte[] bytes) {
        int[] result = new int[bytes.length];
        for (int index = 0; index < bytes.length; index++) {
            result[index] = bytes[index] & 0xff;
        }
        return result;
    }

    private static int littleShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static int littleInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16) | ((bytes[offset + 3] & 0xff) << 24);
    }

    record Table(List<Field> fields, List<Row> rows, Charset charset) {
    }

    record Field(String name, char type, int length, int decimal) {
    }

    record Row(Map<String, Object> values) {
        Row {
            values = java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(values));
        }
    }
}
