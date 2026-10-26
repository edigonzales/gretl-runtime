package ch.so.agi.gretl.tasks.io.s3;

import ch.so.agi.gretl.runtime.api.ConfigBuilder;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.SecretValue;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Shared metadata and conversion helpers for the S3 task family. */
final class S3Fields {
    static final String DEFAULT_ENDPOINT = "https://s3.eu-central-1.amazonaws.com";
    static final String DEFAULT_REGION = "eu-central-1";
    static final String DEFAULT_ACL = "private";
    private static final Set<String> ACLS = Set.of(
            "private", "public-read", "public-read-write", "authenticated-read",
            "aws-exec-read", "bucket-owner-read", "bucket-owner-full-control");

    private S3Fields() {
    }

    static <C> List<InputField<C, ?>> commonInputs() {
        InputField<C, String> accessKey = new InputField<>(
                "accessKey", "text", "S3 access key", true,
                Optional.empty(), List.of(), "accessKey = 'AKIA...'",
                S3Fields::text,
                (builder, value) -> ((BaseS3Builder<C>) builder).setAccessKey(value));
        InputField<C, SecretValue> secretKey = new InputField<>(
                "secretKey", "secret", "S3 secret key from a secret alias", true,
                Optional.empty(), List.of(), "secretKey = secret('s3SecretKey')",
                S3Fields::secret,
                (builder, value) -> ((BaseS3Builder<C>) builder).setSecretKey(value));
        InputField<C, String> endpoint = new InputField<>(
                "endpoint", "text", "S3 endpoint URL", false,
                Optional.of(DEFAULT_ENDPOINT), List.of(),
                "endpoint = 'http://127.0.0.1:9000'", S3Fields::text,
                (builder, value) -> ((BaseS3Builder<C>) builder).setEndpoint(value));
        InputField<C, String> region = new InputField<>(
                "region", "text", "S3 region", false,
                Optional.of(DEFAULT_REGION), List.of(),
                "region = 'eu-central-1'", S3Fields::text,
                (builder, value) -> ((BaseS3Builder<C>) builder).setRegion(value));
        return List.of(accessKey, secretKey, endpoint, region);
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
                "secretKey requires a SecretValue from secret('alias')");
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

    static List<Path> paths(Object value) {
        List<Object> values = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(values::add);
        } else if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                values.add(java.lang.reflect.Array.get(value, index));
            }
        } else {
            throw new IllegalArgumentException("sourceFiles must be a list of paths");
        }
        List<Path> result = new ArrayList<>(values.size());
        for (Object element : values) {
            result.add(path(element));
        }
        return List.copyOf(result);
    }

    static Map<String, String> metadata(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("metadata must be a map");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof CharSequence key)
                    || !(entry.getValue() instanceof CharSequence metadataValue)
                    || key.toString().isBlank()) {
                throw new IllegalArgumentException(
                        "metadata keys and values must be non-blank text");
            }
            result.put(key.toString(), metadataValue.toString());
        }
        return Map.copyOf(result);
    }

    static Boolean bool(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new IllegalArgumentException("Boolean value expected");
    }

    static String acl(Object value) {
        String acl = text(value).toLowerCase(Locale.ROOT);
        if (!ACLS.contains(acl)) {
            throw new IllegalArgumentException("Unsupported S3 canned ACL: " + acl);
        }
        return acl;
    }

    abstract static class BaseS3Builder<C> implements ConfigBuilder<C> {
        private String accessKey;
        private SecretValue secretKey;
        private String endpoint = DEFAULT_ENDPOINT;
        private String region = DEFAULT_REGION;

        public void setAccessKey(String value) { accessKey = value; }
        public void setSecretKey(SecretValue value) { secretKey = value; }
        public void setEndpoint(String value) { endpoint = value; }
        public void setRegion(String value) { region = value; }

        protected S3Connection connection() {
            if (accessKey == null || accessKey.isBlank()) {
                throw new IllegalArgumentException("accessKey is missing");
            }
            if (secretKey == null) {
                throw new IllegalArgumentException("secretKey is missing");
            }
            String resolvedEndpoint = endpoint == null || endpoint.isBlank()
                    ? DEFAULT_ENDPOINT : endpoint;
            try {
                URI.create(resolvedEndpoint);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("endpoint is not a valid URI", exception);
            }
            return new S3Connection(resolvedEndpoint,
                    region == null || region.isBlank() ? DEFAULT_REGION : region,
                    accessKey, secretKey);
        }

        protected static String requiredBucket(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("bucketName must not be blank");
            }
            return value;
        }
    }
}
