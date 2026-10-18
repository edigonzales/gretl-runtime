package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.runtime.api.CancellationToken;
import ch.so.agi.gretl.runtime.api.ConfigBuilder;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import ch.so.agi.gretl.runtime.api.Progress;
import ch.so.agi.gretl.runtime.api.SecretValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** HttpDownload task: GET/POST, no redirects, bounded streamed target file. */
public final class HttpDownloadOperation {
    public static final String ID = "gretl.http-download";
    public static final String SYMBOL = "HttpDownload";
    public static final long DEFAULT_MAX_BYTES = 1L << 30;

    private HttpDownloadOperation() {
    }

    public static OperationType<HttpDownloadRequest, HttpDownloadResult> type() {
        InputField<HttpDownloadRequest, URI> url = new InputField<>(
                "url", "uri", "Absolute HTTP/HTTPS URL", true, Optional.empty(),
                List.of("http", "https"), "url = 'https://example.invalid/data.zip'",
                HttpDownloadOperation::uri,
                (builder, value) -> ((HttpDownloadBuilder) builder).setUrl(value));
        InputField<HttpDownloadRequest, Path> output = new InputField<>(
                "output", "path", "Target file for the completed download", true,
                Optional.empty(), List.of(), "output = workPath('data.zip')",
                HttpDownloadOperation::path,
                (builder, value) -> ((HttpDownloadBuilder) builder).setOutput(value));
        InputField<HttpDownloadRequest, Boolean> overwrite = new InputField<>(
                "overwrite", "boolean", "Replace an existing target file after a successful download",
                false, Optional.of(false), List.of(), "overwrite = true",
                HttpDownloadOperation::bool,
                (builder, value) -> ((HttpDownloadBuilder) builder).setOverwrite(value));
        InputField<HttpDownloadRequest, String> method = new InputField<>(
                "method", "text", "GET oder POST", false, Optional.of("GET"),
                List.of("GET", "POST"), "method = 'GET'",
                HttpDownloadOperation::method,
                (builder, value) -> ((HttpDownloadBuilder) builder).setMethod(value));
        InputField<HttpDownloadRequest, Map<String, HeaderValue>> headers = new InputField<>(
                "headers", "map", "HTTP headers; values as text or SecretValue", false,
                Optional.of(Map.of()), List.of(),
                "headers = ['Accept': 'application/zip']",
                HttpDownloadOperation::headers,
                (builder, value) -> ((HttpDownloadBuilder) builder).setHeaders(value));
        InputField<HttpDownloadRequest, String> bodyText = new InputField<>(
                "bodyText", "text", "Optionaler UTF-8-Body nur fuer POST", false,
                Optional.empty(), List.of(), "bodyText = 'grant_type=client_credentials'",
                HttpDownloadOperation::text,
                (builder, value) -> ((HttpDownloadBuilder) builder).setBodyText(value));
        InputField<HttpDownloadRequest, Long> maxBytes = new InputField<>(
                "maxBytes", "integer", "Maximum number of bytes read", false,
                Optional.of(DEFAULT_MAX_BYTES), List.of(), "maxBytes = 1048576L",
                HttpDownloadOperation::positiveLong,
                (builder, value) -> ((HttpDownloadBuilder) builder).setMaxBytes(value));
        OutputField<HttpDownloadResult, Path> file = Fields.output(
                "file", "path", "Completed file", false, HttpDownloadResult::file);
        OutputField<HttpDownloadResult, URI> finalUri = Fields.output(
                "finalUri", "uri", "URI actually requested", false,
                HttpDownloadResult::finalUri);
        OutputField<HttpDownloadResult, Integer> status = Fields.output(
                "status", "integer", "HTTP status", false, HttpDownloadResult::status);
        OutputField<HttpDownloadResult, Long> bytes = Fields.output(
                "bytes", "integer", "Bytes actually read", false,
                HttpDownloadResult::bytes);
        OutputField<HttpDownloadResult, String> sha256 = Fields.output(
                "sha256", "text", "SHA-256 of the file", false, HttpDownloadResult::sha256);
        return new OperationType<>(
                ID, SYMBOL, "Download a file over HTTP",
                HttpDownloadRequest.class, HttpDownloadResult.class,
                HttpDownloadBuilder::new, HttpDownloadHandler::new,
                List.of(url, output, overwrite, method, headers, bodyText, maxBytes),
                List.of(file, finalUri, status, bytes, sha256),
                """
                task('download', HttpDownload) {
                    url = 'https://example.invalid/data.zip'
                    output = workPath('data.zip')
                }
                """);
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new IllegalArgumentException("overwrite must be true or false");
    }

    private static URI uri(Object value) {
        URI uri;
        try {
            if (value instanceof URI direct) {
                uri = direct;
            } else if (value instanceof CharSequence text) {
                uri = URI.create(text.toString());
            } else {
                throw new IllegalArgumentException("url must be a URI or text");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("url is not a valid URI", exception);
        }
        if (!uri.isAbsolute() || uri.getHost() == null
                || !(uri.getScheme().equalsIgnoreCase("http")
                || uri.getScheme().equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("url must be an absolute HTTP/HTTPS address");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("url darf keine Userinfo enthalten");
        }
        return uri;
    }

    private static Path path(Object value) {
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

    private static String method(Object value) {
        if (!(value instanceof CharSequence text)) {
            throw new IllegalArgumentException("method must be text");
        }
        String method = text.toString().toUpperCase(Locale.ROOT);
        if (!method.equals("GET") && !method.equals("POST")) {
            throw new IllegalArgumentException("method must be GET or POST");
        }
        return method;
    }

    private static String text(Object value) {
        if (!(value instanceof CharSequence text)) {
            throw new IllegalArgumentException("Textwert erwartet");
        }
        return text.toString();
    }

    private static Long positiveLong(Object value) {
        long result;
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            result = ((Number) value).longValue();
        } else if (value instanceof CharSequence text && text.toString().matches("[0-9]+")) {
            try {
                result = Long.parseLong(text.toString());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("maxBytes liegt ausserhalb des Long-Bereichs");
            }
        } else {
            throw new IllegalArgumentException("maxBytes must be a positive integer");
        }
        if (result <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        return result;
    }

    private static Map<String, HeaderValue> headers(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("headers must be a map");
        }
        Map<String, HeaderValue> result = new LinkedHashMap<>();
        Map<String, String> seen = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof CharSequence nameText)) {
                throw new IllegalArgumentException("Header name must be text");
            }
            String name = nameText.toString();
            if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) {
                throw new IllegalArgumentException("Ungueltiger HTTP-Headername: " + name);
            }
            String key = name.toLowerCase(Locale.ROOT);
            if (seen.putIfAbsent(key, name) != null) {
                throw new IllegalArgumentException(
                        "Header " + name + " is duplicated (case-insensitive)");
            }
            Object raw = entry.getValue();
            HeaderValue header;
            if (raw instanceof SecretValue secret) {
                header = new HeaderValue(name, null, secret);
            } else if (raw instanceof CharSequence text) {
                String valueText = text.toString();
                if (valueText.chars().anyMatch(character -> character < 0x20 || character == 0x7f)) {
                    throw new IllegalArgumentException(
                            "Header value for " + name + " contains control characters");
                }
                header = new HeaderValue(name, valueText, null);
            } else {
                throw new IllegalArgumentException(
                        "Header value for " + name + " must be text or SecretValue");
            }
            result.put(name, header);
        }
        return Map.copyOf(result);
    }

    /** Header value that keeps secret material lazy until the actual request. */
    public record HeaderValue(String name, String text, SecretValue secret) {
        public boolean isSecret() {
            return secret != null;
        }

        public String reveal(OperationContext context) {
            if (secret != null) {
                return context.sensitive(secret.reveal());
            }
            return text;
        }

        @Override
        public String toString() {
            return name + ": " + (secret == null ? text : "[REDACTED]");
        }
    }

    public record HttpDownloadRequest(
            URI url, Path output, boolean overwrite, String method,
            Map<String, HeaderValue> headers, String bodyText, long maxBytes) {
        public HttpDownloadRequest {
            java.util.Objects.requireNonNull(url, "url");
            java.util.Objects.requireNonNull(output, "output");
            java.util.Objects.requireNonNull(method, "method");
            headers = Map.copyOf(headers);
            if (maxBytes <= 0) {
                throw new IllegalArgumentException("maxBytes must be positive");
            }
        }
    }

    public static final class HttpDownloadBuilder implements ConfigBuilder<HttpDownloadRequest> {
        private URI url;
        private Path output;
        private boolean overwrite;
        private String method = "GET";
        private Map<String, HeaderValue> headers = Map.of();
        private String bodyText;
        private long maxBytes = DEFAULT_MAX_BYTES;

        public void setUrl(URI value) { url = value; }
        public void setOutput(Path value) { output = value; }
        public void setOverwrite(Boolean value) { overwrite = Boolean.TRUE.equals(value); }
        public void setMethod(String value) { method = value; }
        public void setHeaders(Map<String, HeaderValue> value) { headers = value; }
        public void setBodyText(String value) { bodyText = value; }
        public void setMaxBytes(Long value) { maxBytes = value; }

        @Override
        public HttpDownloadRequest build() {
            if (url == null) {
                throw new IllegalArgumentException("url is missing");
            }
            url = uri(url);
            if (output == null) {
                throw new IllegalArgumentException("output is missing");
            }
            method = method(method);
            if (method.equals("GET") && bodyText != null) {
                throw new IllegalArgumentException("bodyText ist nur fuer POST zulaessig");
            }
            return new HttpDownloadRequest(url, output, overwrite, method, headers, bodyText,
                    maxBytes);
        }
    }

    private static String msg(OperationContext context, String key, Object... args) {
        return Messages.format(context.locale(), key, args);
    }

    static final class HttpDownloadHandler
            implements ch.so.agi.gretl.runtime.api.OperationHandler<
                    HttpDownloadRequest, HttpDownloadResult> {
        @Override
        public HttpDownloadResult execute(HttpDownloadRequest request, OperationContext context)
                throws Exception {
            CancellationToken cancellation = context.cancellation();
            cancellation.throwIfCancellationRequested();
            Path target = request.output().toAbsolutePath().normalize();
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!request.overwrite()) {
                    throw new HttpTaskException("HTTP_TARGET_EXISTS",
                            msg(context, "task.http.targetExists"),
                            request.method(), request.url(), 0, 0, "prepare", null);
                }
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new HttpTaskException("HTTP_TARGET_UNSAFE",
                            msg(context, "task.http.targetUnsafe"),
                            request.method(), request.url(), 0, 0, "prepare", null);
                }
            }
            Path parent = target.getParent();
            if (parent == null) {
                throw new HttpTaskException("HTTP_TARGET_INVALID",
                        msg(context, "task.http.targetInvalid"), request.method(),
                        request.url(), 0, 0, "prepare", null);
            }
            Files.createDirectories(parent);

            HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER);
            Duration remaining = context.budget().remaining().orElse(null);
            if (remaining != null) {
                if (remaining.isZero() || remaining.isNegative()) {
                    throw new HttpTaskException("HTTP_TIMEOUT", msg(context, "task.http.timeout"),
                            request.method(), request.url(), 0, 0, "connect", null);
                }
                clientBuilder.connectTimeout(remaining);
            }
            HttpClient client = clientBuilder.build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(request.url());
            request.headers().forEach((name, header) ->
                    builder.header(name, header.reveal(context)));
            if (remaining != null) {
                builder.timeout(remaining);
            }
            if (request.method().equals("POST")) {
                builder.POST(HttpRequest.BodyPublishers.ofString(
                        request.bodyText() == null ? "" : request.bodyText(),
                        java.nio.charset.StandardCharsets.UTF_8));
            } else {
                builder.GET();
            }
            HttpRequest httpRequest;
            try {
                httpRequest = builder.build();
            } catch (IllegalArgumentException exception) {
                throw new HttpTaskException("HTTP_HEADER_INVALID",
                        msg(context, "task.http.requestInvalid"), request.method(), request.url(),
                        0, 0, "configuration", exception);
            }

            Path temporary = Files.createTempFile(parent,
                    "." + target.getFileName() + ".", ".part");
            long bytes = 0;
            try {
                HttpResponse<InputStream> response;
                try {
                    response = client.send(httpRequest,
                            HttpResponse.BodyHandlers.ofInputStream());
                } catch (IOException | InterruptedException exception) {
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    throw new HttpTaskException("HTTP_TRANSPORT",
                            msg(context, "task.http.transport"), request.method(), request.url(),
                            0, bytes, "connect", exception);
                }
                int status = response.statusCode();
                if (status < 200 || status > 299) {
                    response.body().close();
                    // Deliberately do not read/log an error response body.
                    throw new HttpTaskException("HTTP_STATUS",
                            msg(context, "task.http.status"), request.method(),
                            request.url(), status, bytes, "execute", null);
                }
                MessageDigest digest = sha256();
                try (InputStream input = response.body();
                     OutputStream output = Files.newOutputStream(temporary)) {
                    byte[] buffer = new byte[8192];
                    while (true) {
                        cancellation.throwIfCancellationRequested();
                        int read = input.read(buffer);
                        if (read < 0) {
                            break;
                        }
                        bytes += read;
                        if (bytes > request.maxBytes()) {
                            throw new HttpTaskException("HTTP_LIMIT",
                                    msg(context, "task.http.limit"), request.method(),
                                    request.url(), status, bytes, "execute", null);
                        }
                        output.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                        if (bytes == read || bytes % (1024 * 1024) < read) {
                            context.progress(new Progress("download", bytes,
                                    OptionalLong.empty(), "bytes"));
                        }
                    }
                }
                try {
                    moveIntoPlace(temporary, target, request.overwrite());
                } catch (java.nio.file.FileAlreadyExistsException exception) {
                    throw new HttpTaskException("HTTP_TARGET_EXISTS",
                            msg(context, "task.http.targetCreated"),
                            request.method(), request.url(), status, bytes, "publish", exception);
                }
                String sha = HexFormat.of().formatHex(digest.digest());
                context.log().log(LogLevel.INFO, "gretl.http.download",
                        msg(context, "task.http.finished"),
                        Map.of("http.method", request.method(),
                                "http.origin", HttpTaskException.origin(request.url()),
                                "http.status", Integer.toString(status),
                                "http.bytes", Long.toString(bytes),
                                "http.overwrite", Boolean.toString(request.overwrite())), null);
                return new HttpDownloadResult(target, response.uri(), status, bytes, sha);
            } catch (HttpTaskException exception) {
                deleteQuietly(temporary, exception);
                context.log().log(LogLevel.WARN, "gretl.http.failed",
                        msg(context, "task.http.failed"),
                        Map.of("http.method", request.method(),
                                "http.origin", HttpTaskException.origin(request.url()),
                                "http.status", Integer.toString(exception.status()),
                                "http.bytes", Long.toString(exception.bytes()),
                                "http.phase", exception.phase()), exception);
                throw exception;
            } catch (IOException exception) {
                HttpTaskException wrapped = new HttpTaskException("HTTP_IO",
                        msg(context, "task.http.io"), request.method(), request.url(),
                        0, bytes, "execute", exception);
                deleteQuietly(temporary, wrapped);
                throw wrapped;
            } catch (RuntimeException exception) {
                deleteQuietly(temporary, exception);
                throw exception;
            }
        }

        private static void moveIntoPlace(Path temporary, Path target, boolean overwrite)
                throws IOException {
            StandardCopyOption[] atomic = overwrite
                    ? new StandardCopyOption[] {StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING}
                    : new StandardCopyOption[] {StandardCopyOption.ATOMIC_MOVE};
            try {
                Files.move(temporary, target, atomic);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                if (overwrite) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temporary, target);
                }
            }
        }

        private static MessageDigest sha256() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 unavailable", exception);
            }
        }

        private static void deleteQuietly(Path path, Throwable primary) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                primary.addSuppressed(exception);
            }
        }
    }
}
