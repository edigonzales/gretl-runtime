package ch.so.agi.gretl.internal.ioxwkf;

import ch.ehi.basics.settings.Settings;
import ch.interlis.iom_j.csv.CsvReader;
import ch.interlis.ioxwkf.dbtools.Csv2db;
import ch.interlis.ioxwkf.dbtools.Db2Csv;
import ch.interlis.ioxwkf.dbtools.Db2Gpkg;
import ch.interlis.ioxwkf.dbtools.Gpkg2db;
import ch.interlis.ioxwkf.dbtools.IoxWkfConfig;
import ch.so.agi.gretl.internal.shapefile.ShapefileConstants;
import ch.so.agi.gretl.internal.sql.DatabaseSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;

public final class IoxWkfDatabaseEngine {

    public void importCsv(CsvImportRequest request) throws Exception {
        validateDatabase(request.database());
        requireFile(request.dataFile(), "dataFile");
        requireNonBlank(request.tableName(), "tableName");

        Connection connection = null;
        try {
            connection = open(request.database());
            connection.setAutoCommit(false);
            new Csv2db().importData(request.dataFile().toFile(), connection, csvImportSettings(request));
            connection.commit();
        } catch (Exception e) {
            rollback(connection);
            throw e;
        } finally {
            close(connection);
        }
    }

    public void exportCsv(CsvExportRequest request) throws Exception {
        validateDatabase(request.database());
        requireNonBlank(request.tableName(), "tableName");
        requireOutput(request.dataFile(), "dataFile");

        Connection connection = null;
        try {
            connection = open(request.database());
            connection.setAutoCommit(false);
            Db2Csv db2Csv = new Db2Csv();
            if (request.attributes() != null && !request.attributes().isEmpty()) {
                db2Csv.setAttributes(request.attributes().toArray(String[]::new));
            }
            db2Csv.exportData(request.dataFile().toFile(), connection, csvExportSettings(request));
            connection.commit();
        } catch (Exception e) {
            rollback(connection);
            throw e;
        } finally {
            close(connection);
        }
    }

    public void importGpkg(GpkgImportRequest request) throws Exception {
        validateDatabase(request.database());
        requireFile(request.dataFile(), "dataFile");
        requireNonBlank(request.sourceTableName(), "srcTableName");
        requireNonBlank(request.targetTableName(), "dstTableName");

        Connection connection = null;
        try {
            connection = open(request.database());
            connection.setAutoCommit(false);
            new Gpkg2db().importData(request.dataFile().toFile(), connection, gpkgImportSettings(request));
            connection.commit();
        } catch (Exception e) {
            rollback(connection);
            throw e;
        } finally {
            close(connection);
        }
    }

    public void importShp(ShpImportRequest request) throws Exception {
        validateDatabase(request.database());
        requireFile(request.dataFile(), "dataFile");
        requireNonBlank(request.tableName(), "tableName");

        Connection connection = null;
        try {
            connection = open(request.database());
            connection.setAutoCommit(false);
            new Shp2Db().importData(request.dataFile().toFile(), connection, shpImportSettings(request));
            connection.commit();
        } catch (Exception e) {
            rollback(connection);
            throw e;
        } finally {
            close(connection);
        }
    }

    public void exportGpkg(GpkgExportRequest request) throws Exception {
        validateDatabase(request.database());
        requireOutput(request.dataFile(), "dataFile");
        if (request.sourceTableNames() == null || request.sourceTableNames().isEmpty()) {
            throw new IllegalArgumentException("srcTableName must not be empty");
        }
        if (request.targetTableNames() == null || request.targetTableNames().isEmpty()) {
            throw new IllegalArgumentException("dstTableName must not be empty");
        }
        if (request.sourceTableNames().size() != request.targetTableNames().size()) {
            throw new IllegalArgumentException("number of source table names (" + request.sourceTableNames().size()
                    + ") doesn't match number of destination table names (" + request.targetTableNames().size() + ")");
        }

        Connection connection = null;
        try {
            connection = open(request.database());
            connection.setAutoCommit(false);
            for (int i = 0; i < request.sourceTableNames().size(); i++) {
                new Db2Gpkg().exportData(request.dataFile().toFile(), connection,
                        gpkgExportSettings(request, request.sourceTableNames().get(i), request.targetTableNames().get(i)));
            }
            connection.commit();
        } catch (Exception e) {
            rollback(connection);
            throw e;
        } finally {
            close(connection);
        }
    }

    public void exportShp(ShpExportRequest request) throws Exception {
        validateDatabase(request.database());
        requireOutput(request.dataFile(), "dataFile");
        requireNonBlank(request.tableName(), "tableName");

        Connection connection = null;
        try {
            connection = open(request.database());
            connection.setAutoCommit(false);
            new Db2Shp().exportData(request.dataFile().toFile(), connection, shpExportSettings(request));
            connection.commit();
        } catch (Exception e) {
            rollback(connection);
            throw e;
        } finally {
            close(connection);
        }
    }

    public int importJson(JsonImportRequest request) throws Exception {
        validateDatabase(request.database());
        requireFile(request.jsonFile(), "jsonFile");
        requireNonBlank(request.qualifiedTableName(), "qualifiedTableName");
        requireNonBlank(request.columnName(), "columnName");

        Connection connection = null;
        try {
            connection = open(request.database());
            connection.setAutoCommit(false);
            if (request.deleteAllRows()) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM " + request.qualifiedTableName())) {
                    statement.execute();
                }
            }

            JsonNode root = new ObjectMapper().readTree(Files.readString(request.jsonFile(), StandardCharsets.UTF_8));
            int count = 0;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + request.qualifiedTableName() + " (" + request.columnName() + ") VALUES (?)")) {
                if (root.isArray()) {
                    for (JsonNode node : root) {
                        statement.setString(1, node.toString());
                        count += statement.executeUpdate();
                    }
                } else {
                    statement.setString(1, root.toString());
                    count += statement.executeUpdate();
                }
            }
            connection.commit();
            return count;
        } catch (Exception e) {
            rollback(connection);
            throw e;
        } finally {
            close(connection);
        }
    }

    private Settings csvImportSettings(CsvImportRequest request) {
        Settings settings = commonCsvSettings(request.firstLineIsHeader(), request.valueDelimiter(),
                request.valueSeparator(), request.schemaName(), request.encoding());
        settings.setValue(IoxWkfConfig.SETTING_DBTABLE, request.tableName());
        if (request.batchSize() != null) {
            settings.setValue(IoxWkfConfig.SETTING_BATCHSIZE, request.batchSize().toString());
        }
        return settings;
    }

    private Settings csvExportSettings(CsvExportRequest request) {
        Settings settings = commonCsvSettings(request.firstLineIsHeader(), request.valueDelimiter(),
                request.valueSeparator(), request.schemaName(), request.encoding());
        settings.setValue(IoxWkfConfig.SETTING_DBTABLE, request.tableName());
        return settings;
    }

    private Settings commonCsvSettings(boolean firstLineIsHeader, String valueDelimiter, String valueSeparator,
                                       String schemaName, String encoding) {
        Settings settings = new Settings();
        settings.setValue(IoxWkfConfig.SETTING_FIRSTLINE,
                firstLineIsHeader ? IoxWkfConfig.SETTING_FIRSTLINE_AS_HEADER : IoxWkfConfig.SETTING_FIRSTLINE_AS_VALUE);
        if (valueDelimiter != null) {
            settings.setValue(IoxWkfConfig.SETTING_VALUEDELIMITER, requireSingleCharacter("valueDelimiter", valueDelimiter));
        }
        if (valueSeparator != null) {
            settings.setValue(IoxWkfConfig.SETTING_VALUESEPARATOR, requireSingleCharacter("valueSeparator", valueSeparator));
        }
        if (schemaName != null) {
            settings.setValue(IoxWkfConfig.SETTING_DBSCHEMA, schemaName);
        }
        if (encoding != null) {
            settings.setValue(CsvReader.ENCODING, encoding);
        }
        return settings;
    }

    private Settings gpkgImportSettings(GpkgImportRequest request) {
        Settings settings = commonGpkgSettings(request.schemaName(), request.batchSize(), request.fetchSize());
        settings.setValue(IoxWkfConfig.SETTING_GPKGTABLE, request.sourceTableName());
        settings.setValue(IoxWkfConfig.SETTING_DBTABLE, request.targetTableName());
        return settings;
    }

    private Settings shpImportSettings(ShpImportRequest request) {
        Settings settings = new Settings();
        settings.setValue(IoxWkfConfig.SETTING_DBTABLE, request.tableName());
        if (request.schemaName() != null) {
            settings.setValue(IoxWkfConfig.SETTING_DBSCHEMA, request.schemaName());
        }
        if (request.encoding() != null) {
            settings.setValue(ShapefileConstants.ENCODING, request.encoding());
        }
        if (request.batchSize() != null) {
            settings.setValue(IoxWkfConfig.SETTING_BATCHSIZE, request.batchSize().toString());
        }
        return settings;
    }

    private Settings shpExportSettings(ShpExportRequest request) {
        Settings settings = new Settings();
        settings.setValue(IoxWkfConfig.SETTING_DBTABLE, request.tableName());
        if (request.schemaName() != null) {
            settings.setValue(IoxWkfConfig.SETTING_DBSCHEMA, request.schemaName());
        }
        if (request.encoding() != null) {
            settings.setValue(ShapefileConstants.ENCODING, request.encoding());
        }
        return settings;
    }

    private Settings gpkgExportSettings(GpkgExportRequest request, String sourceTableName, String targetTableName) {
        Settings settings = commonGpkgSettings(request.schemaName(), request.batchSize(), request.fetchSize());
        settings.setValue(IoxWkfConfig.SETTING_DBTABLE, sourceTableName);
        settings.setValue(IoxWkfConfig.SETTING_GPKGTABLE, targetTableName);
        return settings;
    }

    private Settings commonGpkgSettings(String schemaName, Integer batchSize, Integer fetchSize) {
        Settings settings = new Settings();
        if (schemaName != null) {
            settings.setValue(IoxWkfConfig.SETTING_DBSCHEMA, schemaName);
        }
        if (batchSize != null) {
            settings.setValue(IoxWkfConfig.SETTING_BATCHSIZE, batchSize.toString());
        }
        if (fetchSize != null) {
            settings.setValue(IoxWkfConfig.SETTING_FETCHSIZE, fetchSize.toString());
        }
        return settings;
    }

    private Connection open(DatabaseSpec spec) throws Exception {
        if (spec.username() == null) {
            return DriverManager.getConnection(spec.jdbcUrl());
        }
        return DriverManager.getConnection(spec.jdbcUrl(), spec.username(), spec.password());
    }

    private void validateDatabase(DatabaseSpec database) {
        if (database == null || database.jdbcUrl() == null || database.jdbcUrl().isBlank()) {
            throw new IllegalArgumentException("database is not configured");
        }
    }

    private void requireFile(Path path, String name) {
        if (path == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(name + " does not exist: " + path);
        }
    }

    private void requireOutput(Path path, String name) throws Exception {
        if (path == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
    }

    private void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }

    private String requireSingleCharacter(String propertyName, String value) {
        if (value.length() != 1) {
            throw new IllegalArgumentException(propertyName + " must be a single character");
        }
        return value;
    }

    private void rollback(Connection connection) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (Exception ignored) {
            }
        }
    }

    private void close(Connection connection) throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    public record CsvImportRequest(
            DatabaseSpec database,
            Path dataFile,
            String tableName,
            boolean firstLineIsHeader,
            String valueDelimiter,
            String valueSeparator,
            String schemaName,
            String encoding,
            Integer batchSize) {
    }

    public record CsvExportRequest(
            DatabaseSpec database,
            Path dataFile,
            String tableName,
            boolean firstLineIsHeader,
            String valueDelimiter,
            String valueSeparator,
            String schemaName,
            List<String> attributes,
            String encoding) {
    }

    public record GpkgImportRequest(
            DatabaseSpec database,
            Path dataFile,
            String sourceTableName,
            String targetTableName,
            String schemaName,
            Integer batchSize,
            Integer fetchSize) {
    }

    public record GpkgExportRequest(
            DatabaseSpec database,
            Path dataFile,
            List<String> sourceTableNames,
            List<String> targetTableNames,
            String schemaName,
            Integer batchSize,
            Integer fetchSize) {
    }

    public record ShpImportRequest(
            DatabaseSpec database,
            Path dataFile,
            String tableName,
            String schemaName,
            String encoding,
            Integer batchSize) {
    }

    public record ShpExportRequest(
            DatabaseSpec database,
            Path dataFile,
            String tableName,
            String schemaName,
            String encoding) {
    }

    public record JsonImportRequest(
            DatabaseSpec database,
            Path jsonFile,
            String qualifiedTableName,
            String columnName,
            boolean deleteAllRows) {
    }
}
