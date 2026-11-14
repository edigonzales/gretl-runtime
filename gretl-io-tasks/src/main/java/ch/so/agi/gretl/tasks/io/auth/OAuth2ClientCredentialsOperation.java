package ch.so.agi.gretl.tasks.io.auth;

import ch.so.agi.gretl.runtime.api.ConfigBuilder;
import ch.so.agi.gretl.runtime.api.DiagnosticCarrier;
import ch.so.agi.gretl.runtime.api.DiagnosticInfo;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.SecretValue;
import ch.so.agi.gretl.runtime.api.TaskCancelledException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscribers;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Obtain a bearer credential for the current run. */
public final class OAuth2ClientCredentialsOperation {
    public static final String ID = "gretl.oauth2-client-credentials";
    public static final String SYMBOL = "OAuth2ClientCredentials";

    private OAuth2ClientCredentialsOperation() {}

    public static OperationType<Request, Result> type() {
        return new OperationType<>(ID, SYMBOL, "Obtain an OAuth2 bearer credential",
                Request.class, Result.class, Builder::new, Handler::new,
                List.of(
                        Fields.input("tokenUrl", "uri", "Absolute HTTPS token endpoint", true, Optional.empty(),
                                List.of(), "tokenUrl = 'https://identity.example.invalid/token'", Builder.class,
                                OAuth2ClientCredentialsOperation::uri, (builder, value) -> builder.tokenUrl = value),
                        Fields.input("clientId", "text", "OAuth2 client identifier", true, Optional.empty(),
                                List.of(), "clientId = 'job-client'", Builder.class,
                                value -> text(value, "clientId"), (builder, value) -> builder.clientId = value),
                        Fields.input("clientSecret", "secret", "OAuth2 client secret from a SecretValue",
                                true, Optional.empty(), List.of(), "clientSecret = secret('oauthClientSecret')",
                                Builder.class, OAuth2ClientCredentialsOperation::secret,
                                (builder, value) -> builder.clientSecret = value),
                        Fields.input("scope", "text", "Optional OAuth2 scope", false, Optional.empty(),
                                List.of(), "scope = 'read write'", Builder.class,
                                value -> text(value, "scope"), (builder, value) -> builder.scope = value),
                        Fields.input("allowInsecureHttp", "boolean", "Allow HTTP for isolated local development",
                                false, Optional.of(false), List.of(), "allowInsecureHttp = false",
                                Builder.class, OAuth2ClientCredentialsOperation::bool,
                                (builder, value) -> builder.allowInsecureHttp = value)),
                List.of(Fields.output("authorization", "secret", "Bearer authorization header value",
                        false, Result::authorization)),
                """
                task('auth', OAuth2ClientCredentials) {
                    tokenUrl = 'https://identity.example.invalid/token'
                    clientId = 'job-client'
                    clientSecret = secret('oauthClientSecret')
                }

                task('fetch', Curl, dependsOn: 'auth') {
                    serverUrl = 'https://api.example.invalid/status'
                    expectedStatusCode = 200
                    headers = ['Authorization': result('auth').authorization]
                }
                defaults 'fetch'
                """);
    }

    private static URI uri(Object value) {
        if (value instanceof URI uri) {
            return uri;
        }
        if (value instanceof CharSequence text) {
            try {
                return URI.create(text.toString());
            } catch (IllegalArgumentException ignored) {
                // URI parser diagnostics include the input text.
            }
        }
        throw configuration("tokenUrl must be a valid URI");
    }

    private static String text(Object value, String field) {
        if (value instanceof CharSequence text) {
            return text.toString();
        }
        throw configuration(field + " must be text");
    }

    private static SecretValue secret(Object value) {
        if (value instanceof SecretValue secret) {
            return secret;
        }
        throw configuration("clientSecret requires a SecretValue from secret('alias')");
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean allowed) {
            return allowed;
        }
        throw configuration("allowInsecureHttp must be true or false");
    }

    public record Request(URI tokenUrl, String clientId, SecretValue clientSecret,
                          String scope, boolean allowInsecureHttp) {
        public Request {
            if (tokenUrl == null || tokenUrl.getHost() == null
                    || tokenUrl.getRawUserInfo() != null || tokenUrl.getRawFragment() != null
                    || tokenUrl.getRawQuery() != null || tokenUrl.getPort() > 65535
                    || !("https".equalsIgnoreCase(tokenUrl.getScheme())
                    || (allowInsecureHttp && "http".equalsIgnoreCase(tokenUrl.getScheme())))) {
                throw configuration("tokenUrl requires HTTPS without userinfo, query or fragment (HTTP requires explicit opt-in)");
            }
            if (clientId == null || clientId.isBlank() || clientId.length() > 1024
                    || clientId.codePoints().anyMatch(Character::isISOControl)) {
                throw configuration("clientId must contain 1 to 1024 characters without control characters");
            }
            if (clientSecret == null) {
                throw configuration("clientSecret is required");
            }
        }
    }

    public record Result(SecretValue authorization) {
        public Result {
            Objects.requireNonNull(authorization, "authorization");
        }
    }

    public static final class Builder implements ConfigBuilder<Request> {
        private URI tokenUrl;
        private String clientId;
        private SecretValue clientSecret;
        private String scope;
        private boolean allowInsecureHttp;

        @Override public Request build() {
            return new Request(tokenUrl, clientId, clientSecret, scope, allowInsecureHttp);
        }
    }

    static final class Handler implements OperationHandler<Request, Result> {
        private static final int MAX_BODY_BYTES = 64 * 1024;
        private static final Duration CANCELLATION_POLL = Duration.ofMillis(100);
        private static final Pattern BEARER_TOKEN = Pattern.compile("[A-Za-z0-9._~+/-]+=*");
        private static final JsonMapper JSON = JsonMapper.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();

        @Override public Result execute(Request request, OperationContext context) throws InterruptedException {
            remaining(context);
            String secret;
            try {
                secret = request.clientSecret().reveal();
            } catch (TaskCancelledException cancelled) {
                throw cancelled;
            } catch (RuntimeException ignored) {
                throw configuration("Could not resolve clientSecret");
            }
            if (secret.isEmpty()) {
                throw configuration("clientSecret must not be empty");
            }
            context.sensitive(secret);
            String basic = "Basic " + Base64.getEncoder().encodeToString(
                    (encode(request.clientId()) + ":" + encode(secret)).getBytes(UTF_8));
            context.sensitive(basic);

            String body = "grant_type=client_credentials";
            if (request.scope() != null && !request.scope().isEmpty()) {
                body += "&scope=" + encode(request.scope());
            }
            var post = HttpRequest.newBuilder(request.tokenUrl())
                    .header("Authorization", basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            Optional<Duration> budget = remaining(context);
            budget.ifPresent(post::timeout);
            String token = accessToken(exchange(post.build(), context));
            context.sensitive(token);
            return new Result(SecretValue.deferred(() -> "Bearer " + token));
        }

        private static byte[] exchange(HttpRequest request, OperationContext context) throws InterruptedException {
            var status = new AtomicInteger();
            try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.NEVER).build()) {
                remaining(context);
                var response = client.sendAsync(request, headers -> {
                    status.set(headers.statusCode());
                    if (headers.statusCode() != 200) {
                        throw statusFailure(headers.statusCode());
                    }
                    return BodySubscribers.limiting(BodySubscribers.ofByteArray(), MAX_BODY_BYTES);
                });
                try {
                    while (true) {
                        Duration budget = remaining(context).orElse(CANCELLATION_POLL);
                        Duration wait = budget.compareTo(CANCELLATION_POLL) < 0 ? budget : CANCELLATION_POLL;
                        try {
                            byte[] bytes = response.get(wait.toNanos(), TimeUnit.NANOSECONDS).body();
                            remaining(context);
                            return bytes;
                        } catch (TimeoutException ignored) {
                            // Recheck the run's cancellation signal and remaining budget.
                        }
                    }
                } catch (ExecutionException failed) {
                    remaining(context);
                    if (failed.getCause() instanceof HttpTimeoutException) {
                        throw timeout();
                    }
                    if (status.get() == 200) {
                        throw invalidResponse();
                    }
                    if (status.get() != 0) {
                        throw statusFailure(status.get());
                    }
                    throw new Failure("OAUTH2_TRANSPORT", "Could not reach the token endpoint");
                } finally {
                    response.cancel(true);
                }
            }
        }

        private static Optional<Duration> remaining(OperationContext context) {
            context.cancellation().throwIfCancellationRequested();
            Optional<Duration> remaining = context.budget().remaining();
            if (remaining.filter(limit -> limit.isZero() || limit.isNegative()).isPresent()) {
                throw timeout();
            }
            return remaining;
        }

        private static String accessToken(byte[] bytes) {
            try {
                JsonNode root = JSON.readTree(bytes);
                String token = root.path("access_token").textValue();
                String type = root.path("token_type").textValue();
                JsonNode expiry = root.path("expires_in");
                if (token == null || !BEARER_TOKEN.matcher(token).matches()
                        || !"Bearer".equalsIgnoreCase(type) || expired(expiry)) {
                    throw invalidResponse();
                }
                return token;
            } catch (IOException ignored) {
                throw invalidResponse();
            }
        }

        private static boolean expired(JsonNode expiry) {
            if (expiry.isNumber()) {
                return expiry.doubleValue() <= 0;
            }
            if (expiry.isTextual()) {
                try {
                    return new java.math.BigDecimal(expiry.textValue()).signum() <= 0;
                } catch (NumberFormatException ignored) {
                    // The lifetime is optional and is not used for caching.
                }
            }
            return false;
        }

        private static String encode(String value) {
            return URLEncoder.encode(value, UTF_8);
        }
    }

    private static Failure configuration(String message) {
        return new Failure("OAUTH2_CONFIGURATION", message);
    }

    private static Failure timeout() {
        return new Failure("OAUTH2_TIMEOUT", "Token request exceeded the execution budget");
    }

    private static Failure invalidResponse() {
        return new Failure("OAUTH2_RESPONSE", "Invalid, incomplete or oversized token response");
    }

    private static Failure statusFailure(int status) {
        return new Failure("OAUTH2_STATUS", "Token endpoint returned HTTP " + status);
    }

    static final class Failure extends IllegalArgumentException implements DiagnosticCarrier {
        private static final long serialVersionUID = 1L;
        private final String code;

        Failure(String code, String message) {
            super(code + ": " + message);
            this.code = code;
        }

        @Override public DiagnosticInfo diagnostic() {
            return new DiagnosticInfo(code, Optional.empty(), Optional.empty(), List.of(), Optional.empty());
        }
    }
}
