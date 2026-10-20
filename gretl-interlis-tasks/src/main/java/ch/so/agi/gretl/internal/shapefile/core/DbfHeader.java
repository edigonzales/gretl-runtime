package ch.so.agi.gretl.internal.shapefile.core;

import java.util.List;

public record DbfHeader(
        byte version,
        int lastUpdateYear,
        int lastUpdateMonth,
        int lastUpdateDay,
        int recordCount,
        int headerLength,
        int recordLength,
        List<DbfField> fields) {}
