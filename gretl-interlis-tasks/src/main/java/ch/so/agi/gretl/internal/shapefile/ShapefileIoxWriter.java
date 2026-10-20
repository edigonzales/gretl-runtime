package ch.so.agi.gretl.internal.shapefile;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxFactoryCollection;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox_j.DefaultIoxFactoryCollection;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.ObjectEvent;
import ch.so.agi.gretl.internal.shapefile.core.DbfField;
import ch.so.agi.gretl.internal.shapefile.core.DbfFieldType;
import ch.so.agi.gretl.internal.shapefile.core.ShapeType;
import ch.so.agi.gretl.internal.shapefile.core.ShapefileDatasetWriter;
import ch.so.agi.gretl.internal.shapefile.core.ShapefileSchema;
import ch.so.agi.gretl.internal.shapefile.core.ShapefileWriteOptions;
import ch.so.agi.gretl.internal.shapefile.geom.IoxToJtsGeometry;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ShapefileIoxWriter implements IoxWriter {
    private final Path target;
    private final Charset charset;
    private final Optional<String> prjWkt;
    private final IoxToJtsGeometry geometryConverter = new IoxToJtsGeometry();

    private List<ShapefileAttributeDescriptor> descriptors = List.of();
    private List<ShapefileAttributeDescriptor> dbfDescriptors = List.of();
    private ShapefileAttributeDescriptor geometryDescriptor;
    private ShapefileDatasetWriter writer;
    private IoxFactoryCollection factory;
    private boolean finished;

    public ShapefileIoxWriter(Path target, Charset charset, Optional<String> prjWkt) {
        this.target = target;
        this.charset = charset;
        this.prjWkt = prjWkt;
    }

    public void setAttributeDescriptors(List<ShapefileAttributeDescriptor> descriptors) throws IoxException {
        if (writer != null) {
            throw new IoxException("attribute descriptors must be set before writing objects");
        }
        this.descriptors = List.copyOf(descriptors);
        List<ShapefileAttributeDescriptor> dbf = new ArrayList<>();
        ShapefileAttributeDescriptor geom = null;
        for (ShapefileAttributeDescriptor descriptor : this.descriptors) {
            if (descriptor.geometry()) {
                if (geom != null) {
                    throw new IoxException("Shapefile writer supports one geometry attribute only");
                }
                geom = descriptor;
            } else {
                dbf.add(descriptor);
            }
        }
        this.geometryDescriptor = geom;
        this.dbfDescriptors = List.copyOf(dbf);
    }

    @Override
    public void write(IoxEvent event) throws IoxException {
        if (event instanceof ObjectEvent objectEvent) {
            writeObject(objectEvent.getIomObject());
        } else if (event instanceof EndTransferEvent) {
            finish();
        }
    }

    private void writeObject(IomObject object) throws IoxException {
        ensureOpen();
        Geometry geometry = geometry(object);
        validateGeometry(geometry);
        Object[] values = dbfValues(object);
        try {
            writer.write(geometry, values);
        } catch (IOException | ShapefileMappingException e) {
            throw new IoxException("failed to write Shapefile object " + object.getobjecttag(), e);
        }
    }

    private void ensureOpen() throws IoxException {
        if (writer != null) {
            return;
        }
        if (descriptors.isEmpty()) {
            throw new IoxException("attribute descriptors are required before writing a Shapefile");
        }
        ShapeType shapeType = geometryDescriptor == null ? ShapeType.NULL : geometryDescriptor.shapeType();
        List<DbfField> fields = dbfDescriptors.stream().map(ShapefileAttributeDescriptor::toDbfField).toList();
        try {
            writer = ShapefileDatasetWriter.open(target, new ShapefileSchema(shapeType, fields),
                    new ShapefileWriteOptions(charset, prjWkt, ShapefileWriteOptions.OverflowPolicy.STRICT));
        } catch (IOException | ShapefileMappingException e) {
            throw new IoxException("failed to open Shapefile writer for " + target, e);
        }
    }

    private Geometry geometry(IomObject object) throws IoxException {
        if (geometryDescriptor == null) {
            return null;
        }
        IomObject geometry = object.getattrobj(geometryDescriptor.iomAttributeName(), 0);
        if (geometry == null && !ShapefileConstants.GEOMETRY_ATTRIBUTE.equals(geometryDescriptor.iomAttributeName())) {
            geometry = object.getattrobj(ShapefileConstants.GEOMETRY_ATTRIBUTE, 0);
        }
        try {
            return geometryConverter.convert(geometry);
        } catch (ShapefileMappingException e) {
            throw new IoxException(e);
        }
    }

    private void validateGeometry(Geometry geometry) throws IoxException {
        if (geometry == null || geometry.isEmpty() || geometryDescriptor == null) {
            return;
        }
        ShapeType expected = geometryDescriptor.shapeType();
        boolean ok = switch (expected) {
            case POINT -> geometry instanceof Point;
            case MULTIPOINT -> geometry instanceof MultiPoint;
            case POLYLINE -> geometry instanceof LineString || geometry instanceof MultiLineString;
            case POLYGON -> geometry instanceof Polygon || geometry instanceof MultiPolygon;
            default -> false;
        };
        if (!ok) {
            throw new IoxException("geometry type " + geometry.getGeometryType()
                    + " is not compatible with Shapefile shape type " + expected);
        }
    }

    private Object[] dbfValues(IomObject object) {
        Object[] values = new Object[dbfDescriptors.size()];
        for (int i = 0; i < dbfDescriptors.size(); i++) {
            ShapefileAttributeDescriptor descriptor = dbfDescriptors.get(i);
            String value = object.getattrvalue(descriptor.iomAttributeName());
            values[i] = descriptor.dbfType() == DbfFieldType.CHARACTER ? trimToField(value, descriptor.length()) : value;
        }
        return values;
    }

    private String trimToField(String value, int fieldLength) {
        if (value == null || fieldLength <= 0 || value.getBytes(charset).length <= fieldLength) {
            return value;
        }
        String suffix = " TRUNCATED!";
        if (fieldLength > suffix.length()) {
            int limit = fieldLength - suffix.getBytes(charset).length;
            StringBuilder text = new StringBuilder();
            for (int offset = 0; offset < value.length();) {
                int codePoint = value.codePointAt(offset);
                String candidate = text.toString() + new String(Character.toChars(codePoint));
                if (candidate.getBytes(charset).length > limit) {
                    break;
                }
                text.appendCodePoint(codePoint);
                offset += Character.charCount(codePoint);
            }
            return text + suffix;
        }
        StringBuilder text = new StringBuilder();
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String candidate = text.toString() + new String(Character.toChars(codePoint));
            if (candidate.getBytes(charset).length > fieldLength) {
                break;
            }
            text.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }
        return text.toString();
    }

    private void finish() throws IoxException {
        if (finished) {
            return;
        }
        ensureOpen();
        try {
            writer.finish();
            writer.close();
            finished = true;
        } catch (IOException | ShapefileMappingException e) {
            throw new IoxException("failed to finish Shapefile writer for " + target, e);
        }
    }

    @Override
    public void close() throws IoxException {
        if (writer != null && !finished) {
            try {
                writer.close();
            } catch (Exception e) {
                throw new IoxException(e);
            }
        }
    }

    @Override
    public void flush() throws IoxException {
    }

    @Override
    public IomObject createIomObject(String type, String oid) throws IoxException {
        return new Iom_jObject(type, oid);
    }

    @Override
    public IoxFactoryCollection getFactory() throws IoxException {
        if (factory == null) {
            factory = new DefaultIoxFactoryCollection();
        }
        return factory;
    }

    @Override
    public void setFactory(IoxFactoryCollection factory) throws IoxException {
        this.factory = factory;
    }
}
