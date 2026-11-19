package ch.so.agi.gretl.runtime.groovy;

import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Extracts original script file/line from Groovy stack frames. */
public final class SourceLocations {
    private SourceLocations() {
    }

    static SourceLocation current(Path original, String virtualName) {
        return projectFrame(Thread.currentThread().getStackTrace(), original, virtualName);
    }

    static SourceLocation from(Throwable failure, Path original, String virtualName) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
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
}
