package ch.so.agi.gretl.runtime.groovy.project;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Shared deterministic names for the future script and packaged project loaders. */
public final class ProjectSourceNames {
    private ProjectSourceNames() { }

    public static String virtualGroovyName(ProjectPath projectPath) {
        return entryClassName(projectPath) + ".groovy";
    }

    public static String entryClassName(ProjectPath projectPath) {
        Objects.requireNonNull(projectPath, "projectPath");
        try {
            return "GretlProject_" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(projectPath.canonical().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("JDK must provide SHA-256", impossible);
        }
    }
}
