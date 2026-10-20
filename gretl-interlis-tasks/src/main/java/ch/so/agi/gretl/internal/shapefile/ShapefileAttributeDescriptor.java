package ch.so.agi.gretl.internal.shapefile;

import ch.so.agi.gretl.internal.shapefile.core.DbfField;
import ch.so.agi.gretl.internal.shapefile.core.DbfFieldType;
import ch.so.agi.gretl.internal.shapefile.core.ShapeType;

public record ShapefileAttributeDescriptor(
        String iomAttributeName,
        String dbfFieldName,
        DbfFieldType dbfType,
        int length,
        int decimalCount,
        ShapeType shapeType,
        Integer srid,
        boolean mandatory) {

    public boolean geometry() {
        return shapeType != null && shapeType != ShapeType.NULL;
    }

    public DbfField toDbfField() {
        if (geometry()) {
            throw new IllegalStateException("Geometry descriptors are not DBF fields");
        }
        return new DbfField(dbfFieldName, dbfType, length, decimalCount);
    }
}
