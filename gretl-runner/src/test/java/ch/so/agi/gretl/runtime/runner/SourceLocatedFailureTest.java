package ch.so.agi.gretl.runtime.runner;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
class SourceLocatedFailureTest {
    @Test void explicitAdapterSourceWinsWithoutChangingThrowableOrFrames() {
        var error = new Located(); var stack = error.getStackTrace(); var failure = FailureInfo.from(error);
        assertEquals(new SourceLocation("original/job.groovy", 27, 4), failure.source().orElseThrow());
        assertEquals(Located.class.getName(), failure.type()); assertArrayEquals(stack, error.getStackTrace());
        assertEquals(stack[0].getFileName(), failure.frames().getFirst().fileName());
    }
    private static final class Located extends RuntimeException implements SourceLocatedFailure {
        private static final long serialVersionUID = 1L;
        public Optional<SourceLocation> failureSource() { return Optional.of(new SourceLocation("original/job.groovy", 27, 4)); }
    }
}
