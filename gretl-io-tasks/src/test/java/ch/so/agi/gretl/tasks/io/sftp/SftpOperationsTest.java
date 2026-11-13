package ch.so.agi.gretl.tasks.io.sftp;

import static org.junit.jupiter.api.Assertions.*;

import ch.so.agi.gretl.tasks.io.TestOperationContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SftpOperationsTest {
    @TempDir Path root;

    @Test
    void transfersListsAndDeletesExactFilesThroughKnownHost() throws Exception {
        Path remote = Files.createDirectory(root.resolve("remote"));
        Path incoming = Files.createDirectory(remote.resolve("incoming"));
        Files.writeString(incoming.resolve("top.csv"), "top");
        Path nested = Files.createDirectory(incoming.resolve("nested"));
        Files.writeString(nested.resolve("second.CSV"), "nested");
        Files.writeString(nested.resolve("ignore.csv"), "keep");
        Files.createSymbolicLink(incoming.resolve("link"), nested);
        try (var server = SshServer.setUpDefaultServer()) {
            var hostKey = new SimpleGeneratorHostKeyProvider(root.resolve("host-key"));
            server.setHost("127.0.0.1");
            server.setKeyPairProvider(hostKey);
            server.setPasswordAuthenticator((user, password, session) ->
                    user.equals("fixture") && password.equals("fixture-password"));
            server.setFileSystemFactory(new VirtualFileSystemFactory(remote));
            server.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
            server.start();
            Path knownHosts = root.resolve("known_hosts");
            var key = hostKey.loadKeys(null).iterator().next().getPublic();
            Files.writeString(knownHosts, "[127.0.0.1]:" + server.getPort() + " "
                    + PublicKeyEntry.toString(key) + "\n");
            var context = context(server.getPort(), knownHosts, "fixture-password");
            var list = SftpListOperation.type().handlerFactory().get().execute(
                    new SftpListOperation.Request("fixture", "incoming", true, List.of(), List.of(), true), context);
            var link = list.entries().stream().filter(entry -> entry.name().equals("link")).findFirst().orElseThrow();
            assertEquals(SftpListOperation.EntryType.SYMLINK, link.type());
            assertTrue(list.entries().stream().noneMatch(entry -> entry.relativePath().startsWith("link/")));
            var top = list.entries().stream().filter(entry -> entry.name().equals("top.csv")).findFirst().orElseThrow();
            assertEquals(3L, top.size());
            assertNotNull(top.modifiedAt());

            Path local = root.resolve("output");
            var download = SftpDownloadOperation.type().handlerFactory().get().execute(
                    new SftpDownloadOperation.Request("fixture", "incoming", null, local, true,
                            List.of("**/*.csv"), List.of("**/ignore.csv"), false, false), context);
            assertEquals(List.of("incoming/nested/second.CSV", "incoming/top.csv"), download.remotePaths());
            assertEquals("nested", Files.readString(local.resolve("nested/second.CSV")));
            assertEquals("top", Files.readString(local.resolve("top.csv")));
            assertFalse(Files.exists(local.resolve("nested/ignore.csv")));

            var upload = SftpUploadOperation.type().handlerFactory().get();
            var put = new SftpUploadOperation.Request("fixture", local.resolve("top.csv"), "new.csv", false);
            assertEquals(3, upload.execute(put, context).bytes());
            assertEquals("top", Files.readString(remote.resolve("new.csv")));
            assertThrows(SftpTaskException.class, () -> upload.execute(put, context));
            upload.execute(new SftpUploadOperation.Request("fixture", local.resolve("nested/second.CSV"),
                    "new.csv", true), context);
            assertEquals("nested", Files.readString(remote.resolve("new.csv")));

            var delete = SftpDeleteOperation.type().handlerFactory().get();
            var deleted = delete.execute(new SftpDeleteOperation.Request("fixture", download.remotePaths(), false), context);
            assertEquals(download.remotePaths(), deleted.deletedPaths());
            assertEquals("keep", Files.readString(nested.resolve("ignore.csv")));
            assertEquals(download.remotePaths(), delete.execute(
                    new SftpDeleteOperation.Request("fixture", download.remotePaths(), true), context).missingPaths());
            assertEquals(List.of(), delete.execute(
                    new SftpDeleteOperation.Request("unknown", List.of(), false), context).deletedPaths());

            var badPassword = context(server.getPort(), knownHosts, "fixture-wrong-password");
            var failure = assertThrows(SftpTaskException.class, () -> SftpListOperation.type().handlerFactory().get()
                    .execute(new SftpListOperation.Request("fixture", ".", false, List.of(), List.of(), true), badPassword));
            assertEquals("SFTP_AUTH", failure.diagnostic().code());
            assertFalse(failure.toString().contains("fixture-wrong-password"));
            var wrongKey = java.security.KeyPairGenerator.getInstance("EC").generateKeyPair().getPublic();
            String changedHost = "[127.0.0.1]:" + server.getPort() + " " + PublicKeyEntry.toString(wrongKey) + "\n";
            Files.writeString(knownHosts, changedHost);
            var changed = assertThrows(SftpTaskException.class, () -> SftpListOperation.type().handlerFactory().get()
                    .execute(new SftpListOperation.Request("fixture", ".", false, List.of(), List.of(), true), context));
            assertEquals("SFTP_HOST_KEY", changed.diagnostic().code());
            assertEquals(changedHost, Files.readString(knownHosts));
            Files.writeString(knownHosts, "");
            var untrusted = assertThrows(SftpTaskException.class, () -> SftpListOperation.type().handlerFactory().get()
                    .execute(new SftpListOperation.Request("fixture", ".", false, List.of(), List.of(), true), context));
            assertEquals("SFTP_HOST_KEY", untrusted.diagnostic().code());
            assertEquals(0, Files.size(knownHosts));
        }
    }

    @Test
    void loadsPlainAndEncryptedOpenSshKeysWithoutOtherIdentities() throws Exception {
        var key = java.security.KeyPairGenerator.getInstance("EC").generateKeyPair();
        try (var server = SshServer.setUpDefaultServer()) {
            var hostKey = new SimpleGeneratorHostKeyProvider(root.resolve("host-key"));
            server.setHost("127.0.0.1");
            server.setKeyPairProvider(hostKey);
            server.setPublickeyAuthenticator((user, offered, session) ->
                    user.equals("fixture") && org.apache.sshd.common.config.keys.KeyUtils.compareKeys(
                            offered, key.getPublic()));
            server.setFileSystemFactory(new VirtualFileSystemFactory(root));
            server.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
            server.start();
            Path knownHosts = root.resolve("known_hosts");
            Files.writeString(knownHosts, "[127.0.0.1]:" + server.getPort() + " "
                    + PublicKeyEntry.toString(hostKey.loadKeys(null).iterator().next().getPublic()) + "\n");
            for (boolean encrypted : List.of(false, true)) {
                var encryption = encrypted
                        ? new org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext()
                        : null;
                if (encryption != null) {
                    encryption.setPassword("fixture-key-passphrase");
                    encryption.setCipherType("256");
                }
                Path privateKey = root.resolve(encrypted ? "encrypted.key" : "plain.key");
                try (var output = Files.newOutputStream(privateKey)) {
                    org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter.INSTANCE
                            .writePrivateKey(key, "fixture", encryption, output);
                }
                var fields = new java.util.LinkedHashMap<String, Object>(Map.of(
                        "host", "127.0.0.1", "port", server.getPort(), "user", "fixture",
                        "knownHostsFile", knownHosts, "privateKeyFile", privateKey));
                if (encrypted) {
                    fields.put("privateKeyPassphraseSecret", "pass");
                }
                var context = new TestOperationContext(root, Map.of(), Map.of("fixture", fields),
                        Map.of("pass", "fixture-key-passphrase"));
                var list = SftpListOperation.type().handlerFactory().get().execute(
                        new SftpListOperation.Request("fixture", ".", false, List.of(), List.of(), true), context);
                assertTrue(list.entries().stream().anyMatch(entry -> entry.name().equals("known_hosts")));
            }
        }
    }

    @Test
    void deniedDeleteAndInterruptedUploadPreserveRemoteFiles() throws Exception {
        Path remote = Files.createDirectory(root.resolve("remote"));
        Files.writeString(remote.resolve("protected.csv"), "original");
        Path source = root.resolve("source.csv");
        Files.write(source, new byte[256 * 1024]);
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        var subsystem = new SftpSubsystemFactory();
        subsystem.addSftpEventListener(new org.apache.sshd.sftp.server.SftpEventListener() {
            @Override
            public void removing(org.apache.sshd.server.session.ServerSession session, Path file,
                                 boolean directory) throws java.io.IOException {
                if (file.getFileName().toString().equals("protected.csv")) {
                    throw new java.nio.file.AccessDeniedException("protected.csv");
                }
            }

            @Override
            public void writing(org.apache.sshd.server.session.ServerSession session, String handle,
                                org.apache.sshd.sftp.server.FileHandle file, long offset,
                                byte[] data, int start, int size) throws java.io.IOException {
                writes.incrementAndGet();
                throw new java.nio.file.AccessDeniedException("fixture rejects temporary writes");
            }
        });
        try (var server = SshServer.setUpDefaultServer()) {
            var hostKey = new SimpleGeneratorHostKeyProvider(root.resolve("host-key"));
            server.setHost("127.0.0.1");
            server.setKeyPairProvider(hostKey);
            server.setPasswordAuthenticator((user, password, session) -> password.equals("fixture-password"));
            server.setFileSystemFactory(new VirtualFileSystemFactory(remote));
            server.setSubsystemFactories(List.of(subsystem));
            server.start();
            Path knownHosts = root.resolve("known_hosts");
            Files.writeString(knownHosts, "[127.0.0.1]:" + server.getPort() + " "
                    + PublicKeyEntry.toString(hostKey.loadKeys(null).iterator().next().getPublic()) + "\n");
            var context = context(server.getPort(), knownHosts, "fixture-password");
            var denied = assertThrows(SftpTaskException.class, () -> SftpDeleteOperation.type().handlerFactory().get()
                    .execute(new SftpDeleteOperation.Request("fixture", List.of("protected.csv"), true), context));
            assertEquals("SFTP_PERMISSION", denied.diagnostic().code());
            assertThrows(SftpTaskException.class, () -> SftpUploadOperation.type().handlerFactory().get()
                    .execute(new SftpUploadOperation.Request("fixture", source, "protected.csv", true), context));
            assertTrue(writes.get() > 0);
            assertEquals("original", Files.readString(remote.resolve("protected.csv")));
            try (var files = Files.list(remote)) {
                assertEquals(List.of("protected.csv"), files.map(path -> path.getFileName().toString()).toList());
            }
        }
    }

    @Test
    void timedOutDownloadLeavesExistingTargetUntouched() throws Exception {
        Path remote = Files.createDirectory(root.resolve("remote"));
        Files.writeString(remote.resolve("data.csv"), "new");
        Path target = Files.writeString(root.resolve("target.csv"), "existing");
        var subsystem = new SftpSubsystemFactory();
        subsystem.addSftpEventListener(new org.apache.sshd.sftp.server.SftpEventListener() {
            @Override
            public void reading(org.apache.sshd.server.session.ServerSession session, String handle,
                                org.apache.sshd.sftp.server.FileHandle file, long offset,
                                byte[] data, int start, int length) throws java.io.IOException {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new java.io.InterruptedIOException();
                }
            }
        });
        try (var server = SshServer.setUpDefaultServer()) {
            var hostKey = new SimpleGeneratorHostKeyProvider(root.resolve("host-key"));
            server.setHost("127.0.0.1");
            server.setKeyPairProvider(hostKey);
            server.setPasswordAuthenticator((user, password, session) -> password.equals("fixture-password"));
            server.setFileSystemFactory(new VirtualFileSystemFactory(remote));
            server.setSubsystemFactories(List.of(subsystem));
            server.start();
            Path knownHosts = root.resolve("known_hosts");
            Files.writeString(knownHosts, "[127.0.0.1]:" + server.getPort() + " "
                    + PublicKeyEntry.toString(hostKey.loadKeys(null).iterator().next().getPublic()) + "\n");
            var context = new TestOperationContext(root, Map.of(), Map.of("fixture", Map.of(
                    "host", "127.0.0.1", "port", server.getPort(), "user", "fixture", "ioTimeout", "PT0.5S",
                    "knownHostsFile", knownHosts, "passwordSecret", "pass")), Map.of("pass", "fixture-password"));
            var failure = assertThrows(SftpTaskException.class,
                    () -> SftpDownloadOperation.type().handlerFactory().get().execute(
                            new SftpDownloadOperation.Request("fixture", "data.csv", target, null,
                                    false, List.of(), List.of(), true, true), context));
            assertEquals("SFTP_IO", failure.diagnostic().code());
            assertEquals("existing", Files.readString(target));
            try (var files = Files.list(root)) {
                assertFalse(files.anyMatch(path -> path.toString().endsWith(".part")));
            }
        }
    }

    @Test
    void connectionRefusalUsesOnlyConfiguredRetries() throws Exception {
        Path knownHosts = Files.createFile(root.resolve("known_hosts"));
        int closedPort;
        try (var socket = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        var context = new TestOperationContext(root, Map.of(), Map.of("fixture", Map.of(
                "host", "127.0.0.1", "port", closedPort, "user", "fixture", "connectTimeout", "PT1S",
                "knownHostsFile", knownHosts, "passwordSecret", "pass", "connectRetries", 2)),
                Map.of("pass", "fixture-password"));
        var failure = assertThrows(SftpTaskException.class, () -> SftpListOperation.type().handlerFactory().get()
                .execute(new SftpListOperation.Request("fixture", ".", false, List.of(), List.of(), true), context));
        assertEquals("SFTP_CONNECT", failure.diagnostic().code());
        assertEquals(2, context.logs().stream().filter(line -> line.contains("gretl.sftp.connect.retry")).count());
    }

    private TestOperationContext context(int port, Path knownHosts, String password) {
        return new TestOperationContext(root, Map.of(), Map.of("fixture", Map.of(
                "host", "127.0.0.1", "port", port, "user", "fixture",
                "knownHostsFile", knownHosts, "passwordSecret", "pass")), Map.of("pass", password));
    }
}
