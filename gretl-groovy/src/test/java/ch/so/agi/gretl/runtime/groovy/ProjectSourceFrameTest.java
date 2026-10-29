package ch.so.agi.gretl.runtime.groovy;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectSourceFrameTest {
    @Test void prj209UnknownLibrarySourceFramesDoNotReplaceTheOriginalException() {
        var failure=new IllegalStateException("original-library-failure");
        failure.setStackTrace(new StackTraceElement[]{new StackTraceElement("Library","execute",null,-1),
                new StackTraceElement("Entry","run","Virtual.groovy",9)});
        Path original=Path.of("import/job.groovy");
        var sources=Map.of("Virtual.groovy",original);
        assertDoesNotThrow(()->SourceLocations.remap(failure,sources));
        assertEquals(IllegalStateException.class,failure.getClass());assertEquals("original-library-failure",failure.getMessage());
        assertNull(failure.getStackTrace()[0].getFileName());assertEquals(-1,failure.getStackTrace()[0].getLineNumber());
        assertEquals(original.toString(),failure.getStackTrace()[1].getFileName());assertEquals(9,failure.getStackTrace()[1].getLineNumber());
    }
}
