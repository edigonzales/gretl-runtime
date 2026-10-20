package ch.so.agi.gretl.internal.interlis;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox_j.PipelinePool;
import ch.interlis.iox_j.logging.LogEventFactory;
import ch.so.agi.gretl.internal.shapefile.ShapefileIoxReader;
import org.interlis2.validator.Validator;

import java.io.File;

final class ShpValidatorImpl extends Validator {
    @Override
    protected IoxReader createReader(String filename, TransferDescription td, LogEventFactory errFactory,
                                     Settings settings, PipelinePool pool) throws IoxException {
        ShapefileIoxReader reader = new ShapefileIoxReader(new File(filename), settings);
        reader.setModel(td);
        return reader;
    }
}
