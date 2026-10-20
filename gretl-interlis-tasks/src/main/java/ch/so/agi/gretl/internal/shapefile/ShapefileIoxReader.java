package ch.so.agi.gretl.internal.shapefile;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.CoordType;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Extendable;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.MultiCoordType;
import ch.interlis.ili2c.metamodel.MultiPolylineType;
import ch.interlis.ili2c.metamodel.MultiSurfaceType;
import ch.interlis.ili2c.metamodel.PolylineType;
import ch.interlis.ili2c.metamodel.SurfaceOrAreaType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxFactoryCollection;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox_j.DefaultIoxFactoryCollection;
import ch.interlis.iox_j.EndBasketEvent;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.IoxIliReader;
import ch.interlis.iox_j.ObjectEvent;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;
import ch.interlis.iox_j.jts.Jts2iox;
import ch.so.agi.gretl.internal.shapefile.core.DbfField;
import ch.so.agi.gretl.internal.shapefile.core.DbfFieldType;
import ch.so.agi.gretl.internal.shapefile.core.DbfReader;
import ch.so.agi.gretl.internal.shapefile.core.DbfRecord;
import ch.so.agi.gretl.internal.shapefile.core.ShapeRecord;
import ch.so.agi.gretl.internal.shapefile.core.ShapeType;
import ch.so.agi.gretl.internal.shapefile.core.ShapefileDataset;
import ch.so.agi.gretl.internal.shapefile.core.ShpReader;
import ch.so.agi.gretl.internal.shapefile.geom.ShpGeometryDecoder;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ShapefileIoxReader implements IoxReader, IoxIliReader {
    private enum State {
        START,
        BASKET,
        OBJECTS,
        END_BASKET,
        END_TRANSFER,
        DONE
    }

    private final File file;
    private final ShpReader shpReader;
    private final DbfReader dbfReader;
    private final ShpGeometryDecoder geometryDecoder = new ShpGeometryDecoder();
    private final List<DbfField> dbfFields;

    private TransferDescription model;
    private State state = State.START;
    private String topicName;
    private String className;
    private String geometryAttribute = ShapefileConstants.GEOMETRY_ATTRIBUTE;
    private Map<String, String> dbfToIom = new LinkedHashMap<>();
    private IoxFactoryCollection factory;
    private long recordNumber;

    public ShapefileIoxReader(File file, Settings settings) throws IoxException {
        this.file = file;
        try {
            ShapefileDataset dataset = ShapefileDataset.fromPath(file.toPath(), false);
            Charset charset = charset(dataset, settings);
            this.shpReader = ShpReader.open(dataset.shp());
            this.dbfReader = DbfReader.open(dataset.dbf(), charset);
            this.dbfFields = List.copyOf(dbfReader.fields());
            configureDefaultNames();
        } catch (IOException | ShapefileMappingException e) {
            throw new IoxException("failed to open Shapefile " + file, e);
        }
    }

    @Override
    public void setModel(TransferDescription model) {
        this.model = model;
        inferNamesFromModel();
    }

    public String[] getAttributes() {
        List<String> result = new ArrayList<>();
        for (DbfField field : dbfFields) {
            result.add(dbfToIom.getOrDefault(field.name(), field.name()));
        }
        if (shpReader.header().shapeType() != ShapeType.NULL) {
            result.add(geometryAttribute);
        }
        return result.toArray(String[]::new);
    }

    public String getGeomAttr() {
        return geometryAttribute;
    }

    @Override
    public IoxEvent read() throws IoxException {
        return switch (state) {
            case START -> {
                state = State.BASKET;
                yield new StartTransferEvent();
            }
            case BASKET -> {
                state = State.OBJECTS;
                yield new StartBasketEvent(topicName, "b1");
            }
            case OBJECTS -> readObjectOrEndBasket();
            case END_BASKET -> {
                state = State.END_TRANSFER;
                yield new EndBasketEvent();
            }
            case END_TRANSFER -> {
                state = State.DONE;
                yield new EndTransferEvent();
            }
            case DONE -> null;
        };
    }

    private IoxEvent readObjectOrEndBasket() throws IoxException {
        try {
            Optional<ShapeRecord> shape = shpReader.readNext();
            Optional<DbfRecord> dbf = dbfReader.readNext();
            if (shape.isEmpty() && dbf.isEmpty()) {
                state = State.END_BASKET;
                return read();
            }
            if (shape.isEmpty() || dbf.isEmpty()) {
                throw new IoxException("SHP and DBF record counts differ in " + file);
            }
            recordNumber++;
            if (dbf.get().deleted()) {
                return readObjectOrEndBasket();
            }
            return new ObjectEvent(toObject(shape.get(), dbf.get()));
        } catch (IOException | ShapefileMappingException e) {
            throw new IoxException("failed to read Shapefile record " + (recordNumber + 1), e);
        }
    }

    private IomObject toObject(ShapeRecord shape, DbfRecord dbf) throws ShapefileMappingException {
        Iom_jObject object = new Iom_jObject(className, "o" + recordNumber);
        for (int i = 0; i < dbfFields.size() && i < dbf.values().size(); i++) {
            String value = normalizeDbfValue(dbfFields.get(i), dbf.values().get(i));
            if (value == null) {
                continue;
            }
            object.setattrvalue(dbfToIom.getOrDefault(dbfFields.get(i).name(), dbfFields.get(i).name()), value);
        }
        if (shape.shapeType() != ShapeType.NULL) {
            Geometry geometry = geometryDecoder.decode(shape);
            if (geometry != null) {
                object.addattrobj(geometryAttribute, toIoxGeometry(geometry));
            }
        }
        return object;
    }

    private String normalizeDbfValue(DbfField field, String raw) throws ShapefileMappingException {
        if (raw == null) {
            return null;
        }
        if (field.type() == DbfFieldType.CHARACTER) {
            if (!raw.isEmpty() && raw.chars().allMatch(c -> c == '\0')) {
                return null;
            }
            String value = raw.replaceAll(" +$", "");
            return value.isEmpty() ? null : value;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.equals("?")) {
            return null;
        }
        if ((field.type() == DbfFieldType.NUMERIC || field.type() == DbfFieldType.FLOAT)
                && value.chars().allMatch(c -> c == '*')) {
            return null;
        }
        if ((field.type() == DbfFieldType.NUMERIC || field.type() == DbfFieldType.FLOAT)
                && value.indexOf('*') >= 0) {
            throw new ShapefileMappingException(
                    "Partially filled numeric NULL in DBF field " + field.name());
        }
        if (field.type() == DbfFieldType.DATE) {
            if (value.equals("00000000") || value.equals("0")) {
                return null;
            }
            if (value.length() == 8 && value.chars().allMatch(Character::isDigit)) {
                return value.substring(0, 4) + "-" + value.substring(4, 6)
                        + "-" + value.substring(6, 8);
            }
            throw new ShapefileMappingException(
                    "Invalid DBF date in field " + field.name() + ": " + value);
        }
        if (field.type() == DbfFieldType.LOGICAL) {
            return switch (value.toUpperCase(Locale.ROOT)) {
                case "T", "Y", "1" -> "true";
                case "F", "N", "0" -> "false";
                default -> throw new ShapefileMappingException(
                        "Invalid DBF logical in field " + field.name() + ": " + value);
            };
        }
        return value;
    }

    private IomObject toIoxGeometry(Geometry geometry) throws ShapefileMappingException {
        if (geometry instanceof Point point) {
            return Jts2iox.JTS2coord(point.getCoordinate());
        }
        if (geometry instanceof MultiPoint multiPoint) {
            return Jts2iox.JTS2multicoord(multiPoint.getCoordinates());
        }
        if (geometry instanceof LineString lineString) {
            return Jts2iox.JTS2polyline(lineString);
        }
        if (geometry instanceof MultiLineString multiLineString) {
            return Jts2iox.JTS2multipolyline(multiLineString);
        }
        if (geometry instanceof Polygon polygon) {
            return Jts2iox.JTS2surface(polygon);
        }
        if (geometry instanceof MultiPolygon multiPolygon) {
            return Jts2iox.JTS2multisurface(multiPolygon);
        }
        throw new ShapefileMappingException("unsupported geometry type " + geometry.getGeometryType());
    }

    private void inferNamesFromModel() {
        if (model == null) {
            return;
        }
        Table table = findMatchingTable();
        if (table == null) {
            return;
        }
        className = table.getScopedName();
        topicName = table.getContainer().getScopedName();
        Map<String, String> mapped = new LinkedHashMap<>();
        for (DbfField field : dbfFields) {
            AttributeDef attribute = findAttributeIgnoreCase(table, field.name());
            mapped.put(field.name(), attribute == null ? field.name() : attribute.getName());
        }
        dbfToIom = mapped;
        AttributeDef geometry = firstGeometryAttribute(table, shpReader.header().shapeType());
        if (geometry != null) {
            geometryAttribute = geometry.getName();
        }
    }

    private Table findMatchingTable() {
        Iterator<Model> models = model.iterator();
        while (models.hasNext()) {
            Model currentModel = models.next();
            Iterator<Element> elements = currentModel.iterator();
            while (elements.hasNext()) {
                Element element = elements.next();
                if (element instanceof Topic topic) {
                    Iterator<Element> topicElements = topic.iterator();
                    while (topicElements.hasNext()) {
                        Element topicElement = topicElements.next();
                        if (topicElement instanceof Table table && matches(table)) {
                            return table;
                        }
                    }
                } else if (element instanceof Table table && matches(table)) {
                    return table;
                }
            }
        }
        return null;
    }

    private boolean matches(Table table) {
        for (DbfField field : dbfFields) {
            if (findAttributeIgnoreCase(table, field.name()) == null) {
                return false;
            }
        }
        return shpReader.header().shapeType() == ShapeType.NULL
                || firstGeometryAttribute(table, shpReader.header().shapeType()) != null;
    }

    private AttributeDef findAttributeIgnoreCase(Table table, String name) {
        Iterator<Extendable> attributes = table.getAttributes();
        while (attributes.hasNext()) {
            Extendable extendable = attributes.next();
            if (extendable instanceof AttributeDef attribute
                    && attribute.getName() != null
                    && attribute.getName().equalsIgnoreCase(name)) {
                return attribute;
            }
        }
        return null;
    }

    private AttributeDef firstGeometryAttribute(Table table, ShapeType shapeType) {
        Iterator<Extendable> attributes = table.getAttributes();
        while (attributes.hasNext()) {
            Extendable extendable = attributes.next();
            if (extendable instanceof AttributeDef attribute && geometryCompatible(attribute, shapeType)) {
                return attribute;
            }
        }
        return null;
    }

    private boolean geometryCompatible(AttributeDef attribute, ShapeType shapeType) {
        Type type = attribute.getDomainResolvingAll();
        if (type == null) {
            type = attribute.getDomainResolvingAliases();
        }
        return switch (shapeType) {
            case POINT -> type instanceof CoordType;
            case MULTIPOINT -> type instanceof MultiCoordType;
            case POLYLINE -> type instanceof PolylineType || type instanceof MultiPolylineType;
            case POLYGON -> type instanceof SurfaceOrAreaType || type instanceof MultiSurfaceType;
            case NULL -> false;
            default -> false;
        };
    }

    private void configureDefaultNames() {
        String baseName = file.getName();
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }
        String safeBaseName = baseName.replaceAll("[^A-Za-z0-9_]", "_");
        topicName = safeBaseName + ".Topic";
        className = topicName + ".Class1";
        Map<String, String> mapped = new LinkedHashMap<>();
        for (DbfField field : dbfFields) {
            mapped.put(field.name(), field.name());
        }
        dbfToIom = mapped;
    }

    private Charset charset(ShapefileDataset dataset, Settings settings) throws ShapefileMappingException {
        String configured = settings == null ? null
                : settings.getValue(ShapefileConstants.ENCODING);
        try {
            return DbfEncoding.resolve(dataset.dbf(), dataset.cpg(), configured);
        } catch (IOException exception) {
            throw new ShapefileMappingException(
                    "Cannot determine DBF encoding for " + dataset.dbf(), exception);
        }
    }

    @Override
    public void close() throws IoxException {
        closeQuietly(shpReader);
        closeQuietly(dbfReader);
        state = State.DONE;
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

    public String getMimeType() {
        return "application/x-esri-shape";
    }

    public void setTopicFilter(String[] topicFilter) {
    }

    private void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
