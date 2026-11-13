package ch.so.agi.gretl.tasks.io.sftp;

import ch.so.agi.gretl.runtime.api.*;
import ch.so.agi.gretl.tasks.io.internal.RelativePathSelection;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class SftpDownloadOperation {
    private SftpDownloadOperation() {
    }

    public static OperationType<Request, Result> type() {
        return new OperationType<>("gretl.sftp-download", "SftpDownload", "Download over SFTP",
                Request.class, Result.class, Builder::new, () -> SftpDownloadOperation::execute,
                List.of(
                        new InputField<Request, String>("connection", "text", "connection", true,
                                Optional.empty(), List.of(), "connection = 'exchange'",
                                SftpSupport::text, (builder, value) -> ((Builder) builder).connection = value),
                        new InputField<Request, String>("remotePath", "text", "remotePath", true,
                                Optional.empty(), List.of(), "remotePath = 'incoming/data.xtf'",
                                SftpSupport::normalizeRemotePath, (builder, value) -> ((Builder) builder).remotePath = value),
                        new InputField<Request, Path>("outputFile", "path", "outputFile", false,
                                Optional.empty(), List.of(), "outputFile = workPath('data.xtf')",
                                SftpSupport::localPath, (builder, value) -> ((Builder) builder).outputFile = value),
                        new InputField<Request, Path>("outputDirectory", "path", "outputDirectory", false,
                                Optional.empty(), List.of(), "outputDirectory = workPath('download')",
                                SftpSupport::localPath, (builder, value) -> ((Builder) builder).outputDirectory = value),
                        new InputField<Request, Boolean>("recursive", "boolean", "recursive", false,
                                Optional.of(false), List.of(), "recursive = true",
                                SftpSupport::flag, (builder, value) -> ((Builder) builder).recursive = value),
                        new InputField<Request, List<String>>("includes", "list", "includes", false,
                                Optional.of(List.of()), List.of(), "includes = ['**/*.csv']",
                                RelativePathSelection::patterns, (builder, value) -> ((Builder) builder).includes = value),
                        new InputField<Request, List<String>>("excludes", "list", "excludes", false,
                                Optional.of(List.of()), List.of(), "excludes = ['backup/**']",
                                RelativePathSelection::patterns, (builder, value) -> ((Builder) builder).excludes = value),
                        new InputField<Request, Boolean>("caseSensitive", "boolean", "caseSensitive", false,
                                Optional.of(true), List.of(), "caseSensitive = false",
                                SftpSupport::flag, (builder, value) -> ((Builder) builder).caseSensitive = value),
                        new InputField<Request, Boolean>("overwrite", "boolean", "overwrite", false,
                                Optional.of(false), List.of(), "overwrite = true",
                                SftpSupport::flag, (builder, value) -> ((Builder) builder).overwrite = value)),
                List.of(Fields.output("files", "list", "files", false, Result::files),
                        Fields.output("remotePaths", "list", "remotePaths", false, Result::remotePaths),
                        Fields.output("entries", "list", "entries", false, Result::entries),
                        Fields.output("bytes", "integer", "bytes", false, Result::bytes)),
                """
                task('download', SftpDownload) {
                    connection = 'exchange'
                    remotePath = 'incoming/data.xtf'
                    outputFile = workPath('data.xtf')
                }
                """);
    }

    public record Request(String connection, String remotePath, Path outputFile, Path outputDirectory, boolean recursive, List<String> includes, List<String> excludes, boolean caseSensitive, boolean overwrite) {
        public Request {
            Objects.requireNonNull(connection, "connection");
            Objects.requireNonNull(remotePath, "remotePath");
            includes = List.copyOf(includes);
            excludes = List.copyOf(excludes);
            RelativePathSelection.compile(includes, excludes, caseSensitive);
            if ((outputFile == null) == (outputDirectory == null)) {
                throw new IllegalArgumentException("Set exactly one SFTP output path");
            }
            if (outputFile != null && (recursive || !includes.isEmpty() || !excludes.isEmpty())) {
                throw new IllegalArgumentException("SFTP filters and recursion require outputDirectory");
            }
        }
    }

    public record Result(List<Path> files, List<String> remotePaths, List<Entry> entries, long bytes) {
        public Result {
            files = List.copyOf(files);
            remotePaths = List.copyOf(remotePaths);
            entries = List.copyOf(entries);
        }
    }

    public record Entry(String remotePath, Path localFile, long bytes) {
    }

    private static final class Builder implements ConfigBuilder<Request> {
        private String connection;
        private String remotePath;
        private Path outputFile;
        private Path outputDirectory;
        private boolean recursive;
        private List<String> includes = List.of();
        private List<String> excludes = List.of();
        private boolean caseSensitive = true;
        private boolean overwrite;

        @Override
        public Request build() {
            return new Request(connection, remotePath, outputFile, outputDirectory, recursive, includes, excludes, caseSensitive, overwrite);
        }
    }

    private static Result execute(Request request, OperationContext context) throws IOException {
        var selection = RelativePathSelection.compile(request.includes(), request.excludes(), request.caseSensitive());
        List<Entry> entries = new ArrayList<>();
        try (var connection = SftpConnection.open(SftpSupport.readConfig(request.connection(), context), context)) {
            var client = connection.client();
            if (request.outputFile() != null) {
                String remote = SftpSupport.filePath(request.remotePath());
                entries.add(downloadFile(client, remote, request.outputFile(), request.overwrite(), context));
            } else {
                for (var entry : SftpSupport.enumerate(client, request.remotePath(), request.recursive(),
                        selection, context)) {
                    if (entry.type() == SftpListOperation.EntryType.FILE) {
                        Path target = request.outputDirectory().resolve(entry.relativePath()).normalize();
                        if (!target.startsWith(request.outputDirectory())) {
                            throw SftpSupport.error(context, "LOCAL_PATH");
                        }
                        entries.add(downloadFile(client, entry.remotePath(), target, request.overwrite(), context));
                    }
                }
            }
        } catch (IOException failure) {
            throw SftpSupport.safe(context, failure);
        }
        return new Result(entries.stream().map(Entry::localFile).toList(),
                entries.stream().map(Entry::remotePath).toList(), entries,
                entries.stream().mapToLong(Entry::bytes).sum());
    }

    private static Entry downloadFile(org.apache.sshd.sftp.client.SftpClient client, String remote,
                                      Path target, boolean overwrite, OperationContext context) throws IOException {
        SftpSupport.requireParents(client, SftpSupport.parent(remote), context);
        if (!SftpSupport.lstat(client, remote, context).isRegularFile()) {
            throw SftpSupport.error(context, "REMOTE_TYPE");
        }
        ch.so.agi.gretl.tasks.io.internal.LocalFilePaths.createDirectories(target.getParent());
        if (java.nio.file.Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                && (!overwrite || !java.nio.file.Files.isRegularFile(target, java.nio.file.LinkOption.NOFOLLOW_LINKS))) {
            throw SftpSupport.error(context, "EXISTS");
        }
        Path temporary = java.nio.file.Files.createTempFile(target.getParent(), ".gretl-sftp-", ".part");
        try {
            long bytes;
            try (var source = client.read(remote); var output = java.nio.file.Files.newOutputStream(temporary)) {
                bytes = SftpSupport.copy(source, output, context);
            }
            context.cancellation().throwIfCancellationRequested();
            ch.so.agi.gretl.tasks.io.internal.CompletedFilePublisher.publish(temporary, target, overwrite);
            return new Entry(remote, target, bytes);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                java.nio.file.Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                failure.addSuppressed(SftpSupport.error(context, "CLEANUP"));
            }
            throw failure;
        }
    }
}
