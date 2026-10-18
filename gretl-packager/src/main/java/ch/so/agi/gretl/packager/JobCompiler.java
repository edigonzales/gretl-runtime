package ch.so.agi.gretl.packager;

import ch.so.agi.gretl.runtime.groovy.CompilerSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.tools.GroovyClass;

/** Compiles a job and declared helper sources without running the script. */
public final class JobCompiler {
    public CompiledJob compile(PackageDescriptor descriptor) throws PackageException {
        List<SourceFile> sources = sourceFiles(descriptor);
        if (sources.isEmpty()) {
            throw new PackageException("PACKAGE_NO_SOURCES", "Keine Groovy-Quellen gefunden");
        }
        CompilationUnit unit = new CompilationUnit(CompilerSettings.create());
        for (SourceFile source : sources) {
            unit.addSource(source.path().toFile());
        }
        try {
            unit.compile(Phases.CLASS_GENERATION);
        } catch (RuntimeException exception) {
            throw new PackageException("PACKAGE_COMPILE_FAILED",
                    "Groovy-Job konnte nicht kompiliert werden: " + exception.getMessage(),
                    exception);
        }
        ClassNode script = null;
        for (var module : unit.getAST().getModules()) {
            for (ClassNode node : module.getClasses()) {
                if (!node.isScript()) {
                    continue;
                }
                if (script != null) {
                    throw new PackageException("PACKAGE_MULTIPLE_SCRIPTS",
                            "Nur entryScript darf ein Groovy-Script sein: "
                                    + script.getName() + ", " + node.getName());
                }
                script = node;
            }
        }
        if (script == null) {
            throw new PackageException("PACKAGE_NO_SCRIPT",
                    "entryScript enthaelt keine Groovy-Scriptklasse");
        }
        if (!"ch.so.agi.gretl.runtime.groovy.GretlScript"
                .equals(script.getSuperClass().getName())) {
            throw new PackageException("PACKAGE_WRONG_BASE_CLASS",
                    "entryScript muss von GretlScript erben, gefunden: "
                            + script.getSuperClass().getName());
        }
        Map<String, byte[]> classes = new TreeMap<>();
        for (GroovyClass groovyClass : unit.getClasses()) {
            classes.put(groovyClass.getName().replace('.', '/') + ".class",
                    groovyClass.getBytes());
        }
        if (!classes.containsKey(script.getName().replace('.', '/') + ".class")) {
            throw new PackageException("PACKAGE_ENTRY_BYTECODE_MISSING",
                    "Kompilierte Scriptklasse fehlt: " + script.getName());
        }
        return new CompiledJob(script.getName(), classes, sources,
                sha256(descriptor.entryPath()));
    }

    private List<SourceFile> sourceFiles(PackageDescriptor descriptor) throws PackageException {
        Map<Path, SourceFile> result = new LinkedHashMap<>();
        addSource(descriptor, descriptor.entryPath(), result);
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
                            "Quellen konnten nicht gelesen werden: " + sourcePath, exception);
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
                    "Quelle konnte nicht gelesen werden: " + file, exception);
        }
    }

    private static String sha256(Path file) throws PackageException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new PackageException("PACKAGE_SOURCE_READ",
                    "entryScript konnte nicht gelesen werden: " + file, exception);
        }
    }

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
