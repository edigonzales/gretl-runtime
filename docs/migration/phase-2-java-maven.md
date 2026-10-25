# Java- und Maven-Migration in Phase 2

Die neutrale Task-SPI in `gretl-api`, DSL-Symbole, Operation-IDs und alle
Resultatfelder bleiben unverändert. Die konkrete Java-Klassenorganisation und
Maven-Artefakte ändern sich bewusst; alte Taskklassen werden nicht binär
weitergeliefert. Extensions gegen die neutrale API benötigen keine Migration.
Das externe FileSize-Beispiel bleibt derselbe SPI-Nachweis.

| Bisheriges Artefakt | Neues Artefakt |
| --- | --- |
| gretl-basic-tasks: SqlExecutor, Db2Db, DuckDbSqlExecutor | gretl-database-tasks |
| gretl-basic-tasks: HTTP, Curl, Gzip, Extract | gretl-io-tasks |
| gretl-core-tasks: FTP, S3, XSLT | gretl-io-tasks |
| gretl-interlis-tasks | gleicher Maven-Name; geordnete Java-Pakete |
| gretl-ilivalidator | unverändert |

GroupId bleibt `ch.so.agi.gretl`, aktuelle Version `0.1.0-SNAPSHOT`.
Alte direkte Java-Consumer passen Dependency und Import an und kompilieren neu.
Es gibt keine Übergangs-JARs oder zweiten Provider. `BasicTaskProvider` wird
in `DatabaseTaskProvider` und `IoTaskProvider` aufgeteilt; `CoreTaskProvider`
entfällt zugunsten desselben `IoTaskProvider`. Provider werden ausschliesslich
über die aktualisierten SPI-Ressourcen geladen.

Die vor dem Umbau erfassten [103 öffentlichen Einstiegsklassen](../implementation/improvement-plan/phase-2-previous-public-entrypoints.json)
und deren [javap-Ausgabe](../implementation/improvement-plan/phase-2-previous-public-api.txt)
beziehen sich auf den abgenommenen Phase-1-Commit `52675c5`. Klassen unter
`internal` bleiben interne Implementierung trotz notwendiger Java-Sichtbarkeit.
Die vollständige folgende Zuordnung enthält auch solche Helfer. Geschachtelte
Typen ziehen mit ihrer äusseren Klasse um. Nicht aufgeführte Klassen behalten
ihren Paketnamen. Laufzeithelfer aus der CLI ziehen in die Job-Runtime um;
`CliOptions` bleibt die Authoring-Fassade, `JobOptions` enthält den gemeinsamen
Startvertrag.

| Alte Klasse | Neue Klasse |
| --- | --- |
| `ch.so.agi.gretl.cli.CliException` | `ch.so.agi.gretl.job.runtime.StartException` |
| `ch.so.agi.gretl.cli.JobSource` | `ch.so.agi.gretl.job.runtime.JobSource` |
| `ch.so.agi.gretl.cli.OperationHelpText` | `ch.so.agi.gretl.job.runtime.OperationHelpText` |
| `ch.so.agi.gretl.cli.ParameterFile` | `ch.so.agi.gretl.job.runtime.ParameterFile` |
| `ch.so.agi.gretl.cli.PlanGraphFormat` | `ch.so.agi.gretl.job.runtime.PlanGraphFormat` |
| `ch.so.agi.gretl.cli.PlanGraphRenderer` | `ch.so.agi.gretl.job.runtime.PlanGraphRenderer` |
| `ch.so.agi.gretl.cli.RuntimeConfig` | `ch.so.agi.gretl.job.runtime.RuntimeConfig` |
| `ch.so.agi.gretl.cli.RuntimeInfo` | `ch.so.agi.gretl.job.runtime.RuntimeInfo` |
| `ch.so.agi.gretl.cli.ScriptJobSource` | `ch.so.agi.gretl.job.runtime.ScriptJobSource` |
| `ch.so.agi.gretl.cli.TerminationMessage` | `ch.so.agi.gretl.job.runtime.TerminationMessage` |
| `ch.so.agi.gretl.internal.interlis.CsvValidatorImpl` | `ch.so.agi.gretl.internal.validation.CsvValidatorImpl` |
| `ch.so.agi.gretl.internal.interlis.GpkgValidatorImpl` | `ch.so.agi.gretl.internal.validation.GpkgValidatorImpl` |
| `ch.so.agi.gretl.internal.interlis.Ili2DbDatasetNames` | `ch.so.agi.gretl.internal.ili2db.Ili2DbDatasetNames` |
| `ch.so.agi.gretl.internal.interlis.Ili2DbExecutionSupport` | `ch.so.agi.gretl.internal.ili2db.Ili2DbExecutionSupport` |
| `ch.so.agi.gretl.internal.interlis.Ili2DbFlavor` | `ch.so.agi.gretl.internal.ili2db.Ili2DbFlavor` |
| `ch.so.agi.gretl.internal.interlis.Ili2DbRequest` | `ch.so.agi.gretl.internal.ili2db.Ili2DbRequest` |
| `ch.so.agi.gretl.internal.interlis.Ili2DbRequestBuilder` | `ch.so.agi.gretl.internal.ili2db.Ili2DbRequestBuilder` |
| `ch.so.agi.gretl.internal.interlis.InterlisTaskException` | `ch.so.agi.gretl.internal.InterlisTaskException` |
| `ch.so.agi.gretl.internal.interlis.InterlisValidationRequest` | `ch.so.agi.gretl.internal.validation.InterlisValidationRequest` |
| `ch.so.agi.gretl.internal.interlis.InterlisValidationSupport` | `ch.so.agi.gretl.internal.validation.InterlisValidationSupport` |
| `ch.so.agi.gretl.internal.interlis.JsonValidatorImpl` | `ch.so.agi.gretl.internal.validation.JsonValidatorImpl` |
| `ch.so.agi.gretl.internal.interlis.ShpValidatorImpl` | `ch.so.agi.gretl.internal.validation.ShpValidatorImpl` |
| `ch.so.agi.gretl.tasks.basic.CurlOperation` | `ch.so.agi.gretl.tasks.io.http.CurlOperation` |
| `ch.so.agi.gretl.tasks.basic.CurlTaskException` | `ch.so.agi.gretl.tasks.io.http.CurlTaskException` |
| `ch.so.agi.gretl.tasks.basic.Db2DbOperation` | `ch.so.agi.gretl.tasks.database.Db2DbOperation` |
| `ch.so.agi.gretl.tasks.basic.Db2DbSupport` | `ch.so.agi.gretl.tasks.database.Db2DbSupport` |
| `ch.so.agi.gretl.tasks.basic.Db2DbTaskException` | `ch.so.agi.gretl.tasks.database.Db2DbTaskException` |
| `ch.so.agi.gretl.tasks.basic.DuckDbFederationEngine` | `ch.so.agi.gretl.tasks.database.DuckDbFederationEngine` |
| `ch.so.agi.gretl.tasks.basic.DuckDbSqlExecutorOperation` | `ch.so.agi.gretl.tasks.database.DuckDbSqlExecutorOperation` |
| `ch.so.agi.gretl.tasks.basic.DuckDbTaskException` | `ch.so.agi.gretl.tasks.database.DuckDbTaskException` |
| `ch.so.agi.gretl.tasks.basic.ExtractOperation` | `ch.so.agi.gretl.tasks.io.archive.ExtractOperation` |
| `ch.so.agi.gretl.tasks.basic.ExtractResult` | `ch.so.agi.gretl.tasks.io.archive.ExtractResult` |
| `ch.so.agi.gretl.tasks.basic.ExtractTaskException` | `ch.so.agi.gretl.tasks.io.archive.ExtractTaskException` |
| `ch.so.agi.gretl.tasks.basic.GzipOperation` | `ch.so.agi.gretl.tasks.io.archive.GzipOperation` |
| `ch.so.agi.gretl.tasks.basic.GzipTaskException` | `ch.so.agi.gretl.tasks.io.archive.GzipTaskException` |
| `ch.so.agi.gretl.tasks.basic.HttpDownloadOperation` | `ch.so.agi.gretl.tasks.io.http.HttpDownloadOperation` |
| `ch.so.agi.gretl.tasks.basic.HttpDownloadResult` | `ch.so.agi.gretl.tasks.io.http.HttpDownloadResult` |
| `ch.so.agi.gretl.tasks.basic.HttpTaskException` | `ch.so.agi.gretl.tasks.io.http.HttpTaskException` |
| `ch.so.agi.gretl.tasks.basic.PreparedStatementSpec` | `ch.so.agi.gretl.tasks.database.PreparedStatementSpec` |
| `ch.so.agi.gretl.tasks.basic.SqlExecutionResult` | `ch.so.agi.gretl.tasks.database.SqlExecutionResult` |
| `ch.so.agi.gretl.tasks.basic.SqlExecutorOperation` | `ch.so.agi.gretl.tasks.database.SqlExecutorOperation` |
| `ch.so.agi.gretl.tasks.basic.SqlResult` | `ch.so.agi.gretl.tasks.database.SqlResult` |
| `ch.so.agi.gretl.tasks.basic.SqlTaskException` | `ch.so.agi.gretl.tasks.database.SqlTaskException` |
| `ch.so.agi.gretl.tasks.formats.AvOperations` | `ch.so.agi.gretl.tasks.interlis.av.AvOperations` |
| `ch.so.agi.gretl.tasks.formats.Csv2ExcelOperation` | `ch.so.agi.gretl.tasks.interlis.formats.Csv2ExcelOperation` |
| `ch.so.agi.gretl.tasks.formats.CsvExportOperation` | `ch.so.agi.gretl.tasks.interlis.formats.CsvExportOperation` |
| `ch.so.agi.gretl.tasks.formats.CsvImportOperation` | `ch.so.agi.gretl.tasks.interlis.formats.CsvImportOperation` |
| `ch.so.agi.gretl.tasks.formats.FormatTaskSupport` | `ch.so.agi.gretl.internal.formats.FormatTaskSupport` |
| `ch.so.agi.gretl.tasks.formats.Gpkg2ShpOperation` | `ch.so.agi.gretl.tasks.interlis.formats.Gpkg2ShpOperation` |
| `ch.so.agi.gretl.tasks.formats.GpkgExportOperation` | `ch.so.agi.gretl.tasks.interlis.formats.GpkgExportOperation` |
| `ch.so.agi.gretl.tasks.formats.GpkgImportOperation` | `ch.so.agi.gretl.tasks.interlis.formats.GpkgImportOperation` |
| `ch.so.agi.gretl.tasks.formats.Ili2DbOperations` | `ch.so.agi.gretl.tasks.interlis.ili2db.Ili2DbOperations` |
| `ch.so.agi.gretl.tasks.formats.InterlisTaskProvider` | `ch.so.agi.gretl.tasks.interlis.InterlisTaskProvider` |
| `ch.so.agi.gretl.tasks.formats.JsonImportOperation` | `ch.so.agi.gretl.tasks.interlis.formats.JsonImportOperation` |
| `ch.so.agi.gretl.tasks.formats.ShpExportOperation` | `ch.so.agi.gretl.tasks.interlis.formats.ShpExportOperation` |
| `ch.so.agi.gretl.tasks.formats.ShpImportOperation` | `ch.so.agi.gretl.tasks.interlis.formats.ShpImportOperation` |
| `ch.so.agi.gretl.tasks.formats.ValidatorBuilder` | `ch.so.agi.gretl.tasks.interlis.validation.ValidatorBuilder` |
| `ch.so.agi.gretl.tasks.formats.ValidatorOperations` | `ch.so.agi.gretl.tasks.interlis.validation.ValidatorOperations` |
| `ch.so.agi.gretl.tasks.formats.ValidatorRequest` | `ch.so.agi.gretl.tasks.interlis.validation.ValidatorRequest` |
| `ch.so.agi.gretl.tasks.ftp.FtpConnection` | `ch.so.agi.gretl.tasks.io.ftp.FtpConnection` |
| `ch.so.agi.gretl.tasks.ftp.FtpDeleteOperation` | `ch.so.agi.gretl.tasks.io.ftp.FtpDeleteOperation` |
| `ch.so.agi.gretl.tasks.ftp.FtpDownloadOperation` | `ch.so.agi.gretl.tasks.io.ftp.FtpDownloadOperation` |
| `ch.so.agi.gretl.tasks.ftp.FtpFields` | `ch.so.agi.gretl.tasks.io.ftp.FtpFields` |
| `ch.so.agi.gretl.tasks.ftp.FtpListOperation` | `ch.so.agi.gretl.tasks.io.ftp.FtpListOperation` |
| `ch.so.agi.gretl.tasks.ftp.FtpSupport` | `ch.so.agi.gretl.tasks.io.ftp.FtpSupport` |
| `ch.so.agi.gretl.tasks.ftp.FtpTaskException` | `ch.so.agi.gretl.tasks.io.ftp.FtpTaskException` |
| `ch.so.agi.gretl.tasks.ftp.FtpUploadOperation` | `ch.so.agi.gretl.tasks.io.ftp.FtpUploadOperation` |
| `ch.so.agi.gretl.tasks.s3.S3Bucket2BucketOperation` | `ch.so.agi.gretl.tasks.io.s3.S3Bucket2BucketOperation` |
| `ch.so.agi.gretl.tasks.s3.S3Connection` | `ch.so.agi.gretl.tasks.io.s3.S3Connection` |
| `ch.so.agi.gretl.tasks.s3.S3DeleteOperation` | `ch.so.agi.gretl.tasks.io.s3.S3DeleteOperation` |
| `ch.so.agi.gretl.tasks.s3.S3DownloadOperation` | `ch.so.agi.gretl.tasks.io.s3.S3DownloadOperation` |
| `ch.so.agi.gretl.tasks.s3.S3Fields` | `ch.so.agi.gretl.tasks.io.s3.S3Fields` |
| `ch.so.agi.gretl.tasks.s3.S3Support` | `ch.so.agi.gretl.tasks.io.s3.S3Support` |
| `ch.so.agi.gretl.tasks.s3.S3TaskException` | `ch.so.agi.gretl.tasks.io.s3.S3TaskException` |
| `ch.so.agi.gretl.tasks.s3.S3UploadOperation` | `ch.so.agi.gretl.tasks.io.s3.S3UploadOperation` |
| `ch.so.agi.gretl.tasks.xslt.XslTransformerOperation` | `ch.so.agi.gretl.tasks.io.xslt.XslTransformerOperation` |
| `ch.so.agi.gretl.tasks.xslt.XsltTaskException` | `ch.so.agi.gretl.tasks.io.xslt.XsltTaskException` |
