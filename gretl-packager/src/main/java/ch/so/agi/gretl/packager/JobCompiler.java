package ch.so.agi.gretl.packager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.codehaus.groovy.control.Phases;

/** Compiles a job and declared helper sources without running the script. */
public final class JobCompiler {
    public CompiledJob compile(PackageDescriptor descriptor) throws PackageException {
        return compile(descriptor, new PackageCatalog(JobPackager.catalogFile(), descriptor.runtimeVersion())
                .select(descriptor).stream().map(ResolvedArtifact::file).toList());
    }

    public CompiledJob compile(PackageDescriptor descriptor, List<Path> classpath) throws PackageException {
        List<SourceFile> sources = sourceFiles(descriptor);
        if (sources.isEmpty()) {
            throw new PackageException("PACKAGE_NO_SOURCES", PackagerMessages.get("package.noSources"));
        }
        Compilation compilation = compileSources(sources, Map.of(), classpath);
        if (compilation.scripts().isEmpty()) throw new PackageException("PACKAGE_NO_SCRIPT", PackagerMessages.get("package.noScript"));
        if (compilation.scripts().size() > 1) throw new PackageException("PACKAGE_MULTIPLE_SCRIPTS",
                PackagerMessages.format("package.multipleScripts", compilation.scripts().getFirst().name(), compilation.scripts().getLast().name()));
        ScriptEntry script = compilation.scripts().getFirst();
        requireBase(script);
        if (!compilation.classes().containsKey(script.name().replace('.', '/') + ".class"))
            throw new PackageException("PACKAGE_ENTRY_BYTECODE_MISSING", PackagerMessages.format("package.entryBytecodeMissing", script.name()));
        return new CompiledJob(script.name(), compilation.classes(), sources, sha256(descriptor.entryPath()));
    }

    static void requireBase(ScriptEntry script) throws PackageException {
        if (!"ch.so.agi.gretl.runtime.groovy.GretlScript".equals(script.base()))
            throw new PackageException("PACKAGE_WRONG_BASE_CLASS", PackagerMessages.format("package.wrongBaseClass", script.base()));
    }

    static Compilation compileSources(List<SourceFile> sources, Map<Path, String> virtualNames, List<Path> classpath) throws PackageException {
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
                    String virtual = virtualNames.get(source.path());
                    if (virtual == null) unitType.getMethod("addSource", java.io.File.class).invoke(unit, source.path().toFile());
                    else unitType.getMethod("addSource", String.class, String.class).invoke(unit, virtual, source.text());
                }
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

    List<SourceFile> sourceFiles(PackageDescriptor descriptor) throws PackageException {
        return sourceFiles(descriptor, List.of(descriptor.entryPath()));
    }

    List<SourceFile> sourceFiles(PackageDescriptor descriptor, List<Path> entries) throws PackageException {
        Map<Path, SourceFile> result = new LinkedHashMap<>();
        for (Path entry : entries) addSource(descriptor, entry, result);
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

    private void addSource(PackageDescriptor descriptor, Path file, Map<Path, SourceFile> result)
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

    private static String sha256(Path file) throws PackageException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new PackageException("PACKAGE_SOURCE_READ",
                    PackagerMessages.format("package.entryRead", file), exception);
        }
    }

    record ScriptEntry(String sourceName, String name, String base) { }
    record Compilation(Map<String, byte[]> classes, List<ScriptEntry> scripts) { }

    public record SourceFile(String relativePath, Path path, String text) {
    }

    public record CompiledJob(
            String entryClass, Map<String, byte[]> classes,
            List<SourceFile> sources, String scriptSha256) {
        public CompiledJob {
            classes = Map.copyOf(classes);
            sources = List.copyOf(sources);
        }

        public int classCount() {
            return classes.size();
        }
    }
}
