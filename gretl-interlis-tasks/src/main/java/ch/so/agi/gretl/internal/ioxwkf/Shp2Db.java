package ch.so.agi.gretl.internal.ioxwkf;

import ch.ehi.basics.settings.Settings;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxReader;
import ch.interlis.ioxwkf.dbtools.AbstractImport2db;
import ch.interlis.ioxwkf.dbtools.AttributeDescriptor;
import ch.so.agi.gretl.internal.shapefile.ShapefileIoxReader;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class Shp2Db extends AbstractImport2db {
    @Override
    protected IoxReader createReader(File file, Settings settings) throws IoxException {
        if (file == null) {
            throw new IoxException("file==null.");
        }
        if (!file.exists()) {
            throw new IoxException("file " + file.getName() + " not found.");
        }
        return new ShapefileIoxReader(file, settings);
    }

    @Override
    protected List<AttributeDescriptor> assignIomAttr2DbColumn(IoxReader reader,
                                                               List<AttributeDescriptor> dbAttributes,
                                                               List<String> unmappedAttributes) {
        ShapefileIoxReader shpReader = (ShapefileIoxReader) reader;
        List<AttributeDescriptor> result = new ArrayList<>();
        HashMap<String, AttributeDescriptor> dbAttributesByLowerName = new HashMap<>();
        AttributeDescriptor geometryDescriptor = null;
        for (AttributeDescriptor dbAttribute : dbAttributes) {
            if (dbAttribute.getDbColumnGeomTypeName() != null) {
                geometryDescriptor = dbAttribute;
            }
            dbAttributesByLowerName.put(dbAttribute.getDbColumnName().toLowerCase(), dbAttribute);
        }

        String geometryAttribute = shpReader.getGeomAttr();
        for (String shapeAttribute : shpReader.getAttributes()) {
            AttributeDescriptor descriptor;
            if (shapeAttribute.equals(geometryAttribute)) {
                descriptor = geometryDescriptor;
            } else {
                descriptor = dbAttributesByLowerName.get(shapeAttribute.toLowerCase());
            }
            if (descriptor == null) {
                unmappedAttributes.add(shapeAttribute);
            } else {
                descriptor.setIomAttributeName(shapeAttribute);
                result.add(descriptor);
            }
        }
        return result;
    }
}
