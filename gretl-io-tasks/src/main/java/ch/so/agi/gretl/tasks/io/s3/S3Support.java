package ch.so.agi.gretl.tasks.io.s3;

import ch.so.agi.gretl.tasks.io.internal.CompletedFilePublisher;
import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.CancellationToken;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.Progress;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
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

    static long download(S3Client client, String bucket, String key, Path realRoot, Path target,
                         boolean overwrite, OperationContext context) throws IOException {
        checkDownloadParent(realRoot, target, true, "prepare", context);
        prepareTarget(target, overwrite, context);
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
            checkDownloadParent(realRoot, target, false, "publish", context);
            try {
                CompletedFilePublisher.publish(temporary, target, overwrite);
            } catch (FileAlreadyExistsException exception) {
                throw new S3TaskException("S3_TARGET_EXISTS",
                        Messages.format(context.locale(), "task.s3.targetExists"), "publish", exception);
            } catch (IOException exception) {
                throw new S3TaskException("S3_DOWNLOAD",
                        Messages.format(context.locale(), "task.s3.download.failed"), "publish", exception);
            }
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

    static Path prepareDownloadRoot(Path configuredRoot) throws IOException {
        Path realRoot;
        try {
            realRoot = configuredRoot.toRealPath();
        } catch (NoSuchFileException missing) {
            Files.createDirectories(configuredRoot);
            realRoot = configuredRoot.toRealPath();
        }
        requireDirectory(realRoot);
        return realRoot;
    }

    static Path downloadTarget(Path realRoot, String key, boolean explicit) {
        String relative = explicit ? key.substring(key.lastIndexOf('/') + 1) : key;
        if (relative.isBlank() || relative.indexOf('\0') >= 0 || relative.endsWith("/")) {
            throw new IllegalArgumentException("S3 key cannot be mapped to a local file");
        }
        Path keyPath = realRoot.getFileSystem().getPath(relative);
        if (keyPath.getRoot() != null || keyPath.getFileName().toString().equals(".")) {
            throw new IllegalArgumentException("S3 key cannot be mapped to a relative local file");
        }
        for (Path component : keyPath) {
            if (component.toString().equals("..")) {
                throw new IllegalArgumentException("S3 key contains a parent traversal segment");
            }
        }
        return realRoot.resolve(keyPath).normalize();
    }

    private static void checkDownloadParent(Path realRoot, Path target, boolean create,
                                            String phase, OperationContext context) {
        try {
            if (!target.startsWith(realRoot) || target.equals(realRoot)
                    || !target.equals(target.normalize())) {
                throw new IOException("Download target must be below the real download root");
            }
            requireDirectory(realRoot);
            Path parent = target.getParent();
            if (parent.equals(realRoot)) {
                return;
            }
            Path directory = realRoot;
            for (Path component : realRoot.relativize(parent)) {
                directory = directory.resolve(component);
                try {
                    requireDirectory(directory);
                } catch (NoSuchFileException missing) {
                    if (!create) {
                        throw missing;
                    }
                    try {
                        Files.createDirectory(directory);
                    } catch (FileAlreadyExistsException concurrentCreation) {
                        requireDirectory(directory);
                    }
                }
            }
        } catch (IOException exception) {
            throw new S3TaskException("S3_LOCAL_PATH",
                    Messages.format(context.locale(), "task.s3.localPath"), phase, exception);
        }
    }

    private static void requireDirectory(Path path) throws IOException {
        if (!Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
                .isDirectory()) {
            throw new IOException("Download parent must be a directory and cannot be a symbolic link: " + path);
        }
    }

    private static void prepareTarget(Path target, boolean overwrite, OperationContext context) {
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException absent) {
            return;
        } catch (IOException exception) {
            throw new S3TaskException("S3_LOCAL_PATH",
                    Messages.format(context.locale(), "task.s3.localPath"), "prepare", exception);
        }
        if (!overwrite) {
            throw new S3TaskException("S3_TARGET_EXISTS",
                    Messages.format(context.locale(), "task.s3.targetExists"), "prepare", null);
        }
        if (!attributes.isRegularFile()) {
            throw new S3TaskException("S3_TARGET_UNSAFE",
                    Messages.format(context.locale(), "task.s3.targetUnsafe"), "prepare", null);
        }
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

    private static S3TaskException timeout(OperationContext context) {
        return new S3TaskException("S3_TIMEOUT",
                Messages.format(context.locale(), "task.s3.timeout"), "transfer", null);
    }
}
