package ch.so.agi.gretl.it;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.ResourceReaper;

/** Resources shared by the IMAGE test class within its Gradle test worker. */
final class ImageTestEnvironment {
    private static final String RUN_LABEL = "gretl.verification.run";
    private static final boolean MACOS = System.getProperty("os.name").startsWith("Mac");
    private static Path workspace;
    private static Network network;

    private ImageTestEnvironment() { }

    static void start() throws IOException {
        // The Docker daemon must be able to see the host bind paths.
        workspace = Files.createTempDirectory(Path.of("/tmp"), "gretl-image-jobs.");
        if (((Number) Files.getAttribute(workspace, "unix:uid")).intValue() == 0) {
            throw new IOException("IMAGE bind tests require a non-root host user");
        }
        network = Network.newNetwork();
        network.getId();
        ResourceReaper.instance().registerLabelsFilterForCleanup(Map.of(RUN_LABEL, DockerClientFactory.SESSION_ID));
    }

    @SuppressWarnings("try")
    static void stop() throws Exception {
        // Release the backing services before closing their network.
        try (Network ownedNetwork = network) {
            try {
                PostgresFixture.stop();
            } finally {
                S3Fixture.stop();
            }
        } finally {
            if (workspace != null) JobJarSupport.delete(workspace);
        }
    }

    static Path workspace() {
        if (workspace == null) throw new IllegalStateException("IMAGE test environment is not running");
        return workspace;
    }

    static Network network() {
        return network;
    }

    static String httpHost() {
        return MACOS ? "host.docker.internal" : "services";
    }

    static String extraHost() {
        return MACOS ? "" : "services:host-gateway";
    }

    static Map<String, String> jobLabels() {
        // The default session filter is acknowledged at startup. It also covers
        // an abort before Ryuk acknowledges the additional GRETL label filter.
        var labels = new LinkedHashMap<>(DockerClientFactory.DEFAULT_LABELS);
        labels.put(DockerClientFactory.TESTCONTAINERS_SESSION_ID_LABEL, DockerClientFactory.SESSION_ID);
        labels.put(RUN_LABEL, DockerClientFactory.SESSION_ID);
        return labels;
    }
}
