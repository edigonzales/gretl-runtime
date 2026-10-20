package ch.so.agi.gretl.internal.interlis;

import ch.ehi.basics.settings.Settings;
import ch.interlis.iom.IomObject;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxFactoryCollection;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox_j.PipelinePool;
import ch.interlis.iox_j.logging.LogEventFactory;
import ch.interlis.ioxwkf.json.JsonReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.interlis2.validator.Validator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

final class JsonValidatorImpl extends Validator {

    @Override
    protected IoxReader createReader(String filename, TransferDescription td, LogEventFactory errFactory,
                                     Settings settings, PipelinePool pool) throws IoxException {
        try {
            Path preprocessedFile = preprocessJsonFile(Path.of(filename));
            JsonReader reader = new JsonReader(preprocessedFile.toFile(), settings);
            reader.setModel(td);
            return new CleanupReader(reader, preprocessedFile.getParent());
        } catch (IOException e) {
            throw new IoxException(e);
        }
    }

    Path preprocessJsonFile(Path jsonFile) throws IOException {
        Path tempDir = Files.createTempDirectory("jsonvalidator_");
        Path target = tempDir.resolve(jsonFile.getFileName());

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonFile.toFile());

            if (!rootNode.isArray()) {
                if (rootNode.isObject()) {
                    addAttributes((ObjectNode) rootNode, 1);
                }
                ArrayNode array = objectMapper.createArrayNode();
                array.add(rootNode);
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), array);
            } else {
                int idCounter = 1;
                for (JsonNode node : rootNode) {
                    if (node.isObject()) {
                        addAttributes((ObjectNode) node, idCounter++);
                    }
                }
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), rootNode);
            }
            return target;
        } catch (IOException | RuntimeException e) {
            deleteDirectory(tempDir);
            throw e;
        }
    }

    private void addAttributes(ObjectNode objectNode, int id) {
        JsonNode typeNode = objectNode.get("@type");
        if (typeNode == null || !typeNode.isTextual()) {
            throw new IllegalArgumentException("Missing or invalid @type attribute in JSON object.");
        }

        if (!objectNode.has("@topic")) {
            String[] parts = typeNode.asText().split("\\.");
            if (parts.length >= 2) {
                objectNode.put("@topic", parts[0] + "." + parts[1]);
            }
        }
        if (!objectNode.has("@id")) {
            objectNode.put("@id", "o" + id);
        }
        if (!objectNode.has("@bid")) {
            objectNode.put("@bid", "b1");
        }
    }

    private static final class CleanupReader implements IoxReader {
        private final IoxReader delegate;
        private final Path tempDir;

        private CleanupReader(IoxReader delegate, Path tempDir) {
            this.delegate = delegate;
            this.tempDir = tempDir;
        }

        @Override
        public IoxEvent read() throws IoxException {
            return delegate.read();
        }

        @Override
        public void close() throws IoxException {
            try {
                delegate.close();
            } finally {
                cleanupTempDir();
            }
        }

        @Override
        public void setFactory(IoxFactoryCollection factory) throws IoxException {
            delegate.setFactory(factory);
        }

        @Override
        public IoxFactoryCollection getFactory() throws IoxException {
            return delegate.getFactory();
        }

        @Override
        public IomObject createIomObject(String type, String oid) throws IoxException {
            return delegate.createIomObject(type, oid);
        }

        private void cleanupTempDir() throws IoxException {
            if (tempDir == null || !Files.exists(tempDir)) {
                return;
            }
            try {
                deleteDirectory(tempDir);
            } catch (IOException e) {
                throw new IoxException(e);
            }
        }
    }

    private static void deleteDirectory(Path tempDir) throws IOException {
        if (tempDir == null || !Files.exists(tempDir)) {
            return;
        }
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    });
        } catch (IllegalStateException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }
}
