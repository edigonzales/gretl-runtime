package ch.so.agi.gretl.tasks.io.sftp;

import ch.so.agi.gretl.runtime.api.OperationContext;
import ch.so.agi.gretl.runtime.api.LogLevel;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.auth.password.UserAuthPasswordFactory;
import org.apache.sshd.client.auth.pubkey.UserAuthPublicKeyFactory;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.sftp.SftpModuleProperties;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;

final class SftpConnection implements AutoCloseable {
    private final SshClient client;
    private ClientSession session;
    private SftpClient sftp;

    private SftpConnection(SshClient client) {
        this.client = client;
    }

    static SftpConnection open(SftpSupport.Config config, OperationContext context) throws IOException {
        List<KeyPair> identities = loadKeys(config, context);
        for (int attempt = 0; ; attempt++) {
            context.cancellation().throwIfCancellationRequested();
            var client = SshClient.setUpDefaultClient();
            var connection = new SftpConnection(client);
            var rejected = new AtomicBoolean();
            var verifier = new KnownHostsServerKeyVerifier(RejectAllServerKeyVerifier.INSTANCE, config.knownHosts());
            client.setServerKeyVerifier((session, address, key) -> {
                boolean accepted = verifier.verifyServerKey(session, address, key);
                rejected.set(!accepted);
                return accepted;
            });
            client.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);
            client.setAgentFactory(null);
            client.setKeyIdentityProvider(KeyIdentityProvider.wrapKeyPairs(identities));
            if (config.privateKey() == null) {
                client.setUserAuthFactories(List.of(UserAuthPasswordFactory.INSTANCE));
            } else {
                client.setUserAuthFactories(List.of(UserAuthPublicKeyFactory.INSTANCE));
            }
            Duration connectTimeout = limit(config.connectTimeout(), context);
            Duration ioTimeout = limit(config.ioTimeout(), context);
            CoreModuleProperties.IO_CONNECT_TIMEOUT.set(client, connectTimeout);
            CoreModuleProperties.NIO2_READ_TIMEOUT.set(client, ioTimeout);
            CoreModuleProperties.IDLE_TIMEOUT.set(client, ioTimeout);
            CoreModuleProperties.WINDOW_TIMEOUT.set(client, ioTimeout);
            CoreModuleProperties.DISCONNECT_TIMEOUT.set(client, ioTimeout);
            SftpModuleProperties.SFTP_CHANNEL_OPEN_TIMEOUT.set(client, ioTimeout);
            boolean connected = false;
            boolean authenticated = false;
            try {
                client.start();
                connection.session = client.connect(config.user(), config.host(), config.port())
                        .verify(connectTimeout).getSession();
                connected = true;
                context.cancellation().throwIfCancellationRequested();
                if (config.password() != null) {
                    connection.session.addPasswordIdentity(config.password());
                }
                connection.session.auth().verify(limit(config.ioTimeout(), context));
                authenticated = true;
                context.cancellation().throwIfCancellationRequested();
                connection.sftp = SftpClientFactory.instance().createSftpClient(connection.session);
                context.cancellation().throwIfCancellationRequested();
                return connection;
            } catch (IOException exception) {
                String code = rejected.get() ? "HOST_KEY" : !connected ? "CONNECT"
                        : !authenticated ? "AUTH" : "IO";
                var failure = SftpSupport.error(context, code);
                closeAfterFailure(connection, failure);
                if (connected || rejected.get() || attempt >= config.retries() || !retryable(exception)) {
                    throw failure;
                }
                context.log().log(LogLevel.INFO, "gretl.sftp.connect.retry",
                        ch.so.agi.gretl.i18n.Messages.get(context.locale(), "task.sftp.retry"),
                        Map.of("attempt", Integer.toString(attempt + 2)), null);
            } catch (RuntimeException | Error failure) {
                closeAfterFailure(connection, failure);
                throw failure;
            }
        }
    }

    private static boolean retryable(IOException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static List<KeyPair> loadKeys(SftpSupport.Config config, OperationContext context) throws IOException {
        if (config.privateKey() == null) {
            return List.of();
        }
        try {
            var provider = new FileKeyPairProvider(config.privateKey());
            if (config.passphrase() != null) {
                provider.setPasswordFinder(FilePasswordProvider.of(config.passphrase()));
            }
            var keys = new java.util.ArrayList<KeyPair>();
            provider.loadKeys(null).forEach(keys::add);
            if (!keys.isEmpty()) {
                return List.copyOf(keys);
            }
        } catch (RuntimeException failure) {
            throw SftpSupport.error(context, "KEY");
        }
        throw SftpSupport.error(context, "KEY");
    }

    private static Duration limit(Duration configured, OperationContext context) {
        Duration value = context.budget().remaining().filter(left -> left.compareTo(configured) < 0)
                .orElse(configured);
        return value.compareTo(Duration.ofMillis(1)) < 0 ? Duration.ofMillis(1) : value;
    }

    private static void closeAfterFailure(SftpConnection connection, Throwable failure) {
        try {
            connection.close();
        } catch (IOException cleanup) {
            failure.addSuppressed(new IOException("SFTP connection cleanup failed"));
        }
    }

    SftpClient client() {
        return sftp;
    }

    @Override
    public void close() throws IOException {
        try (SshClient ignored = client; ClientSession openSession = session; SftpClient openSftp = sftp) {
            // Close in subsystem, session, client order.
        }
    }
}
