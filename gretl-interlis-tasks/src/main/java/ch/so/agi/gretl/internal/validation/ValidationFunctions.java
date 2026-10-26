package ch.so.agi.gretl.internal.validation;

import ch.interlis.iox_j.plugins.IoxPlugin;
import ch.interlis.iox_j.validator.InterlisFunction;
import ch.so.agi.gretl.internal.InterlisTaskException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/** Strict registration over the existing IOX plugin/function types.
 * IOX's property remains the explicit selection; the distribution supplies a
 * normal Java service descriptor as its default. No organization is built in.
 */
final class ValidationFunctions {
    static final String PROPERTY = "ch.ehi.iox-ili.pluginClasses";

    private ValidationFunctions() {}

    static Map<String, Class<?>> load(ClassLoader loader) {
        List<IoxPlugin> plugins = new ArrayList<>();
        String selected = System.getProperty(PROPERTY);
        if (selected != null) {
            for (String name : selected.split(",", -1)) {
                if (name.isBlank()) continue;
                try {
                    plugins.add(Class.forName(name.strip(), true, loader)
                            .asSubclass(IoxPlugin.class).getDeclaredConstructor().newInstance());
                } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                    throw failure("VALIDATION_FUNCTION", "Cannot load selected IOX plugin " + name, exception);
                }
            }
        } else {
            try {
                ServiceLoader.load(IoxPlugin.class, loader).forEach(plugins::add);
            } catch (ServiceConfigurationError | LinkageError | RuntimeException exception) {
                throw failure("VALIDATION_FUNCTION", "Cannot load selected IOX service: " + exception.getMessage(), exception);
            }
        }
        Map<String, Class<?>> functions = new LinkedHashMap<>();
        for (IoxPlugin plugin : plugins) {
            if (!(plugin instanceof InterlisFunction function)) continue;
            String name;
            try {
                name = function.getQualifiedIliName();
                if (name == null || name.isBlank()) throw new IllegalArgumentException("Empty qualified function name");
            } catch (RuntimeException | LinkageError exception) {
                throw failure("VALIDATION_FUNCTION", "Invalid selected IOX plugin " + plugin.getClass().getName(), exception);
            }
            Class<?> previous = functions.putIfAbsent(name, plugin.getClass());
            if (previous != null) {
                throw failure("VALIDATION_FUNCTION_DUPLICATE", "Duplicate qualified INTERLIS function " + name
                        + ": " + previous.getName() + " and " + plugin.getClass().getName(), null);
            }
        }
        return Collections.unmodifiableMap(functions);
    }

    private static InterlisTaskException failure(String code, String message, Throwable cause) {
        return new InterlisTaskException(code, message, "configuration", Optional.empty(), cause);
    }
}
