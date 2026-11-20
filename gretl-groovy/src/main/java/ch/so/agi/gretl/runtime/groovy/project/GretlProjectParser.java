package ch.so.agi.gretl.runtime.groovy.project;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.groovy.CompilerSettings;
import ch.so.agi.gretl.runtime.groovy.SourceLocation;
import ch.so.agi.gretl.syntax.SettingsAstInspector;
import groovy.lang.GroovyClassLoader;
import groovyjarjarantlr4.v4.runtime.CharStreams;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.groovy.parser.antlr4.GroovyLangLexer;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;

/** Reads only a bounded, declarative settings AST; never compiles or runs settings or jobs. */
public final class GretlProjectParser {
    private static final int MAX_SETTINGS_BYTES = 256 * 1024;
    private final Locale locale;

    public GretlProjectParser() {
        this(Messages.sourceLocale());
    }

    public GretlProjectParser(Locale locale) {
        this.locale = Objects.requireNonNull(locale, "locale");
    }

    public GretlProject parse(Path projectDirectory) {
        Path root = Objects.requireNonNull(projectDirectory, "projectDirectory").toAbsolutePath().normalize();
        try {
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw error("PROJECT_PATH_UNSAFE", location(root), null, "project.pathUnsafe", root);
            }
            root = root.toRealPath();
        } catch (IOException exception) {
            throw error("PROJECT_PATH_UNSAFE", location(root), exception, "project.pathUnsafe", root);
        }
        Path settings = root.resolve("settings.gretl");
        validateContainedRegularFile(root, settings, "settings.gretl");
        String text;
        // Bound the read itself, including a file that grows after the path check.
        try (var stream = Files.newInputStream(settings, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = stream.readNBytes(MAX_SETTINGS_BYTES + 1);
            if (bytes.length > MAX_SETTINGS_BYTES) {
                throw error("PROJECT_SETTINGS_INVALID", location(settings), null, "project.settingsSize");
            }
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw error("PROJECT_SETTINGS_INVALID", location(settings), exception, "project.settingsEncoding");
        } catch (IOException exception) {
            throw error("PROJECT_SETTINGS_INVALID", location(settings), exception, "project.settingsRead", settings);
        }
        ProjectSettings parsed = parseSettings(settings, text);
        List<ProjectEntry> entries = resolveEntries(root, parsed.included(), parsed.includeSources());
        if (entries.isEmpty()) {
            throw error("PROJECT_EMPTY", location(settings), null, "project.empty", root);
        }
        return new GretlProject(root, settings, entries, parsed.defaultTargets(), parsed.defaultSources(),text);
    }

    ProjectSettings parseSettings(Path settingsFile, String source) {
        Objects.requireNonNull(source, "source");
        if (source.getBytes(StandardCharsets.UTF_8).length > MAX_SETTINGS_BYTES) {
            throw error("PROJECT_SETTINGS_INVALID", location(settingsFile), null, "project.settingsSize");
        }
        var configuration = CompilerSettings.create();
        // Settings are not GretlScript jobs. Avoid Groovy's synthetic @BaseScript annotation.
        configuration.setScriptBaseClass(null);
        SourceUnit unit = null;
        try (var loader = new GroovyClassLoader(getClass().getClassLoader(), configuration)) {
            unit = new SourceUnit(settingsFile.toString(), source, configuration, loader,
                    new ErrorCollector(configuration));
            unit.parse();
            unit.completePhase();
            unit.nextPhase();
            unit.convert();
            var tokens = new GroovyLangLexer(CharStreams.fromString(source)).getAllTokens();
            Map<ProjectPath, SourceLocation> includes = new LinkedHashMap<>();
            Map<ProjectPath, String> originalIncludes = new LinkedHashMap<>();
            Map<String, SourceLocation> defaults = new LinkedHashMap<>();
            String file = settingsFile.toAbsolutePath().normalize().toString();
            for (var finding : new SettingsAstInspector().inspect(unit.getAST(), tokens, () -> {})) {
                if (finding instanceof SettingsAstInspector.Unsupported unsupported) {
                    var token = unsupported.importToken();
                    if (token == null) throw unsupported(settingsFile, unsupported.node());
                    throw error("PROJECT_SETTINGS_UNSUPPORTED",
                            new SourceLocation(file, token.getLine(), token.getCharPositionInLine() + 1),
                            null, "project.settingsUnsupported");
                }
                var directive = (SettingsAstInspector.Directive) finding;
                String name = directive.name();
                boolean include = directive.isInclude();
                SourceLocation callSource = location(settingsFile, directive.node());
                if (directive.repeated()) {
                    throw error("PROJECT_SETTINGS_INVALID", callSource, null, "project.directiveRepeated", name);
                }
                if (directive.arguments().isEmpty()) {
                    throw error(include ? "PROJECT_INCLUDE_INVALID" : "PROJECT_TARGET_INVALID",
                            callSource, null, "project.directiveEmpty", name);
                }
                for (var argument : directive.arguments()) {
                    if (argument.literal() == null) throw unsupported(settingsFile, argument.node());
                    String text = argument.value();
                    SourceLocation position = location(settingsFile, argument.node());
                    ProjectPath path;
                    try {
                        path = include ? ProjectPath.parseInclude(text) : ProjectPath.parseQualified(text);
                        if (!include && path.isRoot()) throw new IllegalArgumentException("target required");
                    } catch (IllegalArgumentException invalid) {
                        throw error(include ? "PROJECT_INCLUDE_INVALID" : "PROJECT_TARGET_INVALID", position,
                                invalid, include ? "project.includeInvalid" : "project.targetInvalid", text);
                    }
                    if (include) {
                        SourceLocation first = includes.putIfAbsent(path, position);
                        if (first != null) {
                            throw error("PROJECT_INCLUDE_DUPLICATE", position, null, "project.includeDuplicate",
                                    text, position, originalIncludes.get(path), first);
                        }
                        originalIncludes.put(path, text);
                    } else if (defaults.putIfAbsent(path.canonical(), position) != null) {
                        throw error("DUPLICATE_DEFAULT_TARGET", position, null, "project.defaultDuplicate", text);
                    }
                }
            }
            return new ProjectSettings(List.copyOf(includes.keySet()), List.copyOf(defaults.keySet()),
                    includes, defaults);
        } catch (CompilationFailedException exception) {
            SourceLocation position = location(settingsFile);
            if (unit != null && unit.getErrorCollector().getErrors() != null) {
                for (Object item : unit.getErrorCollector().getErrors()) {
                    if (item instanceof SyntaxErrorMessage syntax) {
                        var cause = syntax.getCause();
                        position = new SourceLocation(settingsFile.toAbsolutePath().normalize().toString(),
                                Math.max(0, cause.getStartLine()), Math.max(0, cause.getStartColumn()));
                        break;
                    }
                }
            }
            throw error("PROJECT_SETTINGS_INVALID", position, exception, "project.settingsSyntax");
        } catch (IOException exception) {
            throw error("PROJECT_SETTINGS_INVALID", location(settingsFile), exception, "project.settingsRead", settingsFile);
        }
    }

    List<ProjectEntry> resolveEntries(Path root, List<ProjectPath> included) {
        return resolveEntries(root, included, Map.of());
    }

    private List<ProjectEntry> resolveEntries(Path root, List<ProjectPath> included,
                                              Map<ProjectPath, SourceLocation> sources) {
        List<ProjectEntry> entries = new ArrayList<>();
        Path rootScript = root.resolve("job.groovy");
        if (Files.exists(rootScript, LinkOption.NOFOLLOW_LINKS)) {
            validateContainedRegularFile(root, rootScript, "job.groovy");
            entries.add(new ProjectEntry(ProjectPath.root(), rootScript));
        }
        for (ProjectPath path : included) {
            Path script = root.resolve(path.relativeDirectory()).resolve("job.groovy");
            validateContainedRegularFile(root, script, "job.groovy", sources.getOrDefault(path, location(script)));
            entries.add(new ProjectEntry(path, script));
        }
        return List.copyOf(entries);
    }

    void validateContainedRegularFile(Path root, Path candidate, String role) {
        validateContainedRegularFile(root, candidate, role, location(candidate));
    }

    private void validateContainedRegularFile(Path root, Path candidate, String role, SourceLocation source) {
        Path base = root.toAbsolutePath().normalize();
        Path file = candidate.toAbsolutePath().normalize();
        if (!file.startsWith(base) || Files.isSymbolicLink(base)) {
            throw error("PROJECT_PATH_UNSAFE", source, null, "project.pathUnsafe", file);
        }
        Path current = base;
        for (Path segment : base.relativize(file)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw error("PROJECT_PATH_UNSAFE", source, null, "project.pathUnsafe", current);
            }
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw error(role.equals("settings.gretl") ? "PROJECT_SETTINGS_MISSING" : "PROJECT_ENTRY_MISSING",
                    source, null, "project.fileMissing", role, file);
        }
        try {
            if (!file.toRealPath().startsWith(base.toRealPath())) {
                throw error("PROJECT_PATH_UNSAFE", source, null, "project.pathUnsafe", file);
            }
        } catch (IOException exception) {
            throw error("PROJECT_PATH_UNSAFE", source, exception, "project.pathUnsafe", file);
        }
    }

    private ProjectException unsupported(Path settings, ASTNode node) {
        return error("PROJECT_SETTINGS_UNSUPPORTED", location(settings, node), null, "project.settingsUnsupported");
    }

    private ProjectException error(String code, SourceLocation source, Throwable cause, String key, Object... values) {
        return new ProjectException(code, Messages.format(locale, key, values), source, cause);
    }

    private static SourceLocation location(Path path) {
        return new SourceLocation(path.toAbsolutePath().normalize().toString(), 0, 0);
    }

    private static SourceLocation location(Path path, ASTNode node) {
        return new SourceLocation(path.toAbsolutePath().normalize().toString(),
                Math.max(0, node.getLineNumber()), Math.max(0, node.getColumnNumber()));
    }

    record ProjectSettings(List<ProjectPath> included, List<String> defaultTargets,
                           Map<ProjectPath, SourceLocation> includeSources, Map<String, SourceLocation> defaultSources) {
        ProjectSettings {
            included = List.copyOf(included);
            defaultTargets = List.copyOf(defaultTargets);
            includeSources = Map.copyOf(includeSources);
            defaultSources = Map.copyOf(defaultSources);
        }
    }
}
