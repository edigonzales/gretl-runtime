package ch.so.agi.gretl.runtime.qualification.groovy;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * M0 probe script base class.
 *
 * <p>This is deliberately a test fixture, not the product DSL or the M2
 * {@code GretlScript}. It exists only to execute a real external Groovy script
 * against map-first method signatures, closure delegation and a stored closure
 * that is evaluated later exactly once per explicit invocation.</p>
 */
public abstract class M0ProbeScriptBase extends Script {

    /** A declaration captured at script load time. */
    public record ParameterDeclaration(String name, Map<String, Object> options) {
        public ParameterDeclaration {
            options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
        }
    }

    /** A task whose configuration closure is stored, not evaluated at load time. */
    public record TaskDeclaration(
            String id, String type, Map<String, Object> options, Closure<?> body, int registrationIndex) {
        public TaskDeclaration {
            options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
            Objects.requireNonNull(body, "body");
        }
    }

    private final Map<String, ParameterDeclaration> parameters = new LinkedHashMap<>();
    private final Map<String, TaskDeclaration> tasks = new LinkedHashMap<>();
    private final Map<String, String> parameterValues = new LinkedHashMap<>();
    private final List<String> defaultTargets = new ArrayList<>();
    private int evaluationCount;

    public M0ProbeScriptBase() {
        super();
    }

    public M0ProbeScriptBase(Binding binding) {
        super(binding);
    }

    /** Map-first declaration as reached by {@code parameter('name', required: true, ...)}. */
    public void parameter(Map<String, ?> options, String name) {
        Objects.requireNonNull(options, "options");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("parameter name must not be blank");
        }
        if (parameters.containsKey(name)) {
            throw new IllegalArgumentException("duplicate parameter: " + name);
        }
        if (options.containsKey("defaultValue")) {
            parameterValues.put(name, String.valueOf(options.get("defaultValue")));
        }
        parameters.put(name, new ParameterDeclaration(name, new LinkedHashMap<>(options)));
    }

    /** Positional task registration without a header map. */
    public void task(String id, String type, Closure<?> configure) {
        task(Map.of(), id, type, configure);
    }

    /**
     * Map-first task registration as reached by
     * {@code task('name', Type, dependsOn: 'other') { ... }}.
     */
    public void task(Map<String, ?> options, String id, String type, Closure<?> configure) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(configure, "configure");
        if (id == null || id.isBlank() || type == null || type.isBlank()) {
            throw new IllegalArgumentException("task id and type must not be blank");
        }
        if (tasks.containsKey(id)) {
            throw new IllegalArgumentException("duplicate task: " + id);
        }
        tasks.put(id, new TaskDeclaration(
                id, type, new LinkedHashMap<>(options), configure, tasks.size()));
    }

    /** Records the normal target name(s); no execution. */
    public void defaults(Object... targets) {
        for (Object target : targets) {
            if (target == null) {
                throw new IllegalArgumentException("default target must not be null");
            }
            defaultTargets.add(String.valueOf(target));
        }
    }

    /**
     * Evaluates exactly one stored closure in a fresh delegate-only scope and
     * returns the values explicitly assigned by that closure.
     */
    public Map<String, Object> runTask(String id) {
        TaskDeclaration task = tasks.get(id);
        if (task == null) {
            throw new IllegalArgumentException("unknown task: " + id);
        }
        ProbeConfigurationScope scope = new ProbeConfigurationScope(this);
        Closure<?> copy = (Closure<?>) task.body().rehydrate(
                scope, task.body().getOwner(), task.body().getThisObject());
        copy.setResolveStrategy(Closure.DELEGATE_ONLY);
        evaluationCount++;
        int parameters = copy.getMaximumNumberOfParameters();
        if (parameters == 0) {
            copy.call();
        } else if (parameters == 1) {
            copy.call(scope.contextFacade());
        } else {
            throw new IllegalArgumentException(
                    "task closure must declare zero or one parameter, got " + parameters);
        }
        return scope.assignedValues();
    }

    public void setParameterValue(String name, String value) {
        if (!parameters.containsKey(name)) {
            throw new IllegalArgumentException("unknown parameter: " + name);
        }
        parameterValues.put(name, value);
    }

    public String parameterValue(String name) {
        if (!parameters.containsKey(name)) {
            throw new IllegalArgumentException("unknown parameter: " + name);
        }
        return parameterValues.get(name);
    }

    public Map<String, ParameterDeclaration> parameters() {
        return Collections.unmodifiableMap(parameters);
    }

    public Map<String, TaskDeclaration> tasks() {
        return Collections.unmodifiableMap(tasks);
    }

    public List<String> defaultTargets() {
        return List.copyOf(defaultTargets);
    }

    public int evaluationCount() {
        return evaluationCount;
    }

    @Override
    public Object run() {
        return null;
    }

    /** Delegate for an unqualified task configuration closure. */
    static final class ProbeConfigurationScope extends GroovyObjectSupport {
        private static final Set<String> FIELDS = Set.of("input", "note", "summary");
        private static final Map<String, Object> DEFAULTS = Map.of("note", "default-note");

        private final M0ProbeScriptBase script;
        private final Map<String, Object> values = new LinkedHashMap<>();
        private final ProbeContextFacade contextFacade;

        ProbeConfigurationScope(M0ProbeScriptBase script) {
            this.script = script;
            this.contextFacade = new ProbeContextFacade(script);
        }

        @Override
        public void setProperty(String property, Object newValue) {
            if (!FIELDS.contains(property)) {
                throw new MissingPropertyException(property, getClass());
            }
            values.put(property, convertText(newValue));
        }

        @Override
        public Object getProperty(String property) {
            if ("ctx".equals(property)) {
                return contextFacade;
            }
            if ("class".equals(property)) {
                return getClass();
            }
            if ("metaClass".equals(property)) {
                return super.getMetaClass();
            }
            if (values.containsKey(property)) {
                return values.get(property);
            }
            if (DEFAULTS.containsKey(property)) {
                return DEFAULTS.get(property);
            }
            if (FIELDS.contains(property)) {
                return null;
            }
            throw new MissingPropertyException(property, getClass());
        }

        public String parameter(String name) {
            return script.parameterValue(name);
        }

        public Path file(String path) {
            return Path.of(path);
        }

        ProbeContextFacade contextFacade() {
            return contextFacade;
        }

        Map<String, Object> assignedValues() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        private static Object convertText(Object value) {
            return value instanceof CharSequence text ? text.toString() : value;
        }
    }

    /** Facade passed to a closure that explicitly declares one {@code ctx} parameter. */
    public static final class ProbeContextFacade {
        private final M0ProbeScriptBase script;

        ProbeContextFacade(M0ProbeScriptBase script) {
            this.script = script;
        }

        public String parameter(String name) {
            return script.parameterValue(name);
        }

        public Path file(String path) {
            return Path.of(path);
        }

        public String result(String name) {
            return "probe-result:" + name;
        }
    }
}
