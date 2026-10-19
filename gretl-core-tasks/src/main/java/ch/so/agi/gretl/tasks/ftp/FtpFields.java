package ch.so.agi.gretl.tasks.ftp;

import ch.so.agi.gretl.runtime.api.ConfigBuilder;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.SecretValue;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Shared metadata and conversion helpers for the FTP task family. */
final class FtpFields {
    private FtpFields() {
    }

    static <C> List<InputField<C, ?>> commonInputs() {
        InputField<C, String> server = new InputField<>(
                "server", "text", "FTP server host, optionally as host:port", true,
                Optional.empty(), List.of(), "server = 'ftp.example.ch'",
                FtpFields::text,
                (builder, value) -> ((BaseFtpBuilder<C>) builder).setServer(value));
        InputField<C, String> user = new InputField<>(
                "user", "text", "FTP username", true, Optional.empty(), List.of(),
                "user = 'ftp-user'", FtpFields::text,
                (builder, value) -> ((BaseFtpBuilder<C>) builder).setUser(value));
        InputField<C, SecretValue> password = new InputField<>(
                "password", "secret", "FTP password from a secret alias", true,
                Optional.empty(), List.of(), "password = secret('ftpPassword')",
                FtpFields::secret,
                (builder, value) -> ((BaseFtpBuilder<C>) builder).setPassword(value));
        InputField<C, String> systemType = new InputField<>(
                "systemType", "text", "FTP server system type", false,
                Optional.of(FtpSupport.DEFAULT_SYSTEM_TYPE), List.of(),
                "systemType = 'UNIX'", FtpFields::text,
                (builder, value) -> ((BaseFtpBuilder<C>) builder).setSystemType(value));
        InputField<C, String> fileSeparator = new InputField<>(
                "fileSeparator", "text", "Remote path separator", false,
                Optional.empty(), List.of(), "fileSeparator = '/'",
                FtpFields::text,
                (builder, value) -> ((BaseFtpBuilder<C>) builder).setFileSeparator(value));
        InputField<C, Boolean> passiveMode = new InputField<>(
                "passiveMode", "boolean", "Use passive FTP mode", false,
                Optional.of(true), List.of(), "passiveMode = true",
                FtpFields::bool,
                (builder, value) -> ((BaseFtpBuilder<C>) builder).setPassiveMode(value));
        InputField<C, Long> keepAlive = new InputField<>(
                "controlKeepAliveTimeout", "integer",
                "Control keep-alive timeout in seconds; 0 disables it", false,
                Optional.of(FtpSupport.DEFAULT_KEEPALIVE_SECONDS), List.of(),
                "controlKeepAliveTimeout = 300", FtpFields::nonNegativeLong,
                (builder, value) -> ((BaseFtpBuilder<C>) builder)
                        .setControlKeepAliveTimeout(value));
        return List.of(server, user, password, systemType, fileSeparator,
                passiveMode, keepAlive);
    }

    static Boolean bool(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new IllegalArgumentException("Boolean value expected");
    }

    static String text(Object value) {
        if (!(value instanceof CharSequence text)) {
            throw new IllegalArgumentException("Text value expected");
        }
        String result = text.toString();
        if (result.isBlank()) {
            throw new IllegalArgumentException("Text value must not be blank");
        }
        return result;
    }

    static SecretValue secret(Object value) {
        if (value instanceof SecretValue secret) {
            return secret;
        }
        throw new IllegalArgumentException(
                "password requires a SecretValue from secret('alias')");
    }

    static Long nonNegativeLong(Object value) {
        long result;
        if (value instanceof Number number) {
            result = number.longValue();
        } else if (value instanceof CharSequence text
                && text.toString().matches("[0-9]+")) {
            try {
                result = Long.parseLong(text.toString());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Value is out of range", exception);
            }
        } else {
            throw new IllegalArgumentException("Non-negative integer expected");
        }
        if (result < 0) {
            throw new IllegalArgumentException("Value must not be negative");
        }
        return result;
    }

    static String fileType(Object value) {
        String fileType = text(value).toUpperCase(Locale.ROOT);
        if (!fileType.equals("ASCII") && !fileType.equals("BINARY")) {
            throw new IllegalArgumentException("fileType must be ASCII or BINARY");
        }
        return fileType;
    }

    static Path path(Object value) {
        Path path;
        if (value instanceof Path direct) {
            path = direct;
        } else if (value instanceof CharSequence text) {
            path = Path.of(text.toString());
        } else {
            throw new IllegalArgumentException("Path must be a Path or text");
        }
        if (path.toString().isBlank()) {
            throw new IllegalArgumentException("Path must not be blank");
        }
        return path.normalize();
    }

    static List<String> remoteNames(Object value) {
        List<Object> values = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(values::add);
        } else if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                values.add(java.lang.reflect.Array.get(value, index));
            }
        } else {
            throw new IllegalArgumentException("remoteFiles must be a list of names");
        }
        List<String> names = new ArrayList<>(values.size());
        for (Object element : values) {
            String name = text(element);
            FtpSupport.validateRemoteName(name, true);
            if (!FtpSupport.containsWildcard(name)) {
                FtpSupport.validateRemoteName(name, false);
            }
            names.add(name);
        }
        return List.copyOf(names);
    }

    abstract static class BaseFtpBuilder<C> implements ConfigBuilder<C> {
        private String server;
        private String user;
        private SecretValue password;
        private String systemType = FtpSupport.DEFAULT_SYSTEM_TYPE;
        private String fileSeparator;
        private boolean passiveMode = true;
        private long controlKeepAliveTimeout = FtpSupport.DEFAULT_KEEPALIVE_SECONDS;

        public void setServer(String value) { server = value; }
        public void setUser(String value) { user = value; }
        public void setPassword(SecretValue value) { password = value; }
        public void setSystemType(String value) { systemType = value; }
        public void setFileSeparator(String value) { fileSeparator = value; }
        public void setPassiveMode(Boolean value) { passiveMode = Boolean.TRUE.equals(value); }
        public void setControlKeepAliveTimeout(Long value) {
            controlKeepAliveTimeout = value == null
                    ? FtpSupport.DEFAULT_KEEPALIVE_SECONDS : value;
        }

        protected FtpConnection connection() {
            if (server == null || server.isBlank()) {
                throw new IllegalArgumentException("server is missing");
            }
            if (user == null || user.isBlank()) {
                throw new IllegalArgumentException("user is missing");
            }
            if (password == null) {
                throw new IllegalArgumentException("password is missing");
            }
            if (controlKeepAliveTimeout < 0) {
                throw new IllegalArgumentException(
                        "controlKeepAliveTimeout must not be negative");
            }
            return new FtpConnection(server, user, password,
                    systemType == null || systemType.isBlank()
                            ? FtpSupport.DEFAULT_SYSTEM_TYPE : systemType,
                    fileSeparator, passiveMode, controlKeepAliveTimeout);
        }

        protected static String requiredRemoteDir(String value) {
            Objects.requireNonNull(value, "remoteDir");
            if (value.isBlank()) {
                throw new IllegalArgumentException("remoteDir must not be blank");
            }
            return value;
        }
    }
}
