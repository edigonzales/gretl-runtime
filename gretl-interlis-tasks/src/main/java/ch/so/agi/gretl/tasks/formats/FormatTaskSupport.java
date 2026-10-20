package ch.so.agi.gretl.tasks.formats;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.internal.interlis.InterlisTaskException;
import ch.so.agi.gretl.internal.sql.DatabaseSpec;
import ch.so.agi.gretl.runtime.api.OperationContext;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared conversion, database-alias and error helpers for format tasks. */
final class FormatTaskSupport {
    private FormatTaskSupport() {
    }

    static DatabaseSpec database(OperationContext context, String alias) {
        Map<String, Object> config = context.config(alias);
        Set<String> allowed = Set.of("jdbcUrl", "user", "passwordSecret");
        for (String key : config.keySet()) {
            if (!allowed.contains(key)) {
                throw new InterlisTaskException("DB_CONFIG",
                        msg(context, "task.db.unknownField", key), "configuration",
                        java.util.Optional.empty(), null);
            }
        }
        Object url = config.get("jdbcUrl");
        if (!(url instanceof CharSequence urlText) || urlText.toString().isBlank()) {
            throw new InterlisTaskException("DB_CONFIG",
                    msg(context, "task.db.jdbcUrl"), "configuration",
                    java.util.Optional.empty(), null);
        }
        String user = null;
        String password = null;
        Object username = config.get("user");
        if (username != null) {
            if (!(username instanceof CharSequence userText)) {
                throw new InterlisTaskException("DB_CONFIG",
                        msg(context, "task.db.user"), "configuration",
                        java.util.Optional.empty(), null);
            }
            user = userText.toString();
        }
        Object secret = config.get("passwordSecret");
        if (secret != null) {
            if (!(secret instanceof CharSequence secretAlias)) {
                throw new InterlisTaskException("DB_CONFIG",
                        msg(context, "task.db.passwordSecret"), "configuration",
                        java.util.Optional.empty(), null);
            }
            password = context.sensitive(context.secret(secretAlias.toString()).reveal());
        }
        return new DatabaseSpec(url.toString(), user, password);
    }

    static Path path(Object value) {
        if (value instanceof Path path) {
            return path.toAbsolutePath().normalize();
        }
        if (value instanceof CharSequence text) {
            return Path.of(text.toString()).toAbsolutePath().normalize();
        }
        throw new IllegalArgumentException("Path expected");
    }

    static String text(Object value) {
        if (value instanceof CharSequence text && !text.toString().isBlank()) {
            return text.toString();
        }
        throw new IllegalArgumentException("Non-blank text expected");
    }

    static Boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new IllegalArgumentException("Boolean expected");
    }

    static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof CharSequence text && text.toString().matches("-?[0-9]+")) {
            return Integer.parseInt(text.toString());
        }
        throw new IllegalArgumentException("Integer expected");
    }

    static List<String> strings(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                result.add(text(element));
            }
        } else if (value instanceof Object[] array) {
            for (Object element : array) {
                result.add(text(element));
            }
        } else {
            throw new IllegalArgumentException("List expected");
        }
        return List.copyOf(result);
    }

    static List<String> pathStrings(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                result.add(path(element).toString());
            }
        } else if (value instanceof Object[] array) {
            for (Object element : array) {
                result.add(path(element).toString());
            }
        } else {
            throw new IllegalArgumentException("List expected");
        }
        return List.copyOf(result);
    }

    static List<Path> paths(Object value) {
        List<Path> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                result.add(path(element));
            }
        } else if (value instanceof Object[] array) {
            for (Object element : array) {
                result.add(path(element));
            }
        } else {
            throw new IllegalArgumentException("List expected");
        }
        return List.copyOf(result);
    }

    static String msg(OperationContext context, String key, Object... args) {
        return Messages.format(context.locale(), key, args);
    }
}
