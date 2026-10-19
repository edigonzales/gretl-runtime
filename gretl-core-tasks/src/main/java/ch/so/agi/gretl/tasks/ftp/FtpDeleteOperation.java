package ch.so.agi.gretl.tasks.ftp;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/** FtpDelete task: delete files from one remote directory. */
public final class FtpDeleteOperation {
    public static final String ID = "gretl.ftp-delete";
    public static final String SYMBOL = "FtpDelete";

    private FtpDeleteOperation() {
    }

    public static OperationType<FtpDeleteRequest, FtpDeleteResult> type() {
        List<InputField<FtpDeleteRequest, ?>> inputs =
                new ArrayList<>(FtpFields.commonInputs());
        inputs.add(new InputField<>(
                "remoteDir", "text", "Remote FTP directory", true,
                Optional.empty(), List.of(), "remoteDir = '/incoming'",
                FtpFields::text,
                (builder, value) -> ((FtpDeleteBuilder) builder).setRemoteDir(value)));
        InputField<FtpDeleteRequest, List<String>> remoteFiles = new InputField<>(
                "remoteFiles", "list", "Remote file names or wildcard patterns; empty means all",
                false, Optional.of(List.of()), List.of(),
                "remoteFiles = ['*.tmp']", FtpFields::remoteNames,
                (builder, value) -> ((FtpDeleteBuilder) builder).setRemoteFiles(value));
        inputs.add(remoteFiles);
        OutputField<FtpDeleteResult, List<String>> files = Fields.output(
                "files", "list", "Deleted remote file names", false,
                FtpDeleteResult::files);
        OutputField<FtpDeleteResult, Integer> count = Fields.output(
                "count", "integer", "Number of deleted files", false,
                FtpDeleteResult::count);
        return new OperationType<>(
                ID, SYMBOL, "Delete files from an FTP directory",
                FtpDeleteRequest.class, FtpDeleteResult.class,
                FtpDeleteBuilder::new, FtpDeleteHandler::new,
                inputs, List.of(files, count),
                """
                task('delete', FtpDelete) {
                    server = 'ftp.example.ch'
                    user = 'ftp-user'
                    password = secret('ftpPassword')
                    remoteDir = '/incoming'
                    remoteFiles = ['*.tmp']
                }
                """);
    }

    public record FtpDeleteRequest(
            FtpConnection connection, String remoteDir, List<String> remoteFiles) {
        public FtpDeleteRequest {
            remoteFiles = List.copyOf(remoteFiles);
        }
    }

    public record FtpDeleteResult(List<String> files, int count) {
        public FtpDeleteResult {
            files = List.copyOf(files);
        }
    }

    public static final class FtpDeleteBuilder
            extends FtpFields.BaseFtpBuilder<FtpDeleteRequest> {
        private String remoteDir;
        private List<String> remoteFiles = List.of();

        public FtpDeleteBuilder setRemoteDir(String value) { remoteDir = value; return this; }
        public FtpDeleteBuilder setRemoteFiles(List<String> value) {
            remoteFiles = value == null ? List.of() : value;
            return this;
        }

        @Override
        public FtpDeleteRequest build() {
            for (String name : remoteFiles) {
                FtpSupport.validateRemoteName(name, true);
            }
            return new FtpDeleteRequest(connection(), requiredRemoteDir(remoteDir),
                    remoteFiles);
        }
    }

    static final class FtpDeleteHandler
            implements OperationHandler<FtpDeleteRequest, FtpDeleteResult> {
        @Override
        public FtpDeleteResult execute(FtpDeleteRequest request,
                                       OperationContext context) throws Exception {
            try (FtpSupport.FtpSession session = FtpSupport.open(
                    request.connection(), context)) {
                List<String> names = request.remoteFiles().isEmpty()
                        ? FtpSupport.list(session.client(), request.remoteDir(), context)
                        : FtpSupport.expand(session.client(), request.remoteDir(),
                                request.remoteFiles(), context);
                List<String> deleted = new ArrayList<>();
                int index = 0;
                for (String name : names) {
                    FtpSupport.validateRemoteName(name, false);
                    FtpSupport.delete(session.client(),
                            FtpSupport.join(request.remoteDir(), name,
                                    session.separator()), context);
                    deleted.add(name);
                    index++;
                    context.progress(new Progress("ftp-delete", index,
                            OptionalLong.of(names.size()), "files"));
                }
                context.log().log(LogLevel.INFO, "gretl.ftp.delete.finished",
                        msg(context, "task.ftp.delete.finished"),
                        Map.of("ftp.files", Integer.toString(deleted.size())), null);
                return new FtpDeleteResult(deleted, deleted.size());
            } catch (FtpTaskException exception) {
                logFailure(context, exception);
                throw exception;
            } catch (IOException exception) {
                FtpTaskException wrapped = new FtpTaskException("FTP_IO",
                        msg(context, "task.ftp.io"), "delete", exception);
                logFailure(context, wrapped);
                throw wrapped;
            }
        }

        private static void logFailure(OperationContext context,
                                       FtpTaskException exception) {
            context.log().log(LogLevel.WARN, "gretl.ftp.delete.failed",
                    msg(context, "task.ftp.delete.failed"),
                    Map.of("ftp.phase", exception.phase()), exception);
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }
    }
}
