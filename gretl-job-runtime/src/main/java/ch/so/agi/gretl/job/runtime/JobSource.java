package ch.so.agi.gretl.job.runtime;

import ch.so.agi.gretl.runtime.groovy.UserMessageSink;
import ch.so.agi.gretl.runtime.runner.JobDefinition;
import ch.so.agi.gretl.runtime.runner.OperationRegistry;
import java.nio.file.Path;
import java.util.Locale;

/** The job-definition source used by the script and packaged execution paths. */
public interface JobSource {
    Path displayFile();

    /** Materialize resources only for an actual run, inside its owned workspace. */
    default Path prepareRun(Path runRoot, Path definitionRoot, Locale locale) throws java.io.IOException {
        return definitionRoot;
    }

    default boolean projectMode() { return false; }

    String jobName();

    /** One load's definition and the identity of the sources it used. */
    record Loaded(JobDefinition definition, String sha256) { }

    Loaded load(OperationRegistry registry, Path startCwd, UserMessageSink sink, Locale locale);
}
