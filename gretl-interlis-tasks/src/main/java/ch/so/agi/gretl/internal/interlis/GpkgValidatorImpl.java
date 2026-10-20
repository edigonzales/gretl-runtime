package ch.so.agi.gretl.internal.interlis;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox_j.PipelinePool;
import ch.interlis.iox_j.logging.LogEventFactory;
import ch.interlis.ioxwkf.dbtools.IoxWkfConfig;
import ch.interlis.ioxwkf.gpkg.GeoPackageReader;
import org.interlis2.validator.Validator;

import java.io.File;

final class GpkgValidatorImpl extends Validator {

    @Override
    protected IoxReader createReader(String filename, TransferDescription td, LogEventFactory errFactory,
                                     Settings settings, PipelinePool pool) throws IoxException {
        GeoPackageReader reader = new GeoPackageReader(new File(filename),
                settings.getValue(IoxWkfConfig.SETTING_GPKGTABLE), settings);
        reader.setModel(td);
        return reader;
    }
}
