package ch.so.agi.gretl.runtime.groovy.project;

import ch.so.agi.gretl.runtime.groovy.SourceLocation;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable parsed project. Default positions survive until later task-name validation. */
public record GretlProject(Path root, Path settingsFile, List<ProjectEntry> entries,
                           List<String> defaultTargets, Map<String, SourceLocation> defaultTargetSources) {
    public GretlProject {
        root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        settingsFile = Objects.requireNonNull(settingsFile, "settingsFile").toAbsolutePath().normalize();
        entries = List.copyOf(entries);
        defaultTargets = List.copyOf(defaultTargets);
        defaultTargetSources = Map.copyOf(defaultTargetSources);
    }

    public GretlProject(Path root, Path settingsFile, List<ProjectEntry> entries, List<String> defaultTargets) {
        this(root, settingsFile, entries, defaultTargets, Map.of());
    }
}
