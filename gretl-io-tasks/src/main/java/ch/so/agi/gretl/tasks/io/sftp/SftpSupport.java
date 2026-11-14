package ch.so.agi.gretl.tasks.io.sftp;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.tasks.io.internal.RelativePathSelection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;

final class SftpSupport {
    private static final Set<String> CONFIG_FIELDS = Set.of("host", "port", "user", "passwordSecret",
            "privateKeyFile", "privateKeyPassphraseSecret", "knownHostsFile", "connectTimeout", "ioTimeout",
            "connectRetries");

    private SftpSupport() {
    }

    record Config(String host, int port, String user, String password, Path privateKey,
                  String passphrase, Path knownHosts, Duration connectTimeout, Duration ioTimeout, int retries) {
        @Override public String toString() {
            return "SFTP connection settings";
        }
    }

    static Config readConfig(String alias, OperationContext context) throws SftpTaskException {
        try {
            Map<String, Object> fields = context.config(alias);
            if (!CONFIG_FIELDS.containsAll(fields.keySet())) {
                throw new IllegalArgumentException();
            }
            boolean password = fields.containsKey("passwordSecret");
            boolean key = fields.containsKey("privateKeyFile");
            if (password == key || !key && fields.containsKey("privateKeyPassphraseSecret")) {
                throw new IllegalArgumentException();
            }
            Path knownHosts = localPath(fields.get("knownHostsFile"));
            if (!Files.isRegularFile(knownHosts) || !Files.isReadable(knownHosts)) {
                throw error(context, "KNOWN_HOSTS");
            }
            return new Config(text(fields.get("host")), integer(fields, "port", 22, 1, 65535),
                    text(fields.get("user")), password ? secret(fields, "passwordSecret", context) : null,
                    key ? localPath(fields.get("privateKeyFile")) : null,
                    fields.containsKey("privateKeyPassphraseSecret")
                            ? secret(fields, "privateKeyPassphraseSecret", context) : null,
                    knownHosts, duration(fields, "connectTimeout", "PT20S"),
                    duration(fields, "ioTimeout", "PT60S"), integer(fields, "connectRetries", 0, 0, Integer.MAX_VALUE));
        } catch (IllegalArgumentException | java.time.DateTimeException failure) {
            throw error(context, "CONFIG");
        }
    }

    private static String secret(Map<String, Object> fields, String name, OperationContext context) {
        return context.sensitive(context.secret(text(fields.get(name))).reveal());
    }

    private static int integer(Map<String, Object> fields, String key, int fallback, int min, int max) {
        Object value = fields.getOrDefault(key, fallback);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            throw new IllegalArgumentException();
        }
        long number = ((Number) value).longValue();
        if (number < min || number > max) {
            throw new IllegalArgumentException();
        }
        return (int) number;
    }

    private static Duration duration(Map<String, Object> fields, String key, String fallback) {
        var duration = Duration.parse(text(fields.getOrDefault(key, fallback)));
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException();
        }
        return duration;
    }

    static String text(Object value) {
        if (value instanceof CharSequence text && !text.toString().isBlank()) {
            return text.toString();
        }
        throw new IllegalArgumentException("Expected non-empty text");
    }

    static Path localPath(Object value) {
        return (value instanceof Path path ? path : Path.of(text(value))).toAbsolutePath().normalize();
    }

    static boolean flag(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new IllegalArgumentException("Expected a boolean");
    }

    static String normalizeRemotePath(Object value) {
        String path = text(value);
        if (path.indexOf('\0') >= 0 || path.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid SFTP path");
        }
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.equals("..")) {
                throw new IllegalArgumentException("Parent segments are not allowed in SFTP paths");
            }
            if (!part.isEmpty() && !part.equals(".")) {
                parts.add(part);
            }
        }
        String joined = String.join("/", parts);
        return path.startsWith("/") ? "/" + joined : joined.isEmpty() ? "." : joined;
    }

    static String filePath(Object value) {
        String path = normalizeRemotePath(value);
        if (path.equals(".") || path.equals("/")) {
            throw new IllegalArgumentException("Expected an SFTP file path");
        }
        return path;
    }

    static List<String> deletePaths(Object value) {
        return RelativePathSelection.patterns(value).stream().map(SftpSupport::filePath).map(path -> {
            if (path.indexOf('*') >= 0 || path.indexOf('?') >= 0) {
                throw new IllegalArgumentException("SftpDelete requires exact paths");
            }
            return path;
        }).distinct().toList();
    }

    static String join(String parent, String name) {
        return parent.equals("/") ? "/" + name : parent.equals(".") ? name : parent + "/" + name;
    }

    static String parent(String file) {
        int separator = file.lastIndexOf('/');
        return separator < 0 ? "." : separator == 0 ? "/" : file.substring(0, separator);
    }

    static void requireParents(SftpClient client, String path, OperationContext context) throws IOException {
        String root = path.startsWith("/") ? "/" : ".";
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            root = join(root, segment);
            if (!lstat(client, root, context).isDirectory()) {
                throw error(context, "REMOTE_TYPE");
            }
        }
    }

    static SftpClient.Attributes lstat(SftpClient client, String path, OperationContext context) throws IOException {
        context.cancellation().throwIfCancellationRequested();
        var attrs = client.lstat(path);
        context.cancellation().throwIfCancellationRequested();
        return attrs;
    }

    static List<SftpListOperation.Entry> enumerate(SftpClient client, String directory, boolean recursive,
                                                  RelativePathSelection selection, OperationContext context)
            throws IOException {
        requireParents(client, directory, context);
        List<SftpListOperation.Entry> result = new ArrayList<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add("");
        while (!pending.isEmpty()) {
            String relative = pending.removeFirst();
            String path = relative.isEmpty() ? directory : join(directory, relative);
            context.cancellation().throwIfCancellationRequested();
            try (var handle = client.openDir(path)) {
                List<SftpClient.DirEntry> page;
                while ((page = client.readDir(handle)) != null) {
                    context.cancellation().throwIfCancellationRequested();
                    for (var item : page) {
                        String name = item.getFilename();
                        if (name.equals(".") || name.equals("..")) {
                            continue;
                        }
                        if (name.contains("/")) {
                            throw error(context, "REMOTE_TYPE");
                        }
                        RelativePathSelection.validate(name);
                        String child = relative.isEmpty() ? name : relative + "/" + name;
                        String remote = join(directory, child);
                        var attrs = lstat(client, remote, context);
                        var entry = entry(name, remote, child, attrs);
                        if (selection.matches(child)) {
                            result.add(entry);
                        }
                        if (recursive && entry.type() == SftpListOperation.EntryType.DIRECTORY) {
                            pending.addLast(child);
                        }
                    }
                }
            }
        }
        result.sort(Comparator.comparing(SftpListOperation.Entry::relativePath));
        return List.copyOf(result);
    }

    private static SftpListOperation.Entry entry(String name, String remote, String relative,
                                                 SftpClient.Attributes attrs) {
        var type = attrs.isRegularFile() ? SftpListOperation.EntryType.FILE
                : attrs.isDirectory() ? SftpListOperation.EntryType.DIRECTORY
                : attrs.isSymbolicLink() ? SftpListOperation.EntryType.SYMLINK : SftpListOperation.EntryType.OTHER;
        Long size = attrs.getFlags().contains(SftpClient.Attribute.Size) ? attrs.getSize() : null;
        Instant time = attrs.getFlags().contains(SftpClient.Attribute.ModifyTime)
                ? attrs.getModifyTime().toInstant() : null;
        return new SftpListOperation.Entry(name, remote, relative, type, size, time);
    }

    static long copy(InputStream source, OutputStream target, OperationContext context) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        long bytes = 0;
        while (true) {
            context.cancellation().throwIfCancellationRequested();
            int read = source.read(buffer);
            if (read < 0) {
                return bytes;
            }
            target.write(buffer, 0, read);
            bytes += read;
        }
    }

    static boolean missing(SftpException failure) {
        return failure.getStatus() == SftpConstants.SSH_FX_NO_SUCH_FILE
                || failure.getStatus() == SftpConstants.SSH_FX_NO_SUCH_PATH;
    }

    static SftpTaskException safe(OperationContext context, IOException failure) {
        if (failure instanceof SftpTaskException safe) {
            return safe;
        }
        String code = "IO";
        if (failure instanceof SftpException remote) {
            code = missing(remote) ? "MISSING"
                    : remote.getStatus() == SftpConstants.SSH_FX_PERMISSION_DENIED ? "PERMISSION" : "IO";
        }
        var safe = error(context, code);
        for (Throwable ignored : failure.getSuppressed()) {
            safe.addSuppressed(error(context, "CLEANUP"));
        }
        return safe;
    }

    static SftpTaskException error(OperationContext context, String code) {
        return new SftpTaskException("SFTP_" + code,
                Messages.get(context.locale(), "task.sftp." + code.toLowerCase(Locale.ROOT)));
    }
}
