package ch.so.agi.gretl.runtime.runner;

import java.util.Optional;

/** Optional original-source metadata from a language adapter; no fabricated stack frames. */
public interface SourceLocatedFailure {
    Optional<SourceLocation> failureSource();
}
