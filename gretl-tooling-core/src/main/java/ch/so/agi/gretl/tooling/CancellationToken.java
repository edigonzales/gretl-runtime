package ch.so.agi.gretl.tooling;

/** Cooperative cancellation without a dependency on executable runtime APIs. */
@FunctionalInterface
public interface CancellationToken {
    CancellationToken NONE = () -> false;
    boolean isCancelled();
    default void check() { if (isCancelled()) throw new java.util.concurrent.CancellationException("Analysis cancelled"); }
}
