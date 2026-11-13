package ch.so.agi.gretl.tasks.io.archive;

import ch.so.agi.gretl.i18n.Messages;
import ch.so.agi.gretl.runtime.api.*;
import ch.so.agi.gretl.tasks.io.internal.CompletedFilePublisher;
import ch.so.agi.gretl.tasks.io.internal.LocalFilePaths;
import ch.so.agi.gretl.tasks.io.internal.RelativePathSelection;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

/** Writes a ZIP from explicitly selected files and directory trees. */
public final class ZipOperation {
    private ZipOperation() {
    }

    public static OperationType<ZipRequest, ZipResult> type() {
        return new OperationType<>("gretl.zip", "Zip", "Create a ZIP archive",
                ZipRequest.class, ZipResult.class, ZipBuilder::new, () -> ZipOperation::write,
                List.of(
                        new InputField<ZipRequest, Path>("outputFile", "path", "ZIP output file",
                                true, Optional.empty(), List.of(), "outputFile = workPath('delivery.zip')",
                                ZipOperation::path, (builder, value) -> ((ZipBuilder) builder).output = value),
                        new InputField<ZipRequest, List<Source>>("sources", "list", "Files and directory selections",
                                true, Optional.empty(), List.of(), "sources = [[file: file('data.xtf')]]",
                                ZipOperation::sources, (builder, value) -> ((ZipBuilder) builder).sources = value),
                        new InputField<ZipRequest, Boolean>("overwrite", "boolean", "Replace an existing ZIP",
                                false, Optional.of(false), List.of(), "overwrite = true",
                                ZipOperation::bool, (builder, value) -> ((ZipBuilder) builder).overwrite = value),
                        new InputField<ZipRequest, Boolean>("allowEmpty", "boolean", "Allow a ZIP without entries",
                                false, Optional.of(false), List.of(), "allowEmpty = true",
                                ZipOperation::bool, (builder, value) -> ((ZipBuilder) builder).allowEmpty = value)),
                List.of(Fields.output("file", "path", "Published ZIP", false, ZipResult::file),
                        Fields.output("filesCount", "integer", "Files in the ZIP", false, ZipResult::filesCount),
                        Fields.output("bytes", "integer", "ZIP size in bytes", false, ZipResult::bytes)),
                """
                task('bundle', Zip) {
                    outputFile = workPath('delivery.zip')
                    sources = [[file: file('data.xtf'), entryName: 'data.xtf']]
                }
                """);
    }

    private static Path path(Object value) {
        if (value instanceof Path direct && !direct.toString().isBlank()) {
            return direct.toAbsolutePath().normalize();
        }
        if (value instanceof CharSequence text && !text.toString().isBlank()) {
            return Path.of(text.toString()).toAbsolutePath().normalize();
        }
        throw new IllegalArgumentException("Expected a non-empty path");
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new IllegalArgumentException("Expected a boolean");
    }

    private static List<Source> sources(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("sources must be a non-empty list");
        }
        List<Source> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Each ZIP source must be a map");
            }
            if (map.containsKey("file") && Set.of("file", "entryName").containsAll(map.keySet())) {
                Path file = path(map.get("file"));
                String name = map.containsKey("entryName") ? name(map.get("entryName"))
                        : name(file.getFileName().toString());
                result.add(new Source(file, false, name, null));
            } else if (map.containsKey("directory")
                    && Set.of("directory", "includes", "excludes", "prefix").containsAll(map.keySet())) {
                List<String> includes = map.containsKey("includes")
                        ? RelativePathSelection.patterns(map.get("includes")) : List.of();
                List<String> excludes = map.containsKey("excludes")
                        ? RelativePathSelection.patterns(map.get("excludes")) : List.of();
                result.add(new Source(path(map.get("directory")), true,
                        map.containsKey("prefix") ? name(map.get("prefix")) : "",
                        RelativePathSelection.compile(includes, excludes, true)));
            } else {
                throw new IllegalArgumentException("ZIP source requires either file or directory and its supported fields");
            }
        }
        return List.copyOf(result);
    }

    private static String name(Object value) {
        if (!(value instanceof CharSequence text)) {
            throw new IllegalArgumentException("ZIP entry name must be text");
        }
        return RelativePathSelection.validate(text.toString());
    }

    public record ZipRequest(Path outputFile, List<Source> sources, boolean overwrite, boolean allowEmpty) {
        public ZipRequest {
            Objects.requireNonNull(outputFile, "outputFile");
            sources = List.copyOf(sources);
            if (sources.isEmpty()) {
                throw new IllegalArgumentException("sources must be a non-empty list");
            }
        }
    }

    public record Source(Path path, boolean directory, String name, RelativePathSelection selection) {
    }

    public record ZipResult(Path file, int filesCount, long bytes) {
    }

    static final class ZipBuilder implements ConfigBuilder<ZipRequest> {
        private Path output;
        private List<Source> sources = List.of();
        private boolean overwrite;
        private boolean allowEmpty;

        @Override
        public ZipRequest build() {
            return new ZipRequest(output, sources, overwrite, allowEmpty);
        }
    }

    private static ZipResult write(ZipRequest request, OperationContext context) throws IOException {
        Path target = request.outputFile().toAbsolutePath().normalize();
        context.cancellation().throwIfCancellationRequested();
        Map<String, Path> entries = resolveEntries(request, context);
        if (entries.isEmpty() && !request.allowEmpty()) {
            throw new IOException(Messages.get(context.locale(), "task.zip.empty"));
        }
        if (target.getParent() == null) {
            throw new FileAlreadyExistsException(target.toString());
        }
        LocalFilePaths.createDirectories(target.getParent());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && (!request.overwrite() || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))) {
            throw new FileAlreadyExistsException(target.toString());
        }
        Path temporary = Files.createTempFile(target.getParent(), ".gretl-zip-", ".part");
        try {
            writeArchive(entries, temporary, context);
            context.cancellation().throwIfCancellationRequested();
            long bytes = Files.size(temporary);
            CompletedFilePublisher.publish(temporary, target, request.overwrite());
            return new ZipResult(target, entries.size(), bytes);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private static Map<String, Path> resolveEntries(ZipRequest request, OperationContext context) throws IOException {
        Map<String, Path> entries = new LinkedHashMap<>();
        Path output = request.outputFile().toAbsolutePath().normalize();
        for (Source source : request.sources()) {
            context.cancellation().throwIfCancellationRequested();
            Path input = source.path().toAbsolutePath().normalize();
            LocalFilePaths.rejectSymbolicLinks(input);
            if (input.equals(output) || source.directory() && output.startsWith(input)
                    || sameRegularFile(input, output)) {
                throw new IOException(Messages.get(context.locale(), "task.zip.outputInSource"));
            }
            if (!source.directory()) {
                addEntry(entries, source.name(), input, context);
                continue;
            }
            if (!Files.isDirectory(input, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException(Messages.format(context.locale(), "task.zip.invalidSource", input.getFileName()));
            }
            SortedMap<String, Path> selected = new TreeMap<>();
            try (var tree = Files.walk(input)) {
                for (var iterator = tree.iterator(); iterator.hasNext();) {
                    context.cancellation().throwIfCancellationRequested();
                    Path path = iterator.next();
                    if (Files.isSymbolicLink(path)) {
                        throw new IOException(Messages.format(context.locale(), "task.zip.invalidSource", path.getFileName()));
                    }
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    String relative = input.relativize(path).toString().replace(java.io.File.separatorChar, '/');
                    if (source.selection().matches(relative)) {
                        selected.put(relative, path);
                    }
                }
            }
            for (var entry : selected.entrySet()) {
                String name = source.name().isEmpty() ? entry.getKey() : source.name() + "/" + entry.getKey();
                if (sameRegularFile(entry.getValue(), output)) {
                    throw new IOException(Messages.get(context.locale(), "task.zip.outputInSource"));
                }
                addEntry(entries, name, entry.getValue(), context);
            }
        }
        for (String name : entries.keySet()) {
            for (int separator = name.indexOf('/'); separator >= 0;
                    separator = name.indexOf('/', separator + 1)) {
                if (entries.containsKey(name.substring(0, separator))) {
                    throw new IOException(Messages.format(context.locale(), "task.zip.duplicate", name));
                }
            }
        }
        return entries;
    }

    private static boolean sameRegularFile(Path source, Path output) throws IOException {
        return Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)
                && Files.isSameFile(source, output);
    }

    private static void addEntry(Map<String, Path> entries, String name, Path path, OperationContext context)
            throws IOException {
        RelativePathSelection.validate(name);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(Messages.format(context.locale(), "task.zip.invalidSource", path.getFileName()));
        }
        if (entries.putIfAbsent(name, path) != null) {
            throw new IOException(Messages.format(context.locale(), "task.zip.duplicate", name));
        }
    }

    private static void writeArchive(Map<String, Path> entries, Path temporary, OperationContext context)
            throws IOException {
        byte[] buffer = new byte[8192];
        try (var zip = new ZipArchiveOutputStream(temporary)) {
            for (var entry : entries.entrySet()) {
                context.cancellation().throwIfCancellationRequested();
                LocalFilePaths.rejectSymbolicLinks(entry.getValue());
                try (var input = Files.newInputStream(entry.getValue(), LinkOption.NOFOLLOW_LINKS)) {
                    zip.putArchiveEntry(new ZipArchiveEntry(entry.getKey()));
                    int length;
                    while ((length = input.read(buffer)) >= 0) {
                        context.cancellation().throwIfCancellationRequested();
                        zip.write(buffer, 0, length);
                    }
                    zip.closeArchiveEntry();
                }
            }
        }
    }
}
