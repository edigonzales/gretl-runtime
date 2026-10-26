package ch.so.agi.gretl.tasks.io.s3;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import ch.so.agi.gretl.runtime.api.Progress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import software.amazon.awssdk.core.exception.SdkException;

/** S3Bucket2Bucket task: copy all objects from one bucket to another. */
public final class S3Bucket2BucketOperation {
    public static final String ID = "gretl.s3-bucket2bucket";
    public static final String SYMBOL = "S3Bucket2Bucket";

    private S3Bucket2BucketOperation() {
    }

    public static OperationType<S3Bucket2BucketRequest, S3Bucket2BucketResult> type() {
        List<InputField<S3Bucket2BucketRequest, ?>> inputs =
                new ArrayList<>(S3Fields.commonInputs());
        inputs.add(new InputField<>(
                "sourceBucket", "text", "Source S3 bucket", true,
                Optional.empty(), List.of(), "sourceBucket = 'source-bucket'",
                S3Fields::text,
                (builder, value) -> ((S3Bucket2BucketBuilder) builder)
                        .setSourceBucket(value)));
        inputs.add(new InputField<>(
                "targetBucket", "text", "Target S3 bucket", true,
                Optional.empty(), List.of(), "targetBucket = 'target-bucket'",
                S3Fields::text,
                (builder, value) -> ((S3Bucket2BucketBuilder) builder)
                        .setTargetBucket(value)));
        inputs.add(new InputField<>(
                "acl", "text", "S3 canned ACL", false,
                Optional.of(S3Fields.DEFAULT_ACL), List.of(),
                "acl = 'private'", S3Fields::acl,
                (builder, value) -> ((S3Bucket2BucketBuilder) builder).setAcl(value)));
        InputField<S3Bucket2BucketRequest, Map<String, String>> metadata = new InputField<>(
                "metadata", "map", "Replacement metadata for copied objects", false,
                Optional.of(Map.of()), List.of(), "metadata = [copied: 'true']",
                S3Fields::metadata,
                (builder, value) -> ((S3Bucket2BucketBuilder) builder).setMetadata(value));
        inputs.add(metadata);
        OutputField<S3Bucket2BucketResult, List<String>> keys = Fields.output(
                "keys", "list", "Copied object keys", false,
                S3Bucket2BucketResult::keys);
        OutputField<S3Bucket2BucketResult, Integer> count = Fields.output(
                "count", "integer", "Number of copied objects", false,
                S3Bucket2BucketResult::count);
        return new OperationType<>(
                ID, SYMBOL, "Copy all objects from one S3 bucket to another",
                S3Bucket2BucketRequest.class, S3Bucket2BucketResult.class,
                S3Bucket2BucketBuilder::new, S3Bucket2BucketHandler::new,
                inputs, List.of(keys, count),
                """
                task('copy', S3Bucket2Bucket) {
                    accessKey = 'AKIA...'
                    secretKey = secret('s3SecretKey')
                    sourceBucket = 'source-bucket'
                    targetBucket = 'target-bucket'
                    acl = 'private'
                }
                """);
    }

    public record S3Bucket2BucketRequest(
            S3Connection connection, String sourceBucket, String targetBucket,
            String acl, Map<String, String> metadata) {
        public S3Bucket2BucketRequest {
            metadata = Map.copyOf(metadata);
        }
    }

    public record S3Bucket2BucketResult(List<String> keys, int count) {
        public S3Bucket2BucketResult {
            keys = List.copyOf(keys);
        }
    }

    public static final class S3Bucket2BucketBuilder
            extends S3Fields.BaseS3Builder<S3Bucket2BucketRequest> {
        private String sourceBucket;
        private String targetBucket;
        private String acl = S3Fields.DEFAULT_ACL;
        private Map<String, String> metadata = Map.of();

        public S3Bucket2BucketBuilder setSourceBucket(String value) {
            sourceBucket = value;
            return this;
        }

        public S3Bucket2BucketBuilder setTargetBucket(String value) {
            targetBucket = value;
            return this;
        }

        public S3Bucket2BucketBuilder setAcl(String value) {
            acl = value;
            return this;
        }

        public S3Bucket2BucketBuilder setMetadata(Map<String, String> value) {
            metadata = value == null ? Map.of() : value;
            return this;
        }

        @Override
        public S3Bucket2BucketRequest build() {
            if (sourceBucket == null || sourceBucket.isBlank()) {
                throw new IllegalArgumentException("sourceBucket must not be blank");
            }
            if (targetBucket == null || targetBucket.isBlank()) {
                throw new IllegalArgumentException("targetBucket must not be blank");
            }
            return new S3Bucket2BucketRequest(connection(), sourceBucket, targetBucket,
                    S3Fields.acl(acl), metadata);
        }
    }

    static final class S3Bucket2BucketHandler implements
            OperationHandler<S3Bucket2BucketRequest, S3Bucket2BucketResult> {
        @Override
        public S3Bucket2BucketResult execute(S3Bucket2BucketRequest request,
                                             OperationContext context) throws Exception {
            try (var client = S3Support.open(request.connection(), context)) {
                List<String> sourceKeys = S3Support.keys(client, request.sourceBucket());
                List<String> copied = new ArrayList<>();
                for (String key : sourceKeys) {
                    S3Support.copy(client, request.sourceBucket(), request.targetBucket(),
                            key, request.acl(), request.metadata(), context);
                    copied.add(key);
                    context.progress(new Progress("s3-bucket2bucket", copied.size(),
                            OptionalLong.of(sourceKeys.size()), "objects"));
                }
                context.log().log(LogLevel.INFO, "gretl.s3.copy.finished",
                        msg(context, "task.s3.copy.finished"),
                        Map.of("s3.objects", Integer.toString(copied.size())), null);
                return new S3Bucket2BucketResult(copied, copied.size());
            } catch (S3TaskException exception) {
                logFailure(context, exception);
                throw exception;
            } catch (SdkException exception) {
                S3TaskException wrapped = new S3TaskException("S3_COPY",
                        msg(context, "task.s3.copy.failed"), "copy", exception);
                logFailure(context, wrapped);
                throw wrapped;
            }
        }

        private static void logFailure(OperationContext context, S3TaskException exception) {
            context.log().log(LogLevel.WARN, "gretl.s3.copy.failed",
                    msg(context, "task.s3.copy.failed"),
                    Map.of("s3.phase", exception.phase()), exception);
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }
    }
}
