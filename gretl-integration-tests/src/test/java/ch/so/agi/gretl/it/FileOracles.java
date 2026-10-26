package ch.so.agi.gretl.it;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Independent on-disk DBF/SHP wire-format checks; no GRETL handler/result is used. */
final class FileOracles {
    static int dbfCount(Path file) throws IOException {
        return ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
    }
    static Map<String, String> firstDbfRow(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file); ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int headerSize = Short.toUnsignedInt(header.getShort(8)); int offset = headerSize + 1;
        Map<String, String> row = new LinkedHashMap<>();
        for (int field = 32; bytes[field] != 13; field += 32) {
            int length = 0; while (length < 11 && bytes[field + length] != 0) length++;
            String name = new String(bytes, field, length, StandardCharsets.ISO_8859_1);
            int width = Byte.toUnsignedInt(bytes[field + 16]);
            row.put(name, new String(bytes, offset, width, StandardCharsets.ISO_8859_1).strip()); offset += width;
        }
        return row;
    }
    static List<Double> geoPackagePoint(byte[] bytes) throws IOException {
        if (bytes[0] != 'G' || bytes[1] != 'P') throw new IOException("Not a GeoPackage geometry");
        ByteBuffer data = ByteBuffer.wrap(bytes).order((bytes[3] & 1) == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        if (data.getInt(4) != 2056) throw new IOException("Incorrect GeoPackage SRID");
        int envelope = (bytes[3] >> 1) & 7;
        int offset = 8 + switch (envelope) { case 0 -> 0; case 1 -> 32; case 2, 3 -> 48; case 4 -> 64; default -> throw new IOException("Invalid envelope"); };
        data.order(bytes[offset] == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        if (data.getInt(offset + 1) != 1) throw new IOException("Not a WKB Point");
        return List.of(data.getDouble(offset + 5), data.getDouble(offset + 13));
    }
    static List<Double> firstPoint(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (ByteBuffer.wrap(bytes).getInt() != 9994) throw new IOException("Not a Shapefile");
        ByteBuffer record = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (record.getInt(108) != 1) throw new IOException("Not a Point record");
        return List.of(record.getDouble(112), record.getDouble(120));
    }
}
