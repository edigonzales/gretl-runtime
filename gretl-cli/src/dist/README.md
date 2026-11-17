# GRETL Runtime Script Distribution

Use Java 25 and run these commands from the unpacked distribution root.

```sh
./bin/gretl --version
./bin/gretl operations
./bin/gretl check examples/ili-validator/job.groovy
./bin/gretl run examples/ili-validator/job.groovy
```

`bin/` contains the Unix and Windows launchers. `lib/` contains the runtime,
standard tasks and their dependencies. Place custom task JARs and their
compatible dependencies in `lib/extensions/` before the next start.
Running a script requires Java alone.

See [Examples](examples/README.md) for sample jobs and executable job JARs.
The [local project pipeline](examples/project-pipeline/README.md) runs
without backing services and demonstrates `check`, `plan`, `run` and
JAR packaging.
