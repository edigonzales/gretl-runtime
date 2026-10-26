package ch.so.agi.gretl.internal.ili2db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Ili2DbDatasetNamesTest {
    @TempDir Path work;

    @Test
    void derivesSortedNamesFromDatasetFilesAndSlice() {
        Ili2DbRequestBuilder builder = new Ili2DbRequestBuilder();
        builder.dataFiles = List.of(work.resolve("transfer.xtf"));
        builder.datasetFiles = List.of(work.resolve("B_DatasetB.txt"),
                work.resolve("A_DatasetA.txt"));
        builder.options = Map.of("datasetNameSliceStart", 2);
        assertEquals(List.of("DatasetA", "DatasetB"), Ili2DbDatasetNames.resolve(
                builder.build(), List.of("one", "two")));
    }

    @Test
    void derivesNamesFromTransferFiles() {
        Ili2DbRequestBuilder builder = new Ili2DbRequestBuilder();
        builder.dataFiles = List.of(work.resolve("b.xtf"), work.resolve("a.xtf"));
        builder.options = Map.of("datasetNamesFromTransferFiles", true);
        assertEquals(List.of("a", "b"), Ili2DbDatasetNames.resolve(
                builder.build(), List.of("one", "two")));
    }

    @Test
    void rejectsConflictingStrategies() {
        Ili2DbRequestBuilder builder = new Ili2DbRequestBuilder();
        builder.datasetNames = List.of("explicit");
        builder.datasetFiles = List.of(work.resolve("name.txt"));
        builder.dataFiles = List.of(work.resolve("data.xtf"));
        assertThrows(IllegalArgumentException.class,
                () -> Ili2DbDatasetNames.resolve(builder.build(), List.of("one")));
    }

    @Test
    void rejectsCountMismatch() {
        Ili2DbRequestBuilder builder = new Ili2DbRequestBuilder();
        builder.datasetFiles = List.of(work.resolve("one.txt"));
        builder.dataFiles = List.of(work.resolve("a.xtf"), work.resolve("b.xtf"));
        assertThrows(IllegalArgumentException.class,
                () -> Ili2DbDatasetNames.resolve(builder.build(),
                        List.of("a", "b")));
    }
}
