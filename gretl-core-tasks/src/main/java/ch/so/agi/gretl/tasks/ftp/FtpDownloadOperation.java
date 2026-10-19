package ch.so.agi.gretl.tasks.ftp;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationHandler;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** FtpDownload task: retrieve files from one remote directory. */
public final class FtpDownloadOperation {
    public static final String ID = "gretl.ftp-download";
    public static final String SYMBOL = "FtpDownload";

    private FtpDownloadOperation() {
    }

    public static OperationType<FtpDownloadRequest, FtpDownloadResult> type() {
        List<InputField<FtpDownloadRequest, ?>> inputs =
                new ArrayList<>(FtpFields.commonInputs());
        inputs.add(new InputField<>(
                "remoteDir", "text", "Remote FTP directory", true,
                Optional.empty(), List.of(), "remoteDir = '/incoming'",
                FtpFields::text,
                (builder, value) -> ((FtpDownloadBuilder) builder).setRemoteDir(value)));
        inputs.add(new InputField<>(
                "localDir", "path", "Local download directory", true,
                Optional.empty(), List.of(), "localDir = workPath('download')",
                FtpFields::path,
                (builder, value) -> ((FtpDownloadBuilder) builder).setLocalDir(value)));
        InputField<FtpDownloadRequest, List<String>> remoteFiles = new InputField<>(
                "remoteFiles", "list", "Remote file names or wildcard patterns; empty means all",
                false, Optional.of(List.of()), List.of(),
                "remoteFiles = ['*.zip']", FtpFields::remoteNames,
                (builder, value) -> ((FtpDownloadBuilder) builder).setRemoteFiles(value));
        inputs.add(remoteFiles);
        inputs.add(new InputField<>(
                "fileType", "text", "ASCII or BINARY transfer mode", false,
                Optional.of(FtpSupport.DEFAULT_FILE_TYPE), List.of("ASCII", "BINARY"),
                "fileType = 'BINARY'", FtpFields::fileType,
                (builder, value) -> ((FtpDownloadBuilder) builder).setFileType(value)));
        inputs.add(new InputField<>(
                "overwrite", "boolean", "Replace existing local files", false,
                Optional.of(true), List.of(), "overwrite = false",
                FtpFields::bool,
                (builder, value) -> ((FtpDownloadBuilder) builder).setOverwrite(value)));
        OutputField<FtpDownloadResult, List<Path>> files = Fields.output(
                "files", "list", "Downloaded files", false, FtpDownloadResult::files);
        OutputField<FtpDownloadResult, Long> bytes = Fields.output(
                "bytes", "integer", "Downloaded bytes", false, FtpDownloadResult::bytes);
        return new OperationType<>(
                ID, SYMBOL, "Download files from an FTP directory",
                FtpDownloadRequest.class, FtpDownloadResult.class,
                FtpDownloadBuilder::new, FtpDownloadHandler::new,
                inputs, List.of(files, bytes),
                """
                task('download', FtpDownload) {
                    server = 'ftp.example.ch'
                    user = 'ftp-user'
                    password = secret('ftpPassword')
                    remoteDir = '/incoming'
                    localDir = workPath('download')
                    remoteFiles = ['*.zip']
                }
                """);
    }

    public record FtpDownloadRequest(
            FtpConnection connection, String remoteDir, Path localDir,
            List<String> remoteFiles, String fileType, boolean overwrite) {
        public FtpDownloadRequest {
            remoteFiles = List.copyOf(remoteFiles);
        }
    }

    public record FtpDownloadResult(List<Path> files, long bytes) {
        public FtpDownloadResult {
            files = List.copyOf(files);
        }
    }

    public static final class FtpDownloadBuilder
            extends FtpFields.BaseFtpBuilder<FtpDownloadRequest> {
        private String remoteDir;
        private Path localDir;
        private List<String> remoteFiles = List.of();
        private String fileType = FtpSupport.DEFAULT_FILE_TYPE;
        private boolean overwrite = true;

        public FtpDownloadBuilder setRemoteDir(String value) { remoteDir = value; return this; }
        public FtpDownloadBuilder setLocalDir(Path value) { localDir = value; return this; }
        public FtpDownloadBuilder setRemoteFiles(List<String> value) {
            remoteFiles = value == null ? List.of() : value;
            return this;
        }
        public FtpDownloadBuilder setFileType(String value) { fileType = value; return this; }
        public FtpDownloadBuilder setOverwrite(Boolean value) { overwrite = Boolean.TRUE.equals(value); return this; }

        @Override
        public FtpDownloadRequest build() {
            if (localDir == null) {
                throw new IllegalArgumentException("localDir is missing");
            }
            for (String name : remoteFiles) {
                FtpSupport.validateRemoteName(name, true);
            }
            return new FtpDownloadRequest(connection(), requiredRemoteDir(remoteDir),
                    localDir.toAbsolutePath().normalize(), remoteFiles, fileType, overwrite);
        }
    }

    static final class FtpDownloadHandler
            implements OperationHandler<FtpDownloadRequest, FtpDownloadResult> {
        @Override
        public FtpDownloadResult execute(FtpDownloadRequest request,
                                         OperationContext context) throws Exception {
            Path localDir = request.localDir();
            if (Files.exists(localDir, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(localDir)) {
                throw new FtpTaskException("FTP_LOCAL_DIR",
                        msg(context, "task.ftp.localDir"), "prepare", null);
            }
            Files.createDirectories(localDir);
            try (FtpSupport.FtpSession session = FtpSupport.open(
                    request.connection(), context)) {
                FtpSupport.setFileType(session.client(), request.fileType());
                List<String> names = request.remoteFiles().isEmpty()
                        ? FtpSupport.list(session.client(), request.remoteDir(), context)
                        : FtpSupport.expand(session.client(), request.remoteDir(),
                                request.remoteFiles(), context);
                List<Path> files = new ArrayList<>();
                long bytes = 0;
                int index = 0;
                for (String name : names) {
                    FtpSupport.validateRemoteName(name, false);
                    Path target = localDir.resolve(name).normalize();
                    if (!target.startsWith(localDir)) {
                        throw new FtpTaskException("FTP_LOCAL_PATH",
                                msg(context, "task.ftp.localPath"), "prepare", null);
                    }
                    prepareTarget(target, request.overwrite(), context);
                    Path temporary = Files.createTempFile(localDir,
                            "." + target.getFileName() + ".", ".part");
                    try {
                        bytes += FtpSupport.retrieve(session.client(),
                                FtpSupport.join(request.remoteDir(), name,
                                        session.separator()), temporary, context);
                        moveIntoPlace(temporary, target, request.overwrite());
                        files.add(target);
                        index++;
                        context.progress(new ch.so.agi.gretl.runtime.api.Progress(
                                "ftp-download", index,
                                java.util.OptionalLong.of(names.size()), "files"));
                    } catch (FtpTaskException exception) {
                        deleteQuietly(temporary, exception);
                        throw exception;
                    } catch (IOException exception) {
                        deleteQuietly(temporary, exception);
                        throw new FtpTaskException("FTP_IO",
                                msg(context, "task.ftp.io"), "download", exception);
                    }
                }
                context.log().log(LogLevel.INFO, "gretl.ftp.download.finished",
                        msg(context, "task.ftp.download.finished"),
                        Map.of("ftp.files", Integer.toString(files.size()),
                                "ftp.bytes", Long.toString(bytes)), null);
                return new FtpDownloadResult(files, bytes);
            } catch (FtpTaskException exception) {
                logFailure(context, exception);
                throw exception;
            } catch (IOException exception) {
                FtpTaskException wrapped = new FtpTaskException("FTP_IO",
                        msg(context, "task.ftp.io"), "download", exception);
                logFailure(context, wrapped);
                throw wrapped;
            }
        }

        private static void prepareTarget(Path target, boolean overwrite,
                                          OperationContext context) throws IOException {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!overwrite) {
                    throw new FtpTaskException("FTP_TARGET_EXISTS",
                            msg(context, "task.ftp.targetExists"), "prepare", null);
                }
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new FtpTaskException("FTP_TARGET_UNSAFE",
                            msg(context, "task.ftp.targetUnsafe"), "prepare", null);
                }
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

        private static void deleteQuietly(Path path, Throwable primary) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                primary.addSuppressed(exception);
            }
        }

        private static void logFailure(OperationContext context,
                                       FtpTaskException exception) {
            context.log().log(LogLevel.WARN, "gretl.ftp.download.failed",
                    msg(context, "task.ftp.download.failed"),
                    Map.of("ftp.phase", exception.phase()), exception);
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }
    }
}
