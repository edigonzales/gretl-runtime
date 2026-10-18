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
