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

## Package selection

The existing SQL and IliValidator descriptors use schema 1, retaining the full
qualified standard catalog. Schema 2 accepts `taskModules` (`io`, `database`,
`interlis`, `ilivalidator`) and `standardFunctions` (default false). Missing or
empty modules select no standard tasks. External extensions must be declared
as Maven coordinates in `dependencies` before compilation.

`minimal-action/job-package.json` is an executable schema-2 example without
standard task engines:

```sh
./bin/gretl package examples/minimal-action/job-package.json --output /tmp/minimal.jar
java -jar /tmp/minimal.jar --param output=/tmp/minimal-result.txt
```

The packaged job needs Java 25; Maven is only an authoring tool. Embedded
`META-INF/gretl/dependencies.json` records actual Maven inputs and hashes.

`project-pipeline/` is a complete local Gzip/Extract project with nested includes, qualified targets/parameters and one shared run. Its schema-3 descriptor uses `settingsFile: settings.gretl` and packages the explicit project resources. It runs as a directory or a standalone compiled project JAR; see `project-pipeline/README.md`.
