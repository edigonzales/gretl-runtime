package ch.so.agi.gretl.internal.ioxwkf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import ch.ehi.basics.settings.Settings;
import ch.interlis.iom_j.csv.CsvReader;
import ch.interlis.ioxwkf.dbtools.IoxWkfConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvReaderProbeTest {
    @TempDir Path root;

    @Test
    void readsSemicolonHeader() throws Exception {
        Path csv = Files.writeString(root.resolve("data.csv"), "id;name\n1;red\n");
        Settings settings = new Settings();
        settings.setValue(IoxWkfConfig.SETTING_FIRSTLINE, IoxWkfConfig.SETTING_FIRSTLINE_AS_HEADER);
        settings.setValue(IoxWkfConfig.SETTING_VALUESEPARATOR, ";");
        CsvReader reader = new CsvReader(csv.toFile(), settings);
        reader.setFirstLineIsHeader(true);
        reader.setValueSeparator(';');
        reader.read();
        reader.read();
        assertArrayEquals(new String[]{"id", "name"}, reader.getAttributes());
    }
}
