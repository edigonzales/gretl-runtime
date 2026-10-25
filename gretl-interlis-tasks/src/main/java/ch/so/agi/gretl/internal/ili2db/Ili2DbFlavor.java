package ch.so.agi.gretl.internal.ili2db;

import ch.ehi.ili2db.gui.Config;
import java.nio.file.Path;

public enum Ili2DbFlavor {
    POSTGIS {
        @Override Config createConfig(Ili2DbRequest request) {
            Config config = new Config();
            new ch.ehi.ili2pg.PgMain().initConfig(config);
            return config;
        }
        @Override boolean usesJdbcConnection() { return true; }
    },
    GEOPACKAGE {
        @Override Config createConfig(Ili2DbRequest request) {
            Config config = new Config();
            new ch.ehi.ili2gpkg.GpkgMain().initConfig(config);
            setFile(config, request.databaseFile(), "jdbc:sqlite:");
            return config;
        }
    },
    DUCKDB {
        @Override Config createConfig(Ili2DbRequest request) {
            Config config = new Config();
            new ch.ehi.ili2duckdb.DuckDBMain().initConfig(config);
            config.setStrokeArcs(Config.STROKE_ARCS_ENABLE);
            setFile(config, request.databaseFile(), "jdbc:duckdb:");
            return config;
        }
    };

    abstract Config createConfig(Ili2DbRequest request);
    boolean usesJdbcConnection() { return false; }

    private static void setFile(Config config, Path file, String prefix) {
        if (file == null) {
            throw new IllegalArgumentException("databaseFile is required");
        }
        Path absolute = file.toAbsolutePath().normalize();
        config.setDbfile(absolute.toString());
        config.setDburl(prefix + absolute);
    }
}
