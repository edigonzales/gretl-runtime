package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.runtime.groovy.UserMessageSink;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.nio.file.Path;

/** The job-definition source used by the script and packaged execution paths. */
public interface JobSource {
    Path displayFile();

    Path jobRoot();

    String jobName();

    String sha256();

    JobDefinition load(OperationRegistry registry, Path startCwd, UserMessageSink sink);
}
