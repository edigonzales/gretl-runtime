package ch.so.agi.gretl.internal.ioxwkf;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.Ili2c;
import ch.interlis.ili2c.Ili2cException;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ilirepository.IliManager;
import ch.interlis.iom_j.csv.CsvReader;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox_j.EndBasketEvent;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.ObjectEvent;
import ch.interlis.iox_j.StartTransferEvent;
import ch.interlis.ioxwkf.dbtools.IoxWkfConfig;
import ch.interlis.ioxwkf.excel.ExcelAttributeDescriptor;
import ch.interlis.ioxwkf.excel.ExcelWriter;
import org.interlis2.validator.Validator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Csv2ExcelEngine {

    public void convert(Csv2ExcelRequest request) throws IOException {
        if (request.csvFile() == null || !Files.isRegularFile(request.csvFile())) {
            throw new IllegalArgumentException("csvFile must reference an existing file");
        }
        if (request.outputFile() == null) {
            throw new IllegalArgumentException("outputFile must not be null");
        }
        if (request.outputFile().getParent() != null) {
            Files.createDirectories(request.outputFile().getParent());
        }

        Settings settings = settings(request);
        ExcelWriter writer = null;
        CsvReader reader = null;
        try {
            writer = new ExcelWriter(request.outputFile().toFile(), settings);
            reader = new CsvReader(request.csvFile().toFile(), settings);
            configureReader(reader, request, settings);
            if (request.models() != null && !request.models().isBlank()) {
                TransferDescription transferDescription = transferDescription(request.models(),
                        request.csvFile().getParent(), settings);
                reader.setModel(transferDescription);
                writer.setModel(transferDescription);
            }

            writer.write(new StartTransferEvent());
            IoxEvent event = reader.read();
            if (event instanceof StartTransferEvent) {
                event = reader.read();
                if (request.firstLineIsHeader() && (request.models() == null || request.models().isBlank())) {
                    writer.setAttributeDescriptors(attributeDescriptors(reader.getAttributes()));
                }
                writer.write(event);
            }

            while (event != null) {
                if (event instanceof ObjectEvent) {
                    writer.write(event);
                }
                event = reader.read();
            }
            writer.write(new EndBasketEvent());
            writer.write(new EndTransferEvent());
        } catch (IoxException | Ili2cException e) {
            throw new IOException(e);
        } finally {
            close(reader);
            close(writer);
        }
    }

    private Settings settings(Csv2ExcelRequest request) {
        Settings settings = new Settings();
        settings.setValue(IoxWkfConfig.SETTING_FIRSTLINE,
                request.firstLineIsHeader()
                        ? IoxWkfConfig.SETTING_FIRSTLINE_AS_HEADER
                        : IoxWkfConfig.SETTING_FIRSTLINE_AS_VALUE);
        if (request.valueDelimiter() != null) {
            settings.setValue(IoxWkfConfig.SETTING_VALUEDELIMITER, singleCharacter("valueDelimiter", request.valueDelimiter()));
        }
        if (request.valueSeparator() != null) {
            settings.setValue(IoxWkfConfig.SETTING_VALUESEPARATOR, singleCharacter("valueSeparator", request.valueSeparator()));
        }
        if (request.encoding() != null) {
            settings.setValue(CsvReader.ENCODING, request.encoding());
        }
        if (request.models() != null && !request.models().isBlank()) {
            settings.setValue(Validator.SETTING_MODELNAMES, request.models());
        }
        if (request.modeldir() != null && !request.modeldir().isBlank()) {
            settings.setValue(Validator.SETTING_ILIDIRS, request.modeldir());
        }
        return settings;
    }

    private void configureReader(CsvReader reader, Csv2ExcelRequest request, Settings settings) {
        reader.setFirstLineIsHeader(request.firstLineIsHeader());

        String valueDelimiter = settings.getValue(IoxWkfConfig.SETTING_VALUEDELIMITER);
        if (valueDelimiter != null) {
            reader.setValueDelimiter(valueDelimiter.charAt(0));
        }

        String valueSeparator = settings.getValue(IoxWkfConfig.SETTING_VALUESEPARATOR);
        if (valueSeparator == null) {
            reader.setValueSeparator(IoxWkfConfig.SETTING_VALUESEPARATOR_DEFAULT);
        } else {
            reader.setValueSeparator(valueSeparator.charAt(0));
        }
    }

    private List<ExcelAttributeDescriptor> attributeDescriptors(String[] attributes) {
        List<ExcelAttributeDescriptor> descriptors = new ArrayList<>();
        if (attributes == null) {
            return descriptors;
        }
        for (String attribute : attributes) {
            ExcelAttributeDescriptor descriptor = new ExcelAttributeDescriptor();
            descriptor.setAttributeName(attribute);
            descriptor.setBinding(String.class);
            descriptors.add(descriptor);
        }
        return descriptors;
    }

    private TransferDescription transferDescription(String models, Path additionalRepository, Settings settings)
            throws Ili2cException {
        IliManager manager = new IliManager();
        List<String> repositories = new ArrayList<>();
        String configuredRepositories = settings.getValue(Validator.SETTING_ILIDIRS);
        if (configuredRepositories != null && !configuredRepositories.isBlank()) {
            repositories.addAll(List.of(configuredRepositories.split(";")));
        }
        if (additionalRepository != null) {
            repositories.add(additionalRepository.toString());
        }
        manager.setRepositories(repositories.toArray(String[]::new));

        Configuration configuration = manager.getConfig(new ArrayList<>(List.of(models)), 2.3);
        TransferDescription transferDescription = Ili2c.runCompiler(configuration);
        if (transferDescription == null) {
            throw new IllegalArgumentException("INTERLIS compiler failed");
        }
        return transferDescription;
    }

    private String singleCharacter(String propertyName, String value) {
        if (value.length() != 1) {
            throw new IllegalArgumentException(propertyName + " must be a single character");
        }
        return value;
    }

    private void close(CsvReader reader) throws IOException {
        if (reader != null) {
            try {
                reader.close();
            } catch (IoxException e) {
                throw new IOException(e);
            }
        }
    }

    private void close(ExcelWriter writer) throws IOException {
        if (writer != null) {
            try {
                writer.close();
            } catch (IoxException e) {
                throw new IOException(e);
            }
        }
    }

    public record Csv2ExcelRequest(
            Path csvFile,
            Path outputFile,
            boolean firstLineIsHeader,
            String valueDelimiter,
            String valueSeparator,
            String encoding,
            String models,
            String modeldir) {
    }
}
