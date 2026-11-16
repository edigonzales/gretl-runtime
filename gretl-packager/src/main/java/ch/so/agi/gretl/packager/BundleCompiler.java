package ch.so.agi.gretl.packager;

import ch.so.agi.gretl.runtime.groovy.fragment.CompiledScriptBundle;
import ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleSources;
import ch.so.agi.gretl.runtime.groovy.project.GretlProjectParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.codehaus.groovy.control.Phases;

/** Exact script allowlist plus explicit helper sources, compiled from captured definition bytes. */
public final class BundleCompiler {
    private BundleCompiler() { }

    public record Result(Map<String, byte[]> classes, List<SourceFile> sources, CompiledScriptBundle bundle) {
        public Result {
            classes = Map.copyOf(classes);
            sources = List.copyOf(sources);
        }
    }

    public static Result compile(PackageDescriptor descriptor, List<Path> classpath, Locale locale)
            throws PackageException {
        ScriptBundleSources.Snapshot snapshot;
        List<String> defaults = List.of();
        try {
            if (descriptor.projectPackage()) {
                var project = new GretlProjectParser(locale).parse(descriptor.baseDirectory());
                snapshot = ScriptBundleSources.project(project, locale, false);
                defaults = project.defaultTargets();
            } else {
                snapshot = ScriptBundleSources.single(descriptor.entryPath(), locale, false);
            }
        } catch (IOException failure) {
            throw new PackageException("PACKAGE_SOURCE_READ", failure.getMessage(), failure);
        }
        var bundle = snapshot.bundle();
        var captured = new LinkedHashMap<Path, SourceFile>();
        var names = new LinkedHashMap<Path, String>();
        var ids = new LinkedHashMap<String, String>();
        for (var source : bundle.sources().values()) {
            String id = source.sourceId();
            Path path = snapshot.root().resolve(id);
            captured.put(path, new SourceFile(id, path, source.text()));
            if (!id.equals(bundle.settingsId())) {
                String virtualName = bundle.virtualName(id);
                names.put(path, virtualName);
                ids.put(virtualName, id);
            }
        }
        var sources = sourceFiles(descriptor, captured);
        var compilationSources = sources.stream()
                .filter(source -> !source.relativePath().equals(bundle.settingsId())).toList();
        Compilation compiled;
        try {
            compiled = compileSources(compilationSources, names, classpath, ids.keySet());
        } catch (PackageException failure) {
            String message = failure.getMessage();
            for (var entry : ids.entrySet()) message = message.replace(entry.getKey(), entry.getValue());
            throw new PackageException(failure.code(), message, failure);
        }
        var classes = new LinkedHashMap<String, String>();
        for (var script : compiled.scripts()) {
            String id = ids.get(script.sourceName());
            if (id == null) {
                throw new PackageException("PACKAGE_MULTIPLE_SCRIPTS",
                        PackagerMessages.format("package.project.hiddenScript", script.sourceName()));
            }
            requireBase(script);
            if (classes.putIfAbsent(id, script.name()) != null
                    || !compiled.classes().containsKey(script.name().replace('.', '/') + ".class")) {
                throw new PackageException("PACKAGE_ENTRY_BYTECODE_MISSING", "Missing or duplicate bundle script: " + id);
            }
        }
        if (classes.size() != ids.size()) {
            throw new PackageException("PACKAGE_NO_SCRIPT", PackagerMessages.get("package.noScript"));
        }
        return new Result(compiled.classes(),sources,CompiledScriptBundle.from(bundle, classes, defaults));
    }

    private static void requireBase(ScriptEntry script) throws PackageException {
        if (!"ch.so.agi.gretl.runtime.groovy.GretlScript".equals(script.base()))
            throw new PackageException("PACKAGE_WRONG_BASE_CLASS", PackagerMessages.format("package.wrongBaseClass", script.base()));
    }

    private static Compilation compileSources(List<SourceFile> sources, Map<Path, String> virtualNames,
                                              List<Path> classpath, Set<String> allowedScripts) throws PackageException {
        java.net.URL[] urls = classpath.stream().map(path -> {
            try { return path.toUri().toURL(); }
            catch (java.net.MalformedURLException exception) { throw new IllegalArgumentException(exception); }
        }).toArray(java.net.URL[]::new);
        // Both compiler and program types come from this exact selection. Sharing the
        // host Groovy loader would either leak extensions or split AST type identities.
        try (var selected = new java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            Object configuration = selected.loadClass("ch.so.agi.gretl.runtime.groovy.CompilerSettings")
                    .getMethod("create").invoke(null);
            Class<?> configurationType = selected.loadClass("org.codehaus.groovy.control.CompilerConfiguration");
            configurationType.getMethod("setClasspathList", List.class).invoke(configuration,
                    classpath.stream().map(Path::toString).toList());
            Class<?> loaderType = selected.loadClass("groovy.lang.GroovyClassLoader");
            try (var loader = (java.io.Closeable) loaderType.getConstructor(ClassLoader.class, configurationType)
                    .newInstance(selected, configuration)) {
                Class<?> unitType = selected.loadClass("org.codehaus.groovy.control.CompilationUnit");
                Object unit = unitType.getConstructor(configurationType, java.security.CodeSource.class, loaderType)
                        .newInstance(configuration, null, loader);
                for (SourceFile source : sources) {
                    String virtual = virtualNames.getOrDefault(source.path(), source.path().toString());
                    unitType.getMethod("addSource", String.class, String.class).invoke(unit, virtual, source.text());
                }
                selected.loadClass("ch.so.agi.gretl.runtime.groovy.fragment.ScriptBundleCompilation")
                        .getMethod("prepare", unitType, Set.class).invoke(null, unit, allowedScripts);
                try {
                    unitType.getMethod("compile", int.class).invoke(unit, Phases.CLASS_GENERATION);
                } catch (java.lang.reflect.InvocationTargetException exception) {
                    // Groovy formats source diagnostics lazily. Materialize them while
                    // its selected loader is still open, retaining the original cause.
                    Throwable cause = exception.getCause();
                    throw new PackageException("PACKAGE_COMPILE_FAILED", PackagerMessages.format("package.compileFailed", cause.getMessage()), cause);
                }
                List<ScriptEntry> scripts = new ArrayList<>();
                Object ast = call(unit, "getAST");
                for (Object module : (List<?>) call(ast, "getModules")) {
                    String sourceName = (String) call(call(module, "getContext"), "getName");
                    for (Object node : (List<?>) call(module, "getClasses")) {
                        if (Boolean.TRUE.equals(call(node, "isScript"))) {
                            scripts.add(new ScriptEntry(sourceName, (String) call(node, "getName"),
                                    (String) call(call(node, "getSuperClass"), "getName")));
                        }
                    }
                }
                Map<String, byte[]> classes = new TreeMap<>();
                for (Object compiled : (List<?>) call(unit, "getClasses"))
                    classes.put(((String) call(compiled, "getName")).replace('.', '/') + ".class", (byte[]) call(compiled, "getBytes"));
                return new Compilation(classes, scripts);
            }
        } catch (java.lang.reflect.InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            throw new PackageException("PACKAGE_COMPILE_FAILED", PackagerMessages.format("package.compileFailed", cause.getMessage()), cause);
        } catch (ReflectiveOperationException | IOException exception) {
            throw new PackageException("PACKAGE_COMPILE_CLASSPATH", "Selected compiler/runtime classpath is incomplete: " + exception.getMessage(), exception);
        }
    }

    private static Object call(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static List<SourceFile> sourceFiles(PackageDescriptor descriptor, Map<Path, SourceFile> captured) throws PackageException {
        Map<Path, SourceFile> result = new LinkedHashMap<>(captured);
        for (Path sourcePath : descriptor.sourcePaths()) {
            if (Files.isDirectory(sourcePath)) {
                try (Stream<Path> walk = Files.walk(sourcePath)) {
                    List<Path> files = walk.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".groovy"))
                            .sorted().toList();
                    for (Path file : files) {
                        addSource(descriptor, file, result);
                    }
                } catch (IOException exception) {
                    throw new PackageException("PACKAGE_SOURCE_READ",
                            PackagerMessages.format("package.sourcesRead", sourcePath), exception);
                }
            } else {
                addSource(descriptor, sourcePath, result);
            }
        }
        return List.copyOf(result.values());
    }

    private static void addSource(PackageDescriptor descriptor, Path file, Map<Path, SourceFile> result)
            throws PackageException {
        Path normalized = file.toAbsolutePath().normalize();
        if (result.containsKey(normalized)) {
            return;
        }
        try {
            String relative = descriptor.baseDirectory().relativize(normalized)
                    .toString().replace('\\', '/');
            result.put(normalized, new SourceFile(relative, normalized,
                    Files.readString(normalized, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new PackageException("PACKAGE_SOURCE_READ",
                    PackagerMessages.format("package.sourceRead", file), exception);
        }
    }

    private record ScriptEntry(String sourceName, String name, String base) { }
    private record Compilation(Map<String, byte[]> classes, List<ScriptEntry> scripts) { }

    public record SourceFile(String relativePath, Path path, String text) { }
}
