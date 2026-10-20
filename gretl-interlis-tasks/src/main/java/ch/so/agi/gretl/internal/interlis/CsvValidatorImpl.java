package ch.so.agi.gretl.internal.interlis;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom_j.csv.CsvReader;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox_j.PipelinePool;
import ch.interlis.iox_j.logging.LogEventFactory;
import ch.interlis.ioxwkf.dbtools.IoxWkfConfig;
import org.interlis2.validator.Validator;

import java.io.File;

final class CsvValidatorImpl extends Validator {

    @Override
    protected IoxReader createReader(String filename, TransferDescription td, LogEventFactory errFactory,
                                     Settings settings, PipelinePool pool) throws IoxException {
        CsvReader reader = new CsvReader(new File(filename), settings);
        reader.setModel(td);
        reader.setFirstLineIsHeader(IoxWkfConfig.SETTING_FIRSTLINE_AS_HEADER.equals(
                settings.getValue(IoxWkfConfig.SETTING_FIRSTLINE)));

        char valueDelimiter = IoxWkfConfig.SETTING_VALUEDELIMITER_DEFAULT;
        String configuredDelimiter = settings.getValue(IoxWkfConfig.SETTING_VALUEDELIMITER);
        if (configuredDelimiter != null) {
            valueDelimiter = configuredDelimiter.charAt(0);
        }
        reader.setValueDelimiter(valueDelimiter);

        char valueSeparator = IoxWkfConfig.SETTING_VALUESEPARATOR_DEFAULT;
        String configuredSeparator = settings.getValue(IoxWkfConfig.SETTING_VALUESEPARATOR);
        if (configuredSeparator != null) {
            valueSeparator = configuredSeparator.charAt(0);
        }
        reader.setValueSeparator(valueSeparator);
        return reader;
    }
}
