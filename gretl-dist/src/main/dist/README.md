# GRETL Runtime Script Distribution

Start:
```sh
./bin/gretl --version
./bin/gretl operations
./bin/gretl run examples/download-extract/job.groovy --help
```

The distribution contains the runtime under `lib/runtime`, the standard task
catalog under `lib/tasks`, and the documented extension directory
`lib/extensions`. A custom task JAR and its compatible dependencies can be
placed in `lib/extensions` before the next start. No Maven or Gradle is needed
at job runtime.

The standard catalog of this release is `HttpDownload`, `Curl`, `Gzip`,
`Extract`, `FtpList`, `FtpDownload`, `FtpUpload`, `FtpDelete`, `S3Download`,
`S3Upload`, `S3Delete`, `S3Bucket2Bucket`, `Db2Db`, `DuckDbSqlExecutor`,
`Csv2Excel`, `CsvImport`, `CsvExport`, `JsonImport`, `CsvValidator`,
`JsonValidator`, `Gpkg2Shp`, `GpkgImport`, `GpkgExport`, `ShpImport`,
`ShpExport`, `GpkgValidator`, `ShpValidator`, `Ili2pgImportSchema`,
`Ili2pgImport`, `Ili2pgExport`, `Ili2pgDelete`, `Ili2pgReplace`,
`Ili2pgUpdate`, `Ili2pgValidate`, `Ili2gpkgImport`,
`Ili2duckdbImportSchema`, `Ili2duckdbImport`, `Ili2duckdbExport`, `Av2ch`,
`Av2geobau`, `Gpkg2Dxf`, `SqlExecutor`, `XslTransformer` and `IliValidator`. Stufe A remains separately usable because the
task libraries are separate modules. Publisher and XtfToGpkg are not registered
stubs.

`Curl` is a separate HTTP request/response task for API calls, uploads and
response assertions. Use `HttpDownload` for streaming a file to a path.

Compiled examples:
- `examples/download-extract/job.groovy` needs a ZIP URL and an output directory.
  `HttpDownload` protects existing target files by default; use the explicit
  task option `overwrite = true` only when replacement is intended.
- `examples/sql/job.groovy` needs a prepared PostgreSQL `runtime.json` with an
  `edit` task configuration and local SQL files. `examples/sql/job-package.json`
  packages the SQL resource into a JAR.
- `examples/ili-validator/job.groovy` validates the bundled local model/XTF
  fixture with the real `IliValidator` (stage B).
- `examples/ili-validator/job-package.json` creates an executable job JAR:
  `./bin/gretl package examples/ili-validator/job-package.json --output /tmp/ili.jar`
  then `java -jar /tmp/ili.jar`.
- `examples/action-nodata/optional.groovy` shows `when`/skip; `nodata.groovy`
  shows a controlled NO_DATA finish.
- `examples/token-catalog/job.groovy` is an advanced action/`ctx.run` fixture.
  It expects a local token/catalog service and a configured `sourceAuth` secret.

A local test server or test database is deliberately not started automatically.
See the repository build guide and the M5 report for the fixture commands.

Text errors use the same structure for early CLI problems and later task
failures:

```text
<timestamp> ERROR <title> [<technical-code>]
  Meldung: ...
  Feld: ...
  Quelle: ...
```

JSON logs keep their one-object-per-line schema. `HttpDownload` protects an
existing target by default; `overwrite = true` is the explicit exception.
