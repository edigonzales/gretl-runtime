package ch.so.agi.gretl.tasks.io.sftp;

import ch.so.agi.gretl.runtime.api.*;
import ch.so.agi.gretl.tasks.io.internal.RelativePathSelection;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class SftpUploadOperation {
    private SftpUploadOperation() {
    }

    public static OperationType<Request, Result> type() {
        return new OperationType<>("gretl.sftp-upload", "SftpUpload", "Upload over SFTP",
                Request.class, Result.class, Builder::new, () -> SftpUploadOperation::execute,
                List.of(
                        new InputField<Request, String>("connection", "text", "connection", true,
                                Optional.empty(), List.of(), "connection = 'exchange'",
                                SftpSupport::text, (builder, value) -> ((Builder) builder).connection = value),
                        new InputField<Request, Path>("sourceFile", "path", "sourceFile", true,
                                Optional.empty(), List.of(), "sourceFile = file('data.xtf')",
                                SftpSupport::localPath, (builder, value) -> ((Builder) builder).sourceFile = value),
                        new InputField<Request, String>("remotePath", "text", "remotePath", true,
                                Optional.empty(), List.of(), "remotePath = 'incoming/data.xtf'",
                                SftpSupport::filePath, (builder, value) -> ((Builder) builder).remotePath = value),
                        new InputField<Request, Boolean>("overwrite", "boolean", "overwrite", false,
                                Optional.of(false), List.of(), "overwrite = true",
                                SftpSupport::flag, (builder, value) -> ((Builder) builder).overwrite = value)),
                List.of(Fields.output("remotePath", "text", "remotePath", false, Result::remotePath),
                        Fields.output("bytes", "integer", "bytes", false, Result::bytes)),
                """
                task('upload', SftpUpload) {
                    connection = 'exchange'
                    sourceFile = file('data.xtf')
                    remotePath = 'outgoing/data.xtf'
                }
                """);
    }

    public record Request(String connection, Path sourceFile, String remotePath, boolean overwrite) {
        public Request {
            Objects.requireNonNull(connection, "connection");
            Objects.requireNonNull(sourceFile, "sourceFile");
            Objects.requireNonNull(remotePath, "remotePath");
        }
    }

    public record Result(String remotePath, long bytes) {
    }


    private static final class Builder implements ConfigBuilder<Request> {
        private String connection;
        private Path sourceFile;
        private String remotePath;
        private boolean overwrite;

        @Override
        public Request build() {
            return new Request(connection, sourceFile, remotePath, overwrite);
        }
    }

    private static Result execute(Request request, OperationContext context) throws IOException {
        ch.so.agi.gretl.tasks.io.internal.LocalFilePaths.rejectSymbolicLinks(request.sourceFile());
        if (!java.nio.file.Files.isRegularFile(request.sourceFile(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw SftpSupport.error(context, "LOCAL_PATH");
        }
        try (var connection = SftpConnection.open(SftpSupport.readConfig(request.connection(), context), context)) {
            return uploadFile(connection.client(), request, context);
        } catch (IOException failure) {
            throw SftpSupport.safe(context, failure);
        }
    }

    private static Result uploadFile(org.apache.sshd.sftp.client.SftpClient client,
                                     Request request, OperationContext context) throws IOException {
        String target = request.remotePath();
        String parent = SftpSupport.parent(target);
        SftpSupport.requireParents(client, parent, context);
        try {
            var existing = SftpSupport.lstat(client, target, context);
            if (!request.overwrite() || !existing.isRegularFile()) {
                throw SftpSupport.error(context, "EXISTS");
            }
        } catch (org.apache.sshd.sftp.common.SftpException failure) {
            if (!SftpSupport.missing(failure)) {
                throw failure;
            }
        }
        var rename = client.getExtension(
                org.apache.sshd.sftp.client.extensions.openssh.OpenSSHPosixRenameExtension.class);
        if (request.overwrite() && client.getVersion() < 5 && (rename == null || !rename.isSupported())) {
            throw SftpSupport.error(context, "RENAME");
        }
        String temporary = SftpSupport.join(parent, ".gretl-sftp-" + UUID.randomUUID() + ".part");
        boolean created = false;
        try {
            long bytes;
            try (var source = java.nio.file.Files.newInputStream(request.sourceFile(), java.nio.file.LinkOption.NOFOLLOW_LINKS);
                 var output = client.write(temporary,
                         org.apache.sshd.sftp.client.SftpClient.OpenMode.Write,
                         org.apache.sshd.sftp.client.SftpClient.OpenMode.Create,
                         org.apache.sshd.sftp.client.SftpClient.OpenMode.Exclusive)) {
                created = true;
                bytes = SftpSupport.copy(source, output, context);
            }
            context.cancellation().throwIfCancellationRequested();
            if (!request.overwrite()) {
                client.rename(temporary, target);
            } else if (rename != null && rename.isSupported()) {
                rename.posixRename(temporary, target);
            } else {
                client.rename(temporary, target, org.apache.sshd.sftp.client.SftpClient.CopyMode.Atomic,
                        org.apache.sshd.sftp.client.SftpClient.CopyMode.Overwrite);
            }
            context.cancellation().throwIfCancellationRequested();
            return new Result(target, bytes);
        } catch (IOException | RuntimeException | Error failure) {
            if (created) {
                try {
                    client.remove(temporary);
                } catch (org.apache.sshd.sftp.common.SftpException cleanup) {
                    if (!SftpSupport.missing(cleanup)) {
                        failure.addSuppressed(SftpSupport.error(context, "CLEANUP"));
                    }
                } catch (IOException cleanup) {
                    failure.addSuppressed(SftpSupport.error(context, "CLEANUP"));
                }
            }
            throw failure;
        }
    }
}
