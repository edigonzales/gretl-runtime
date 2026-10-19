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
    void wrongPasswordFailsWithoutLeakingSecret() throws Exception {
        lease = fixture.lease();
        JobRunner.Result result = JobRunner.run(root,
                ftpJob("list", "FtpList", ""), config("definitely-wrong"));

        assertEquals(10, result.code(), result.out());
        assertFalse(result.out().contains("definitely-wrong"));
        assertFalse(result.err().contains("definitely-wrong"));
        if (Files.exists(result.report())) {
            assertFalse(Files.readString(result.report()).contains("definitely-wrong"));
        }
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
