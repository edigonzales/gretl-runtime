package ch.so.agi.gretl.runtime.geotools;

import org.geotools.referencing.CRS;

/**
 * Child-process entry for the negative EPSG-provider probe (TC-G05). The parent
 * test removes gt-epsg-hsql/hsqldb from the classpath and expects a visible
 * failure instead of an unchecked fallback CRS.
 */
public final class NegativeCrsProbe {
    private NegativeCrsProbe() {
    }

    public static void main(String[] args) {
        String code = args.length == 0 ? "EPSG:2056" : args[0];
        try {
            CRS.decode(code);
            System.err.println("UNEXPECTED_CRS_SUCCESS: " + code);
            System.exit(2);
        } catch (Exception expected) {
            System.out.println(expected.getClass().getName() + ": " + expected.getMessage());
            System.exit(0);
        }
    }
}
