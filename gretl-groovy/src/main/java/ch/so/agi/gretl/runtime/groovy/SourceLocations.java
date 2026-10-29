package ch.so.agi.gretl.runtime.groovy;

import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Extracts original script file/line from Groovy stack frames. */
public final class SourceLocations {
    private SourceLocations() {
    }

    public static SourceLocation current(Path scriptFile) {
        return from(Thread.currentThread().getStackTrace(), scriptFile);
    }

    public static SourceLocation from(Throwable throwable, Path scriptFile) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;
        while (current != null && seen.add(current)) {
            SourceLocation found = from(current.getStackTrace(), scriptFile);
            if (found.line() > 0) {
                return found;
            }
            current = current.getCause();
        }
        return fallback(scriptFile);
    }

    public static SourceLocation fallback(Path scriptFile) {
        String name = scriptFile == null || scriptFile.getFileName() == null
                ? "<unknown>" : scriptFile.getFileName().toString();
        return new SourceLocation(name, 0, 0);
    }

    public static SourceLocation current(Path original, String virtualName) {
        if (virtualName == null) return current(original);
        return projectFrame(Thread.currentThread().getStackTrace(), original, virtualName);
    }

    public static SourceLocation from(Throwable failure, Path original, String virtualName) {
        if (virtualName == null) return from(failure, original);
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof org.codehaus.groovy.control.MultipleCompilationErrorsException compilation
                    && compilation.getErrorCollector().getErrors() != null) {
                for (Object item : compilation.getErrorCollector().getErrors()) {
                    if (item instanceof org.codehaus.groovy.control.messages.SyntaxErrorMessage syntax) {
                        return new SourceLocation(original.toString(), Math.max(0, syntax.getCause().getStartLine()),
                                Math.max(0, syntax.getCause().getStartColumn()));
                    }
                }
            }
            SourceLocation source = projectFrame(current.getStackTrace(), original, virtualName);
            if (source.line() > 0) return source;
        }
        return new SourceLocation(original.toString(), 0, 0);
    }

    private static SourceLocation projectFrame(StackTraceElement[] stack, Path original, String virtualName) {
        for (StackTraceElement frame : stack) {
            if ((virtualName.equals(frame.getFileName()) || original.toString().equals(frame.getFileName()))
                    && frame.getLineNumber() > 0) {
                return new SourceLocation(original.toString(), frame.getLineNumber(), 0);
            }
        }
        return new SourceLocation(original.toString(), 0, 0);
    }

    /** Preserve throwable types and control signals while restoring filenames for reports. */
    public static void remap(Throwable failure, java.util.Map<String, Path> sources) {
        if (failure instanceof VirtualMachineError || failure instanceof ThreadDeath) return;
        remap(failure, sources, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    private static void remap(Throwable failure, java.util.Map<String, Path> sources, Set<Throwable> seen, int depth) {
        if (failure == null || depth > 16 || failure instanceof VirtualMachineError || failure instanceof ThreadDeath
                || !seen.add(failure)) return;
        StackTraceElement[] frames = failure.getStackTrace();
        for (int i = 0; i < Math.min(frames.length, 64); i++) {
            var frame = frames[i];
            Path original = frame.getFileName() == null ? null : sources.get(frame.getFileName());
            if (original != null) frames[i] = new StackTraceElement(frame.getClassLoaderName(), frame.getModuleName(),
                    frame.getModuleVersion(), frame.getClassName(), frame.getMethodName(), original.toString(), frame.getLineNumber());
        }
        failure.setStackTrace(frames);
        remap(failure.getCause(), sources, seen, depth + 1);
        Throwable[] suppressed = failure.getSuppressed();
        for (int i = 0; i < Math.min(suppressed.length, 8); i++) remap(suppressed[i], sources, seen, depth + 1);
    }

    private static SourceLocation from(StackTraceElement[] stack, Path scriptFile) {
        if (scriptFile == null) {
            return new SourceLocation("<unknown>", 0, 0);
        }
        String expected = scriptFile.getFileName().toString();
        for (StackTraceElement element : stack) {
            if (expected.equals(element.getFileName()) && element.getLineNumber() > 0) {
                return new SourceLocation(expected, element.getLineNumber(), 0);
            }
        }
        return fallback(scriptFile);
    }
}
