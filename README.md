# gretl-runtime

Implementierung der GRETL Runtime auf Basis der Spezifikation 4.1 in
[`docs/spec/`](docs/spec/).

**Aktueller Stand: `0.1.0-SNAPSHOT` – Script-Runtime mit 44 Operationen und
optionalem Job-Fat-JAR-Packager.** Die
öffentliche Java-API, der sequenzielle Runner, die Groovy-6-Fassade,
Logging/Runbericht, die Tasks `HttpDownload`, `Curl`, `Gzip`, `Extract`,
`FtpList`, `FtpDownload`, `FtpUpload`, `FtpDelete`, `S3Download`, `S3Upload`,
`S3Delete`, `S3Bucket2Bucket`, `Db2Db`, `DuckDbSqlExecutor`, `Csv2Excel`,
`CsvImport`, `CsvExport`, `JsonImport`, `CsvValidator`, `JsonValidator`,
`Gpkg2Shp`, `GpkgImport`, `GpkgExport`, `ShpImport`, `ShpExport`,
`GpkgValidator`, `ShpValidator`, `Ili2pgImportSchema`, `Ili2pgImport`,
`Ili2pgExport`, `Ili2pgDelete`, `Ili2pgReplace`, `Ili2pgUpdate`,
`Ili2pgValidate`, `Ili2gpkgImport`, `Ili2duckdbImportSchema`,
`Ili2duckdbImport`, `Ili2duckdbExport`, `Av2ch`, `Av2geobau`, `Gpkg2Dxf`,
`SqlExecutor`, `XslTransformer` und `IliValidator` sowie die Scriptdistribution sind
umgesetzt. M5-Anwenderabnahme
ist bestanden. M6 ergänzt `gretl package`, vorkompilierte Job-JARs und eine
JAR-Laufzeit mit materialisierten Jobressourcen.

Die M5-Release-/Pilotentscheidung bleibt offen. Der Packager löst deklarierte
Maven-Koordinaten aus dem lokalen Repository/Central auf; eine vollständig
minimale Runtime-Auswahl und der Linux-x86-64-Nachweis stehen noch aus
(siehe [`m7-report.md`](docs/implementation/m7-report.md)). Publisher,
XtfToGpkg und Native Image bleiben spätere Aufträge.

- Build-Anleitung: [`docs/implementation/build.md`](docs/implementation/build.md)
- M6-Bericht: [`docs/implementation/m6-report.md`](docs/implementation/m6-report.md)
- M7-Release-/Packager-Bericht: [`docs/implementation/m7-report.md`](docs/implementation/m7-report.md)
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
- Standardprüfung: `./mvnw -B clean verify` mit JDK 25
- Vollständig mit echten Diensten: `./mvnw -B -Pgeotools-probe,backing-services clean verify`
- Modulübersicht: [`module-boundaries.md`](docs/implementation/module-boundaries.md)
- Java-/Maven-Migration: [`phase-2-java-maven.md`](docs/migration/phase-2-java-maven.md)
- Fachliche JAR-Jobs und IMAGE-Vorbereitung: [`job-artifact-tests.md`](docs/implementation/job-artifact-tests.md)
- Phasenplan und versionsbezogene Testzahlen: [`improvement-plan/`](docs/implementation/improvement-plan/)
- Teststrategie: [`docs/implementation/testing.md`](docs/implementation/testing.md)
- Scriptdistribution: `gretl-dist/target/gretl-runtime-0.1.0-SNAPSHOT-bin.zip`

Paketieren und starten:

```sh
./bin/gretl package examples/ili-validator/job-package.json --output /tmp/ili-job.jar
java -jar /tmp/ili-job.jar --report /tmp/ili-job-report.json
```
