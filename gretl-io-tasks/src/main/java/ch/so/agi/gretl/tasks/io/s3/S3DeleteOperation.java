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

/** S3Delete task: delete one object or all objects from a bucket. */
public final class S3DeleteOperation {
    public static final String ID = "gretl.s3-delete";
    public static final String SYMBOL = "S3Delete";

    private S3DeleteOperation() {
    }

    public static OperationType<S3DeleteRequest, S3DeleteResult> type() {
        List<InputField<S3DeleteRequest, ?>> inputs =
                new ArrayList<>(S3Fields.commonInputs());
        inputs.add(new InputField<>(
                "bucketName", "text", "S3 bucket name", true,
                Optional.empty(), List.of(), "bucketName = 'my-bucket'",
                S3Fields::text,
                (builder, value) -> ((S3DeleteBuilder) builder).setBucketName(value)));
        inputs.add(new InputField<>(
                "key", "text", "Object key; empty means all objects", false,
                Optional.empty(), List.of(), "key = 'data/export.zip'",
                S3Fields::text,
                (builder, value) -> ((S3DeleteBuilder) builder).setKey(value)));
        OutputField<S3DeleteResult, List<String>> keys = Fields.output(
                "keys", "list", "Deleted object keys", false, S3DeleteResult::keys);
        OutputField<S3DeleteResult, Integer> count = Fields.output(
                "count", "integer", "Number of deleted objects", false,
                S3DeleteResult::count);
        return new OperationType<>(
                ID, SYMBOL, "Delete objects from an S3 bucket",
                S3DeleteRequest.class, S3DeleteResult.class,
                S3DeleteBuilder::new, S3DeleteHandler::new,
                inputs, List.of(keys, count),
                """
                task('delete', S3Delete) {
                    accessKey = 'AKIA...'
                    secretKey = secret('s3SecretKey')
                    bucketName = 'my-bucket'
                    key = 'data/old.zip'
                }
                """);
    }

    public record S3DeleteRequest(
            S3Connection connection, String bucketName, String key) {
    }

    public record S3DeleteResult(List<String> keys, int count) {
        public S3DeleteResult {
            keys = List.copyOf(keys);
        }
    }

    public static final class S3DeleteBuilder
            extends S3Fields.BaseS3Builder<S3DeleteRequest> {
        private String bucketName;
        private String key;

        public S3DeleteBuilder setBucketName(String value) {
            bucketName = value;
            return this;
        }

        public S3DeleteBuilder setKey(String value) {
            key = value;
            return this;
        }

        @Override
        public S3DeleteRequest build() {
            return new S3DeleteRequest(connection(), requiredBucket(bucketName),
                    key == null || key.isBlank() ? null : key);
        }
    }

    static final class S3DeleteHandler
            implements OperationHandler<S3DeleteRequest, S3DeleteResult> {
        @Override
        public S3DeleteResult execute(S3DeleteRequest request,
                                      OperationContext context) throws Exception {
            try (var client = S3Support.open(request.connection(), context)) {
                List<String> keys = new ArrayList<>();
                if (request.key() != null) {
                    S3Support.checkKey(request.key());
                    S3Support.delete(client, request.bucketName(), request.key(), context);
                    keys.add(request.key());
                } else {
                    List<String> remoteKeys = S3Support.keys(client, request.bucketName());
                    for (String key : remoteKeys) {
                        S3Support.delete(client, request.bucketName(), key, context);
                        keys.add(key);
                        context.progress(new Progress("s3-delete", keys.size(),
                                OptionalLong.of(remoteKeys.size()), "objects"));
                    }
                }
                context.log().log(LogLevel.INFO, "gretl.s3.delete.finished",
                        msg(context, "task.s3.delete.finished"),
                        Map.of("s3.objects", Integer.toString(keys.size())), null);
                return new S3DeleteResult(keys, keys.size());
            } catch (S3TaskException exception) {
                logFailure(context, exception);
                throw exception;
            } catch (SdkException exception) {
                S3TaskException wrapped = new S3TaskException("S3_DELETE",
                        msg(context, "task.s3.delete.failed"), "delete", exception);
                logFailure(context, wrapped);
                throw wrapped;
            }
        }

        private static void logFailure(OperationContext context, S3TaskException exception) {
            context.log().log(LogLevel.WARN, "gretl.s3.delete.failed",
                    msg(context, "task.s3.delete.failed"),
                    Map.of("s3.phase", exception.phase()), exception);
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }
    }
}
