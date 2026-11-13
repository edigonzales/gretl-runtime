package ch.so.agi.gretl.tasks.io.sftp;

import ch.so.agi.gretl.runtime.api.*;
import ch.so.agi.gretl.tasks.io.internal.RelativePathSelection;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class SftpDeleteOperation {
    private SftpDeleteOperation() {
    }

    public static OperationType<Request, Result> type() {
        return new OperationType<>("gretl.sftp-delete", "SftpDelete", "Delete over SFTP",
                Request.class, Result.class, Builder::new, () -> SftpDeleteOperation::execute,
                List.of(
                        new InputField<Request, String>("connection", "text", "connection", true,
                                Optional.empty(), List.of(), "connection = 'exchange'",
                                SftpSupport::text, (builder, value) -> ((Builder) builder).connection = value),
                        new InputField<Request, List<String>>("remotePaths", "list", "remotePaths", true,
                                Optional.empty(), List.of(), "remotePaths = ['incoming/data.xtf']",
                                SftpSupport::deletePaths, (builder, value) -> ((Builder) builder).remotePaths = value),
                        new InputField<Request, Boolean>("ignoreMissing", "boolean", "ignoreMissing", false,
                                Optional.of(false), List.of(), "ignoreMissing = true",
                                SftpSupport::flag, (builder, value) -> ((Builder) builder).ignoreMissing = value)),
                List.of(Fields.output("deletedPaths", "list", "deletedPaths", false, Result::deletedPaths),
                        Fields.output("missingPaths", "list", "missingPaths", false, Result::missingPaths)),
                """
                task('delete', SftpDelete) {
                    connection = 'exchange'
                    remotePaths = ['incoming/data.xtf']
                }
                """);
    }

    public record Request(String connection, List<String> remotePaths, boolean ignoreMissing) {
        public Request {
            Objects.requireNonNull(connection, "connection");
            Objects.requireNonNull(remotePaths, "remotePaths");
            remotePaths = SftpSupport.deletePaths(remotePaths);
        }
    }

    public record Result(List<String> deletedPaths, List<String> missingPaths) {
        public Result {
            deletedPaths = List.copyOf(deletedPaths);
            missingPaths = List.copyOf(missingPaths);
        }
    }


    private static final class Builder implements ConfigBuilder<Request> {
        private String connection;
        private List<String> remotePaths;
        private boolean ignoreMissing;

        @Override
        public Request build() {
            return new Request(connection, remotePaths, ignoreMissing);
        }
    }

    private static Result execute(Request request, OperationContext context) throws IOException {
        List<String> deleted = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        if (request.remotePaths().isEmpty()) {
            return new Result(deleted, missing);
        }
        try (var connection = SftpConnection.open(SftpSupport.readConfig(request.connection(), context), context)) {
            var client = connection.client();
            for (String path : request.remotePaths()) {
                try {
                    SftpSupport.requireParents(client, SftpSupport.parent(path), context);
                    if (!SftpSupport.lstat(client, path, context).isRegularFile()) {
                        throw SftpSupport.error(context, "REMOTE_TYPE");
                    }
                    client.remove(path);
                    context.cancellation().throwIfCancellationRequested();
                    deleted.add(path);
                } catch (org.apache.sshd.sftp.common.SftpException failure) {
                    if (!request.ignoreMissing() || !SftpSupport.missing(failure)) {
                        throw failure;
                    }
                    missing.add(path);
                }
            }
        } catch (IOException failure) {
            throw SftpSupport.safe(context, failure);
        }
        return new Result(deleted, missing);
    }
}
