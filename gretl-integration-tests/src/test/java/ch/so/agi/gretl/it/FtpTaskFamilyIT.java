package ch.so.agi.gretl.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(300)
class FtpTaskFamilyIT {
    @TempDir
    Path root;

    private static FtpFixture fixture;
    private FtpFixture.Lease lease;

    @BeforeAll
    void startFtp() {
        fixture = new FtpFixture();
    }

    @AfterAll
    void stopFtp() {
        if (fixture != null) {
            fixture.close();
        }
    }

    @AfterEach
    void closeLease() {
        FtpFixture.Lease current = lease;
        lease = null;
        if (current != null) {
            current.close();
        }
    }

    @Test
    void uploadsListsDownloadsAndDeletesAgainstRealFtpServer() throws Exception {
        lease = fixture.lease();
        Files.writeString(root.resolve("upload.txt"), "hello ftp",
                StandardCharsets.UTF_8);
        Map<String, Object> config = config(fixture.password());

        JobRunner.Result upload = JobRunner.run(root, ftpJob("upload", "FtpUpload",
                "    localFile = file('upload.txt')\n"
                        + "    fileType = 'BINARY'\n"), config);
        assertEquals(0, upload.code(), upload.err());
        assertTrue(lease.exists("upload.txt"));
        assertEquals("hello ftp", new String(lease.read("upload.txt"),
                StandardCharsets.UTF_8));

        JobRunner.Result list = JobRunner.run(root, ftpJob("list", "FtpList", ""), config);
        assertEquals(0, list.code(), list.err());

        JobRunner.Result download = JobRunner.run(root, ftpJob("download", "FtpDownload",
                "    localDir = path('download')\n"
                        + "    remoteFiles = ['upload.txt']\n"
                        + "    fileType = 'BINARY'\n"), config);
        assertEquals(0, download.code(), download.err());
        assertEquals("hello ftp", Files.readString(root.resolve("download/upload.txt"),
                StandardCharsets.UTF_8));

        JobRunner.Result delete = JobRunner.run(root, ftpJob("delete", "FtpDelete",
                "    remoteFiles = ['upload.txt']\n"), config);
        assertEquals(0, delete.code(), delete.err());
        assertFalse(lease.exists("upload.txt"));
    }


    @Test
    void deletesAllFilesAndHandlesEmptyDirectory() throws Exception {
        lease = fixture.lease();
        Files.writeString(root.resolve("one.txt"), "one", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("two.txt"), "two", StandardCharsets.UTF_8);
        Map<String, Object> config = config(fixture.password());
        assertEquals(0, JobRunner.run(root, ftpJob("uploadOne", "FtpUpload",
                "    localFile = file('one.txt')\n"), config).code());
        assertEquals(0, JobRunner.run(root, ftpJob("uploadTwo", "FtpUpload",
                "    localFile = file('two.txt')\n"), config).code());

        JobRunner.Result delete = JobRunner.run(root,
                ftpJob("deleteAll", "FtpDelete", ""), config);
        assertEquals(0, delete.code(), delete.err());
        assertTrue(lease.list().isEmpty());
        JobRunner.Result empty = JobRunner.run(root,
                ftpJob("deleteEmpty", "FtpDelete", ""), config);
        assertEquals(0, empty.code(), empty.err());
    }

    @Test
    void binaryTransferPreservesBytesAndHonorsOverwrite() throws Exception {
        lease = fixture.lease();
        byte[] data = new byte[] {0, 1, 2, 13, 10, 27, (byte) 255};
        Files.write(root.resolve("binary.bin"), data);
        Map<String, Object> config = config(fixture.password());
        JobRunner.Result upload = JobRunner.run(root, ftpJob("uploadBinary", "FtpUpload",
                "    localFile = file('binary.bin')\n"
                        + "    fileType = 'BINARY'\n"), config);
        assertEquals(0, upload.code(), upload.err());
        Path local = root.resolve("download/binary.bin");
        Files.createDirectories(local.getParent());
        Files.write(local, new byte[] {9, 9});
        JobRunner.Result blocked = JobRunner.run(root, ftpJob("downloadBlocked", "FtpDownload",
                "    localDir = path('download')\n"
                        + "    remoteFiles = ['binary.bin']\n"
                        + "    fileType = 'BINARY'\n"
                        + "    overwrite = false\n"), config);
        assertEquals(10, blocked.code(), blocked.out());
        assertEquals(2, Files.size(local));
        JobRunner.Result download = JobRunner.run(root, ftpJob("downloadBinary", "FtpDownload",
                "    localDir = path('download')\n"
                        + "    remoteFiles = ['binary.bin']\n"
                        + "    fileType = 'BINARY'\n"
                        + "    overwrite = true\n"), config);
        assertEquals(0, download.code(), download.err());
        assertTrue(java.util.Arrays.equals(data, Files.readAllBytes(local)));
    }

    @Test
    void wrongPasswordFailsWithoutLeakingSecret() throws Exception {
        lease = fixture.lease();
        JobRunner.Result result = JobRunner.run(root,
                ftpJob("list", "FtpList", ""), config("definitely-wrong"));

        assertEquals(10, result.code(), result.out());
        assertFalse(result.out().contains("definitely-wrong"));
        assertFalse(result.err().contains("definitely-wrong"));
        String report = Files.readString(result.report());
        assertFalse(report.contains("definitely-wrong"));
        assertTrue(report.contains("FtpTaskException"), report);
        assertTrue(report.contains("FTP-Anmeldung fehlgeschlagen"), report);
        // A rejected login must leave the same server usable, including cleanup.
        assertTrue(lease.list().isEmpty());
        JobRunner.Result valid = JobRunner.run(root, ftpJob("validLogin", "FtpList", ""),
                config(fixture.password()));
        assertEquals(0, valid.code(), valid.err());
    }

    private String ftpJob(String id, String task, String extra) {
        return "task('" + id + "', " + task + ") {\n"
                + "    server = '" + fixture.server() + "'\n"
                + "    user = '" + fixture.user() + "'\n"
                + "    password = secret('ftpPassword')\n"
                + "    remoteDir = '" + lease.directory() + "'\n"
                + extra
                + "}\n"
                + "defaults '" + id + "'\n";
    }

    private Map<String, Object> config(String password) throws Exception {
        Map<String, Object> config = JobRunner.baseConfig(root);
        JobRunner.secret(root, config, "ftpPassword", password);
        return config;
    }
}
