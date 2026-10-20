package ch.so.agi.gretl.internal.ioxwkf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Csv2ExcelEngineTest {
    @TempDir Path root;

    @Test
    void convertsCsvToXlsx() throws Exception {
        Path csv = Files.writeString(root.resolve("data.csv"),
                "id,name\n1,red\n2,blue\n", StandardCharsets.UTF_8);
        Path xlsx = root.resolve("data.xlsx");
        new Csv2ExcelEngine().convert(new Csv2ExcelEngine.Csv2ExcelRequest(
                csv, xlsx, true, ",", "\"", "UTF-8", null, null));
        assertTrue(Files.isRegularFile(xlsx));
        assertTrue(Files.size(xlsx) > 0);
    }
}
