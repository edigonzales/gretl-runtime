package ch.so.agi.gretl.tasks.basic;

import ch.so.agi.gretl.runtime.api.ConfigBuilder;
import ch.so.agi.gretl.runtime.api.Fields;
import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.LogLevel;
import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.runtime.api.OutputField;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/** ZIP/GZIP extraction with traversal, symlink, collision and size protection. */
public final class ExtractOperation {
    public static final String ID = "gretl.extract";
    public static final String SYMBOL = "Extract";
    public static final long DEFAULT_MAX_EXPANDED_BYTES = 4L << 30;
    public static final int DEFAULT_MAX_ENTRIES = 10_000;

    private ExtractOperation() {
    }

    public static OperationType<ExtractRequest, ExtractResult> type() {
        InputField<ExtractRequest, Path> archive = new InputField<>(
                "archive", "path", "Vorhandene Archivdatei", true, Optional.empty(),
                List.of(), "archive = result('download').file",
                ExtractOperation::path, (builder, value) -> ((ExtractBuilder) builder).setArchive(value));
        InputField<ExtractRequest, String> compression = new InputField<>(
                "compression", "text", "ZIP oder GZIP", true, Optional.empty(),
                List.of("ZIP", "GZIP"), "compression = 'ZIP'",
                ExtractOperation::compression, (builder, value) -> ((ExtractBuilder) builder).setCompression(value));
        InputField<ExtractRequest, Path> outputDirectory = new InputField<>(
                "outputDirectory", "path", "Zielverzeichnis fuer ZIP", false,
                Optional.empty(), List.of(), "outputDirectory = workPath('out')",
                ExtractOperation::path, (builder, value) -> ((ExtractBuilder) builder).setOutputDirectory(value));
        InputField<ExtractRequest, Path> outputFile = new InputField<>(
                "outputFile", "path", "Zieldatei fuer GZIP", false,
                Optional.empty(), List.of(), "outputFile = workPath('data.csv')",
                ExtractOperation::path, (builder, value) -> ((ExtractBuilder) builder).setOutputFile(value));
        InputField<ExtractRequest, Long> maxExpandedBytes = new InputField<>(
                "maxExpandedBytes", "integer", "Maximal entpackte Bytes", false,
                Optional.of(DEFAULT_MAX_EXPANDED_BYTES), List.of(),
                "maxExpandedBytes = 4294967296L", ExtractOperation::positiveLong,
                (builder, value) -> ((ExtractBuilder) builder).setMaxExpandedBytes(value));
        InputField<ExtractRequest, Integer> maxEntries = new InputField<>(
                "maxEntries", "integer", "Maximale ZIP-Eintraege", false,
                Optional.of(DEFAULT_MAX_ENTRIES), List.of(), "maxEntries = 10000",
                ExtractOperation::positiveInt, (builder, value) -> ((ExtractBuilder) builder).setMaxEntries(value));
        OutputField<ExtractResult, Path> file = Fields.output(
                "file", "path", "Einzelne GZIP-Datei, sonst null", true,
                ExtractResult::file);
        OutputField<ExtractResult, List<Path>> files = Fields.output(
                "files", "list", "Abgeschlossene regulaere Dateien", false,
                ExtractResult::files);
        OutputField<ExtractResult, Path> directory = Fields.output(
                "directory", "path", "ZIP-Ausgabeverzeichnis, sonst null", true,
                ExtractResult::directory);
        OutputField<ExtractResult, Long> bytes = Fields.output(
                "bytes", "integer", "Tatsaechlich entpackte Bytes", false,
                ExtractResult::bytes);
        return new OperationType<>(
                ID, SYMBOL, "ZIP- oder GZIP-Archiv entpacken",
                ExtractRequest.class, ExtractResult.class,
                ExtractBuilder::new, ExtractHandler::new,
                List.of(archive, compression, outputDirectory, outputFile,
                        maxExpandedBytes, maxEntries),
                List.of(file, files, directory, bytes),
                """
                task('extract', Extract, dependsOn: 'download') {
                    archive = result('download').file
                    compression = 'ZIP'
                    outputDirectory = workPath('out')
                }
                """);
    }

    private static Path path(Object value) {
        Path path;
        if (value instanceof Path direct) {
            path = direct;
        } else if (value instanceof CharSequence text) {
            path = Path.of(text.toString());
        } else {
            throw new IllegalArgumentException("Pfad muss Path oder Text sein");
        }
        if (path.toString().isBlank()) {
            throw new IllegalArgumentException("Pfad darf nicht leer sein");
        }
        return path.normalize();
    }

    private static String compression(Object value) {
        if (!(value instanceof CharSequence text)) {
            throw new IllegalArgumentException("compression muss Text sein");
        }
        String compression = text.toString().toUpperCase(Locale.ROOT);
        if (!compression.equals("ZIP") && !compression.equals("GZIP")) {
            throw new IllegalArgumentException("compression muss ZIP oder GZIP sein");
        }
        return compression;
    }

    private static Long positiveLong(Object value) {
        long result;
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            result = ((Number) value).longValue();
        } else if (value instanceof CharSequence text && text.toString().matches("[0-9]+")) {
            try {
                result = Long.parseLong(text.toString());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("maxExpandedBytes ausserhalb Long");
            }
        } else {
            throw new IllegalArgumentException("maxExpandedBytes muss positive Ganzzahl sein");
        }
        if (result <= 0) {
            throw new IllegalArgumentException("maxExpandedBytes muss positiv sein");
        }
        return result;
    }

    private static Integer positiveInt(Object value) {
        long result;
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            result = ((Number) value).longValue();
        } else if (value instanceof CharSequence text && text.toString().matches("[0-9]+")) {
            try {
                result = Long.parseLong(text.toString());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("maxEntries ausserhalb Long");
            }
        } else {
            throw new IllegalArgumentException("maxEntries muss positive Ganzzahl sein");
        }
        if (result <= 0 || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxEntries muss zwischen 1 und 2147483647 liegen");
        }
        return (int) result;
    }

    public record ExtractRequest(
            Path archive, String compression, Path outputDirectory,
            Path outputFile, long maxExpandedBytes, int maxEntries) {
        public ExtractRequest {
            java.util.Objects.requireNonNull(archive, "archive");
            java.util.Objects.requireNonNull(compression, "compression");
            if (maxExpandedBytes <= 0 || maxEntries <= 0) {
                throw new IllegalArgumentException("limits must be positive");
            }
        }
    }

    public static final class ExtractBuilder implements ConfigBuilder<ExtractRequest> {
        private Path archive;
        private String compression;
        private Path outputDirectory;
        private Path outputFile;
        private long maxExpandedBytes = DEFAULT_MAX_EXPANDED_BYTES;
        private int maxEntries = DEFAULT_MAX_ENTRIES;

        public void setArchive(Path value) { archive = value; }
        public void setCompression(String value) { compression = value; }
        public void setOutputDirectory(Path value) { outputDirectory = value; }
        public void setOutputFile(Path value) { outputFile = value; }
        public void setMaxExpandedBytes(Long value) { maxExpandedBytes = value; }
        public void setMaxEntries(Integer value) { maxEntries = value; }

        @Override
        public ExtractRequest build() {
            if (archive == null) {
                throw new IllegalArgumentException("archive fehlt");
            }
            if (compression == null) {
                throw new IllegalArgumentException("compression fehlt");
            }
            if (compression.equals("ZIP")) {
                if (outputDirectory == null || outputFile != null) {
                    throw new IllegalArgumentException(
                            "ZIP verlangt outputDirectory und kein outputFile");
                }
            } else if (outputFile == null || outputDirectory != null) {
                throw new IllegalArgumentException(
                        "GZIP verlangt outputFile und kein outputDirectory");
            }
            return new ExtractRequest(archive, compression, outputDirectory,
                    outputFile, maxExpandedBytes, maxEntries);
        }
    }

    static final class ExtractHandler
            implements ch.so.agi.gretl.runtime.api.OperationHandler<
                    ExtractRequest, ExtractResult> {
        @Override
        public ExtractResult execute(ExtractRequest request, OperationContext context)
                throws Exception {
            context.cancellation().throwIfCancellationRequested();
            Path archive = request.archive().toAbsolutePath().normalize();
            if (!Files.isRegularFile(archive)) {
                throw new ExtractTaskException("ARCHIVE_NOT_FOUND",
                        "Archivdatei ist nicht vorhanden", null);
            }
            long started = System.nanoTime();
            ExtractResult result = request.compression().equals("ZIP")
                    ? unzip(archive, request, context)
                    : gunzip(archive, request, context);
            long duration = (System.nanoTime() - started) / 1_000_000;
            context.log().log(LogLevel.INFO, "gretl.extract.finished",
                    "Archiv entpackt",
                    Map.of("archive.file", archive.getFileName().toString(),
                            "compression", request.compression(),
                            "bytes", Long.toString(result.bytes()),
                            "duration_ms", Long.toString(duration)), null);
            return result;
        }

        private static ExtractResult unzip(
                Path archive, ExtractRequest request, OperationContext context) {
            Path root = request.outputDirectory().toAbsolutePath().normalize();
            try {
                Files.createDirectories(root);
            } catch (IOException exception) {
                throw new ExtractTaskException("OUTPUT_NOT_WRITABLE",
                        "Ausgabeverzeichnis konnte nicht angelegt werden", exception);
            }
            List<Path> files = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            long total = 0;
            int count = 0;
            try (ZipFile zip = ZipFile.builder().setPath(archive).get()) {
                Enumeration<ZipArchiveEntry> entries = zip.getEntries();
                while (entries.hasMoreElements()) {
                    context.cancellation().throwIfCancellationRequested();
                    ZipArchiveEntry entry = entries.nextElement();
                    if (++count > request.maxEntries()) {
                        throw new ExtractTaskException("ARCHIVE_ENTRY_LIMIT",
                                "Archiv ueberschreitet maxEntries", null);
                    }
                    String name = entry.getName();
                    if (name == null || name.isBlank()) {
                        throw new ExtractTaskException("ARCHIVE_INVALID_ENTRY",
                                "Archiv enthaelt einen leeren Eintragsnamen", null);
                    }
                    if (name.indexOf('\\') >= 0) {
                        throw new ExtractTaskException("ARCHIVE_TRAVERSAL",
                                "Archiv eintrag enthaelt einen unzulaessigen Pfadtrenner", null);
                    }
                    Path relative;
                    try {
                        relative = Path.of(name);
                    } catch (RuntimeException exception) {
                        throw new ExtractTaskException("ARCHIVE_INVALID_ENTRY",
                                "Archiv enthaelt einen ungueltigen Pfad", exception);
                    }
                    if (relative.isAbsolute() || name.startsWith("/")) {
                        throw new ExtractTaskException("ARCHIVE_TRAVERSAL",
                                "Archiv eintrag ist absolut", null);
                    }
                    Path target = root.resolve(relative).normalize();
                    if (!target.startsWith(root)) {
                        throw new ExtractTaskException("ARCHIVE_TRAVERSAL",
                                "Archiv eintrag verlaesst das Ausgabeverzeichnis", null);
                    }
                    if (entry.isUnixSymlink()) {
                        throw new ExtractTaskException("ARCHIVE_SYMLINK",
                                "Archiv enthaelt einen Symlink-Eintrag", null);
                    }
                    String normalized = root.relativize(target).toString().replace('\\', '/');
                    if (normalized.isEmpty()) {
                        continue;
                    }
                    if (!seen.add(normalized.toLowerCase(Locale.ROOT))) {
                        throw new ExtractTaskException("ARCHIVE_COLLISION",
                                "Archiv enthaelt kollidierende Eintragsnamen", null);
                    }
                    if (entry.isDirectory()) {
                        try {
                            Files.createDirectories(target);
                        } catch (IOException exception) {
                            throw new ExtractTaskException("OUTPUT_NOT_WRITABLE",
                                    "Ausgabeverzeichnis konnte nicht angelegt werden", exception);
                        }
                        continue;
                    }
                    if (Files.exists(target)) {
                        throw new ExtractTaskException("OUTPUT_EXISTS",
                                "Zieldatei existiert bereits", null);
                    }
                    try {
                        Files.createDirectories(target.getParent());
                    } catch (IOException exception) {
                        throw new ExtractTaskException("OUTPUT_NOT_WRITABLE",
                                "Elternverzeichnis konnte nicht angelegt werden", exception);
                    }
                    long written = copyEntry(zip, entry, target, request.maxExpandedBytes() - total,
                            context);
                    total += written;
                    files.add(target);
                }
            } catch (ExtractTaskException exception) {
                throw exception;
            } catch (IOException exception) {
                throw new ExtractTaskException("ARCHIVE_INVALID",
                        "Archiv konnte nicht gelesen werden", exception);
            }
            files.sort(java.util.Comparator.comparing(path ->
                    root.relativize(path).toString().replace('\\', '/')));
            return new ExtractResult(null, files, root, total);
        }

        private static long copyEntry(ZipFile zip, ZipArchiveEntry entry, Path target,
                                      long remaining, OperationContext context) {
            long written = 0;
            try (InputStream input = zip.getInputStream(entry);
                 OutputStream output = Files.newOutputStream(target,
                         StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[8192];
                while (true) {
                    context.cancellation().throwIfCancellationRequested();
                    int read = input.read(buffer);
                    if (read < 0) {
                        break;
                    }
                    written += read;
                    if (written > remaining) {
                        throw new ExtractTaskException("ARCHIVE_SIZE_LIMIT",
                                "Entpackte Daten ueberschreiten maxExpandedBytes", null);
                    }
                    output.write(buffer, 0, read);
                    if (written == read || written % (1024 * 1024) < read) {
                        context.progress(new ch.so.agi.gretl.runtime.api.Progress(
                                "extract", written, java.util.OptionalLong.empty(), "bytes"));
                    }
                }
            } catch (ExtractTaskException exception) {
                throw exception;
            } catch (IOException exception) {
                throw new ExtractTaskException("OUTPUT_WRITE_FAILED",
                        "Entpackte Datei konnte nicht geschrieben werden", exception);
            }
            return written;
        }

        private static ExtractResult gunzip(
                Path archive, ExtractRequest request, OperationContext context) {
            Path target = request.outputFile().toAbsolutePath().normalize();
            if (Files.exists(target)) {
                throw new ExtractTaskException("OUTPUT_EXISTS",
                        "Zieldatei existiert bereits", null);
            }
            try {
                Files.createDirectories(target.getParent());
            } catch (IOException exception) {
                throw new ExtractTaskException("OUTPUT_NOT_WRITABLE",
                        "Elternverzeichnis konnte nicht angelegt werden", exception);
            }
            long total = 0;
            try (InputStream raw = Files.newInputStream(archive);
                 GzipCompressorInputStream gzip = new GzipCompressorInputStream(raw);
                 OutputStream output = Files.newOutputStream(target,
                         StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[8192];
                while (true) {
                    context.cancellation().throwIfCancellationRequested();
                    int read = gzip.read(buffer);
                    if (read < 0) {
                        break;
                    }
                    total += read;
                    if (total > request.maxExpandedBytes()) {
                        throw new ExtractTaskException("ARCHIVE_SIZE_LIMIT",
                                "Entpackte Daten ueberschreiten maxExpandedBytes", null);
                    }
                    output.write(buffer, 0, read);
                    if (total == read || total % (1024 * 1024) < read) {
                        context.progress(new ch.so.agi.gretl.runtime.api.Progress(
                                "extract", total, java.util.OptionalLong.empty(), "bytes"));
                    }
                }
            } catch (ExtractTaskException exception) {
                throw exception;
            } catch (IOException exception) {
                throw new ExtractTaskException("ARCHIVE_INVALID",
                        "GZIP konnte nicht entpackt werden", exception);
            }
            return new ExtractResult(target, List.of(target), null, total);
        }
    }
}
