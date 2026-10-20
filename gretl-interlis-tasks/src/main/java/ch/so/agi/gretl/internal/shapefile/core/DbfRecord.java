package ch.so.agi.gretl.internal.shapefile.core;

import java.util.List;

public record DbfRecord(boolean deleted, List<String> values) {}
