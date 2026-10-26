package ch.so.agi.gretl.internal.av;

import ch.so.agi.gretl.internal.InterlisTaskException;

import ch.ehi.basics.logging.EhiLogger;
import ch.ehi.basics.logging.FileListener;
import ch.ehi.basics.settings.Settings;
import ch.ehi.basics.view.GenericFileFilter;
import ch.interlis.iox_j.logging.FileLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class Av2geobauEngine {
    private static final List<String> ZIP_ADDONS = List.of(
            "DXF_Geobau_Layerdefinition.pdf",
            "Hinweise.pdf",
            "Musterplan.pdf"
    );

    public void convert(Av2geobauRequest request) {
        if (request.itfFiles() == null || request.itfFiles().isEmpty()) {
            return;
        }
        if (request.dxfDirectory() == null) {
            throw new IllegalArgumentException("dxfDirectory must not be null");
        }

        FileListener fileLogger = null;
        try {
            Files.createDirectories(request.dxfDirectory());
            if (request.logFile() != null) {
                if (request.logFile().getParent() != null) {
                    Files.createDirectories(request.logFile().getParent());
                }
                fileLogger = new FileLogger(request.logFile().toFile());
                EhiLogger.getInstance().addListener(fileLogger);
            }

            Settings settings = settings(request);
            List<Path> itfFiles = request.itfFiles().stream()
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            for (Path itfFile : itfFiles) {
                if (!Files.isRegularFile(itfFile)) {
                    throw new IllegalArgumentException("itfFiles entry does not exist: " + itfFile);
                }
                Path dxfFile = request.dxfDirectory()
                        .resolve(GenericFileFilter.stripFileExtension(itfFile.getFileName().toString()) + ".dxf");
                boolean ok = org.interlis2.av2geobau.Av2geobau.convert(itfFile.toFile(), dxfFile.toFile(), settings);
                if (!ok) {
                    throw new IllegalStateException("Av2geobau failed for " + itfFile);
                }
                if (request.zip()) {
                    zipDxfWithAddons(dxfFile, request.dxfDirectory());
                }
            }
        } catch (Exception e) {
            throw new ch.so.agi.gretl.internal.InterlisTaskException("AV_CONVERT", "AV conversion failed", "convert", java.util.Optional.empty(), e);
        } finally {
            if (fileLogger != null) {
                EhiLogger.getInstance().removeListener(fileLogger);
                fileLogger.close();
            }
        }
    }

    private Settings settings(Av2geobauRequest request) {
        Settings settings = new Settings();
        settings.setValue(org.interlis2.av2geobau.Av2geobau.SETTING_ILIDIRS,
                org.interlis2.av2geobau.Av2geobau.SETTING_DEFAULT_ILIDIRS);
        if (request.modeldir() != null && !request.modeldir().isBlank()) {
            settings.setValue(org.interlis2.av2geobau.Av2geobau.SETTING_ILIDIRS, request.modeldir());
        }
        if (request.proxy() != null && !request.proxy().isBlank()) {
            settings.setValue(ch.interlis.ili2c.gui.UserSettings.HTTP_PROXY_HOST, request.proxy());
        }
        if (request.proxyPort() != null) {
            settings.setValue(ch.interlis.ili2c.gui.UserSettings.HTTP_PROXY_PORT, request.proxyPort().toString());
        }
        return settings;
    }

    private void zipDxfWithAddons(Path dxfFile, Path dxfDirectory) throws IOException {
        Path zipFile = dxfDirectory.resolve(stripExtension(dxfFile.getFileName().toString()) + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            addFile(zip, dxfFile, dxfFile.getFileName().toString());
            for (String addon : ZIP_ADDONS) {
                Path addonFile = copyAddon(addon, dxfDirectory);
                addFile(zip, addonFile, addonFile.getFileName().toString());
            }
        }
    }

    private Path copyAddon(String addon, Path dxfDirectory) throws IOException {
        Path target = dxfDirectory.resolve(addon);
        try (InputStream input = Av2geobauEngine.class.getResourceAsStream("/av2geobau/" + addon)) {
            if (input == null) {
                throw new IOException("Missing av2geobau addon resource: " + addon);
            }
            Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private void addFile(ZipOutputStream zip, Path source, String entryName) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream input = Files.newInputStream(source)) {
            input.transferTo(zip);
        }
        zip.closeEntry();
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    public record Av2geobauRequest(
            List<Path> itfFiles,
            Path dxfDirectory,
            String modeldir,
            Path logFile,
            String proxy,
            Integer proxyPort,
            boolean zip) {
    }
}
