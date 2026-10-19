package ch.so.agi.gretl.tasks.s3;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.CancellationToken;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.Progress;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.utils.http.SdkHttpUtils;

/** Shared S3 client, transfer and path-safety support for the S3 task family. */
final class S3Support {
    private S3Support() {
    }

    static S3Client open(S3Connection connection, OperationContext context) {
        String endpoint = connection.endpoint() == null || connection.endpoint().isBlank()
                ? S3Fields.DEFAULT_ENDPOINT : connection.endpoint();
        String region = connection.region() == null || connection.region().isBlank()
                ? S3Fields.DEFAULT_REGION : connection.region();
        S3ClientBuilder builder = S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(connection.accessKey(),
                                connection.secretKey().reveal())))
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .endpointOverride(URI.create(endpoint));
        Optional<Duration> remaining = context.budget().remaining();
        if (remaining.isPresent()) {
            Duration duration = remaining.get();
            if (duration.isZero() || duration.isNegative()) {
                throw timeout(context);
            }
            builder.overrideConfiguration(ClientOverrideConfiguration.builder()
                    .apiCallTimeout(duration)
                    .build());
        }
        return builder.build();
    }

    static List<String> keys(S3Client client, String bucket) {
        List<String> keys = new ArrayList<>();
        client.listObjectsV2Paginator(request -> request.bucket(bucket))
                .contents().forEach(object -> keys.add(object.key()));
        keys.sort(Comparator.naturalOrder());
        return List.copyOf(keys);
    }

    static long download(S3Client client, String bucket, String key, Path target,
                         boolean overwrite, OperationContext context) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(),
                "." + target.getFileName() + ".", ".part");
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket).key(key).build();
            long bytes;
            try (var response = client.getObject(request);
                 InputStream input = response;
                 OutputStream output = Files.newOutputStream(temporary)) {
                GetObjectResponse metadata = response.response();
                long total = metadata.contentLength() == null ? -1 : metadata.contentLength();
                bytes = copy(input, output, total < 0 ? OptionalLong.empty()
                        : OptionalLong.of(total), context, "s3-download");
            }
            moveIntoPlace(temporary, target, overwrite);
            return bytes;
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                exception.addSuppressed(cleanup);
            }
            throw exception;
        }
    }

    static long upload(S3Client client, String bucket, String key, Path source,
                       String acl, String contentType, Map<String, String> metadata,
                       OperationContext context) throws IOException {
        checkBudget(context);
        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .acl(ObjectCannedACL.fromValue(acl))
                .metadata(metadata == null ? Map.of() : metadata);
        if (contentType != null && !contentType.isBlank()) {
            request.contentType(contentType);
        }
        client.putObject(request.build(), RequestBody.fromFile(source));
        return Files.size(source);
    }

    static void delete(S3Client client, String bucket, String key,
                       OperationContext context) {
        checkBudget(context);
        client.deleteObject(request -> request.bucket(bucket).key(key));
    }

    static long copy(S3Client client, String sourceBucket, String targetBucket,
                     String key, String acl, Map<String, String> metadata,
                     OperationContext context) {
        checkBudget(context);
        CopyObjectRequest.Builder request = CopyObjectRequest.builder()
                .copySource(SdkHttpUtils.urlEncodeIgnoreSlashes(
                        sourceBucket + "/" + key))
                .destinationBucket(targetBucket)
                .destinationKey(key)
                .acl(ObjectCannedACL.fromValue(acl));
        if (metadata != null && !metadata.isEmpty()) {
            request.metadata(metadata);
            request.metadataDirective(MetadataDirective.REPLACE);
        }
        client.copyObject(request.build());
        return 0;
    }

    static Path downloadTarget(Path downloadDir, String key, boolean explicit) {
        Path root = downloadDir.toAbsolutePath().normalize();
        String relative = explicit
                ? key.substring(key.lastIndexOf('/') + 1)
                : key;
        if (relative.isBlank() || relative.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("S3 key cannot be mapped to a local file");
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalArgumentException(
                    "S3 key would escape the download directory: " + key);
        }
        return target;
    }

    static void checkBudget(OperationContext context) {
        if (context.budget().expired()) {
            throw timeout(context);
        }
        context.cancellation().throwIfCancellationRequested();
    }

    static void checkKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("S3 key must not be blank");
        }
        if (key.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("S3 key contains a NUL character");
        }
    }

    private static long copy(InputStream input, OutputStream output, OptionalLong total,
                             OperationContext context, String phase) throws IOException {
        CancellationToken cancellation = context.cancellation();
        byte[] buffer = new byte[8192];
        long copied = 0;
        while (true) {
            cancellation.throwIfCancellationRequested();
            if (context.budget().expired()) {
                throw timeout(context);
            }
            int read = input.read(buffer);
            if (read < 0) {
                return copied;
            }
            output.write(buffer, 0, read);
            copied += read;
            context.progress(new Progress(phase, copied, total, "bytes"));
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

    private static S3TaskException timeout(OperationContext context) {
        return new S3TaskException("S3_TIMEOUT",
                Messages.format(context.locale(), "task.s3.timeout"), "transfer", null);
    }
}
