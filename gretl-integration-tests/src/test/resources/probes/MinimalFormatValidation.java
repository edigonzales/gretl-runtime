package probe;

import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.tasks.interlis.validation.*;
import ch.so.agi.gretl.internal.InterlisTaskException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;

/** Component probe compiled against the task artifact's own production graph. */
public final class MinimalFormatValidation {
    public static void main(String[] args) throws Exception {
        for (String absent : List.of("ch.so.agi.ilivalidator.ext.AreaIoxPlugin",
                "ch.geowerkstatt.ilivalidator.extensions.functions.GetAreaIoxPlugin",
                "ch.geowerkstatt.ilivalidator.extensions.functions.ngk.IsInsideAreaByCodeIoxPlugin",
                "ch.so.agi.gretl.cli.GretlCli", "ch.so.agi.gretl.packager.JobPackager",
                "ch.so.agi.gretl.runtime.runner.SequentialJobRunner", "groovy.lang.GroovyObject")) {
            try { Class.forName(absent); throw new AssertionError("Unexpected dependency: " + absent); }
            catch (ClassNotFoundException expected) { }
        }
        String kind = args[0];
        Path input = Path.of(args[1]);
        var builder = new ValidatorBuilder();
        builder.setLogFile(Path.of(args[2]));
        builder.setModelDirectories(List.of(input.toString()));
        OperationType<ValidatorRequest, ValidatorOperations.Result> operation;
        switch (kind) {
            case "csv" -> {
                operation = ValidatorOperations.csv(); builder.setModelNames(List.of("CsvModel"));
                builder.setDataFiles(List.of(input.resolve(args[3]))); builder.setFirstLineIsHeader(false);
            }
            case "json" -> {
                operation = ValidatorOperations.json(); builder.setModelNames(List.of("Test2"));
                builder.setDataFiles(List.of(input.resolve("structAttrList.json")));
            }
            case "gpkg" -> {
                operation = ValidatorOperations.gpkg(); builder.setModelNames(List.of("GpkgModel"));
                builder.setDataFiles(List.of(input.resolve("attributes.gpkg"))); builder.setTableName("Attributes");
            }
            case "shp" -> {
                operation = ValidatorOperations.shp(); builder.setModelNames(List.of("ShpModel"));
                builder.setDataFiles(List.of(input.resolve("data.shp")));
            }
            default -> throw new IllegalArgumentException(kind);
        }
        OperationContext context = (OperationContext) Proxy.newProxyInstance(
                MinimalFormatValidation.class.getClassLoader(), new Class<?>[]{OperationContext.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("locale")) return java.util.Locale.GERMAN;
                    throw new AssertionError("Unexpected context use: " + method.getName());
                });
        try {
            var result = operation.handlerFactory().get().execute(builder.build(), context);
            if (!result.valid()) throw new AssertionError("Validation returned false");
            System.out.println("minimal-validation=true");
        } catch (InterlisTaskException failure) {
            if (!failure.code().equals("VALIDATION_FAILED")) throw failure;
            System.err.println("expected-validation-failure=" + failure.code());
            System.exit(10);
        }
    }
}
