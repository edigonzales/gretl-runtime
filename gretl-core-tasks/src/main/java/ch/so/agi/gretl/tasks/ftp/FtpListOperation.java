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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** FtpList task: list files in one remote directory. */
public final class FtpListOperation {
    public static final String ID = "gretl.ftp-list";
    public static final String SYMBOL = "FtpList";

    private FtpListOperation() {
    }

    public static OperationType<FtpListRequest, FtpListResult> type() {
        List<InputField<FtpListRequest, ?>> inputs =
                new ArrayList<>(FtpFields.commonInputs());
        inputs.add(new InputField<>(
                "remoteDir", "text", "Remote FTP directory", true,
                Optional.empty(), List.of(), "remoteDir = '/incoming'",
                FtpFields::text,
                (builder, value) -> ((FtpListBuilder) builder).setRemoteDir(value)));
        OutputField<FtpListResult, List<String>> files = Fields.output(
                "files", "list", "Remote file names", false, FtpListResult::files);
        return new OperationType<>(
                ID, SYMBOL, "List files in an FTP directory",
                FtpListRequest.class, FtpListResult.class,
                FtpListBuilder::new, FtpListHandler::new,
                inputs, List.of(files),
                """
                task('list', FtpList) {
                    server = 'ftp.example.ch'
                    user = 'ftp-user'
                    password = secret('ftpPassword')
                    remoteDir = '/incoming'
                }
                """);
    }

    public record FtpListRequest(FtpConnection connection, String remoteDir) {
    }

    public record FtpListResult(List<String> files) {
        public FtpListResult {
            files = List.copyOf(files);
        }
    }

    public static final class FtpListBuilder
            extends FtpFields.BaseFtpBuilder<FtpListRequest> {
        private String remoteDir;

        public void setRemoteDir(String value) { remoteDir = value; }

        @Override
        public FtpListRequest build() {
            return new FtpListRequest(connection(), requiredRemoteDir(remoteDir));
        }
    }

    static final class FtpListHandler implements OperationHandler<FtpListRequest, FtpListResult> {
        @Override
        public FtpListResult execute(FtpListRequest request, OperationContext context)
                throws Exception {
            try (FtpSupport.FtpSession session = FtpSupport.open(
                    request.connection(), context)) {
                List<String> files = FtpSupport.list(session.client(),
                        request.remoteDir(), context);
                context.log().log(LogLevel.INFO, "gretl.ftp.list.finished",
                        msg(context, "task.ftp.list.finished"),
                        Map.of("ftp.files", Integer.toString(files.size())), null);
                return new FtpListResult(files);
            } catch (FtpTaskException exception) {
                logFailure(context, exception);
                throw exception;
            } catch (IOException exception) {
                FtpTaskException wrapped = new FtpTaskException("FTP_IO",
                        msg(context, "task.ftp.io"), "list", exception);
                logFailure(context, wrapped);
                throw wrapped;
            }
        }

        private static void logFailure(OperationContext context,
                                       FtpTaskException exception) {
            context.log().log(LogLevel.WARN, "gretl.ftp.list.failed",
                    msg(context, "task.ftp.list.failed"),
                    Map.of("ftp.phase", exception.phase()), exception);
        }

        private static String msg(OperationContext context, String key, Object... args) {
            return Messages.format(context.locale(), key, args);
        }
    }
}
