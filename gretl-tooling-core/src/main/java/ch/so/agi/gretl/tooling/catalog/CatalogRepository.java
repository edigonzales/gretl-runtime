package ch.so.agi.gretl.tooling.catalog;

import java.util.*;
import static ch.so.agi.gretl.tooling.catalog.OperationCatalog.*;

/** Atomic immutable data snapshots; local descriptors never load Java providers. */
public final class CatalogRepository {
    public record CatalogSnapshot(long generation, CatalogJsonCodec.CatalogLoadResult result) { }
    private final CatalogJsonCodec codec = new CatalogJsonCodec();
    private CatalogSnapshot current = new CatalogSnapshot(0, codec.decode(null, null));
    public synchronized CatalogSnapshot loadBundled(byte[] bytes, String expectedIdentity) {
        current = new CatalogSnapshot(current.generation() + 1, codec.decode(bytes, expectedIdentity));
        return current;
    }
    public synchronized CatalogSnapshot mergeExplicitDescriptors(List<OperationDescriptor> descriptors) {
        OperationCatalog base = current.result().catalog().orElseThrow(() -> new IllegalStateException("CATALOG_UNAVAILABLE"));
        var ops = new ArrayList<>(base.operations()); ops.addAll(descriptors);
        ops.sort(Comparator.comparing(OperationDescriptor::operationId));
        var producers = new TreeSet<>(base.producers()); descriptors.forEach(d -> producers.add(d.origin()));
        var merged = codec.identify(new OperationCatalog(1, base.runtimeVersion(), base.dslVersion(), "", List.copyOf(producers), ops, base.coverage()));
        var decoded = codec.decode(codec.encodeCanonical(merged), null);
        if (decoded.catalog().isEmpty()) throw new IllegalArgumentException("Invalid descriptor merge: " + decoded.problems());
        current = new CatalogSnapshot(current.generation() + 1, decoded);
        return current;
    }
    public synchronized CatalogSnapshot snapshot() { return current; }
}
