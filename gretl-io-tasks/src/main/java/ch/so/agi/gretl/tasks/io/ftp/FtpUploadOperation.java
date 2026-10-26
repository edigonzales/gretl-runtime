package ch.so.agi.gretl.tasks.io.ftp;

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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** FtpUpload task: upload one local file into a remote directory. */
public final class FtpUploadOperation {
    public static final String ID = "gretl.ftp-upload";
    public static final String SYMBOL = "FtpUpload";

    private FtpUploadOperation() {
    }

    public static OperationType<FtpUploadRequest, FtpUploadResult> type() {
        List<InputField<FtpUploadRequest, ?>> inputs =
                new ArrayList<>(FtpFields.commonInputs());
        inputs.add(new InputField<>(
                "localFile", "path", "Local file to upload", true,
                Optional.empty(), List.of(), "localFile = file('data/upload.zip')",
                FtpFields::path,
                (builder, value) -> ((FtpUploadBuilder) builder).setLocalFile(value)));
        inputs.add(new InputField<>(
                "remoteDir", "text", "Target FTP directory", true,
                Optional.empty(), List.of(), "remoteDir = '/incoming'",
                FtpFields::text,
                (builder, value) -> ((FtpUploadBuilder) builder).setRemoteDir(value)));
        inputs.add(new InputField<>(
                "fileType", "text", "ASCII or BINARY transfer mode", false,
                Optional.of(FtpSupport.DEFAULT_FILE_TYPE), List.of("ASCII", "BINARY"),
                "fileType = 'BINARY'", FtpFields::fileType,
                (builder, value) -> ((FtpUploadBuilder) builder).setFileType(value)));
        OutputField<FtpUploadResult, String> remoteFile = Fields.output(
                "remoteFile", "text", "Remote path written", false,
                FtpUploadResult::remoteFile);
        OutputField<FtpUploadResult, Long> bytes = Fields.output(
                "bytes", "integer", "Uploaded bytes", false, FtpUploadResult::bytes);
        return new OperationType<>(
                ID, SYMBOL, "Upload a file to an FTP directory",
                FtpUploadRequest.class, FtpUploadResult.class,
                FtpUploadBuilder::new, FtpUploadHandler::new,
                inputs, List.of(remoteFile, bytes),
                """
                task('upload', FtpUpload) {
                    server = 'ftp.example.ch'
                    user = 'ftp-user'
                    password = secret('ftpPassword')
                    localFile = file('data/upload.zip')
                    remoteDir = '/incoming'
                    fileType = 'BINARY'
                }
                """);
    }

    public record FtpUploadRequest(
            FtpConnection connection, Path localFile, String remoteDir, String fileType) {
    }

    public record FtpUploadResult(String remoteFile, long bytes) {
    }

    public static final class FtpUploadBuilder
            extends FtpFields.BaseFtpBuilder<FtpUploadRequest> {
        private Path localFile;
        private String remoteDir;
        private String fileType = FtpSupport.DEFAULT_FILE_TYPE;

        public void setLocalFile(Path value) { localFile = value; }
        public void setRemoteDir(String value) { remoteDir = value; }
        public void setFileType(String value) { fileType = value; }

        @Override
        public FtpUploadRequest build() {
            if (localFile == null) {
                throw new IllegalArgumentException("localFile is missing");
            }
            return new FtpUploadRequest(connection(),
                    localFile.toAbsolutePath().normalize(),
                    requiredRemoteDir(remoteDir), fileType);
        }
    }

    static final class FtpUploadHandler
            implements OperationHandler<FtpUploadRequest, FtpUploadResult> {
        @Override
        public FtpUploadResult execute(FtpUploadRequest request,
                                       OperationContext context) throws Exception {
            Path localFile = request.localFile();
            if (!Files.isRegularFile(localFile) || !Files.isReadable(localFile)) {
                throw new FtpTaskException("FTP_FILE",
                        msg(context, "task.ftp.file"), "prepare", null);
            }
            String fileName = localFile.getFileName().toString();
            FtpSupport.validateRemoteName(fileName, false);
            try (FtpSupport.FtpSession session = FtpSupport.open(
                    request.connection(), context)) {
                FtpSupport.setFileType(session.client(), request.fileType());
                String remotePath = FtpSupport.join(request.remoteDir(), fileName,
                        session.separator());
                long bytes = FtpSupport.store(session.client(), remotePath, localFile, context);
                context.log().log(LogLevel.INFO, "gretl.ftp.upload.finished",
                        msg(context, "task.ftp.upload.finished"),
                        Map.of("ftp.file", fileName, "ftp.bytes", Long.toString(bytes)),
                        null);
                return new FtpUploadResult(remotePath, bytes);
            } catch (FtpTaskException exception) {
                logFailure(context, exception);
                throw exception;
            } catch (IOException exception) {
                FtpTaskException wrapped = new FtpTaskException("FTP_IO",
                        msg(context, "task.ftp.io"), "upload", exception);
                logFailure(context, wrapped);
                throw wrapped;
            }
        }

        private static void logFailure(OperationContext context,
                                       FtpTaskException exception) {
            context.log().log(LogLevel.WARN, "gretl.ftp.upload.failed",
                    msg(context, "task.ftp.upload.failed"),
                    Map.of("ftp.phase", exception.phase()), exception);
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }
    }
}
