package ch.so.agi.gretl.tasks.formats;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.so.agi.gretl.runtime.api.OperationHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AvOperationsTest {
    @TempDir Path work;

    @Test
    void convertsAv2ch() throws Exception {
        Path input = fixture("av/254900.itf");
        AvOperations.Av2chBuilder builder = new AvOperations.Av2chBuilder();
        builder.setInputFiles(List.of(input));
        builder.setOutputDirectory(work.resolve("av"));
        builder.setLanguage("de");
        OperationHandler<AvOperations.Av2chRequest, AvOperations.DirectoryResult> handler =
                AvOperations.av2ch().handlerFactory().get();
        handler.execute(builder.build(), context());
        try (var files = Files.list(work.resolve("av"))) {
            assertTrue(files.findAny().isPresent());
        }
    }

    @Test
    void convertsAv2geobau() throws Exception {
        Path input = fixture("av/empty.itf");
        AvOperations.Av2geobauBuilder builder = new AvOperations.Av2geobauBuilder();
        builder.setItfFiles(List.of(input));
        builder.setDxfDirectory(work.resolve("dxf"));
        OperationHandler<AvOperations.Av2geobauRequest, AvOperations.DirectoryResult> handler =
                AvOperations.av2geobau().handlerFactory().get();
        handler.execute(builder.build(), context());
        try (var files = Files.list(work.resolve("dxf"))) {
            assertTrue(files.findAny().isPresent());
        }
    }

    @Test
    void convertsGpkg2Dxf() throws Exception {
        Path input = Path.of(getClass().getResource(
                "/fixtures/iox-wkf/Gpkg2Shp/aggloprogramme.gpkg").toURI());
        AvOperations.Gpkg2DxfBuilder builder = new AvOperations.Gpkg2DxfBuilder();
        builder.setDataFile(input);
        builder.setOutputDir(work.resolve("dxf"));
        OperationHandler<AvOperations.Gpkg2DxfRequest, AvOperations.DirectoryResult> handler =
                AvOperations.gpkg2dxf().handlerFactory().get();
        handler.execute(builder.build(), context());
        try (var files = Files.list(work.resolve("dxf"))) {
            assertTrue(files.anyMatch(path -> path.toString().endsWith(".dxf")));
        }
    }

    private Path fixture(String relative) throws Exception {
        return Path.of(getClass().getResource("/fixtures/" + relative).toURI());
    }

    private TestOperationContext context() {
        return new TestOperationContext(work, Map.of(), Map.of(), Map.of());
    }
}
