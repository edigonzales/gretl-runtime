package ch.so.agi.gretl.internal.shapefile;

import ch.interlis.ioxwkf.dbtools.AttributeDescriptor;
import ch.so.agi.gretl.internal.shapefile.core.DbfFieldType;
import ch.so.agi.gretl.internal.shapefile.core.ShapeType;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ShapefileDescriptorMapper {
    private static final int DEFAULT_TEXT_LENGTH = 254;

    public List<ShapefileAttributeDescriptor> fromIoxWkf(AttributeDescriptor[] descriptors) {
        ShapefileFieldNames names = new ShapefileFieldNames();
        List<ShapefileAttributeDescriptor> result = new ArrayList<>();
        for (AttributeDescriptor descriptor : descriptors) {
            String iomName = descriptor.getIomAttributeName();
            if (iomName == null || iomName.isBlank()) {
                iomName = descriptor.getDbColumnName();
            }
            if (descriptor.isGeometry()) {
                result.add(geometry(iomName, descriptor.getDbColumnGeomTypeName(), descriptor.getSrId()));
            } else {
                result.add(scalar(iomName, names.map(iomName), descriptor.getDbColumnType(),
                        descriptor.getDbColumnTypeName(), descriptor.getPrecision()));
            }
        }
        return result;
    }

    public List<ShapefileAttributeDescriptor> fromGeoPackage(DatabaseMetaData metaData, String tableName,
                                                            String geometryColumn, String geometryType,
                                                            Integer srid) throws Exception {
        ShapefileFieldNames names = new ShapefileFieldNames();
        List<ShapefileAttributeDescriptor> result = new ArrayList<>();
        try (ResultSet columns = metaData.getColumns(null, null, tableName, null)) {
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                if (geometryColumn != null && geometryColumn.equalsIgnoreCase(columnName)) {
                    result.add(geometry(columnName, geometryType, srid));
                } else {
                    result.add(scalar(columnName, names.map(columnName), columns.getInt("DATA_TYPE"),
                            columns.getString("TYPE_NAME"), columns.getInt("COLUMN_SIZE")));
                }
            }
        }
        if (geometryColumn != null && result.stream().noneMatch(ShapefileAttributeDescriptor::geometry)) {
            result.add(geometry(geometryColumn, geometryType, srid));
        }
        return result;
    }

    private ShapefileAttributeDescriptor geometry(String iomName, String geometryType, Integer srid) {
        return new ShapefileAttributeDescriptor(
                iomName,
                iomName,
                null,
                0,
                0,
                shapeType(geometryType),
                srid,
                false);
    }

    private ShapefileAttributeDescriptor scalar(String iomName, String dbfName, Integer sqlType, String typeName,
                                                Integer precision) {
        DbfFieldType dbfType = dbfType(sqlType, typeName);
        int length = length(dbfType, sqlType, typeName, precision);
        int decimals = decimals(dbfType, sqlType);
        return new ShapefileAttributeDescriptor(iomName, dbfName, dbfType, length, decimals, null, null, false);
    }

    private ShapeType shapeType(String geometryType) {
        if (geometryType == null) {
            return ShapeType.POINT;
        }
        return switch (geometryType.toUpperCase(Locale.ROOT)) {
            case "POINT" -> ShapeType.POINT;
            case "MULTIPOINT" -> ShapeType.MULTIPOINT;
            case "LINESTRING", "COMPOUNDCURVE" -> ShapeType.POLYLINE;
            case "MULTILINESTRING", "MULTICURVE" -> ShapeType.POLYLINE;
            case "POLYGON", "CURVEPOLYGON" -> ShapeType.POLYGON;
            case "MULTIPOLYGON", "MULTISURFACE" -> ShapeType.POLYGON;
            default -> ShapeType.POINT;
        };
    }

    private DbfFieldType dbfType(Integer sqlType, String typeName) {
        String lowerTypeName = typeName == null ? "" : typeName.toLowerCase(Locale.ROOT);
        if (lowerTypeName.contains("bool")) {
            return DbfFieldType.LOGICAL;
        }
        if (sqlType == null) {
            return DbfFieldType.CHARACTER;
        }
        return switch (sqlType) {
            case Types.BOOLEAN, Types.BIT -> DbfFieldType.LOGICAL;
            case Types.DATE -> DbfFieldType.DATE;
            case Types.SMALLINT, Types.TINYINT, Types.INTEGER, Types.BIGINT,
                    Types.NUMERIC, Types.DECIMAL, Types.FLOAT, Types.REAL, Types.DOUBLE -> DbfFieldType.NUMERIC;
            default -> lowerTypeName.equals("date") ? DbfFieldType.DATE : DbfFieldType.CHARACTER;
        };
    }

    private int length(DbfFieldType dbfType, Integer sqlType, String typeName, Integer precision) {
        if (dbfType == DbfFieldType.LOGICAL) {
            return 1;
        }
        if (dbfType == DbfFieldType.DATE) {
            return 8;
        }
        if (dbfType == DbfFieldType.NUMERIC) {
            if (sqlType != null && (sqlType == Types.SMALLINT || sqlType == Types.TINYINT || sqlType == Types.INTEGER)) {
                return 11;
            }
            if (sqlType != null && sqlType == Types.BIGINT) {
                return 20;
            }
            return 32;
        }
        int configured = precision == null || precision <= 0 ? DEFAULT_TEXT_LENGTH : precision;
        return Math.min(DEFAULT_TEXT_LENGTH, Math.max(1, configured));
    }

    private int decimals(DbfFieldType dbfType, Integer sqlType) {
        if (dbfType != DbfFieldType.NUMERIC || sqlType == null) {
            return 0;
        }
        return switch (sqlType) {
            case Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> 8;
            default -> 0;
        };
    }
}
