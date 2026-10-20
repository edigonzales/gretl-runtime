package ch.so.agi.gretl.internal.interlis;

import ch.ehi.basics.settings.Settings;
import ch.interlis.iom_j.csv.CsvReader;
import ch.interlis.iox_j.plugins.IoxPlugin;
import ch.interlis.iox_j.validator.InterlisFunction;
import ch.interlis.ioxwkf.dbtools.IoxWkfConfig;
import ch.so.agi.gretl.internal.shapefile.ShapefileConstants;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.interlis2.validator.Validator;

/** Runtime adapter around ilivalidator and the IOX format readers. */
public final class InterlisValidationSupport {
    public boolean validateCsv(InterlisValidationRequest request) {
        Settings settings = settings(request);
        settings.setValue(IoxWkfConfig.SETTING_FIRSTLINE,
                request.firstLineIsHeader()
                        ? IoxWkfConfig.SETTING_FIRSTLINE_AS_HEADER
                        : IoxWkfConfig.SETTING_FIRSTLINE_AS_VALUE);
        if (request.valueDelimiter() != null) {
            settings.setValue(IoxWkfConfig.SETTING_VALUEDELIMITER,
                    singleCharacter("valueDelimiter", request.valueDelimiter()));
        }
        if (request.valueSeparator() != null) {
            settings.setValue(IoxWkfConfig.SETTING_VALUESEPARATOR,
                    singleCharacter("valueSeparator", request.valueSeparator()));
        }
        if (request.encoding() != null) {
            settings.setValue(CsvReader.ENCODING, request.encoding());
        }
        return new CsvValidatorImpl().validate(paths(request), settings);
    }

    public boolean validateJson(InterlisValidationRequest request) {
        Settings settings = settings(request);
        settings.setTransientObject(
                ch.interlis.iox_j.validator.Validator.CONFIG_CUSTOM_FUNCTIONS,
                customFunctions());
        return new JsonValidatorImpl().validate(paths(request), settings);
    }

    public boolean validateGpkg(InterlisValidationRequest request) {
        Settings settings = settings(request);
        settings.setValue(IoxWkfConfig.SETTING_GPKGTABLE, request.tableName());
        settings.setTransientObject(
                ch.interlis.iox_j.validator.Validator.CONFIG_CUSTOM_FUNCTIONS,
                customFunctions());
        return new GpkgValidatorImpl().validate(paths(request), settings);
    }

    public boolean validateShp(InterlisValidationRequest request) {
        Settings settings = settings(request);
        if (request.encoding() != null) {
            settings.setValue(ShapefileConstants.ENCODING, request.encoding());
        }
        settings.setTransientObject(
                ch.interlis.iox_j.validator.Validator.CONFIG_CUSTOM_FUNCTIONS,
                customFunctions());
        return new ShpValidatorImpl().validate(paths(request), settings);
    }

    private Settings settings(InterlisValidationRequest request) {
        Settings settings = new Settings();
        settings.setValue(Validator.SETTING_DISABLE_STD_LOGGER, Validator.TRUE);
        if (!request.modelNames().isEmpty()) {
            settings.setValue(Validator.SETTING_MODELNAMES,
                    String.join(";", request.modelNames()));
        }
        if (!request.modelDirectories().isEmpty()) {
            settings.setValue(Validator.SETTING_ILIDIRS,
                    String.join(";", request.modelDirectories()));
        }
        mutuallyExclusive(settings, Validator.SETTING_CONFIGFILE,
                request.configFile(), request.configRepositoryId(),
                "configFile", "configRepositoryId");
        mutuallyExclusive(settings, Validator.SETTING_META_CONFIGFILE,
                request.metaConfigFile(), request.metaConfigRepositoryId(),
                "metaConfigFile", "metaConfigRepositoryId");
        if (request.forceTypeValidation()) {
            settings.setValue(Validator.SETTING_FORCE_TYPE_VALIDATION, Validator.TRUE);
        }
        if (request.disableAreaValidation()) {
            settings.setValue(Validator.SETTING_DISABLE_AREA_VALIDATION, Validator.TRUE);
        }
        if (request.multiplicityOff()) {
            settings.setValue(Validator.SETTING_MULTIPLICITY_VALIDATION,
                    ch.interlis.iox_j.validator.ValidationConfig.OFF);
        }
        if (request.allObjectsAccessible()) {
            settings.setValue(Validator.SETTING_ALL_OBJECTS_ACCESSIBLE, Validator.TRUE);
        }
        if (request.skipPolygonBuilding()) {
            settings.setValue(
                    ch.interlis.iox_j.validator.Validator.CONFIG_DO_ITF_LINETABLES,
                    ch.interlis.iox_j.validator.Validator.CONFIG_DO_ITF_LINETABLES_DO);
        }
        if (request.logFile() != null) {
            settings.setValue(Validator.SETTING_LOGFILE,
                    request.logFile().toAbsolutePath().toString());
        }
        if (request.xtfLogFile() != null) {
            settings.setValue(Validator.SETTING_XTFLOG,
                    request.xtfLogFile().toAbsolutePath().toString());
        }
        if (request.proxy() != null) {
            settings.setValue(ch.interlis.ili2c.gui.UserSettings.HTTP_PROXY_HOST,
                    request.proxy());
        }
        if (request.proxyPort() != null) {
            settings.setValue(ch.interlis.ili2c.gui.UserSettings.HTTP_PROXY_PORT,
                    request.proxyPort().toString());
        }
        return settings;
    }

    private void mutuallyExclusive(Settings settings, String key, Path file,
                                   String repositoryId, String fileMethod,
                                   String repositoryMethod) {
        if (file != null && repositoryId != null) {
            throw new InterlisTaskException("VALIDATION_CONFIG",
                    "Use either " + fileMethod + "(...) or " + repositoryMethod
                            + "(...), not both.", "configuration",
                    java.util.Optional.empty(), null);
        }
        if (file != null) {
            settings.setValue(key, file.toAbsolutePath().toString());
        } else if (repositoryId != null) {
            settings.setValue(key, repositoryId);
        }
    }

    private Map<String, Class<?>> customFunctions() {
        List<String> names = List.of(
                "ch.so.agi.ilivalidator.ext.IsHttpResourceIoxPlugin",
                "ch.so.agi.ilivalidator.ext.AreaIoxPlugin",
                "ch.so.agi.ilivalidator.ext.LengthIoxPlugin",
                "ch.so.agi.ilivalidator.ext.IsValidDocumentsCycleIoxPlugin",
                "ch.so.agi.ilivalidator.ext.RingSelfIntersectionIoxPlugin",
                "ch.so.agi.ilivalidator.ext.TooFewPointsPolylineIoxPlugin",
                "ch.geowerkstatt.ilivalidator.extensions.functions.GetAreaIoxPlugin",
                "ch.geowerkstatt.ilivalidator.extensions.functions.GetLengthIoxPlugin",
                "ch.geowerkstatt.ilivalidator.extensions.functions.GetInnerRingsCountIoxPlugin",
                "ch.geowerkstatt.ilivalidator.extensions.functions.GetInGroupsIoxPlugin",
                "ch.geowerkstatt.ilivalidator.extensions.functions.IsInsideExternalXtfIoxPlugin",
                "ch.geowerkstatt.ilivalidator.extensions.functions.IsInsideExternalXtfResourceIoxPlugin",
                "ch.geowerkstatt.ilivalidator.extensions.functions.IsInsideIoxPlugin",
                "ch.geowerkstatt.ilivalidator.extensions.functions.UnionIoxPlugin",
                "ch.geowerkstatt.ilivalidator.extensions.functions.PolylinesOverlapIoxPlugin",
                "ch.geowerkstatt.ilivalidator.extensions.functions.FindObjectsIoxPlugin",
                "ch.geowerkstatt.ilivalidator.extensions.functions.FilterIoxPlugin",
                "ch.geowerkstatt.ilivalidator.extensions.functions.ngk.IsInsideAreaByCodeIoxPlugin");
        Map<String, Class<?>> functions = new LinkedHashMap<>();
        for (String name : names) {
            try {
                Class<?> type = Class.forName(name);
                IoxPlugin plugin = (IoxPlugin) type.getDeclaredConstructor().newInstance();
                functions.put(((InterlisFunction) plugin).getQualifiedIliName(), type);
            } catch (Exception exception) {
                throw new InterlisTaskException("VALIDATION_FUNCTION",
                        "Failed to register ilivalidator custom function " + name,
                        "configuration", java.util.Optional.empty(), exception);
            }
        }
        return functions;
    }

    private String[] paths(InterlisValidationRequest request) {
        List<String> paths = new ArrayList<>();
        for (Path path : request.dataFiles()) {
            paths.add(path.toAbsolutePath().toString());
        }
        return paths.toArray(String[]::new);
    }

    private String singleCharacter(String name, String value) {
        if (value.length() != 1) {
            throw new InterlisTaskException("VALIDATION_CONFIG",
                    name + " must be a single character", "configuration",
                    java.util.Optional.empty(), null);
        }
        return value;
    }
}
