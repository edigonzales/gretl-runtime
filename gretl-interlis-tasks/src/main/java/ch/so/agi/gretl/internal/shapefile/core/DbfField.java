package ch.so.agi.gretl.internal.shapefile.core;

public record DbfField(String name, DbfFieldType type, int length, int decimalCount) {}
