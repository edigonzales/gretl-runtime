package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.runtime.groovy.UserMessageSink;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.nio.file.Path;
import java.util.Locale;

/** The job-definition source used by the script and packaged execution paths. */
public interface JobSource {
    Path displayFile();

    /** Definition root; packaged execution uses the root returned by prepareRun. */
    Path jobRoot();

    /** Materialize resources only for an actual run, inside its owned workspace. */
    default Path prepareRun(Path runRoot, Locale locale) throws java.io.IOException {
        return jobRoot();
    }

    default boolean projectMode() { return false; }

    String jobName();

    String sha256(Locale locale);

    JobDefinition load(OperationRegistry registry, Path startCwd, UserMessageSink sink, Locale locale);
}
