package ch.so.agi.gretl.tooling.catalog;

import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OperationProvider;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.util.*;

/** Additive owned metadata only. Existing SPI facts cannot be overridden. */
public final class MetadataSupplementLoader {
    public record Supplement(String operationId, String field, OperationCatalog.ValueShape shape,
            String pathRole, boolean sensitive, Map<String,String> hints) {
        public Supplement { hints = Map.copyOf(hints); }
        public String key() { return operationId + "/" + field; }
    }
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    public List<Supplement> loadFromOwnedResources(ClassLoader loader) throws IOException {
        // Provider-qualified resource names also coexist in a packaged runtime JAR.
        // Discover identities from the same SPI, never from a second provider/operation list.
        var result = new ArrayList<Supplement>();
        for (var provider : ServiceLoader.load(OperationProvider.class, loader).stream().toList()) {
            var urls = loader.getResources("META-INF/gretl/tooling/" + provider.type().getName() + ".json");
            while (urls.hasMoreElements()) {
                try (var in = urls.nextElement().openStream()) { result.addAll(read(in)); }
            }
        }
        return List.copyOf(result);
    }
    public List<Supplement> read(InputStream in) throws IOException {
        byte[] data = in.readNBytes(1024 * 1024 + 1);
        if (data.length > 1024 * 1024) throw new IOException("Supplement too large");
        try {
            JsonNode root = mapper.readTree(data);
            if (!root.path("schemaVersion").isInt() || root.path("schemaVersion").asInt() != 1 || !root.path("fields").isArray()) throw new IOException("Supplement schema");
            requireKeys(root, Set.of("schemaVersion", "fields"));
            var result = new ArrayList<Supplement>();
            for (var row : root.get("fields")) {
                requireKeys(row, Set.of("operationId","field","shape","pathRole","sensitive","hints"));
                if (!row.path("operationId").isString() || !row.path("field").isString()) throw new IOException("Supplement target");
                if (row.has("sensitive") && !row.get("sensitive").isBoolean()) throw new IOException("Supplement sensitive must be boolean");
                if (row.has("pathRole") && !row.get("pathRole").isString()) throw new IOException("Supplement pathRole must be text");
                if (row.has("hints")) {
                    if (!row.get("hints").isObject()) throw new IOException("Supplement hints must be an object");
                    for (var hint : row.get("hints")) if (!hint.isString()) throw new IOException("Supplement hint values must be text");
                }
                result.add(new Supplement(row.get("operationId").stringValue(), row.get("field").stringValue(),
                        row.has("shape") ? mapper.treeToValue(row.get("shape"), OperationCatalog.ValueShape.class) : null,
                        row.has("pathRole") ? row.get("pathRole").stringValue() : null,
                        row.path("sensitive").asBoolean(false), row.has("hints") ? mapper.convertValue(row.get("hints"),
                                new TypeReference<Map<String,String>>() {}) : Map.of()));
            }
            return List.copyOf(result);
        } catch (JacksonException exception) {
            throw new IOException("Invalid supplement JSON", exception);
        }
    }
    private static void requireKeys(JsonNode node, Set<String> allowed) throws IOException {
        if (!node.isObject()) throw new IOException("Supplement object required");
        if (!allowed.containsAll(node.propertyNames())) throw new IOException("Unknown supplement property");
    }
    public Map<String,Supplement> validateAgainstOperations(List<Supplement> supplements, Collection<OperationType<?,?>> operations) {
        var fields = new HashSet<String>();
        operations.forEach(o -> o.inputs().forEach(f -> fields.add(o.id() + "/" + f.name())));
        var result = new TreeMap<String,Supplement>();
        for (var s : supplements) {
            if (!fields.contains(s.key()) || result.putIfAbsent(s.key(),s) != null) throw new IllegalArgumentException("Unknown or duplicate supplement: " + s.key());
        }
        return Map.copyOf(result);
    }
}
