package ch.so.agi.gretl.tasks.ftp;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.CancellationToken;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.Progress;
import ch.so.agi.gretl.runtime.api.SecretValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

/** Shared FTP connection and transfer support for the FTP task family. */
final class FtpSupport {
    static final String DEFAULT_SYSTEM_TYPE = FTPClientConfig.SYST_UNIX;
    static final long DEFAULT_KEEPALIVE_SECONDS = 300;
    static final String DEFAULT_FILE_TYPE = "ASCII";

    private FtpSupport() {
    }

    static FtpSession open(FtpConnection connection, OperationContext context)
            throws IOException {
        Objects.requireNonNull(connection, "connection");
        String server = requireText(connection.server(), "server");
        String user = requireText(connection.user(), "user");
        Objects.requireNonNull(connection.password(), "password");
        String systemType = blankToDefault(connection.systemType(), DEFAULT_SYSTEM_TYPE);
        FTPClient client = new FTPClient();
        client.configure(new FTPClientConfig(systemType));
        HostAndPort hostAndPort = parseServer(server);
        Duration remaining = context.budget().remaining().orElse(null);
        if (remaining != null) {
            long millis = Math.max(1, Math.min(remaining.toMillis(), 30_000));
            client.setConnectTimeout((int) millis);
            client.setDefaultTimeout((int) millis);
        }
        client.connect(hostAndPort.host(), hostAndPort.port());
        if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
            disconnectQuietly(client);
            throw new FtpTaskException("FTP_CONNECT",
                    msg(context, "task.ftp.connect"), "connect", null);
        }
        if (!client.login(user, connection.password().reveal())) {
            disconnectQuietly(client);
            throw new FtpTaskException("FTP_LOGIN",
                    msg(context, "task.ftp.login"), "connect", null);
        }
        if (connection.passiveMode()) {
            client.enterLocalPassiveMode();
        } else {
            client.enterLocalActiveMode();
        }
        if (connection.controlKeepAliveTimeout() > 0) {
            client.setControlKeepAliveTimeout(connection.controlKeepAliveTimeout());
        }
        return new FtpSession(client, separator(connection, systemType));
    }

    static void setFileType(FTPClient client, String fileType) throws IOException {
        String normalized = fileType == null || fileType.isBlank()
                ? DEFAULT_FILE_TYPE : fileType.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("ASCII") && !normalized.equals("BINARY")) {
            throw new IllegalArgumentException("fileType must be ASCII or BINARY");
        }
        client.setFileType(normalized.equals("BINARY")
                ? FTP.BINARY_FILE_TYPE : FTP.ASCII_FILE_TYPE);
    }

    static List<String> list(FTPClient client, String remoteDir, OperationContext context)
            throws IOException {
        String directory = requireText(remoteDir, "remoteDir");
        FTPFile[] files = client.listFiles(directory);
        if (files == null) {
            throw new FtpTaskException("FTP_LIST",
                    msg(context, "task.ftp.list"), "list", null);
        }
        List<String> names = new ArrayList<>();
        for (FTPFile file : files) {
            if (file.isFile()) {
                names.add(file.getName());
            }
        }
        return List.copyOf(names);
    }

    static List<String> expand(FTPClient client, String remoteDir,
                               List<String> candidates, OperationContext context)
            throws IOException {
        if (candidates == null || candidates.isEmpty()) {
            return list(client, remoteDir, context);
        }
        List<String> remoteNames = null;
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            validateRemoteName(candidate, true);
            if (!containsWildcard(candidate)) {
                validateRemoteName(candidate, false);
                result.add(candidate);
                continue;
            }
            if (remoteNames == null) {
                remoteNames = list(client, remoteDir, context);
            }
            Pattern pattern = wildcardPattern(candidate);
            for (String remoteName : remoteNames) {
                if (pattern.matcher(remoteName).matches()) {
                    result.add(remoteName);
                }
            }
        }
        return List.copyOf(result);
    }

    static long retrieve(FTPClient client, String remotePath, Path target,
                         OperationContext context) throws IOException {
        InputStream input = client.retrieveFileStream(remotePath);
        if (input == null) {
            throw new FtpTaskException("FTP_DOWNLOAD",
                    msg(context, "task.ftp.download", remotePath), "download", null);
        }
        long bytes;
        try (InputStream in = input) {
            OptionalLong total = OptionalLong.empty();
            try (OutputStream out = Files.newOutputStream(target)) {
                bytes = copy(in, out, total, context);
            }
        }
        if (!client.completePendingCommand()) {
            throw new FtpTaskException("FTP_DOWNLOAD",
                    msg(context, "task.ftp.download", remotePath), "download", null);
        }
        return bytes;
    }

    static long store(FTPClient client, String remotePath, Path source,
                      OperationContext context) throws IOException {
        long totalSize = Files.size(source);
        try (InputStream in = Files.newInputStream(source)) {
            OutputStream output = client.storeFileStream(remotePath);
            if (output == null) {
                throw new FtpTaskException("FTP_UPLOAD",
                        msg(context, "task.ftp.upload", remotePath), "upload", null);
            }
            long bytes;
            try (OutputStream out = output) {
                bytes = copy(in, out, OptionalLong.of(totalSize), context);
            }
            if (!client.completePendingCommand()) {
                throw new FtpTaskException("FTP_UPLOAD",
                        msg(context, "task.ftp.upload", remotePath), "upload", null);
            }
            return bytes;
        }
    }

    static void delete(FTPClient client, String remotePath, OperationContext context)
            throws IOException {
        if (!client.deleteFile(remotePath)) {
            throw new FtpTaskException("FTP_DELETE",
                    msg(context, "task.ftp.delete", remotePath), "delete", null);
        }
    }

    static String join(String directory, String fileName, String separator) {
        String dir = requireText(directory, "remoteDir");
        if (dir.endsWith(separator)) {
            return dir + fileName;
        }
        return dir + separator + fileName;
    }

    static String separator(FtpConnection connection, String systemType) {
        if (connection.fileSeparator() != null
                && !connection.fileSeparator().isEmpty()) {
            return connection.fileSeparator();
        }
        return FTPClientConfig.SYST_NT.equalsIgnoreCase(systemType) ? "\\" : "/";
    }

    static void validateRemoteName(String name, boolean pattern) {
        requireText(name, "remoteFile");
        if (name.startsWith("/") || name.startsWith("\\")
                || name.contains("/") || name.contains("\\")
                || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException(
                    "remoteFile must be a name or pattern without path separators");
        }
        if (name.chars().anyMatch(character -> character < 0x20 || character == 0x7f)) {
            throw new IllegalArgumentException("remoteFile contains control characters");
        }
        if (!pattern && (name.contains("*") || name.contains("?"))) {
            throw new IllegalArgumentException("remoteFile contains a wildcard");
        }
    }

    static boolean containsWildcard(String value) {
        return value.contains("*") || value.contains("?");
    }

    private static Pattern wildcardPattern(String wildcard) {
        StringBuilder regex = new StringBuilder();
        for (char character : wildcard.toCharArray()) {
            if (character == '*') {
                regex.append(".*");
            } else if (character == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(character)));
            }
        }
        return Pattern.compile(regex.toString());
    }

    private static long copy(InputStream input, OutputStream output,
                             OptionalLong total, OperationContext context)
            throws IOException {
        CancellationToken cancellation = context.cancellation();
        byte[] buffer = new byte[8192];
        long copied = 0;
        while (true) {
            cancellation.throwIfCancellationRequested();
            if (context.budget().expired()) {
                throw new FtpTaskException("FTP_TIMEOUT",
                        msg(context, "task.ftp.timeout"), "transfer", null);
            }
            int read = input.read(buffer);
            if (read < 0) {
                return copied;
            }
            output.write(buffer, 0, read);
            copied += read;
            context.progress(new Progress("ftp", copied, total, "bytes"));
        }
    }

    private static HostAndPort parseServer(String server) {
        try {
            int colon = server.lastIndexOf(':');
            if (colon > 0 && colon < server.length() - 1
                    && server.indexOf(']') < colon) {
                String port = server.substring(colon + 1);
                if (port.chars().allMatch(Character::isDigit)) {
                    return new HostAndPort(server.substring(0, colon),
                            Integer.parseInt(port));
                }
            }
            return new HostAndPort(server, 21);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("server is not a valid host[:port]", exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void disconnectQuietly(FTPClient client) {
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    private static String msg(OperationContext context, String key, Object... args) {
        return Messages.format(context.locale(), key, args);
    }

    record FtpSession(FTPClient client, String separator) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            if (!client.isConnected()) {
                return;
            }
            try {
                client.logout();
            } catch (IOException ignored) {
                // The disconnect below is the authoritative cleanup.
            }
            client.disconnect();
        }
    }

    private record HostAndPort(String host, int port) {
    }
}
