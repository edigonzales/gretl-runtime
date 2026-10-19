package ch.so.agi.gretl.tasks.basic;

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
import ch.so.agi.gretl.runtime.api.Progress;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.zip.GZIPOutputStream;

/** Gzip task: write a GZIP-compressed copy of one file. */
public final class GzipOperation {
    public static final String ID = "gretl.gzip";
    public static final String SYMBOL = "Gzip";

    private GzipOperation() {
    }

    public static OperationType<GzipRequest, GzipResult> type() {
        InputField<GzipRequest, Path> dataFile = new InputField<>(
                "dataFile", "path", "Existing input file to compress", true,
                Optional.empty(), List.of(),
                "dataFile = file('data/input.xml')",
                GzipOperation::path,
                (builder, value) -> ((GzipBuilder) builder).setDataFile(value));
        InputField<GzipRequest, Path> gzipFile = new InputField<>(
                "gzipFile", "path", "GZIP output file", true,
                Optional.empty(), List.of(),
                "gzipFile = workPath('out/input.xml.gz')",
                GzipOperation::path,
                (builder, value) -> ((GzipBuilder) builder).setGzipFile(value));
        InputField<GzipRequest, Boolean> overwrite = new InputField<>(
                "overwrite", "boolean", "Replace an existing output file",
                false, Optional.of(true), List.of(), "overwrite = false",
                GzipOperation::bool,
                (builder, value) -> ((GzipBuilder) builder).setOverwrite(value));
        OutputField<GzipResult, Path> file = Fields.output(
                "file", "path", "Written GZIP file", false, GzipResult::file);
        OutputField<GzipResult, Long> bytes = Fields.output(
                "bytes", "integer", "Size of the GZIP file", false, GzipResult::bytes);
        return new OperationType<>(
                ID, SYMBOL, "Write a GZIP-compressed copy of a file",
                GzipRequest.class, GzipResult.class,
                GzipBuilder::new, GzipHandler::new,
                List.of(dataFile, gzipFile, overwrite),
                List.of(file, bytes),
                """
                task('compress', Gzip) {
                    dataFile = file('data/input.xml')
                    gzipFile = workPath('out/input.xml.gz')
                }
                """);
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new IllegalArgumentException("overwrite must be true or false");
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

    public record GzipRequest(Path dataFile, Path gzipFile, boolean overwrite) {
        public GzipRequest {
            Objects.requireNonNull(dataFile, "dataFile");
            Objects.requireNonNull(gzipFile, "gzipFile");
        }
    }

    public record GzipResult(Path file, long bytes) {
    }

    public static final class GzipBuilder implements ConfigBuilder<GzipRequest> {
        private Path dataFile;
        private Path gzipFile;
        private boolean overwrite = true;

        public void setDataFile(Path value) { dataFile = value; }
        public void setGzipFile(Path value) { gzipFile = value; }
        public void setOverwrite(Boolean value) { overwrite = Boolean.TRUE.equals(value); }

        @Override
        public GzipRequest build() {
            if (dataFile == null) {
                throw new IllegalArgumentException("dataFile is missing");
            }
            if (gzipFile == null) {
                throw new IllegalArgumentException("gzipFile is missing");
            }
            return new GzipRequest(dataFile.toAbsolutePath().normalize(),
                    gzipFile.toAbsolutePath().normalize(), overwrite);
        }
    }

    static final class GzipHandler implements OperationHandler<GzipRequest, GzipResult> {
        @Override
        public GzipResult execute(GzipRequest request, OperationContext context)
                throws Exception {
            CancellationToken cancellation = context.cancellation();
            cancellation.throwIfCancellationRequested();
            Path source = request.dataFile();
            Path target = request.gzipFile();
            if (source.equals(target)) {
                throw new GzipTaskException("GZIP_TARGET_INVALID",
                        msg(context, "task.gzip.samePath"), "prepare", null);
            }
            if (!Files.isRegularFile(source) || !Files.isReadable(source)) {
                throw new GzipTaskException("GZIP_INPUT",
                        msg(context, "task.gzip.input"), "prepare", null);
            }
            prepareTarget(target, request.overwrite(), context);
            long sourceBytes = Files.size(source);
            Path temporary = Files.createTempFile(target.getParent(),
                    "." + target.getFileName() + ".", ".part");
            try {
                try (InputStream input = Files.newInputStream(source);
                     OutputStream output = Files.newOutputStream(temporary);
                     GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                    byte[] buffer = new byte[8192];
                    long readTotal = 0;
                    while (true) {
                        cancellation.throwIfCancellationRequested();
                        if (context.budget().expired()) {
                            throw new GzipTaskException("GZIP_TIMEOUT",
                                    msg(context, "task.gzip.timeout"), "execute", null);
                        }
                        int read = input.read(buffer);
                        if (read < 0) {
                            break;
                        }
                        gzip.write(buffer, 0, read);
                        readTotal += read;
                        context.progress(new Progress("gzip", readTotal,
                                OptionalLong.of(sourceBytes), "bytes"));
                    }
                }
                moveIntoPlace(temporary, target, request.overwrite());
                long bytes = Files.size(target);
                context.log().log(LogLevel.INFO, "gretl.gzip.finished",
                        msg(context, "task.gzip.finished"),
                        Map.of("file.name", target.getFileName().toString(),
                                "file.bytes", Long.toString(bytes)), null);
                return new GzipResult(target, bytes);
            } catch (GzipTaskException exception) {
                deleteQuietly(temporary, exception);
                logFailure(context, target, exception);
                throw exception;
            } catch (IOException exception) {
                deleteQuietly(temporary, exception);
                GzipTaskException wrapped = new GzipTaskException("GZIP_IO",
                        msg(context, "task.gzip.io"), "execute", exception);
                logFailure(context, target, wrapped);
                throw wrapped;
            } catch (RuntimeException exception) {
                deleteQuietly(temporary, exception);
                throw exception;
            }
        }

        private static void prepareTarget(Path target, boolean overwrite,
                                          OperationContext context) throws IOException {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!overwrite) {
                    throw new GzipTaskException("GZIP_TARGET_EXISTS",
                            msg(context, "task.gzip.targetExists"), "prepare", null);
                }
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new GzipTaskException("GZIP_TARGET_UNSAFE",
                            msg(context, "task.gzip.targetUnsafe"), "prepare", null);
                }
            }
            if (target.getParent() == null) {
                throw new GzipTaskException("GZIP_TARGET_INVALID",
                        msg(context, "task.gzip.targetInvalid"), "prepare", null);
            }
            Files.createDirectories(target.getParent());
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

        private static void logFailure(OperationContext context, Path target,
                                       GzipTaskException exception) {
            context.log().log(LogLevel.WARN, "gretl.gzip.failed",
                    msg(context, "task.gzip.failed"),
                    Map.of("file.name", target.getFileName().toString(),
                            "gzip.phase", exception.phase()), exception);
        }

        private static void deleteQuietly(Path path, Throwable primary) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                primary.addSuppressed(exception);
            }
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }
    }
}
