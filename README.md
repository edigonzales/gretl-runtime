# gretl-runtime

Implementierung der GRETL Runtime auf Basis der Spezifikation 4.1 in
[`docs/spec/`](docs/spec/).

**Aktueller Stand: M6-Snapshot `0.1.0-SNAPSHOT` – Script-MVP plus optionaler
Job-Fat-JAR-Packager.** Die
öffentliche Java-API, der sequenzielle Runner, die Groovy-6-Fassade,
Logging/Runbericht, die Tasks `HttpDownload`, `Curl`, `Gzip`, `Extract`,
`FtpList`, `FtpDownload`, `FtpUpload`, `FtpDelete`, `S3Download`, `S3Upload`,
`S3Delete`, `S3Bucket2Bucket`, `Db2Db`, `DuckDbSqlExecutor`, `Csv2Excel`,
`CsvImport`, `CsvExport`, `JsonImport`, `CsvValidator`, `JsonValidator`,
`Gpkg2Shp`, `GpkgValidator`, `ShpValidator`, `SqlExecutor`, `XslTransformer`
und `IliValidator` sowie die Scriptdistribution sind
umgesetzt. M5-Anwenderabnahme
ist bestanden. M6 ergänzt `gretl package`, vorkompilierte Job-JARs und eine
JAR-Laufzeit mit materialisierten Jobressourcen.

Die M5-Release-/Pilotentscheidung bleibt offen. Der Packager bündelt im ersten
Profil den bereitgestellten Runtime-Klassenpfad; eine Auflösung von
Maven-Koordinaten aus dem Deskriptor ist noch nicht enthalten. Publisher,
XtfToGpkg und Native Image bleiben spätere Aufträge.

- Build-Anleitung: [`docs/implementation/build.md`](docs/implementation/build.md)
- M6-Bericht: [`docs/implementation/m6-report.md`](docs/implementation/m6-report.md)
- Snapshot-Veröffentlichung: [`docs/implementation/publish-snapshots.md`](docs/implementation/publish-snapshots.md)
- Lokalisierung/Diagnose: [`docs/implementation/localization.md`](docs/implementation/localization.md)
- DAG-Visualisierung: [`docs/implementation/plan-graphs.md`](docs/implementation/plan-graphs.md)
- Task-Referenz (AsciiDoc/Thoth): [`docs/reference/reference.adoc`](docs/reference/reference.adoc)
- Core-Task-Migration: [`docs/migration/core-tasks.md`](docs/migration/core-tasks.md)
- M5-Bericht (historisch): [`docs/implementation/m5-report.md`](docs/implementation/m5-report.md)
- M5-Anwenderprotokoll: [`docs/implementation/m5-user-acceptance.md`](docs/implementation/m5-user-acceptance.md)
- M4-Bericht (historisch): [`docs/implementation/m4-report.md`](docs/implementation/m4-report.md)
- M3-Bericht (historisch): [`docs/implementation/m3-report.md`](docs/implementation/m3-report.md)
- M2-Bericht (historisch): [`docs/implementation/m2-report.md`](docs/implementation/m2-report.md)
- M1-Bericht (historisch): [`docs/implementation/m1-report.md`](docs/implementation/m1-report.md)
- M0-Bericht (historisch): [`docs/implementation/m0-report.md`](docs/implementation/m0-report.md)
- Standardprüfung: `./mvnw verify` mit JDK 25 (**359 Tests**)
- GeoTools-Profil: `./mvnw -Pgeotools-probe verify` (**363 Tests**)
- Backing-Services (Docker): `./mvnw -Pbacking-services verify` (19 Integrationstests)
- Kombiniert mit GeoTools: `./mvnw -Pgeotools-probe,backing-services verify` (**382 Tests**)
- Teststrategie: [`docs/implementation/testing.md`](docs/implementation/testing.md)
- Scriptdistribution: `gretl-dist/target/gretl-runtime-0.1.0-SNAPSHOT-bin.zip`

Paketieren und starten:

```sh
./bin/gretl package examples/ili-validator/job-package.json --output /tmp/ili-job.jar
java -jar /tmp/ili-job.jar --report /tmp/ili-job-report.json
```
