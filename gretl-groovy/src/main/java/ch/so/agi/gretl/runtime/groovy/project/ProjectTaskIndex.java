package ch.so.agi.gretl.runtime.groovy.project;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.TaskRef;
import ch.so.agi.gretl.runtime.groovy.GroovyDslException;
import ch.so.agi.gretl.runtime.groovy.SourceLocation;
import ch.so.agi.gretl.runtime.runner.TaskLink;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Definition-local references shared by all project scripts and their stored closures. */
public final class ProjectTaskIndex {
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, List<TaskLink>> links = new LinkedHashMap<>();
    private final Locale locale;

    public ProjectTaskIndex() { this(Locale.ENGLISH); }
    public ProjectTaskIndex(Locale locale) { this.locale = java.util.Objects.requireNonNull(locale); }

    public void ensureAvailable(String id, SourceLocation source) {
        Entry first = entries.get(id);
        if (first != null) {
            throw new ProjectException("DUPLICATE_TASK_ID", Messages.format(locale,
                    "project.taskDuplicate", id, first.source(), source), source, null);
        }
    }

    public void register(String id, TaskRef<?> ref, OperationType<?, ?> operation, SourceLocation source) {
        ensureAvailable(id, source);
        entries.put(id, new Entry(java.util.Objects.requireNonNull(ref), operation, source));
    }

    public TaskRef<?> require(String id, String requester) {
        Entry entry = entries.get(id);
        if (entry == null) {
            SourceLocation source = Optional.ofNullable(entries.get(requester)).map(Entry::source)
                    .orElse(null);
            throw new GroovyDslException("UNKNOWN_TASK_REFERENCE", Messages.format(locale,
                    "project.taskUnknown", requester, id), requester, null, source, taskIds(), null);
        }
        return entry.ref();
    }

    public Optional<OperationType<?, ?>> operation(String id) {
        Entry entry = entries.get(id);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.operation());
    }

    public Optional<SourceLocation> source(String id) { return Optional.ofNullable(entries.get(id)).map(Entry::source); }

    public List<String> taskIds() { return List.copyOf(entries.keySet()); }

    /** Only source-aware existence checking; the existing builder resolves refs and plans the DAG. */
    public void rememberLinks(String owner, List<TaskLink> dependsOn, List<TaskLink> after) {
        var combined = new java.util.ArrayList<>(dependsOn); combined.addAll(after);
        links.put(owner, List.copyOf(combined));
    }

    public void validateReferences() {
        links.forEach((owner, values) -> values.stream().filter(TaskLink::isNamed)
                .forEach(link -> require(link.name(), owner)));
    }

    private record Entry(TaskRef<?> ref, OperationType<?, ?> operation, SourceLocation source) {}
}
