package ch.so.agi.gretl.runtime.runner;

import ch.so.agi.gretl.runtime.api.OperationProvider;
import ch.so.agi.gretl.runtime.api.OperationType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Frozen catalog of complete provider definitions with origin information.
 * Loading only reads metadata; it never creates a handler or opens a domain
 * connection.
 */
public final class OperationRegistry {
    private final List<RegisteredOperation> registered;
    private final List<OperationType<?, ?>> operations;
    private final Map<String, RegisteredOperation> byId;
    private final Map<String, RegisteredOperation> bySymbol;

    private OperationRegistry(List<RegisteredOperation> registered) {
        this.registered = List.copyOf(registered);
        this.operations = registered.stream().map(RegisteredOperation::operation).toList();
        Map<String, RegisteredOperation> ids = new LinkedHashMap<>();
        Map<String, RegisteredOperation> symbols = new LinkedHashMap<>();
        for (RegisteredOperation entry : registered) {
            OperationType<?, ?> operation = entry.operation();
            RegisteredOperation previousId = ids.putIfAbsent(operation.id(), entry);
            if (previousId != null) {
                throw new DefinitionException("DUPLICATE_OPERATION_ID",
                        "Doppelte Operations-ID " + operation.id() + " in "
                                + previousId.origin() + " und " + entry.origin());
            }
            RegisteredOperation previousSymbol = symbols.putIfAbsent(operation.symbol(), entry);
            if (previousSymbol != null) {
                throw new DefinitionException("DUPLICATE_OPERATION_SYMBOL",
                        "Doppeltes Operations-Symbol " + operation.symbol() + " in "
                                + previousSymbol.origin() + " und " + entry.origin());
            }
        }
        this.byId = Map.copyOf(ids);
        this.bySymbol = Map.copyOf(symbols);
    }

    public static OperationRegistry load(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        List<OperationProvider> providers = new ArrayList<>();
        try {
            ServiceLoader.load(OperationProvider.class, classLoader).forEach(providers::add);
        } catch (java.util.ServiceConfigurationError error) {
            throw new DefinitionException("OPERATION_PROVIDER_LOAD_FAILED",
                    "OperationProvider konnten nicht geladen werden", error);
        }
        return fromProviders(providers);
    }

    /** Package-visible/testable construction from already loaded providers. */
    public static OperationRegistry fromProviders(Collection<? extends OperationProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        List<RegisteredOperation> entries = new ArrayList<>();
        for (OperationProvider provider : providers) {
            Objects.requireNonNull(provider, "provider");
            Collection<OperationType<?, ?>> operations;
            try {
                operations = provider.operations();
            } catch (RuntimeException exception) {
                throw new DefinitionException("OPERATION_PROVIDER_DEFECT",
                        "Provider " + provider.getClass().getName()
                                + " konnte seine Definitionen nicht liefern", exception);
            }
            if (operations == null) {
                throw new DefinitionException("OPERATION_PROVIDER_DEFECT",
                        "Provider " + provider.getClass().getName() + " lieferte null");
            }
            for (OperationType<?, ?> operation : operations) {
                if (operation == null) {
                    throw new DefinitionException("OPERATION_PROVIDER_DEFECT",
                            "Provider " + provider.getClass().getName()
                                    + " lieferte eine null-Definition");
                }
                entries.add(new RegisteredOperation(operation, provider.getClass().getName()));
            }
        }
        return new OperationRegistry(entries);
    }

    public List<OperationType<?, ?>> all() {
        return operations;
    }

    public List<RegisteredOperation> registered() {
        return registered;
    }

    public OperationType<?, ?> requireId(String id) {
        RegisteredOperation entry = byId.get(id);
        if (entry == null) {
            throw new DefinitionException("UNKNOWN_OPERATION",
                    "Keine Operation mit ID " + id + " im Katalog");
        }
        return entry.operation();
    }

    public OperationType<?, ?> requireSymbol(String symbol) {
        RegisteredOperation entry = bySymbol.get(symbol);
        if (entry == null) {
            throw new DefinitionException("UNKNOWN_OPERATION",
                    "Kein Baustein mit Symbol " + symbol + " im Katalog");
        }
        return entry.operation();
    }

    public java.util.Optional<String> origin(OperationType<?, ?> operation) {
        RegisteredOperation entry = byId.get(operation.id());
        return entry == null ? java.util.Optional.empty()
                : java.util.Optional.of(entry.origin());
    }

    /** Definition plus the provider class that supplied it. */
    public record RegisteredOperation(OperationType<?, ?> operation, String origin) {
        public RegisteredOperation {
            Objects.requireNonNull(operation, "operation");
            if (origin == null || origin.isBlank()) {
                throw new IllegalArgumentException("origin must not be blank");
            }
        }
    }
}
