package ch.so.agi.gretl.it;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Real FTP server container with isolated lease directories. */
@SuppressWarnings("deprecation")
final class FtpFixture implements AutoCloseable {
    static final String IMAGE =
            "docker.io/delfer/alpine-ftp-server@sha256:60bb774d8408d9d4d5c74d05d1c086a34ce192c6c1a142ffac268cac0dbc6fac";
    private static final String USER = "gretl";
    private static final String PASSWORD = "gretl-pass";
    private FixedHostPortGenericContainer<?> container;

    FtpFixture() {
        container = startContainer();
    }

    private FixedHostPortGenericContainer<?> startContainer() {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            int passive = freePort();
            FixedHostPortGenericContainer<?> candidate = new FixedHostPortGenericContainer<>(
                    DockerImageName.parse(IMAGE).toString())
                    .withExposedPorts(21)
                    .withFixedExposedPort(passive, passive)
                    .withEnv("USERS", USER + "|" + PASSWORD + "|/home/gretl")
                    .withEnv("ADDRESS", "127.0.0.1")
                    .withEnv("MIN_PORT", String.valueOf(passive))
                    .withEnv("MAX_PORT", String.valueOf(passive))
                    .withCreateContainerCmdModifier(command ->
                            command.withHostName("ftp").withAliases("ftp"))
                    .waitingFor(Wait.forListeningPort());
            try {
                candidate.start();
                return candidate;
            } catch (RuntimeException failure) {
                last = failure;
                try {
                    candidate.stop();
                } catch (RuntimeException ignored) {
                    // Keep the original startup failure.
                }
            }
        }
        throw new IllegalStateException("Could not start FTP fixture", last);
    }

    private void restart() {
        FixedHostPortGenericContainer<?> previous = container;
        if (previous != null) {
            try {
                previous.stop();
            } catch (RuntimeException ignored) {
                // Restart below; stale container is discarded.
            }
        }
        container = startContainer();
    }

    Lease lease() throws IOException {
        String directory = "/home/gretl/" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12);
        FTPClient client;
        try {
            client = connect();
        } catch (IOException failure) {
            // Docker Desktop occasionally loses the host port mapping after
            // startup. A fresh container restores a usable control port.
            restart();
            client = connect();
        }
        try {
            if (!client.makeDirectory(directory)) {
                throw new IOException("Could not create FTP lease directory: " + directory);
            }
        } finally {
            disconnectQuietly(client);
        }
        return new Lease(this, directory);
    }

    String server() {
        return "127.0.0.1:" + container.getMappedPort(21);
    }

    String user() {
        return USER;
    }

    String password() {
        return PASSWORD;
    }

    @Override
    public void close() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    static final class Lease implements AutoCloseable {
        private final FtpFixture fixture;
        private final String directory;
        private boolean closed;

        Lease(FtpFixture fixture, String directory) {
            this.fixture = fixture;
            this.directory = directory;
        }

        String directory() {
            return directory;
        }

        List<String> list() throws IOException {
            FTPClient client = fixture.connect();
            try {
                FTPFile[] files = client.listFiles(directory);
                return Arrays.stream(files)
                        .filter(FTPFile::isFile)
                        .map(FTPFile::getName)
                        .sorted()
                        .toList();
            } finally {
                disconnectQuietly(client);
            }
        }

        byte[] read(String name) throws IOException {
            FTPClient client = fixture.connect();
            try {
                try (InputStream input = client.retrieveFileStream(directory + "/" + name)) {
                    if (input == null) {
                        throw new IOException("FTP file not found: " + name);
                    }
                    byte[] bytes = input.readAllBytes();
                    if (!client.completePendingCommand()) {
                        throw new IOException("FTP retrieve did not complete: " + name);
                    }
                    return bytes;
                }
            } finally {
                disconnectQuietly(client);
            }
        }

        void write(Path localFile) throws IOException {
            FTPClient client = fixture.connect();
            try (InputStream input = Files.newInputStream(localFile)) {
                if (!client.storeFile(directory + "/" + localFile.getFileName(), input)) {
                    throw new IOException("FTP upload failed: " + localFile.getFileName());
                }
            } finally {
                disconnectQuietly(client);
            }
        }

        boolean exists(String name) throws IOException {
            return list().contains(name);
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            FTPClient client = null;
            try {
                client = fixture.connect();
                deleteRecursively(client, directory);
            } catch (IOException exception) {
                fixture.recordServerEvidence();
                throw new IllegalStateException("Could not clean FTP lease " + directory,
                        exception);
            } finally {
                disconnectQuietly(client);
            }
        }

        private static void deleteRecursively(FTPClient client, String directory)
                throws IOException {
            FTPFile[] files = client.listFiles(directory);
            if (files == null) {
                return;
            }
            for (FTPFile file : files) {
                String path = directory + "/" + file.getName();
                if (file.isDirectory()) {
                    deleteRecursively(client, path);
                } else {
                    client.deleteFile(path);
                }
            }
            client.removeDirectory(directory);
        }
    }

    private FTPClient connect() throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 60; attempt++) {
            FTPClient client = new FTPClient();
            try {
                client.connect("127.0.0.1", container.getMappedPort(21));
                if (!client.login(USER, PASSWORD)) {
                    throw new IOException("FTP fixture login failed");
                }
                client.enterLocalPassiveMode();
                client.setFileType(FTP.BINARY_FILE_TYPE);
                return client;
            } catch (IOException exception) {
                last = exception;
                disconnectQuietly(client);
                sleep(250);
            }
        }
        throw new IOException("FTP fixture is not reachable", last);
    }

    private void recordServerEvidence() {
        try {
            Path evidence = Path.of("target", "service-evidence");
            Files.createDirectories(evidence);
            Files.writeString(evidence.resolve("ftp-" + container.getContainerId() + ".log"),
                    container.getLogs().replace(PASSWORD, "[REDACTED]")
                            .replace("definitely-wrong", "[REDACTED]"));
        } catch (Exception ignored) {
            // Keep the original test failure even if Docker's diagnostics fail too.
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for FTP fixture",
                    exception);
        }
    }

    private static void disconnectQuietly(FTPClient client) {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not allocate a free FTP passive port",
                    exception);
        }
    }
}
