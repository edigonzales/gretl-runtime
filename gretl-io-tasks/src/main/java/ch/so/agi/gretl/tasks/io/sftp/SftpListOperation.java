package ch.so.agi.gretl.tasks.io.sftp;

import ch.so.agi.gretl.runtime.api.*;
import ch.so.agi.gretl.tasks.io.internal.RelativePathSelection;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class SftpListOperation {
    private SftpListOperation() {
    }

    public static OperationType<Request, Result> type() {
        return new OperationType<>("gretl.sftp-list", "SftpList", "List over SFTP",
                Request.class, Result.class, Builder::new, () -> SftpListOperation::execute,
                List.of(
                        new InputField<Request, String>("connection", "text", "connection", true,
                                Optional.empty(), List.of(), "connection = 'exchange'",
                                SftpSupport::text, (builder, value) -> ((Builder) builder).connection = value),
                        new InputField<Request, String>("remoteDirectory", "text", "remoteDirectory", true,
                                Optional.empty(), List.of(), "remoteDirectory = 'incoming'",
                                SftpSupport::normalizeRemotePath, (builder, value) -> ((Builder) builder).remoteDirectory = value),
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
                                SftpSupport::flag, (builder, value) -> ((Builder) builder).caseSensitive = value)),
                List.of(Fields.output("entries", "list", "entries", false, Result::entries)),
                """
                task('list', SftpList) {
                    connection = 'exchange'
                    remoteDirectory = 'incoming'
                }
                """);
    }

    public record Request(String connection, String remoteDirectory, boolean recursive, List<String> includes, List<String> excludes, boolean caseSensitive) {
        public Request {
            Objects.requireNonNull(connection, "connection");
            Objects.requireNonNull(remoteDirectory, "remoteDirectory");
            includes = List.copyOf(includes);
            excludes = List.copyOf(excludes);
            RelativePathSelection.compile(includes, excludes, caseSensitive);
        }
    }

    public record Result(List<Entry> entries) {
        public Result {
            entries = List.copyOf(entries);
        }
    }

    public enum EntryType { FILE, DIRECTORY, SYMLINK, OTHER }
    public record Entry(String name, String remotePath, String relativePath, EntryType type,
                        Long size, java.time.Instant modifiedAt) {
    }

    private static final class Builder implements ConfigBuilder<Request> {
        private String connection;
        private String remoteDirectory;
        private boolean recursive;
        private List<String> includes = List.of();
        private List<String> excludes = List.of();
        private boolean caseSensitive = true;

        @Override
        public Request build() {
            return new Request(connection, remoteDirectory, recursive, includes, excludes, caseSensitive);
        }
    }

    private static Result execute(Request request, OperationContext context) throws IOException {
        var config = SftpSupport.readConfig(request.connection(), context);
        var selection = RelativePathSelection.compile(request.includes(), request.excludes(), request.caseSensitive());
        try (var connection = SftpConnection.open(config, context)) {
            return new Result(SftpSupport.enumerate(connection.client(), request.remoteDirectory(),
                    request.recursive(), selection, context));
        } catch (IOException failure) {
            throw SftpSupport.safe(context, failure);
        }
    }
}
