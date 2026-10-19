package ch.so.agi.gretl.tasks.ftp;

import ch.so.agi.gretl.runtime.api.SecretValue;

/** Resolved FTP connection configuration shared by the FTP task family. */
public record FtpConnection(
        String server,
        String user,
        SecretValue password,
        String systemType,
        String fileSeparator,
        boolean passiveMode,
        long controlKeepAliveTimeout
) {
}
