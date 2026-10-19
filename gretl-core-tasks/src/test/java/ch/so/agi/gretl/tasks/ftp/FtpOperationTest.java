package ch.so.agi.gretl.tasks.ftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

class FtpOperationTest {
    @TempDir
    Path tempDir;

    private FakeFtpServer ftp;
    private UnixFakeFileSystem fileSystem;
    private String server;
    private FtpTestContext context;

    @BeforeEach
    void startServer() {
        ftp = new FakeFtpServer();
        ftp.setServerControlPort(0);
        ftp.setSystemName("Unix");
        fileSystem = new UnixFakeFileSystem();
        fileSystem.add(new DirectoryEntry("/data"));
        fileSystem.add(new FileEntry("/data/a.txt", "alpha"));
        fileSystem.add(new FileEntry("/data/b.zip", "zip"));
        ftp.setFileSystem(fileSystem);
        ftp.addUserAccount(new UserAccount("ftp-user", "ftp-pass", "/"));
        ftp.start();
        server = "127.0.0.1:" + ftp.getServerControlPort();
        context = new FtpTestContext(tempDir, Map.of("ftpPassword", "ftp-pass"));
    }

    @AfterEach
    void stopServer() {
        if (ftp != null) {
            ftp.stop();
        }
    }

    @Test
    void listsRemoteFiles() throws Exception {
        FtpListOperation.FtpListResult result =
                new FtpListOperation.FtpListHandler().execute(
                        listRequest().build(), context);

        assertEquals(Set.of("a.txt", "b.zip"), Set.copyOf(result.files()));
    }

    @Test
    void downloadsAllFilesAndReportsBytes() throws Exception {
        Path localDir = tempDir.resolve("download");

        FtpDownloadOperation.FtpDownloadResult result =
                new FtpDownloadOperation.FtpDownloadHandler().execute(
                        downloadRequest(localDir).build(), context);

        assertEquals(2, result.files().size());
        assertTrue(result.bytes() > 0);
        assertEquals("alpha", Files.readString(localDir.resolve("a.txt")));
        assertEquals("zip", Files.readString(localDir.resolve("b.zip")));
    }

    @Test
    void downloadsWildcardPattern() throws Exception {
        Path localDir = tempDir.resolve("pattern");

        FtpDownloadOperation.FtpDownloadResult result =
                new FtpDownloadOperation.FtpDownloadHandler().execute(
                        downloadRequest(localDir)
                                .setRemoteFiles(List.of("*.txt")).build(), context);

        assertEquals(List.of(localDir.resolve("a.txt")), result.files());
        assertFalse(Files.exists(localDir.resolve("b.zip")));
    }

    @Test
    void uploadsFileIntoRemoteDirectory() throws Exception {
        Path localFile = tempDir.resolve("upload.txt");
        Files.writeString(localFile, "upload-content");

        FtpUploadOperation.FtpUploadResult result =
                new FtpUploadOperation.FtpUploadHandler().execute(
                        uploadRequest(localFile).build(), context);

        assertEquals("/data/upload.txt", result.remoteFile());
        assertEquals("upload-content", content("/data/upload.txt"));
    }

    @Test
    void deletesMatchingRemoteFiles() throws Exception {
        fileSystem.add(new FileEntry("/data/old.tmp", "old"));
        fileSystem.add(new FileEntry("/data/keep.txt", "keep"));

        FtpDeleteOperation.FtpDeleteResult result =
                new FtpDeleteOperation.FtpDeleteHandler().execute(
                        deleteRequest().setRemoteFiles(List.of("*.tmp")).build(),
                        context);

        assertEquals(List.of("old.tmp"), result.files());
        assertEquals(1, result.count());
        assertFalse(fileSystem.exists("/data/old.tmp"));
        assertTrue(fileSystem.exists("/data/keep.txt"));
    }

    @Test
    void honorsOverwriteForLocalTargets() throws Exception {
        Path localDir = Files.createDirectories(tempDir.resolve("overwrite"));
        Files.writeString(localDir.resolve("a.txt"), "old");

        FtpTaskException exists = assertThrows(FtpTaskException.class,
                () -> new FtpDownloadOperation.FtpDownloadHandler().execute(
                        downloadRequest(localDir)
                                .setRemoteFiles(List.of("a.txt"))
                                .setOverwrite(false).build(),
                        context));
        assertEquals("FTP_TARGET_EXISTS", exists.code());

        new FtpDownloadOperation.FtpDownloadHandler().execute(
                downloadRequest(localDir)
                        .setRemoteFiles(List.of("a.txt"))
                        .setOverwrite(true).build(),
                context);
        assertEquals("alpha", Files.readString(localDir.resolve("a.txt")));
    }

    @Test
    void rejectsBadLoginAndRemotePathTraversal() throws Exception {
        FtpListOperation.FtpListBuilder badLogin = listRequest();
        badLogin.setPassword(ch.so.agi.gretl.runtime.api.SecretValue
                .deferred(() -> "wrong"));
        FtpTaskException login = assertThrows(FtpTaskException.class,
                () -> new FtpListOperation.FtpListHandler().execute(
                        badLogin.build(), context));
        assertEquals("FTP_LOGIN", login.code());

        assertThrows(IllegalArgumentException.class,
                () -> downloadRequest(tempDir.resolve("traversal"))
                        .setRemoteFiles(List.of("../escape.txt")).build());
    }

    @Test
    void metadataCarriesCommonFieldsAndResults() {
        var list = FtpListOperation.type();
        assertTrue(list.inputs().stream().anyMatch(field -> field.name().equals("server")
                && field.required()));
        assertTrue(list.inputs().stream().anyMatch(field -> field.name().equals("password")
                && field.required()));
        assertTrue(list.outputs().stream().anyMatch(field -> field.name().equals("files")));

        var download = FtpDownloadOperation.type();
        assertTrue(download.inputs().stream().anyMatch(field -> field.name().equals("localDir")
                && field.required()));
        assertEquals(true, download.inputs().stream()
                .filter(field -> field.name().equals("overwrite"))
                .findFirst().orElseThrow().defaultValue().orElseThrow());
        assertTrue(download.outputs().stream().anyMatch(field -> field.name().equals("bytes")));

        assertTrue(FtpUploadOperation.type().outputs().stream()
                .anyMatch(field -> field.name().equals("remoteFile")));
        assertTrue(FtpDeleteOperation.type().outputs().stream()
                .anyMatch(field -> field.name().equals("count")));
    }

    private FtpListOperation.FtpListBuilder listRequest() {
        FtpListOperation.FtpListBuilder builder = new FtpListOperation.FtpListBuilder();
        configure(builder);
        builder.setRemoteDir("/data");
        return builder;
    }

    private FtpDownloadOperation.FtpDownloadBuilder downloadRequest(Path localDir) {
        FtpDownloadOperation.FtpDownloadBuilder builder =
                new FtpDownloadOperation.FtpDownloadBuilder();
        configure(builder);
        builder.setRemoteDir("/data");
        builder.setLocalDir(localDir);
        builder.setFileType("BINARY");
        return builder;
    }

    private FtpUploadOperation.FtpUploadBuilder uploadRequest(Path localFile) {
        FtpUploadOperation.FtpUploadBuilder builder =
                new FtpUploadOperation.FtpUploadBuilder();
        configure(builder);
        builder.setLocalFile(localFile);
        builder.setRemoteDir("/data");
        builder.setFileType("BINARY");
        return builder;
    }

    private FtpDeleteOperation.FtpDeleteBuilder deleteRequest() {
        FtpDeleteOperation.FtpDeleteBuilder builder =
                new FtpDeleteOperation.FtpDeleteBuilder();
        configure(builder);
        builder.setRemoteDir("/data");
        return builder;
    }

    private void configure(FtpFields.BaseFtpBuilder<?> builder) {
        builder.setServer(server);
        builder.setUser("ftp-user");
        builder.setPassword(context.secret("ftpPassword"));
    }

    private String content(String path) throws IOException {
        FileEntry entry = (FileEntry) fileSystem.getEntry(path);
        try (InputStream input = entry.createInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
