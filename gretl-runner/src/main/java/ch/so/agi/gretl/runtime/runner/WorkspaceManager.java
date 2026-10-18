package ch.so.agi.gretl.runtime.runner;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Owns run/task/invocation directories. Retention traverses only the run root
 * created by this manager.
 */
public final class WorkspaceManager {
    private final Path workRoot;

    public WorkspaceManager(Path workRoot) {
        this.workRoot = Objects.requireNonNull(workRoot, "workRoot").toAbsolutePath().normalize();
    }

    public Workspace openRun(UUID runId) throws IOException {
        Objects.requireNonNull(runId, "runId");
        Path root = workRoot.resolve(runId.toString()).normalize();
        if (!root.startsWith(workRoot)) {
            throw new IOException("run workspace escapes work root");
        }
        Files.createDirectories(root);
        setPrivateDirectory(root);
        return new Workspace(root);
    }

    public boolean applyRetention(Workspace workspace, RunStatus status, KeepWork mode) throws IOException {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(mode, "mode");
        if (mode.retain(status)) {
            return true;
        }
        deleteRecursively(workspace.runRoot());
        return false;
    }

    public Path workRoot() {
        return workRoot;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void setPrivateDirectory(Path path) {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows or a filesystem without POSIX permissions: retention still owns the path.
        }
    }

    /** One run root with deterministic task and invocation subdirectories. */
    public static final class Workspace {
        private final Path runRoot;

        private Workspace(Path runRoot) {
            this.runRoot = runRoot;
        }

        public Path runRoot() {
            return runRoot;
        }

        public Path taskDirectory(int registrationIndex) throws IOException {
            Path task = runRoot.resolve("task-%06d".formatted(registrationIndex)).normalize();
            if (!task.startsWith(runRoot)) {
                throw new IOException("task workspace escapes run root");
            }
            Files.createDirectories(task);
            return task;
        }

        public Path cleanupDirectory(int cleanupIndex) throws IOException {
            Path cleanup = runRoot.resolve("cleanup-%03d".formatted(cleanupIndex)).normalize();
            if (!cleanup.startsWith(runRoot)) {
                throw new IOException("cleanup workspace escapes run root");
            }
            Files.createDirectories(cleanup);
            return cleanup;
        }

        public Path invocationDirectory(int registrationIndex, String invocationId) throws IOException {
            if (invocationId == null || !invocationId.matches("[A-Za-z0-9_.-]+")) {
                throw new IOException("unsafe invocation id");
            }
            Path task = taskDirectory(registrationIndex);
            Path invocation = task.resolve(invocationId).normalize();
            if (!invocation.startsWith(task)) {
                throw new IOException("invocation workspace escapes task root");
            }
            Files.createDirectories(invocation);
            return invocation;
        }
    }
}
