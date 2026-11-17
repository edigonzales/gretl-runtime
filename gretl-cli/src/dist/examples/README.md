# Examples

- `download-extract/job.groovy` downloads a ZIP file supplied by the operator and
  extracts it to a selected external directory. `data.zip` is a local fixture
  that can be served by any local HTTP test server. `HttpDownload` never
  overwrites silently; an existing target file is replaced only with the
  explicit option `overwrite = true`.
- `sql/job.groovy` runs a complete SQL script against the prepared PostgreSQL
  alias `edit`. `sql/job-package.json` packages the SQL resource into a JAR.
- `ili-validator/job-package.json` packages the local INTERLIS fixture into an
  executable JAR: `gretl package examples/ili-validator/job-package.json
  --output /tmp/ili-validator.jar`.
- `action-nodata/optional.groovy` demonstrates a boolean `when` skip;
  `action-nodata/nodata.groovy` demonstrates `finishJob` with NO_DATA.
- `token-catalog/job.groovy` obtains a token, reads a catalog through
  `JsonSlurper`, selects the newest file, downloads it, and extracts it. It
  requires a local fixture service and a configured `sourceAuth` secret. `sql/runtime.json.example` documents the runtime configuration
  shape; copy it outside the distribution and provide real credentials via an
  environment or file secret source.

These examples contain no production credentials and no hidden test server.

## Job packaging

All job JARs include the standard runtime. Declare any additional external
libraries as Maven coordinates in `dependencies`.

`minimal-action/job-package.json` is an executable single-job example:

```sh
./bin/gretl package examples/minimal-action/job-package.json --output /tmp/minimal.jar
java -jar /tmp/minimal.jar --param output=/tmp/minimal-result.txt
```

The packaged job needs Java 25; Maven is needed at packaging time when additional external dependencies must be resolved. Embedded

`project-pipeline/` is a complete local Gzip/Extract project with nested includes, qualified targets/parameters and one shared run. Its package descriptor uses `settingsFile: settings.gretl` and packages the explicit project resources. It runs as a directory or a standalone compiled project JAR; see `project-pipeline/README.md`.

`task-finalizers/README.md` demonstrates local owner success/failure, a shared
conditional finalizer, explicit prerequisite/order links and global cleanup.
