package ch.so.agi.gretl.internal.interlis;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** gretl-next-compatible dataset-name resolution for ili2db file tasks. */
public final class Ili2DbDatasetNames {
    private Ili2DbDatasetNames() {
    }

    public static List<String> resolve(Ili2DbRequest request, List<String> executionInputs) {
        boolean explicit = !request.datasetNames().isEmpty();
        boolean fromTransfer = optionBool(request, "datasetNamesFromTransferFiles", false);
        boolean fromFiles = !request.datasetFiles().isEmpty();
        int strategies = (explicit ? 1 : 0) + (fromTransfer ? 1 : 0) + (fromFiles ? 1 : 0);
        if (strategies > 1) {
            throw new IllegalArgumentException(
                    "Use only one dataset naming strategy: datasetNames, "
                            + "datasetNamesFromTransferFiles or datasetFiles");
        }
        if (!explicit && !fromTransfer && !fromFiles) {
            return List.of();
        }
        List<String> names;
        if (explicit) {
            names = request.datasetNames();
        } else {
            List<Path> sources = fromFiles
                    ? request.datasetFiles()
                    : request.dataFiles();
            if (sources.isEmpty()) {
                throw new IllegalArgumentException(
                        "Dataset name derivation requires transfer or dataset files");
            }
            names = new ArrayList<>();
            for (Path source : sources.stream()
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                String name = source.getFileName().toString();
                int dot = name.lastIndexOf('.');
                if (dot > 0) name = name.substring(0, dot);
                names.add(applySlice(request, name));
            }
        }
        if (!executionInputs.isEmpty() && names.size() != executionInputs.size()) {
            throw new IllegalArgumentException(
                    "Number of dataset names (" + names.size()
                            + ") does not match number of transfer inputs ("
                            + executionInputs.size() + ")");
        }
        return List.copyOf(names);
    }

    private static String applySlice(Ili2DbRequest request, String name) {
        List<String> substring = optionList(request, "datasetSubstring");
        Integer start = optionInt(request, "datasetNameSliceStart");
        Integer end = optionInt(request, "datasetNameSliceEnd");
        if (substring.size() >= 2) {
            try {
                start = Integer.parseInt(substring.get(0));
                end = Integer.parseInt(substring.get(1));
            } catch (NumberFormatException ignored) {
                // Keep explicitly configured slice bounds.
            }
        }
        if (start == null) return name;
        int from = Math.max(0, Math.min(start, name.length()));
        int to = end == null ? name.length() : Math.max(from, Math.min(end, name.length()));
        return name.substring(from, to);
    }

    private static boolean optionBool(Ili2DbRequest request, String name, boolean fallback) {
        Object value = request.options().get(name);
        return value instanceof Boolean flag ? flag : fallback;
    }

    private static Integer optionInt(Ili2DbRequest request, String name) {
        Object value = request.options().get(name);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static List<String> optionList(Ili2DbRequest request, String name) {
        Object value = request.options().get(name);
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }
}
