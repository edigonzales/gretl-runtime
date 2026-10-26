package ch.so.agi.gretl.runtime.logging;

import java.nio.file.Files;

/** Native Linux assertions, using the same test methods as the normal reactor. */
public final class DiagnosticPosixQualification {
    private DiagnosticPosixQualification() { }
    public static void main(String[] args) throws Exception {
        var files = new DiagnosticFilePermissionsTest();
        files.directory = Files.createTempDirectory("gretl-event-permissions-");
        files.eventFileIsPrivateAndExistingParentPermissionsStayUnchanged();
        files.directory = Files.createTempDirectory("gretl-event-collision-");
        files.refusesExistingEventFileWithoutTruncatingIt();
        var report = new RunReportWriterTest();
        report.tempDir = Files.createTempDirectory("gretl-report-permissions-");
        report.writesSchemaOneReportWithoutResultValuesOrSecrets();
        System.out.println("PASS: native POSIX event/report permissions and unchanged parents; os="
                + System.getProperty("os.name") + " user=" + System.getProperty("user.name"));
    }
}
