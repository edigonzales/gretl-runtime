# Tatsächlich ausgeführte JAR-Operationen in Phase 2

Quelle: eigener vollständiger Root-Profillauf `phase2-revision-1/profiles`; die gespeicherten JAR-Dateien wurden nach Ende erneut mit SHA-256 geprüft. Einzelbelege stehen in [phase-2-revision-1-jar-executions.json](phase-2-revision-1-jar-executions.json), alle erfolgreichen Zuordnungen in [phase-2-revision-1-operation-job-mapping.json](phase-2-revision-1-operation-job-mapping.json). SCRIPT-Regressionsläufe zählen nicht zum 44er-Nachweis. IMAGE wurde nicht ausgeführt.

| Operation | Ein erfolgreicher fachlicher JAR-Lauf |
|---|---|
| Av2ch | av2ch-set |
| Av2geobau | av2geobau-set |
| Csv2Excel | excel-empty |
| CsvExport | format-csv-export |
| CsvImport | format-csv-import |
| CsvValidator | additional-function |
| Curl | io-curl-binary |
| Db2Db | database |
| DuckDbSqlExecutor | original-duckdb |
| Extract | io-extract-file |
| FtpDelete | ftp-delete |
| FtpDownload | ftp-download |
| FtpList | ftp-list-result |
| FtpUpload | ftp-upload |
| Gpkg2Dxf | gpkg2dxf-single |
| Gpkg2Shp | gpkg2shp-single |
| GpkgExport | format-gpkg-export |
| GpkgImport | format-gpkg-import |
| GpkgValidator | valid-gpkg-path |
| Gzip | original-gzip |
| HttpDownload | io-download-file |
| Ili2duckdbExport | ili-duck-export-single |
| Ili2duckdbImport | ili-duck-export-single |
| Ili2duckdbImportSchema | ili-duck-export-single |
| Ili2gpkgImport | gpkg-help-example |
| Ili2pgDelete | ili2pg-delete |
| Ili2pgExport | ili2pg-export |
| Ili2pgImport | ili2pg-import |
| Ili2pgImportSchema | ili2pg-schema |
| Ili2pgReplace | ili2pg-replace |
| Ili2pgUpdate | ili2pg-update |
| Ili2pgValidate | ili2pg-validate |
| IliValidator | additional-function |
| JsonImport | format-json-import |
| JsonValidator | valid-json-path |
| S3Bucket2Bucket | original-s3-copy |
| S3Delete | original-s3-delete |
| S3Download | original-s3-download |
| S3Upload | original-s3-upload |
| ShpExport | format-shp-export |
| ShpImport | format-shp-import |
| ShpValidator | valid-shp-path |
| SqlExecutor | database |
| XslTransformer | io-xsl-file |
