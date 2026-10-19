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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import software.amazon.awssdk.core.exception.SdkException;

/** S3Upload task: upload one file, a directory or a list of files. */
public final class S3UploadOperation {
    public static final String ID = "gretl.s3-upload";
    public static final String SYMBOL = "S3Upload";

    private S3UploadOperation() {
    }

    public static OperationType<S3UploadRequest, S3UploadResult> type() {
        List<InputField<S3UploadRequest, ?>> inputs =
                new ArrayList<>(S3Fields.commonInputs());
        inputs.add(new InputField<>(
                "bucketName", "text", "S3 bucket name", true,
                Optional.empty(), List.of(), "bucketName = 'my-bucket'",
                S3Fields::text,
                (builder, value) -> ((S3UploadBuilder) builder).setBucketName(value)));
        inputs.add(new InputField<>(
                "sourceFile", "path", "Single file to upload", false,
                Optional.empty(), List.of(), "sourceFile = file('data/export.zip')",
                S3Fields::path,
                (builder, value) -> ((S3UploadBuilder) builder).setSourceFile(value)));
        inputs.add(new InputField<>(
                "sourceDir", "path", "Directory whose direct files are uploaded", false,
                Optional.empty(), List.of(), "sourceDir = path('data')",
                S3Fields::path,
                (builder, value) -> ((S3UploadBuilder) builder).setSourceDir(value)));
        InputField<S3UploadRequest, List<Path>> sourceFiles = new InputField<>(
                "sourceFiles", "list", "Files to upload", false,
                Optional.of(List.of()), List.of(), "sourceFiles = [file('a.txt')]",
                S3Fields::paths,
                (builder, value) -> ((S3UploadBuilder) builder).setSourceFiles(value));
        inputs.add(sourceFiles);
        inputs.add(new InputField<>(
                "acl", "text", "S3 canned ACL", false,
                Optional.of(S3Fields.DEFAULT_ACL), List.of(),
                "acl = 'private'", S3Fields::acl,
                (builder, value) -> ((S3UploadBuilder) builder).setAcl(value)));
        inputs.add(new InputField<>(
                "contentType", "text", "Content type of uploaded objects", false,
                Optional.empty(), List.of(), "contentType = 'application/zip'",
                S3Fields::text,
                (builder, value) -> ((S3UploadBuilder) builder).setContentType(value)));
        InputField<S3UploadRequest, Map<String, String>> metadata = new InputField<>(
                "metadata", "map", "S3 object metadata", false,
                Optional.of(Map.of()), List.of(), "metadata = [source: 'export']",
                S3Fields::metadata,
                (builder, value) -> ((S3UploadBuilder) builder).setMetadata(value));
        inputs.add(metadata);
        OutputField<S3UploadResult, List<String>> keys = Fields.output(
                "keys", "list", "Uploaded object keys", false, S3UploadResult::keys);
        OutputField<S3UploadResult, Integer> count = Fields.output(
                "count", "integer", "Number of uploaded objects", false,
                S3UploadResult::count);
        OutputField<S3UploadResult, Long> bytes = Fields.output(
                "bytes", "integer", "Uploaded bytes", false, S3UploadResult::bytes);
        return new OperationType<>(
                ID, SYMBOL, "Upload files to an S3 bucket",
                S3UploadRequest.class, S3UploadResult.class,
                S3UploadBuilder::new, S3UploadHandler::new,
                inputs, List.of(keys, count, bytes),
                """
                task('upload', S3Upload) {
                    accessKey = 'AKIA...'
                    secretKey = secret('s3SecretKey')
                    bucketName = 'my-bucket'
                    sourceFile = file('data/export.zip')
                    acl = 'private'
                }
                """);
    }

    public record S3UploadRequest(
            S3Connection connection, String bucketName, List<Path> sourceFiles,
            String acl, String contentType, Map<String, String> metadata) {
        public S3UploadRequest {
            sourceFiles = List.copyOf(sourceFiles);
            metadata = Map.copyOf(metadata);
        }
    }

    public record S3UploadResult(List<String> keys, int count, long bytes) {
        public S3UploadResult {
            keys = List.copyOf(keys);
        }
    }

    public static final class S3UploadBuilder
            extends S3Fields.BaseS3Builder<S3UploadRequest> {
        private String bucketName;
        private Path sourceFile;
        private Path sourceDir;
        private List<Path> sourceFiles = List.of();
        private String acl = S3Fields.DEFAULT_ACL;
        private String contentType;
        private Map<String, String> metadata = Map.of();

        public S3UploadBuilder setBucketName(String value) {
            bucketName = value;
            return this;
        }

        public S3UploadBuilder setSourceFile(Path value) {
            sourceFile = value;
            return this;
        }

        public S3UploadBuilder setSourceDir(Path value) {
            sourceDir = value;
            return this;
        }

        public S3UploadBuilder setSourceFiles(List<Path> value) {
            sourceFiles = value == null ? List.of() : value;
            return this;
        }

        public S3UploadBuilder setAcl(String value) {
            acl = value;
            return this;
        }

        public S3UploadBuilder setContentType(String value) {
            contentType = value;
            return this;
        }

        public S3UploadBuilder setMetadata(Map<String, String> value) {
            metadata = value == null ? Map.of() : value;
            return this;
        }

        @Override
        public S3UploadRequest build() {
            List<Path> sources = new ArrayList<>();
            if (sourceFile != null) {
                sources.add(sourceFile.toAbsolutePath().normalize());
            }
            if (sourceDir != null) {
                Path directory = sourceDir.toAbsolutePath().normalize();
                if (!Files.isDirectory(directory)) {
                    throw new IllegalArgumentException("sourceDir is not a directory");
                }
                try (var children = Files.list(directory)) {
                    children.filter(Files::isRegularFile)
                            .sorted()
                            .forEach(sources::add);
                } catch (IOException exception) {
                    throw new IllegalArgumentException(
                            "sourceDir could not be read", exception);
                }
            }
            for (Path path : sourceFiles) {
                sources.add(path.toAbsolutePath().normalize());
            }
            LinkedHashSet<Path> unique = new LinkedHashSet<>(sources);
            if (unique.isEmpty()) {
                throw new IllegalArgumentException(
                        "sourceFile, sourceDir or sourceFiles must not be empty");
            }
            Map<String, Path> names = new LinkedHashMap<>();
            for (Path source : unique) {
                if (!Files.isRegularFile(source)) {
                    throw new IllegalArgumentException(
                            "source file does not exist: " + source);
                }
                String name = source.getFileName().toString();
                Path previous = names.putIfAbsent(name, source);
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "duplicate S3 object key: " + name);
                }
            }
            return new S3UploadRequest(connection(), requiredBucket(bucketName),
                    List.copyOf(unique), S3Fields.acl(acl),
                    contentType == null || contentType.isBlank() ? null : contentType,
                    metadata);
        }
    }

    static final class S3UploadHandler
            implements OperationHandler<S3UploadRequest, S3UploadResult> {
        @Override
        public S3UploadResult execute(S3UploadRequest request,
                                      OperationContext context) throws Exception {
            try (var client = S3Support.open(request.connection(), context)) {
                List<String> keys = new ArrayList<>();
                long bytes = 0;
                int count = 0;
                for (Path source : request.sourceFiles()) {
                    String key = source.getFileName().toString();
                    S3Support.checkKey(key);
                    bytes += S3Support.upload(client, request.bucketName(), key, source,
                            request.acl(), request.contentType(), request.metadata(), context);
                    keys.add(key);
                    count++;
                    context.progress(new Progress("s3-upload", count,
                            OptionalLong.of(request.sourceFiles().size()), "objects"));
                }
                context.log().log(LogLevel.INFO, "gretl.s3.upload.finished",
                        msg(context, "task.s3.upload.finished"),
                        Map.of("s3.objects", Integer.toString(count),
                                "s3.bytes", Long.toString(bytes)), null);
                return new S3UploadResult(keys, count, bytes);
            } catch (S3TaskException exception) {
                logFailure(context, exception);
                throw exception;
            } catch (SdkException | IOException exception) {
                S3TaskException wrapped = new S3TaskException("S3_UPLOAD",
                        msg(context, "task.s3.upload.failed"), "upload", exception);
                logFailure(context, wrapped);
                throw wrapped;
            }
        }

        private static void logFailure(OperationContext context, S3TaskException exception) {
            context.log().log(LogLevel.WARN, "gretl.s3.upload.failed",
                    msg(context, "task.s3.upload.failed"),
                    Map.of("s3.phase", exception.phase()), exception);
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }
    }
}
