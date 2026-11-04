package ch.so.agi.gretl.runtime.groovy.fragment;

import ch.so.agi.gretl.runtime.groovy.GroovyDslException;
import java.nio.file.*;
import java.io.IOException;

/** Local contained reads. Limits apply to new fragments, not legacy entry files. */
public final class ContainedFragmentSources implements FragmentSourceAccess {
    public static final int MAX_FRAGMENT_BYTES=8*1024*1024;
    private final Path root;
    public ContainedFragmentSources(Path root) { this.root=root.toAbsolutePath().normalize(); }
    @Override public String resolve(String caller,String from) throws IOException {
        String id=FragmentSourceAccess.super.resolve(caller,from);
        Path parent=Path.of(caller).getParent();
        validate((parent==null?Path.of(from):parent.resolve(from)).toString());
        return id;
    }
    private Path validate(String sourceId) throws IOException {
        Path file=root.resolve(sourceId).normalize();
        if (!file.startsWith(root) || Files.isSymbolicLink(root)) throw error("APPLY_PATH_UNSAFE",sourceId);
        Path path=root;
        // Inspect original components too: a symlink followed by '..' must not disappear during normalization.
        for (Path segment : Path.of(sourceId)) {
            path=path.resolve(segment).normalize();
            if (!path.startsWith(root) || Files.isSymbolicLink(path)) throw error("APPLY_PATH_UNSAFE",sourceId);
        }
        if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw error("APPLY_NOT_FOUND",sourceId);
        if (!file.toRealPath().startsWith(root.toRealPath())) throw error("APPLY_PATH_UNSAFE",sourceId);
        return file;
    }
    @Override public FragmentSource read(String sourceId, boolean fragment) throws IOException {
        Path file=validate(sourceId);
        try (var stream=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes=fragment?stream.readNBytes(MAX_FRAGMENT_BYTES+1):stream.readAllBytes();
            if(fragment && bytes.length>MAX_FRAGMENT_BYTES)throw error("APPLY_LIMIT",sourceId);
            try { return new FragmentSource(sourceId,bytes); }
            catch(IllegalArgumentException invalid){throw new GroovyDslException("APPLY_SYNTAX",ch.so.agi.gretl.i18n.Messages.format(ch.so.agi.gretl.i18n.Messages.sourceLocale(),"apply.APPLY_SYNTAX",sourceId),invalid);}
        }
    }
    private static GroovyDslException error(String code,String source) {return new GroovyDslException(code,ch.so.agi.gretl.i18n.Messages.format(ch.so.agi.gretl.i18n.Messages.sourceLocale(),"apply."+code,source));}
}
