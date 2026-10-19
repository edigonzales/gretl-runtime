package ch.so.agi.gretl.tasks.s3;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import ch.so.agi.gretl.runtime.api.Progress;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import software.amazon.awssdk.core.exception.SdkException;

/** S3Download task: download one object or all objects from a bucket. */
public final class S3DownloadOperation {
    public static final String ID = "gretl.s3-download";
    public static final String SYMBOL = "S3Download";

    private S3DownloadOperation() {
    }

    public static OperationType<S3DownloadRequest, S3DownloadResult> type() {
        List<InputField<S3DownloadRequest, ?>> inputs =
                new ArrayList<>(S3Fields.commonInputs());
        inputs.add(new InputField<>(
                "bucketName", "text", "S3 bucket name", true,
                Optional.empty(), List.of(), "bucketName = 'my-bucket'",
                S3Fields::text,
                (builder, value) -> ((S3DownloadBuilder) builder).setBucketName(value)));
        inputs.add(new InputField<>(
                "key", "text", "Object key; empty means all objects", false,
                Optional.empty(), List.of(), "key = 'data/export.zip'",
                S3Fields::text,
                (builder, value) -> ((S3DownloadBuilder) builder).setKey(value)));
        inputs.add(new InputField<>(
                "downloadDir", "path", "Local download directory", true,
                Optional.empty(), List.of(), "downloadDir = workPath('download')",
                S3Fields::path,
                (builder, value) -> ((S3DownloadBuilder) builder).setDownloadDir(value)));
        inputs.add(new InputField<>(
                "overwrite", "boolean", "Replace existing local files", false,
                Optional.of(true), List.of(), "overwrite = false",
                S3Fields::bool,
                (builder, value) -> ((S3DownloadBuilder) builder).setOverwrite(value)));
        OutputField<S3DownloadResult, List<Path>> files = Fields.output(
                "files", "list", "Downloaded files", false, S3DownloadResult::files);
        OutputField<S3DownloadResult, Integer> count = Fields.output(
                "count", "integer", "Number of downloaded objects", false,
                S3DownloadResult::count);
        OutputField<S3DownloadResult, Long> bytes = Fields.output(
                "bytes", "integer", "Downloaded bytes", false, S3DownloadResult::bytes);
        return new OperationType<>(
                ID, SYMBOL, "Download objects from an S3 bucket",
                S3DownloadRequest.class, S3DownloadResult.class,
                S3DownloadBuilder::new, S3DownloadHandler::new,
                inputs, List.of(files, count, bytes),
                """
                task('download', S3Download) {
                    accessKey = 'AKIA...'
                    secretKey = secret('s3SecretKey')
                    bucketName = 'my-bucket'
                    downloadDir = workPath('download')
                    key = 'data/export.zip'
                }
                """);
    }

    public record S3DownloadRequest(
            S3Connection connection, String bucketName, String key,
            Path downloadDir, boolean overwrite) {
    }

    public record S3DownloadResult(List<Path> files, int count, long bytes) {
        public S3DownloadResult {
            files = List.copyOf(files);
        }
    }

    public static final class S3DownloadBuilder
            extends S3Fields.BaseS3Builder<S3DownloadRequest> {
        private String bucketName;
        private String key;
        private Path downloadDir;
        private boolean overwrite = true;

        public S3DownloadBuilder setBucketName(String value) {
            bucketName = value;
            return this;
        }

        public S3DownloadBuilder setKey(String value) {
            key = value;
            return this;
        }

        public S3DownloadBuilder setDownloadDir(Path value) {
            downloadDir = value;
            return this;
        }

        public S3DownloadBuilder setOverwrite(Boolean value) {
            overwrite = Boolean.TRUE.equals(value);
            return this;
        }

        @Override
        public S3DownloadRequest build() {
            if (downloadDir == null) {
                throw new IllegalArgumentException("downloadDir is missing");
            }
            return new S3DownloadRequest(connection(), requiredBucket(bucketName),
                    key == null || key.isBlank() ? null : key,
                    downloadDir.toAbsolutePath().normalize(), overwrite);
        }
    }

    static final class S3DownloadHandler
            implements OperationHandler<S3DownloadRequest, S3DownloadResult> {
        @Override
        public S3DownloadResult execute(S3DownloadRequest request,
                                        OperationContext context) throws Exception {
            if (Files.exists(request.downloadDir(), LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(request.downloadDir())) {
                throw new S3TaskException("S3_LOCAL_DIR",
                        msg(context, "task.s3.localDir"), "prepare", null);
            }
            Files.createDirectories(request.downloadDir());
            try (var client = S3Support.open(request.connection(), context)) {
                List<Path> files = new ArrayList<>();
                long bytes = 0;
                int count = 0;
                if (request.key() != null) {
                    S3Support.checkKey(request.key());
                    Path target = resolveTarget(request.downloadDir(), request.key(), true, context);
                    prepareTarget(target, request.overwrite(), context);
                    bytes += S3Support.download(client, request.bucketName(), request.key(),
                            target, request.overwrite(), context);
                    files.add(target);
                    count = 1;
                } else {
                    List<String> keys = S3Support.keys(client, request.bucketName());
                    for (String key : keys) {
                        if (key.endsWith("/")) {
                            continue;
                        }
                        Path target = resolveTarget(request.downloadDir(), key, false, context);
                        prepareTarget(target, request.overwrite(), context);
                        bytes += S3Support.download(client, request.bucketName(), key,
                                target, request.overwrite(), context);
                        files.add(target);
                        count++;
                        context.progress(new Progress("s3-download", count,
                                OptionalLong.of(keys.size()), "objects"));
                    }
                }
                context.log().log(LogLevel.INFO, "gretl.s3.download.finished",
                        msg(context, "task.s3.download.finished"),
                        Map.of("s3.objects", Integer.toString(count),
                                "s3.bytes", Long.toString(bytes)), null);
                return new S3DownloadResult(files, count, bytes);
            } catch (S3TaskException exception) {
                logFailure(context, exception);
                throw exception;
            } catch (SdkException | IOException exception) {
                S3TaskException wrapped = new S3TaskException("S3_DOWNLOAD",
                        msg(context, "task.s3.download.failed"), "download", exception);
                logFailure(context, wrapped);
                throw wrapped;
            }
        }

        private static Path resolveTarget(Path downloadDir, String key, boolean explicit,
                                          OperationContext context) {
            try {
                return S3Support.downloadTarget(downloadDir, key, explicit);
            } catch (IllegalArgumentException exception) {
                throw new S3TaskException("S3_LOCAL_PATH",
                        msg(context, "task.s3.localPath"), "prepare", exception);
            }
        }

        private static void prepareTarget(Path target, boolean overwrite,
                                          OperationContext context) {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!overwrite) {
                    throw new S3TaskException("S3_TARGET_EXISTS",
                            msg(context, "task.s3.targetExists"), "prepare", null);
                }
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new S3TaskException("S3_TARGET_UNSAFE",
                            msg(context, "task.s3.targetUnsafe"), "prepare", null);
                }
            }
        }

        private static void logFailure(OperationContext context, S3TaskException exception) {
            context.log().log(LogLevel.WARN, "gretl.s3.download.failed",
                    msg(context, "task.s3.download.failed"),
                    Map.of("s3.phase", exception.phase()), exception);
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }
    }
}
