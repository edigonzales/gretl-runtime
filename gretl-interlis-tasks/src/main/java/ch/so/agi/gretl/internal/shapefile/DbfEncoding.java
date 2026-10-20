package ch.so.agi.gretl.internal.shapefile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Optional;

/** Hop-compatible DBF encoding selection: explicit, CPG, LDID, ISO-8859-1 fallback. */
final class DbfEncoding {
    private DbfEncoding() {
    }

    static Charset resolve(Path dbf, Optional<Path> cpg, String explicit) throws IOException {
        if (explicit != null && !explicit.isBlank()) {
            return charset(explicit);
        }
        if (cpg.isPresent()) {
            String value = Files.readString(cpg.get()).strip().replace("\uFEFF", "");
            if (!value.isBlank()) {
                return charset(value);
            }
        }
        int ldid;
        try (FileChannel channel = FileChannel.open(dbf, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(1);
            channel.position(29);
            if (channel.read(buffer) != 1) {
                throw new IOException("Truncated DBF header: " + dbf);
            }
            ldid = buffer.array()[0] & 0xff;
        }
        String name = switch (ldid) {
            case 0x01 -> "IBM437";
            case 0x02 -> "IBM850";
            case 0x03, 0x57 -> "windows-1252";
            case 0x64 -> "IBM852";
            case 0x65 -> "IBM866";
            case 0x66 -> "IBM865";
            case 0x67 -> "IBM861";
            case 0x6A -> "IBM737";
            case 0x6B -> "IBM857";
            case 0x7A -> "GBK";
            case 0x7B -> "windows-31j";
            case 0x7C -> "Big5";
            case 0xC8 -> "windows-1250";
            case 0xC9 -> "windows-1251";
            case 0xCA -> "windows-1254";
            case 0xCB -> "windows-1253";
            default -> null;
        };
        return name == null ? StandardCharsets.ISO_8859_1 : Charset.forName(name);
    }

    static Charset charset(String value) throws IOException {
        String normalized = value.strip();
        normalized = switch (normalized.toUpperCase(Locale.ROOT)) {
            case "65001", "UTF8" -> "UTF-8";
            case "1252" -> "windows-1252";
            case "88591", "28591" -> "ISO-8859-1";
            case "850" -> "IBM850";
            case "LATIN1" -> "ISO-8859-1";
            case "LATIN-1" -> throw new IOException("Invalid encoding alias: " + value);
            default -> normalized;
        };
        try {
            return Charset.forName(normalized);
        } catch (RuntimeException exception) {
            throw new IOException("Unknown encoding: " + value, exception);
        }
    }
}
