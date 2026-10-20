package ch.so.agi.gretl.internal.shapefile.core;

import java.nio.ByteBuffer;

public record ShapeRecord(int recordNumber, ShapeType shapeType, ByteBuffer content, Bounds bounds) {}
