package ch.so.agi.gretl.tasks.io.http;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.CancellationToken;
import ch.so.agi.gretl.runtime.api.ConfigBuilder;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import ch.so.agi.gretl.runtime.api.SecretValue;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Curl task: one HTTP request with explicit response assertions. */
public final class CurlOperation {
    public static final String ID = "gretl.curl";
    public static final String SYMBOL = "Curl";
    public static final long DEFAULT_MAX_BYTES = 16L * 1024 * 1024;

    private CurlOperation() {
    }

    public static OperationType<CurlRequest, CurlResult> type() {
        InputField<CurlRequest, URI> serverUrl = new InputField<>(
                "serverUrl", "uri", "Absolute HTTP/HTTPS URL", true, Optional.empty(),
                List.of("http", "https"),
                "serverUrl = 'https://example.invalid/token'",
                CurlOperation::uri,
                (builder, value) -> ((CurlBuilder) builder).setServerUrl(value));
        InputField<CurlRequest, String> method = new InputField<>(
                "method", "text", "GET or POST; inferred from the configured body",
                false, Optional.empty(), List.of("GET", "POST"),
                "method = 'POST'", CurlOperation::method,
                (builder, value) -> ((CurlBuilder) builder).setMethod(value));
        InputField<CurlRequest, Integer> expectedStatusCode = new InputField<>(
                "expectedStatusCode", "integer", "Exact expected HTTP status code",
                true, Optional.empty(), List.of(), "expectedStatusCode = 200",
                CurlOperation::statusCode,
                (builder, value) -> ((CurlBuilder) builder).setExpectedStatusCode(value));
        InputField<CurlRequest, String> expectedBody = new InputField<>(
                "expectedBody", "text", "Text that must occur in the UTF-8 response body",
                false, Optional.empty(), List.of(), "expectedBody = '\"success\":true'",
                CurlOperation::expectedBody,
                (builder, value) -> ((CurlBuilder) builder).setExpectedBody(value));
        InputField<CurlRequest, Map<String, HeaderValue>> headers = new InputField<>(
                "headers", "map", "HTTP headers; values as text or SecretValue", false,
                Optional.of(Map.of()), List.of(),
                "headers = ['Content-Type': 'application/json']",
                CurlOperation::headers,
                (builder, value) -> ((CurlBuilder) builder).setHeaders(value));
        InputField<CurlRequest, Map<String, FormValue>> formData = new InputField<>(
                "formData", "map", "Multipart form fields; values as text or file paths",
                false, Optional.of(Map.of()), List.of(),
                "formData = [topic: 'npl', file: file('data.xtf.zip')]",
                CurlOperation::formData,
                (builder, value) -> ((CurlBuilder) builder).setFormData(value));
        InputField<CurlRequest, String> data = new InputField<>(
                "data", "text", "Optional UTF-8 text request body", false,
                Optional.empty(), List.of(), "data = 'grant_type=client_credentials'",
                CurlOperation::text,
                (builder, value) -> ((CurlBuilder) builder).setData(value));
        InputField<CurlRequest, Path> dataBinary = new InputField<>(
                "dataBinary", "path", "Optional binary request body file", false,
                Optional.empty(), List.of(), "dataBinary = file('request.bin')",
                CurlOperation::path,
                (builder, value) -> ((CurlBuilder) builder).setDataBinary(value));
        InputField<CurlRequest, Path> outputFile = new InputField<>(
                "outputFile", "path", "Optional file receiving the response body", false,
                Optional.empty(), List.of(), "outputFile = workPath('response.json')",
                CurlOperation::path,
                (builder, value) -> ((CurlBuilder) builder).setOutputFile(value));
        InputField<CurlRequest, Boolean> overwrite = new InputField<>(
                "overwrite", "boolean", "Replace an existing output file after a successful response",
                false, Optional.of(false), List.of(), "overwrite = true",
                CurlOperation::bool,
                (builder, value) -> ((CurlBuilder) builder).setOverwrite(value));
        InputField<CurlRequest, String> user = new InputField<>(
                "user", "text", "Basic-auth username", false, Optional.empty(), List.of(),
                "user = 'api-user'", CurlOperation::text,
                (builder, value) -> ((CurlBuilder) builder).setUser(value));
        InputField<CurlRequest, SecretValue> password = new InputField<>(
                "password", "secret", "Basic-auth password from a secret alias", false,
                Optional.empty(), List.of(), "password = secret('apiPassword')",
                CurlOperation::secret,
                (builder, value) -> ((CurlBuilder) builder).setPassword(value));
        InputField<CurlRequest, Long> maxBytes = new InputField<>(
                "maxBytes", "integer", "Maximum response body bytes", false,
                Optional.of(DEFAULT_MAX_BYTES), List.of(), "maxBytes = 1048576L",
                CurlOperation::positiveLong,
                (builder, value) -> ((CurlBuilder) builder).setMaxBytes(value));
        OutputField<CurlResult, Integer> status = Fields.output(
                "status", "integer", "HTTP status", false, CurlResult::status);
        OutputField<CurlResult, Long> bytes = Fields.output(
                "bytes", "integer", "Response body bytes", false, CurlResult::bytes);
        OutputField<CurlResult, Path> output = Fields.output(
                "outputFile", "path", "Written response body, otherwise null", true,
                CurlResult::outputFile);
        return new OperationType<>(
                ID, SYMBOL, "Execute an HTTP request and validate the response",
                CurlRequest.class, CurlResult.class,
                CurlBuilder::new, CurlHandler::new,
                List.of(serverUrl, method, expectedStatusCode, expectedBody, headers,
                        formData, data, dataBinary, outputFile, overwrite, user, password,
                        maxBytes),
                List.of(status, bytes, output),
                """
                task('token', Curl) {
                    serverUrl = 'https://example.invalid/token'
                    method = 'POST'
                    expectedStatusCode = 200
                    data = 'grant_type=client_credentials'
                    outputFile = workPath('token.json')
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
                throw new IllegalArgumentException("serverUrl must be a URI or text");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("serverUrl is not a valid URI", exception);
        }
        if (!uri.isAbsolute() || uri.getHost() == null
                || !(uri.getScheme().equalsIgnoreCase("http")
                || uri.getScheme().equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("serverUrl must be an absolute HTTP/HTTPS address");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("serverUrl must not contain userinfo");
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

    private static String text(Object value) {
        if (!(value instanceof CharSequence text)) {
            throw new IllegalArgumentException("Textwert erwartet");
        }
        return text.toString();
    }

    private static String method(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof CharSequence text)) {
            throw new IllegalArgumentException("method must be text");
        }
        String method = text.toString().toUpperCase(Locale.ROOT);
        if (!method.equals("GET") && !method.equals("POST")) {
            throw new IllegalArgumentException("method must be GET or POST");
        }
        return method;
    }

    private static Integer statusCode(Object value) {
        int status;
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            status = ((Number) value).intValue();
        } else if (value instanceof CharSequence text && text.toString().matches("[0-9]+")) {
            try {
                status = Integer.parseInt(text.toString());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("expectedStatusCode is out of range");
            }
        } else {
            throw new IllegalArgumentException("expectedStatusCode must be an integer");
        }
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("expectedStatusCode must be between 100 and 599");
        }
        return status;
    }

    private static String expectedBody(Object value) {
        String text = text(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException("expectedBody must not be blank");
        }
        return text;
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
                throw new IllegalArgumentException("maxBytes is out of range");
            }
        } else {
            throw new IllegalArgumentException("maxBytes must be a positive integer");
        }
        if (result <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        return result;
    }

    private static SecretValue secret(Object value) {
        if (value instanceof SecretValue secret) {
            return secret;
        }
        throw new IllegalArgumentException(
                "password requires a SecretValue from secret('alias')");
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
                throw new IllegalArgumentException("Invalid HTTP header name: " + name);
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
                if (valueText.chars().anyMatch(character ->
                        character < 0x20 || character == 0x7f)) {
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

    private static Map<String, FormValue> formData(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("formData must be a map");
        }
        Map<String, FormValue> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof CharSequence nameText)) {
                throw new IllegalArgumentException("formData field name must be text");
            }
            String name = nameText.toString();
            if (!name.matches("[A-Za-z0-9_.-]+")) {
                throw new IllegalArgumentException("Invalid formData field name: " + name);
            }
            Object raw = entry.getValue();
            if (raw instanceof Path path) {
                result.put(name, FormValue.ofFile(path.normalize()));
            } else if (raw instanceof File file) {
                result.put(name, FormValue.ofFile(file.toPath().normalize()));
            } else if (raw instanceof CharSequence text) {
                result.put(name, FormValue.ofText(text.toString()));
            } else {
                throw new IllegalArgumentException(
                        "formData value for " + name + " must be text, Path or File");
            }
        }
        return Map.copyOf(result);
    }

    private static boolean hasHeader(Map<String, HeaderValue> headers, String name) {
        return headers.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name));
    }

    public record CurlRequest(
            URI serverUrl, String method, int expectedStatusCode, String expectedBody,
            Map<String, HeaderValue> headers, Map<String, FormValue> formData,
            String data, Path dataBinary, Path outputFile, boolean overwrite,
            String user, SecretValue password, long maxBytes) {
        public CurlRequest {
            Objects.requireNonNull(serverUrl, "serverUrl");
            Objects.requireNonNull(method, "method");
            headers = Map.copyOf(headers);
            formData = Map.copyOf(formData);
            if (expectedStatusCode < 100 || expectedStatusCode > 599) {
                throw new IllegalArgumentException(
                        "expectedStatusCode must be between 100 and 599");
            }
            if (maxBytes <= 0) {
                throw new IllegalArgumentException("maxBytes must be positive");
            }
        }
    }

    public record CurlResult(int status, long bytes, Path outputFile) {
    }

    /** Header value with lazy secret material. */
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

    /** One multipart form value: either text or a file. */
    public record FormValue(String text, Path file) {
        public FormValue {
            if ((text == null) == (file == null)) {
                throw new IllegalArgumentException(
                        "FormValue must contain exactly one of text or file");
            }
        }

        public static FormValue ofText(String value) {
            return new FormValue(Objects.requireNonNull(value, "text"), null);
        }

        public static FormValue ofFile(Path value) {
            return new FormValue(null, Objects.requireNonNull(value, "file"));
        }

        public boolean isFile() {
            return file != null;
        }
    }

    public static final class CurlBuilder implements ConfigBuilder<CurlRequest> {
        private URI serverUrl;
        private String method;
        private Integer expectedStatusCode;
        private String expectedBody;
        private Map<String, HeaderValue> headers = Map.of();
        private Map<String, FormValue> formData = Map.of();
        private String data;
        private Path dataBinary;
        private Path outputFile;
        private boolean overwrite;
        private String user;
        private SecretValue password;
        private long maxBytes = DEFAULT_MAX_BYTES;

        public CurlBuilder setServerUrl(URI value) { serverUrl = value; return this; }
        public CurlBuilder setMethod(String value) { method = value; return this; }
        public CurlBuilder setExpectedStatusCode(Integer value) { expectedStatusCode = value; return this; }
        public CurlBuilder setExpectedBody(String value) { expectedBody = value; return this; }
        public CurlBuilder setHeaders(Map<String, HeaderValue> value) { headers = value == null ? Map.of() : value; return this; }
        public CurlBuilder setFormData(Map<String, FormValue> value) { formData = value == null ? Map.of() : value; return this; }
        public CurlBuilder setData(String value) { data = value; return this; }
        public CurlBuilder setDataBinary(Path value) { dataBinary = value; return this; }
        public CurlBuilder setOutputFile(Path value) { outputFile = value; return this; }
        public CurlBuilder setOverwrite(Boolean value) { overwrite = Boolean.TRUE.equals(value); return this; }
        public CurlBuilder setUser(String value) { user = value; return this; }
        public CurlBuilder setPassword(SecretValue value) { password = value; return this; }
        public CurlBuilder setMaxBytes(Long value) { maxBytes = value; return this; }

        @Override
        public CurlRequest build() {
            if (serverUrl == null) {
                throw new IllegalArgumentException("serverUrl is missing");
            }
            if (expectedStatusCode == null) {
                throw new IllegalArgumentException("expectedStatusCode is missing");
            }
            if (expectedBody != null && expectedBody.isBlank()) {
                throw new IllegalArgumentException("expectedBody must not be blank");
            }
            int bodies = (data != null ? 1 : 0) + (dataBinary != null ? 1 : 0)
                    + (!formData.isEmpty() ? 1 : 0);
            if (bodies > 1) {
                throw new IllegalArgumentException(
                        "Use only one of formData, data or dataBinary");
            }
            boolean hasBody = bodies > 0;
            String resolvedMethod = method == null ? (hasBody ? "POST" : "GET") : method;
            if (resolvedMethod.equals("GET") && hasBody) {
                throw new IllegalArgumentException("GET must not have a request body");
            }
            boolean hasUser = user != null && !user.isBlank();
            boolean hasPassword = password != null;
            if (hasUser != hasPassword) {
                throw new IllegalArgumentException(
                        "user and password must be configured together");
            }
            if (hasUser && hasHeader(headers, "Authorization")) {
                throw new IllegalArgumentException(
                        "Use either basic auth or an Authorization header");
            }
            Map<String, FormValue> resolvedFormData = new LinkedHashMap<>();
            for (Map.Entry<String, FormValue> entry : formData.entrySet()) {
                FormValue value = entry.getValue();
                resolvedFormData.put(entry.getKey(), value.isFile()
                        ? FormValue.ofFile(value.file().toAbsolutePath().normalize())
                        : value);
            }
            Path resolvedDataBinary = dataBinary == null ? null
                    : dataBinary.toAbsolutePath().normalize();
            Path resolvedOutputFile = outputFile == null ? null
                    : outputFile.toAbsolutePath().normalize();
            return new CurlRequest(serverUrl, resolvedMethod, expectedStatusCode,
                    expectedBody, headers, resolvedFormData, data, resolvedDataBinary,
                    resolvedOutputFile, overwrite, hasUser ? user : null, password,
                    maxBytes);
        }
    }

    static final class CurlHandler implements OperationHandler<CurlRequest, CurlResult> {
        @Override
        public CurlResult execute(CurlRequest request, OperationContext context)
                throws Exception {
            CancellationToken cancellation = context.cancellation();
            cancellation.throwIfCancellationRequested();

            Path target = request.outputFile() == null ? null
                    : request.outputFile().toAbsolutePath().normalize();
            if (target != null) {
                prepareTarget(target, request.overwrite(), context);
            }
            validateBodyFiles(request, context);

            Duration remaining = context.budget().remaining().orElse(null);
            if (remaining != null && (remaining.isZero() || remaining.isNegative())) {
                throw new CurlTaskException("CURL_TIMEOUT",
                        msg(context, "task.curl.timeout"), 0, 0, "connect", null);
            }
            HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER);
            if (remaining != null) {
                clientBuilder.connectTimeout(remaining);
            }
            HttpClient client = clientBuilder.build();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(request.serverUrl());
            if (remaining != null) {
                requestBuilder.timeout(remaining);
            }
            addHeaders(requestBuilder, request, context);
            Body body;
            try {
                body = body(request);
            } catch (IOException exception) {
                throw new CurlTaskException("CURL_FILE",
                        msg(context, "task.curl.file"), 0, 0, "prepare", exception);
            }
            body.headers().forEach((name, value) -> {
                if (!hasHeader(request.headers(), name)) {
                    requestBuilder.header(name, value);
                }
            });
            if (request.method().equals("GET")) {
                requestBuilder.GET();
            } else {
                requestBuilder.POST(body.publisher());
            }
            HttpRequest httpRequest;
            try {
                httpRequest = requestBuilder.build();
            } catch (IllegalArgumentException exception) {
                throw new CurlTaskException("CURL_REQUEST",
                        msg(context, "task.curl.requestInvalid"), 0, 0, "configuration",
                        exception);
            }

            Path temporary = null;
            try {
                HttpResponse<InputStream> response;
                try {
                    response = client.send(httpRequest,
                            HttpResponse.BodyHandlers.ofInputStream());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new CurlTaskException("CURL_TRANSPORT",
                            msg(context, "task.curl.transport"), 0, 0, "connect", exception);
                } catch (IOException exception) {
                    throw new CurlTaskException("CURL_TRANSPORT",
                            msg(context, "task.curl.transport"), 0, 0, "connect", exception);
                }
                int status = response.statusCode();
                if (status != request.expectedStatusCode()) {
                    response.body().close();
                    throw new CurlTaskException("CURL_STATUS",
                            msg(context, "task.curl.status"), status, 0, "response", null);
                }
                byte[] responseBytes = readBody(response.body(), request.maxBytes(),
                        cancellation, context, status);
                long bytes = responseBytes.length;
                if (request.expectedBody() != null) {
                    String text = new String(responseBytes, StandardCharsets.UTF_8);
                    if (!text.contains(request.expectedBody())) {
                        throw new CurlTaskException("CURL_BODY",
                                msg(context, "task.curl.body"), status, bytes,
                                "response", null);
                    }
                }
                if (target != null) {
                    temporary = Files.createTempFile(target.getParent(),
                            "." + target.getFileName() + ".", ".part");
                    Files.write(temporary, responseBytes);
                    moveIntoPlace(temporary, target, request.overwrite());
                }
                context.log().log(LogLevel.INFO, "gretl.curl.finished",
                        msg(context, "task.curl.finished"),
                        Map.of("http.method", request.method(),
                                "http.origin", origin(request.serverUrl()),
                                "http.status", Integer.toString(status),
                                "http.bytes", Long.toString(bytes),
                                "http.output", Boolean.toString(target != null)), null);
                return new CurlResult(status, bytes, target);
            } catch (CurlTaskException exception) {
                deleteQuietly(temporary, exception);
                logFailure(context, request, exception);
                throw exception;
            } catch (IOException exception) {
                deleteQuietly(temporary, exception);
                CurlTaskException wrapped = new CurlTaskException("CURL_IO",
                        msg(context, "task.curl.io"), 0, 0, "execute", exception);
                logFailure(context, request, wrapped);
                throw wrapped;
            } catch (RuntimeException exception) {
                deleteQuietly(temporary, exception);
                throw exception;
            }
        }

        private static void addHeaders(HttpRequest.Builder builder, CurlRequest request,
                                       OperationContext context) {
            if (request.user() != null) {
                String credentials = request.user() + ":" + request.password().reveal();
                String token = Base64.getEncoder().encodeToString(
                        credentials.getBytes(StandardCharsets.UTF_8));
                builder.header("Authorization", "Basic " + context.sensitive(token));
            }
            request.headers().forEach((name, header) ->
                    builder.header(name, header.reveal(context)));
        }

        private static byte[] readBody(InputStream input, long maxBytes,
                                       CancellationToken cancellation,
                                       OperationContext context, int status)
                throws IOException {
            try (InputStream body = input;
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                long total = 0;
                while (true) {
                    cancellation.throwIfCancellationRequested();
                    int read = body.read(buffer);
                    if (read < 0) {
                        break;
                    }
                    total += read;
                    if (total > maxBytes) {
                        throw new CurlTaskException("CURL_LIMIT",
                                msg(context, "task.curl.limit"), status, total,
                                "response", null);
                    }
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            }
        }

        private static Body body(CurlRequest request) throws IOException {
            if (!request.formData().isEmpty()) {
                return multipartBody(request.formData());
            }
            if (request.dataBinary() != null) {
                return new Body(HttpRequest.BodyPublishers.ofFile(request.dataBinary()),
                        Map.of());
            }
            if (request.data() != null) {
                return new Body(HttpRequest.BodyPublishers.ofString(
                        request.data(), StandardCharsets.UTF_8),
                        Map.of("Content-Type", "text/plain; charset=UTF-8"));
            }
            return new Body(HttpRequest.BodyPublishers.noBody(), Map.of());
        }

        private static Body multipartBody(Map<String, FormValue> formData)
                throws IOException {
            String boundary = "gretl-" + UUID.randomUUID();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (Map.Entry<String, FormValue> entry : formData.entrySet()) {
                out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                FormValue value = entry.getValue();
                if (value.isFile()) {
                    String fileName = value.file().getFileName().toString()
                            .replace("\"", "_");
                    out.write(("Content-Disposition: form-data; name=\""
                            + entry.getKey() + "\"; filename=\"" + fileName + "\"\r\n")
                            .getBytes(StandardCharsets.UTF_8));
                    out.write("Content-Type: application/octet-stream\r\n\r\n"
                            .getBytes(StandardCharsets.UTF_8));
                    Files.copy(value.file(), out);
                    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                } else {
                    out.write(("Content-Disposition: form-data; name=\""
                            + entry.getKey() + "\"\r\n\r\n")
                            .getBytes(StandardCharsets.UTF_8));
                    out.write(value.text().getBytes(StandardCharsets.UTF_8));
                    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                }
            }
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return new Body(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()),
                    Map.of("Content-Type", "multipart/form-data; boundary=" + boundary));
        }

        private static void prepareTarget(Path target, boolean overwrite,
                                          OperationContext context) throws IOException {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!overwrite) {
                    throw new CurlTaskException("CURL_TARGET_EXISTS",
                            msg(context, "task.curl.targetExists"), 0, 0, "prepare",
                            null);
                }
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new CurlTaskException("CURL_TARGET_UNSAFE",
                            msg(context, "task.curl.targetUnsafe"), 0, 0, "prepare",
                            null);
                }
            }
            if (target.getParent() == null) {
                throw new CurlTaskException("CURL_TARGET_INVALID",
                        msg(context, "task.curl.targetInvalid"), 0, 0, "prepare",
                        null);
            }
            Files.createDirectories(target.getParent());
        }

        private static void validateBodyFiles(CurlRequest request,
                                              OperationContext context) {
            if (request.dataBinary() != null) {
                requireReadable(request.dataBinary(), context);
            }
            for (FormValue value : request.formData().values()) {
                if (value.isFile()) {
                    requireReadable(value.file(), context);
                }
            }
        }

        private static void requireReadable(Path path, OperationContext context) {
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new CurlTaskException("CURL_FILE",
                        msg(context, "task.curl.file"), 0, 0, "prepare", null);
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

        private static void logFailure(OperationContext context, CurlRequest request,
                                       CurlTaskException exception) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("http.method", request.method());
            fields.put("http.origin", origin(request.serverUrl()));
            fields.put("http.status", Integer.toString(exception.status()));
            fields.put("http.bytes", Long.toString(exception.bytes()));
            fields.put("http.phase", exception.phase());
            context.log().log(LogLevel.WARN, "gretl.curl.failed",
                    msg(context, "task.curl.failed"), fields, exception);
        }

        private static void deleteQuietly(Path path, Throwable primary) {
            if (path == null) {
                return;
            }
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                primary.addSuppressed(exception);
            }
        }

        private static String origin(URI uri) {
            StringBuilder origin = new StringBuilder(uri.getScheme()).append("://")
                    .append(uri.getHost());
            if (uri.getPort() != -1) {
                origin.append(':').append(uri.getPort());
            }
            if (uri.getPath() != null) {
                origin.append(uri.getPath());
            }
            return origin.toString();
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }
    }

    private record Body(HttpRequest.BodyPublisher publisher,
                        Map<String, String> headers) {
    }
}
