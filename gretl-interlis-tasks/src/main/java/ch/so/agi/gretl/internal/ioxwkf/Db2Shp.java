package ch.so.agi.gretl.internal.ioxwkf;

import ch.ehi.basics.settings.Settings;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxWriter;
import ch.interlis.ioxwkf.dbtools.AbstractExportFromdb;
import ch.interlis.ioxwkf.dbtools.AttributeDescriptor;
import ch.so.agi.gretl.internal.shapefile.ShapefileConstants;
import ch.so.agi.gretl.internal.shapefile.ShapefileDescriptorMapper;
import ch.so.agi.gretl.internal.shapefile.ShapefileIoxWriter;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class Db2Shp extends AbstractExportFromdb {
    @Override
    protected IoxWriter createWriter(File file, Settings settings, AttributeDescriptor[] descriptors) throws IoxException {
        if (file == null) {
            throw new IoxException("file==null.");
        }
        String encoding = settings == null ? null : settings.getValue(ShapefileConstants.ENCODING);
        Charset charset = encoding == null || encoding.isBlank() ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        ShapefileIoxWriter writer = new ShapefileIoxWriter(file.toPath(), charset, Optional.empty());
        writer.setAttributeDescriptors(new ShapefileDescriptorMapper().fromIoxWkf(descriptors));
        return writer;
    }
}
