package ch.so.agi.gretl.it;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Location of the installation assembled by Gradle. */
record CandidateDistribution(Path root) {
    static CandidateDistribution resolve() throws IOException {
        return at(Path.of(System.getProperty("gretl.test.reactorDistribution")));
    }

    static CandidateDistribution at(Path root) throws IOException {
        var installed = new CandidateDistribution(root.toAbsolutePath().normalize());
        if (!Files.isExecutable(installed.launcher())) {
            throw new IOException("Distribution launcher is missing or not executable: " + installed.launcher());
        }
        return installed;
    }

    Path launcher() {
        return root.resolve("bin/gretl");
    }
}
