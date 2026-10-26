package ch.so.agi.gretl.internal.av;

import ch.so.agi.gretl.internal.InterlisTaskException;


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class Av2chEngine {

    public void convert(Av2chRequest request) {
        if (request.inputFiles() == null || request.inputFiles().isEmpty()) {
            return;
        }
        if (request.outputDirectory() == null) {
            throw new IllegalArgumentException("outputDirectory must not be null");
        }
        String language = request.language() == null ? "de" : request.language();
        if (!language.equalsIgnoreCase("de") && !language.equalsIgnoreCase("it")) {
            throw new IllegalArgumentException("language '" + language + "' is not supported.");
        }

        try {
            Files.createDirectories(request.outputDirectory());
            List<Path> inputFiles = request.inputFiles().stream()
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            for (Path inputFile : inputFiles) {
                if (!Files.isRegularFile(inputFile)) {
                    throw new IllegalArgumentException("inputFile does not exist: " + inputFile);
                }
                ch.so.agi.av.Av2ch converter = new ch.so.agi.av.Av2ch();
                if (request.modeldir() != null && !request.modeldir().isBlank()) {
                    converter.setModeldir(request.modeldir());
                }

                String outputFileName = inputFile.getFileName().toString();
                converter.convert(inputFile.toAbsolutePath().toString(),
                        request.outputDirectory().toAbsolutePath().toString(),
                        outputFileName,
                        language);

                if (request.zip()) {
                    zipSingleFile(request.outputDirectory().resolve(outputFileName),
                            request.outputDirectory().resolve(outputFileName + ".zip"));
                }
            }
        } catch (Exception e) {
            throw new ch.so.agi.gretl.internal.InterlisTaskException("AV_CONVERT", "AV conversion failed", "convert", java.util.Optional.empty(), e);
        }
    }

    private void zipSingleFile(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target));
             InputStream input = Files.newInputStream(source)) {
            zip.putNextEntry(new ZipEntry(source.getFileName().toString()));
            input.transferTo(zip);
            zip.closeEntry();
        }
    }

    public record Av2chRequest(
            List<Path> inputFiles,
            Path outputDirectory,
            String modeldir,
            String language,
            boolean zip) {
    }
}
