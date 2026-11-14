package ch.so.agi.gretl.tasks.io.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Checks local source and output paths for symbolic links. */
public final class LocalFilePaths {
    private LocalFilePaths() {
    }

    public static void rejectSymbolicLinks(Path absolutePath) throws IOException {
        for (Path part = absolutePath; part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part) && !macSystemAlias(part)) {
                throw new IOException("Symbolic link is not allowed in local path: " + part.getFileName());
            }
        }
    }

    private static boolean macSystemAlias(Path path) throws IOException {
        if (!System.getProperty("os.name").equals("Mac OS X")) {
            return false;
        }
        String name = path.toString();
        return (name.equals("/var") || name.equals("/tmp"))
                && path.toRealPath().equals(Path.of("/private" + name));
    }

    public static void createDirectories(Path absolutePath) throws IOException {
        rejectSymbolicLinks(absolutePath);
        Files.createDirectories(absolutePath);
        rejectSymbolicLinks(absolutePath);
    }
}
