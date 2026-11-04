package ch.so.agi.gretl.runtime.groovy.fragment;

import java.io.IOException;

/** IO boundary; implementations must return bytes from a validated contained regular source. */
@FunctionalInterface
public interface FragmentSourceAccess {
    default String canonicalId(String sourceId) throws IOException {
        java.util.Objects.requireNonNull(sourceId,"sourceId");
        if(sourceId.indexOf('\\')>=0 || sourceId.indexOf(':')>=0 || sourceId.codePoints().anyMatch(Character::isISOControl))
            throw unsafeId(sourceId);
        try {
            var path=java.nio.file.Path.of(sourceId);
            var canonical=path.normalize();
            if(path.isAbsolute() || canonical.startsWith("..") || canonical.toString().isEmpty())throw unsafeId(sourceId);
            return canonical.toString().replace('\\','/');
        }catch(java.nio.file.InvalidPathException invalid){throw unsafeId(sourceId);}
    }
    private static ch.so.agi.gretl.runtime.groovy.GroovyDslException unsafeId(String sourceId) {
        return new ch.so.agi.gretl.runtime.groovy.GroovyDslException("APPLY_PATH_UNSAFE",
                ch.so.agi.gretl.i18n.Messages.format(ch.so.agi.gretl.i18n.Messages.sourceLocale(),"apply.APPLY_PATH_UNSAFE",sourceId));
    }
    default String resolve(String caller, String from) throws IOException {
        if (from.isBlank() || from.indexOf('\\')>=0 || from.indexOf(':')>=0 || from.startsWith("/")
                || from.codePoints().anyMatch(c->Character.isISOControl(c))
                || from.chars().anyMatch(c->"*?[]{}".indexOf(c)>=0))
            throw new ch.so.agi.gretl.runtime.groovy.GroovyDslException("APPLY_PATH_UNSAFE",ch.so.agi.gretl.i18n.Messages.format(ch.so.agi.gretl.i18n.Messages.sourceLocale(),"apply.APPLY_PATH_UNSAFE",caller));
        var parent=java.nio.file.Path.of(caller).getParent();
        var target=(parent==null?java.nio.file.Path.of(from):parent.resolve(from)).normalize();
        if (target.isAbsolute() || target.startsWith("..") || !target.toString().endsWith(".groovy"))
            throw new ch.so.agi.gretl.runtime.groovy.GroovyDslException("APPLY_PATH_UNSAFE",ch.so.agi.gretl.i18n.Messages.format(ch.so.agi.gretl.i18n.Messages.sourceLocale(),"apply.APPLY_PATH_UNSAFE",caller));
        return target.toString().replace('\\','/');
    }
    FragmentSource read(String sourceId, boolean fragment) throws IOException;
}
