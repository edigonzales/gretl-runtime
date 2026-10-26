package ch.so.agi.gretl.it;

import io.floci.testcontainers.FlociContainer;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Real S3-compatible Floci container with isolated source/target buckets. */
final class S3Fixture implements AutoCloseable {
    private static FlociContainer container;

    static synchronized FlociContainer container() {
        if (container == null) {
            FlociContainer candidate = new FlociContainer().withRegion("us-east-1");
            if (System.getProperty("gretl.test.executionMode", "JAR").equals("IMAGE")) {
                candidate.withNetworkMode(System.getProperty("gretl.test.imageNetwork"));
                candidate.withCreateContainerCmdModifier(command -> command.withHostName("s3").withAliases("s3"));
            }
            candidate.start();
            container = candidate;
        }
        return container;
    }

    Lease lease() {
        FlociContainer running = container();
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String source = "gretl-source-" + token;
        String target = "gretl-target-" + token;
        try (S3Client client = client(running)) {
            client.createBucket(builder -> builder.bucket(source));
            client.createBucket(builder -> builder.bucket(target));
        }
        return new Lease(running, source, target);
    }

    @Override
    public void close() {
        if (container != null && container.isRunning()) {
            container.stop();
            container = null;
        }
    }

    static final class Lease implements AutoCloseable {
        private final FlociContainer container;
        private final String sourceBucket;
        private final String targetBucket;
        private boolean closed;

        Lease(FlociContainer container, String sourceBucket, String targetBucket) {
            this.container = container;
            this.sourceBucket = sourceBucket;
            this.targetBucket = targetBucket;
        }

        String endpoint() {
            return container.getEndpoint();
        }

        String region() {
            return container.getRegion();
        }

        String accessKey() {
            return container.getAccessKey();
        }

        String secretKey() {
            return "gretl-integration-s3-secret-2026";
        }

        String sourceBucket() {
            return sourceBucket;
        }

        String targetBucket() {
            return targetBucket;
        }

        S3Client client() {
            return S3Fixture.client(container);
        }

        void put(String bucket, String key, String text) {
            try (S3Client client = client()) {
                client.putObject(builder -> builder.bucket(bucket).key(key),
                        RequestBody.fromString(text));
            }
        }

        HeadObjectResponse head(String bucket, String key) {
            try (S3Client client = client()) {
                return client.headObject(builder -> builder.bucket(bucket).key(key));
            }
        }

        String read(String bucket, String key) {
            try (S3Client client = client()) {
                return client.getObjectAsBytes(builder -> builder.bucket(bucket).key(key))
                        .asUtf8String();
            }
        }

        List<String> keys(String bucket) {
            try (S3Client client = client()) {
                return client.listObjectsV2Paginator(builder -> builder.bucket(bucket))
                        .contents().stream()
                        .map(object -> object.key())
                        .sorted()
                        .toList();
            }
        }

        boolean exists(String bucket, String key) {
            try (S3Client client = client()) {
                client.headObject(builder -> builder.bucket(bucket).key(key));
                return true;
            } catch (NoSuchKeyException exception) {
                return false;
            } catch (S3Exception exception) {
                if (exception.statusCode() == 404) {
                    return false;
                }
                throw exception;
            }
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            try (S3Client client = client()) {
                for (String bucket : List.of(sourceBucket, targetBucket)) {
                    for (String key : keys(bucket)) {
                        client.deleteObject(builder -> builder.bucket(bucket).key(key));
                    }
                    client.deleteBucket(builder -> builder.bucket(bucket));
                }
            }
        }
    }

    static S3Client client(FlociContainer container) {
        return S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .endpointOverride(URI.create(container.getEndpoint()))
                .region(Region.of(container.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(container.getAccessKey(),
                                "gretl-integration-s3-secret-2026")))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
