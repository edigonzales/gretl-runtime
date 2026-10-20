package ch.so.agi.gretl.tasks.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbfCodecTest {
    @TempDir
    Path root;

    @Test
    void appliesCharacterDateAndNumericNulls() throws Exception {
        Path dbf = root.resolve("values.dbf");
        List<DbfCodec.Field> fields = List.of(
                new DbfCodec.Field("TEXT", 'C', 8, 0),
                new DbfCodec.Field("DAY", 'D', 8, 0),
                new DbfCodec.Field("NUMBER", 'N', 8, 0));
        List<DbfCodec.Row> rows = List.of(
                row(Map.of("TEXT", "Grün", "DAY", LocalDate.of(2024, 2, 29),
                        "NUMBER", new BigDecimal("12.5"))),
                row(nulls("TEXT", "DAY", "NUMBER")),
                row(Map.of("TEXT", "", "DAY", LocalDate.of(2000, 1, 1),
                        "NUMBER", BigDecimal.ZERO)));
        DbfCodec.write(dbf, fields, rows, StandardCharsets.UTF_8);

        DbfCodec.Table table = DbfCodec.read(dbf, "UTF-8");
        assertEquals("Grün", table.rows().get(0).values().get("TEXT"));
        assertEquals(LocalDate.of(2024, 2, 29),
                table.rows().get(0).values().get("DAY"));
        assertEquals(new BigDecimal("12.5"),
                table.rows().get(0).values().get("NUMBER"));
        assertNull(table.rows().get(1).values().get("TEXT"));
        assertNull(table.rows().get(1).values().get("DAY"));
        assertNull(table.rows().get(1).values().get("NUMBER"));
        assertEquals("", table.rows().get(2).values().get("TEXT"));
    }

    @Test
    void rejectsPartiallyFilledNumericNull() throws Exception {
        Path dbf = root.resolve("partial.dbf");
        DbfCodec.write(dbf, List.of(new DbfCodec.Field("N", 'N', 5, 0)),
                List.of(row(Map.of("N", "12*45"))), StandardCharsets.US_ASCII);
        assertThrows(java.io.IOException.class, () -> DbfCodec.read(dbf, "UTF-8"));
    }

    @Test
    void appliesEncodingAliasesAndCpgPriority() throws Exception {
        Path dbf = root.resolve("latin.dbf");
        DbfCodec.write(dbf, List.of(new DbfCodec.Field("NAME", 'C', 20, 0)),
                List.of(row(Map.of("NAME", "Grün"))), StandardCharsets.ISO_8859_1);
        assertEquals("Grün", DbfCodec.read(dbf, "Latin1")
                .rows().get(0).values().get("NAME"));
        assertThrows(java.io.IOException.class, () -> DbfCodec.read(dbf, "Latin-1"));

        Files.writeString(GeoFileSupport.sibling(dbf, ".cpg"), "UTF-8");
        // The CPG is authoritative if no explicit encoding is supplied.
        assertThrows(java.io.IOException.class, () -> DbfCodec.read(dbf, null));
    }

    @Test
    void usesLdidWhenNoExplicitEncodingOrCpgExists() throws Exception {
        Path dbf = root.resolve("ldid.dbf");
        DbfCodec.write(dbf, List.of(new DbfCodec.Field("NAME", 'C', 20, 0)),
                List.of(row(Map.of("NAME", "Grün"))), StandardCharsets.ISO_8859_1);
        byte[] bytes = Files.readAllBytes(dbf);
        bytes[29] = 0x03;
        Files.write(dbf, bytes);
        assertEquals("Grün", DbfCodec.read(dbf, null)
                .rows().get(0).values().get("NAME"));
    }

    private static DbfCodec.Row row(Map<String, Object> values) {
        return new DbfCodec.Row(new LinkedHashMap<>(values));
    }

    private static Map<String, Object> nulls(String... names) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String name : names) {
            values.put(name, null);
        }
        return values;
    }
}
