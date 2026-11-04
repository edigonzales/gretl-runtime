package ch.so.agi.gretl.runtime.groovy.fragment;

import java.io.IOException;

/** IO boundary; implementations must return bytes from a validated contained regular source. */
@FunctionalInterface
public interface FragmentSourceAccess {
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
