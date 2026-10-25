# Build-Anleitung GRETL Runtime (M6)

**Stand:** 18. Oktober 2026, Meilenstein M6 auf Basis der Spezifikation 4.1.
M0–M5 bleiben enthalten. M6 ergänzt den optionalen Job-Fat-JAR-Packager
(`gretl package`) und die vorkompilierte Job-JAR-Laufzeit. Die M5-Anwenderabnahme
ist bestanden; die formale Release-/Pilotentscheidung bleibt offen.

## 1. Voraussetzungen

- **JDK 25** muss die aktive Toolchain sein. Qualifiziert wurde Eclipse Temurin
  `25.0.4.1+1` (macOS ARM64). `JAVA_HOME` muss auf dieses JDK zeigen.
- **Netzwerkzugriff** auf:
  - Maven Central für Maven, Plugins, Groovy 6.0.0, SLF4J/Logback,
    Commons Compress, pgJDBC und die Testbibliothek H2,
  - `https://jars.interlis.ch` für die M0-Qualifikation,
  - `https://repo.osgeo.org/repository/release/` nur für `geotools-probe`.
- **Kein System-Maven** ist erforderlich. Der Wrapper lädt Maven `3.10.0` mit
  fixierter SHA-256-Prüfsumme und erzwingt JDK 25/Maven 3.10.x.
- **Keine externen Dienste im Standardbuild.** PostgreSQL wird für den
  optionalen Live-Nachweis als lokaler Container gestartet; der Standardbuild
  nutzt H2 für SQL-Transaktionstests.

## 2. Standardprüfung

```sh
export JAVA_HOME=/pfad/zu/temurin-25.0.4.1+1/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

./mvnw -v          # Maven 3.10.0 und Java 25
./mvnw clean verify
```

`verify` umfasst:

1. **gretl-api** — 29 JDK-only API-Dateien, 17 Tests,
2. **gretl-runner** — Definition, Planung, Ergebnisse, Fehler, Abbruch,
   Cleanup, Contexts, Workspace, 115 Tests,
3. **gretl-groovy** — echte Groovy-6-DSL, ResultView, `ctx.run`, CORE-040,
   49 Tests,
4. **gretl-i18n** — englischer Quellkatalog, deutscher Katalog, Locale-Priorität,
   4 Tests,
5. **gretl-logging** — Text/JSON, JSONL-Limits, Redaction, Causegraph,
   Runbericht, Logback-Bridge, 18 Tests,
6. **gretl-packager** — Paketdeskriptor, Groovy-Vorkompilation, Shade-Merge,
   Kollisionsprüfung, 8 Tests,
7. **gretl-basic-tasks** — HTTP, Curl, Gzip, ZIP/GZIP, SQL, Db2Db, DuckDB-Föderation, Provider, 51 Tests,
8. **gretl-core-tasks** — XSLT, FTP, S3, Saxon, Commons Net, AWS SDK, 23 Tests,
9. **gretl-interlis-tasks** — gretl-next-Formatengines, nativer Shapefile-Code, INTERLIS-Validierung, ili2db/AV/DXF, 25 Tests,
10. **gretl-cli** — Hilfe, Plan, Parameter, Secrets, Pfade, Timeout, Reports,
   AsciiDoc-Tabellen, Curl-/SQL-/DuckDB-Integration, Distribution-nahe Integration, 60 Tests,
11. **gretl-ilivalidator** — reale lokale INTERLIS-Validierung, 3 Tests,
12. **gretl-job-runtime** — JAR-Einstieg, Ressourcenmaterialisierung,
13. **examples/file-size-extension** — separat gebautes Erweiterungs-JAR,
14. **gretl-dist** — ZIP/TAR.GZ, Launcher, Inventar/Prüfsummen,
    Extension-/HTTP-/IliValidator-/Token-/Action-Integration, Job-JAR-Paket
    und -Start sowie AsciiDoc-Drift- und Include-Prüfung, 1 Surefire- und 10 Failsafe-Tests,
15. **gretl-integration-tests** — nur mit `-Pbacking-services`:
    PostgreSQL/PostGIS, echter FTP- und S3/Floci-Container, Db2Db/PostgreSQL, DuckDB-Föderation, Tabellen/JSON, Geo-Dateien, ili2pg/ili2gpkg/ili2duckdb/AV/DXF, 28 Failsafe-Tests.

Erwartet: **384 Tests**, 0 Fehler, `BUILD SUCCESS`. Das Profil
`backing-services` ergänzt 28 Docker-backed Integrationstests; Details stehen in
[`testing.md`](testing.md).

### Artefakte

| Artefakt | Ort |
|---|---|
| API-JAR | `gretl-api/target/gretl-api-0.1.0-SNAPSHOT.jar` |
| Runner-JAR | `gretl-runner/target/gretl-runner-0.1.0-SNAPSHOT.jar` |
| Groovy-Adapter | `gretl-groovy/target/gretl-groovy-0.1.0-SNAPSHOT.jar` |
| Logging | `gretl-logging/target/gretl-logging-0.1.0-SNAPSHOT.jar` |
| Lokalisierung | `gretl-i18n/target/gretl-i18n-0.1.0-SNAPSHOT.jar` |
| Standard-Tasks | `gretl-basic-tasks/target/gretl-basic-tasks-0.1.0-SNAPSHOT.jar` |
| Core-Tasks | `gretl-core-tasks/target/gretl-core-tasks-0.1.0-SNAPSHOT.jar` |
| IliValidator | `gretl-ilivalidator/target/gretl-ilivalidator-0.1.0-SNAPSHOT.jar` |
| CLI | `gretl-cli/target/gretl-cli-0.1.0-SNAPSHOT.jar` |
| Packager | `gretl-packager/target/gretl-packager-0.1.0-SNAPSHOT.jar` |
| Job-JAR-Runtime | `gretl-job-runtime/target/gretl-job-runtime-0.1.0-SNAPSHOT.jar` |
| Erweiterungs-JAR | `examples/file-size-extension/target/file-size-extension-0.1.0-SNAPSHOT.jar` |
| Scriptdistribution | `gretl-dist/target/gretl-runtime-0.1.0-SNAPSHOT-bin.zip` und `.tar.gz` |
| Prüfsummen | `SHA256SUMS` im Distributionsarchiv |

### Gezielte Aufrufe

```sh
./mvnw -pl gretl-cli -am test
./mvnw -pl gretl-basic-tasks -am test
./mvnw -pl gretl-core-tasks -am test
./mvnw -pl gretl-ilivalidator -am test
./mvnw -pl gretl-dist -am verify
./mvnw -Pgeotools-probe verify -Dmaven.repo.local=/tmp/m5-fresh-final
```

`gretl-dist` benötigt `package` (Assembly) vor der Failsafe-Prüfung; `verify`
erledigt beides. Die frühere M0-Testbaugruppe entfällt; ihre Artefaktprüfung
ist in den Distributions-IT verschoben.

## 3. Scriptdistribution benutzen

```sh
cd gretl-dist/target
unzip gretl-runtime-0.1.0-SNAPSHOT-bin.zip
cd gretl-runtime-0.1.0-SNAPSHOT
export JAVA_HOME=/pfad/zu/temurin-25
./bin/gretl --version
./bin/gretl operations
./bin/gretl run examples/download-extract/job.groovy --help
./bin/gretl run examples/ili-validator/job.groovy --help
./bin/gretl run examples/action-nodata/nodata.groovy --help
./bin/gretl operations IliValidator
./bin/gretl run examples/ili-validator/job.groovy --lang en --help
./bin/gretl package examples/ili-validator/job-package.json --output /tmp/ili-job.jar
java -jar /tmp/ili-job.jar --help
java -jar /tmp/ili-job.jar --report /tmp/ili-job-report.json
```

Layout:

```text
bin/gretl
lib/runtime/     CLI, Groovy, Runner, API, Logging, Logback, Groovy-JSON
lib/tasks/       gretl-basic-tasks, gretl-core-tasks, gretl-ilivalidator
lib/extensions/  eigene Task-JARs und kompatible Dependencies
examples/        Download/Extract, SQL, IliValidator, Token/Katalog, Action/NO_DATA
inventory.json   Module, Tasks, Artefakte, Dependencyversionen
SHA256SUMS       SHA-256 aller Distributionsdateien mit relativen Pfaden
```

Die Property-Tabellen der AsciiDoc-Referenz werden aus derselben Distribution
erzeugt:

```sh
./scripts/generate-task-docs.sh
```

Das Skript schreibt ausschliesslich `docs/reference/generated/task-*.adoc`.
Jede Datei enthält nur die Property-Tabelle. Die redaktionelle Hauptdatei
`docs/reference/reference.adoc` wird nicht generiert; sie bindet die Tabellen
manuell ein. Die Distributions-IT prüft, dass jede Tabelle eingebunden ist und
keine Include-Datei fehlt. Die Thoth-Konfiguration
`docs/reference/biblios.yml` ist eingecheckt. Rendern mit Thoth Biblios ist in
[`docs/reference/README.md`](../reference/README.md) beschrieben; Thoth bleibt ein
externes Werkzeug und ist keine Maven-Abhängigkeit.

Der Launcher verwendet einen gemeinsamen Klassenpfad und `exec java`, damit
Signale den Java-Prozess erreichen. Ein eigener Task wird vor dem Start in
`lib/extensions` gelegt; ein Neubau des Runners ist nicht nötig. Hot-Reload und
JAR-Auswahl aus einem laufenden Job sind nicht vorgesehen.

`gretl package descriptor.json --output job.jar` prüft den Deskriptor,
kompiliert den Job und deklarierte Groovy-Helfer zu Bytecode, prüft Klassen-
und Ressourcenkollisionen und schreibt ein ausführbares JAR. Das JAR enthält
die Runtime und den qualifizierten Klassenpfad; `java -jar` benötigt weder
Maven noch Gradle noch eine installierte GRETL-Distribution. Beim Packen wird
der Job nicht ausgeführt.

## 4. Runtimekonfiguration, Pfade und Secrets

Die optionale Ausgabesprache kann per `--lang`, `GRETL_LANGUAGE`, `language`
in `runtime.json` oder OS-Locale gewählt werden; Fallback ist Englisch.
Details stehen in [`localization.md`](localization.md).

```json
{
  "schemaVersion": 1,
  "runtime": {
    "workRoot": "/var/tmp/gretl",
    "reportRoot": "/var/log/gretl",
    "keepWork": "on-failure",
    "timeout": "PT1H",
    "jobName": "datenbezug"
  },
  "logging": {
    "format": "text",
    "level": "INFO",
    "eventFile": true,
    "maxRecordBytes": 16384,
    "maxEventFileBytes": 134217728,
    "progressInterval": "PT10S",
    "heartbeatInterval": "PT30S"
  },
  "params": {"environment": "test"},
  "secrets": {
    "dbPassword": {"environment": "GRETL_DB_PASSWORD"},
    "sourceAuth": {"file": "source-auth.txt"}
  },
  "taskConfig": {
    "edit": {
      "jdbcUrl": "jdbc:postgresql://localhost/gretl_test",
      "user": "gretl_test",
      "passwordSecret": "dbPassword"
    }
  }
}
```

Pfadbasen: Job-/CLI-Dateien relativ zum Start-CWD; `runtime`-Pfade und
Secretdateien relativ zum Configordner; `file()` relativ zum Jobroot;
`ctx.path()` relativ zum Start-CWD; `workPath()` im privaten Taskworkspace;
`path`-Parameter relativ zum Start-CWD.

Unbekannte Corefelder, doppelte JSON-Schlüssel, falsche Typen, ungültige
Limits/Intervalle und widersprüchliche Secretquellen sind Code 2. Secrets werden
erst beim Zugriff aufgelöst und vor Logger/Report maskiert.

## 5. Logging und Runbericht

- Konsole: Text (Default) oder JSON auf **stderr**; Hilfe/Plan auf stdout.
- Ereignisdatei: `<runId>.events.jsonl` unter `runtime.reportRoot`, 0600,
  Eventlimit 16 KiB, Dateilimit 128 MiB plus Terminalreserve.
- Runbericht: `<runId>.json` bzw. `--report`, atomar ersetzt, 0600.
- Text/JSON tragen dieselbe Event-/Sequenzidentität; JSON ist ein Objekt pro
  Zeile.
- `--log-format text|json` und `--log-level` überschreiben die Config.
- Bekannte Secrets, dynamische `ctx.sensitive`-Werte und sensible Feldnamen
  werden vor allen verwalteten Senken maskiert.
- HTTP-/SQL-Fehler tragen sichere strukturierte Details; Query, Header,
  Cookies, Body und SQL-Parameter werden nicht geloggt.
- Optional schreibt `GRETL_TERMINATION_MESSAGE_PATH` eine kleine
  Kubernetes-Beendigungsmeldung.
- Frühe CLI-Fehler und Laufzeitfehler verwenden im Textmodus dieselbe Struktur:
  Zeitstempel, Level, Titel, technischer Code und eingerückte Felder wie
  `Meldung`, `Feld`, `Task`, `Quelle` und `Ursache`. JSON/JSONL bleiben
  schema-stabil.

Nicht umgesetzt: Heartbeat-Timer, Progress-Koaleszenz und taskgebundene
MDC-Anreicherung von Fremdbibliothekslogs. Das ist im M5-Bericht als offene
Logging-Lücke ausgewiesen.

## 6. Tasks und Grenzen

### HttpDownload

- GET/POST, Header (Text/SecretValue), Body nur POST, 1-GiB-Default.
- Redirects aus, kein Auto-Retry, temporäre Datei + atomare Veröffentlichung,
  SHA-256.
- Standardmässig kein Überschreiben; `overwrite = true` ersetzt eine vorhandene
  reguläre Zieldatei erst nach erfolgreichem Download. Verzeichnisse und
  Symlinks bleiben als Ziel unzulässig.
- Sichere Fehlerdiagnose mit Methode/Origin/Status/Bytes/Phase.

### Curl

- Eigener HTTP-Request-/Response-Task, nicht Teil von `HttpDownload`.
- GET/POST, Text-/Binär-/Multipart-Body, Basic Auth, Header mit
  `SecretValue`, exakter `expectedStatusCode`, optionaler `expectedBody`.
- Response maximal `maxBytes` (Default 16 MiB); Output optional und atomar;
  ohne `overwrite` wird eine vorhandene Datei nicht ersetzt.
- Redirects aus, kein Auto-Retry, Passwort nicht im Klartext, kein Body-Dump
  in Fehlern. Ergebnisse: `status`, `bytes`, `outputFile`.

### Gzip

- Komprimiert genau eine Eingabedatei; temporäre Ausgabe und atomare
  Veröffentlichung.
- `overwrite` ist aus Kompatibilitätsgründen standardmässig `true`;
  `overwrite = false` schützt vorhandene Ziele.
- Ergebnisse: `file`, `bytes`; Quelle und Ziel müssen verschieden sein.

### XslTransformer

- Transformiert eine Liste von XML-Dateien mit lokalem Stylesheet oder
  Klassenpfad-Ressource unter `xslt/`.
- Standarderweiterung `xtf`; Ausgabekollisionen sind Fehler.
- `overwrite` standardmässig `true`; vorhandene Ziele werden nach erfolgreicher
  Transformation ersetzt.
- Saxon 11.4, keine externen Entities/DOCTYPEs/Netzwerkzugriffe; lokale
  Stylesheet-Includes bleiben erlaubt. Ergebnisse: `files`, `count`.

### FTP-Tasks

- `FtpList`, `FtpDownload`, `FtpUpload`, `FtpDelete` teilen
  `server`/`user`/`password` (SecretValue), `systemType`, `fileSeparator`,
  `passiveMode` und `controlKeepAliveTimeout`.
- Download/Upload unterstützen ASCII/BINARY; Download schützt lokale Pfade und
  kann vorhandene Ziele mit `overwrite` ersetzen.
- `remoteFiles` sind Namen oder Wildcards; leere Listen bedeuten „alle Dateien“
  im entfernten Verzeichnis.
- Ergebnisse: Dateilisten, Bytes, entfernter Pfad bzw. Löschzähler; keine
  Passwort- oder Inhaltslogs, keine automatischen Retries.

### CSV-/JSON-Tasks

- `Csv2Excel`, `CsvImport`, `CsvExport` und `JsonImport` sind Ports der
  gretl-next-Engines. CSV und Excel laufen über `iox-wkf`/`iox-formats`,
  JSON über Jackson.
- `CsvValidator` und `JsonValidator` validieren mit ilivalidator und den
  IOX-Readern gegen INTERLIS-Modelle. Die früheren Strukturprüfungen sind
  entfernt.

### Geo-Datei-Tasks

- `Gpkg2Shp`, `GpkgImport`, `GpkgExport`, `ShpImport`, `ShpExport`,
  `GpkgValidator` und `ShpValidator` sind Ports aus gretl-next.
- Shapefile-I/O nutzt den nativen gretl-next-Reader/Writer. Die Hop-Regeln für
  DBF-NULLs, Encoding-Auswahl, CPG, Sidecars und atomisches Überschreiben sind
  zurückportiert.
- `GpkgValidator` und `ShpValidator` validieren mit ilivalidator und den
  jeweiligen IOX-Readern gegen INTERLIS-Modelle.
- GDAL/DuckDB-spatial wird in diesen Tasks nicht mehr verwendet;
  `DuckDbSqlExecutor` bleibt davon getrennt.

### GeoTools-Abgrenzung

- `gretl-next` hält `gretl-core` frei von GeoTools und führt GeoTools im
  separaten `gretl-geotools`-Plugin. Die Runtime übernimmt diese Grenze:
  `iox-wkf` und `iox-formats` excludieren `org.geotools:*`; ein
  Enforcer-Guard `ban-geotools` verbietet `org.geotools` und
  `org.geotools.ogc` in allen produktiven Modulen.
- Nur das explizite Testprofil `-Pgeotools-probe` überschreibt den Guard.
- `DistributionIT` prüft zusätzlich das ZIP auf GeoTools-JARs.

### M7-Release-/Packager-Härtung

- Der Packager löst deklarierte Maven-Koordinaten über den Maven-CLI aus dem
  lokalen Repository und Maven Central auf;
  `-Dgretl.packager.offline=true` erzwingt den Offline-Modus. Bereits im
  Runtime-Klassenpfad vorhandene Artefakte werden nicht erneut aufgelöst.
  Maven wird nur beim Paketieren benötigt.
- Der Linux-x86-64-Workflow liegt unter `.github/workflows/release-candidate.yml`.
- Offene Release-Bedingungen stehen in [`m7-report.md`](m7-report.md).

### ili2db-/AV-/DXF-Tasks

- `Ili2pg*`, `Ili2gpkgImport` und `Ili2duckdb*` sind Ports der gretl-next-
  ili2db-Engines. PostgreSQL läuft über JDBC, GeoPackage und DuckDB über
  Dateidatenbanken.
- `Av2ch` und `Av2geobau` nutzen av2ch/av2geobau; `Gpkg2Dxf` schreibt
  ili2gpkg-Klassentabellen als DXF.
- Die Runtime-Felder bilden die gretl-next-Optionsfläche ab: Schema-, Import-,
  Export-, Dataset-, Validierungs-, Proxy- und Dateioptionen werden mit
  Defaults validiert und auf `Config` abgebildet.
- Der Packager bevorzugt bei identischen INTERLIS-Modellklassen die
  kanonische iox-ili-Version, weil av2geobau eine private Kopie einbettet.
- `DistributionIT` prüft das Inventar auf alle 44 Tasks und das neue
  Task-JAR; der Job-Paketierungstest deckt den Modellklassen-Sonderfall ab.

### Datenbank-Tasks

- `Db2Db` löst Quell- und Zielverbindung über `taskConfig`-Aliasse auf; die
  Zugangsdaten bleiben aus dem Job und den Logs heraus.
- Transfers bestehen aus SQL-Datei, Zieltabelle, `deleteAllRows` und optionalen
  Geometriespalten (WKT/WKB/GeoJSON mit EPSG). Alle Transfers laufen in einer
  Zieltransaktion.
- `sqlParameters`/`sqlParameterSets`, `batchSize` und `fetchSize` entsprechen
  der bisherigen Semantik.
- `DuckDbSqlExecutor` führt SQL-Dateien gegen eine DuckDB-Datei oder
  In-Memory-Datenbank aus und unterstützt die gretl-next-Föderation:
  CSV-, GeoPackage- und PostgreSQL-Quellen, beschreibbare PostgreSQL-Ziele
  sowie Parquet-, GeoPackage-, XLSX- und PostgreSQL-Exporte.
- Quellen und Ziele werden über `view` oder `materialize` aufgebaut; Exporte
  laufen nach den SQL-Dateien, Dateiexporte atomar über Temp-Dateien.
- DuckDB-Extensions (`spatial`, `postgres`, `excel`) werden geladen und mit
  `installExtensions` bei Bedarf installiert.
- DuckDB 1.5.2.0 bringt native Bibliotheken mit; das Job-JAR-Limit für einzelne
  Einträge wurde deshalb auf 256 MiB angehoben.

### S3-Tasks

- `S3Download`, `S3Upload`, `S3Delete` und `S3Bucket2Bucket` teilen
  `accessKey`, `secretKey` (SecretValue), `endpoint` und `region`.
- Upload akzeptiert `sourceFile`, `sourceDir` (nur direkte Dateien) oder
  `sourceFiles`; Objekt-Schlüssel sind Dateinamen, Duplikate werden abgewiesen.
- Download schützt lokale Pfade, unterstützt `key` oder den ganzen Bucket und
  schreibt atomar; `overwrite` defaultet auf `true`.
- Delete und Bucket2Bucket arbeiten paginiert; Ergebnisse sind Schlüssel und
  Zähler. `acl` defaultet auf `private`.
- AWS SDK v2 nutzt den URL-Connection-Client, damit die Job-JAR frei von
  Netty-/Apache-HTTP-4-Konflikten bleibt.

### Extract

- ZIP/GZIP-Modus explizit; 4-GiB- und 10'000-Eintrag-Default.
- Traversal-/Absolutpfad-/Symlink-/Kollisionsschutz, keine Überschreibung,
  sortierte Dateiliste.

### SqlExecutor

- PostgreSQL-Alias aus `taskConfig`.
- Vollständige Scripts ohne Regex-Split; PreparedStatements mit den
  dokumentierten Bindern; eine Transaktion mit Commit/Rollback.
- ResultSets werden konsumiert/geschlossen; `SqlResult` enthält Updatezahlen.
- Der Standardtest nutzt H2; der echte PostgreSQL-17-Lauf ist optional.

### IliValidator

- `dataFile` und `modelDirectory` sind Pflichtpfade; `validationConfig` ist
  optional.
- Die reale `ilivalidator:1.15.0` validiert mit lokalen Modellen.
- Der Handler installiert pro Invocation einen `EhiLogger`-Listener und entfernt
  ihn im `finally`; Engineausgabe wird in den geschützten
  `validation-report.txt` im Invocation-Workspace umgeleitet.
- Ergebnis: `ValidationResult(file, reportFile, warnings)`.
- Ungültige Transfers enden mit einer sicheren Task-Exception, ERROR-Diagnose
  und restricted Reportartefakt; gültige Läufe melden eine INFO-Diagnose.
- Kein EHI-Feldadapter, kein Sampling und keine separate Validator-JVM in M5.

## 7. Backing-Service-Integration

```sh
./mvnw -Pbacking-services verify
```

Das Profil bindet `gretl-integration-tests` ein und startet über Testcontainers
einen PostgreSQL-16-/PostGIS-Container sowie einen echten FTP-Container. Die
Tests prüfen echte Groovy-Jobs über die CLI, inklusive Schema-Isolation,
Transaktion/Rollback, Prepared-Statements, FTP-Upload/Download/Delete,
Passive Mode und Secret-Redaction. Docker muss laufen; fehlt Docker, bricht das
Profil mit einer klaren Meldung ab. Der Standard-`verify` bleibt Docker-frei.

Kombiniert mit GeoTools und Backing-Services werden **416 Tests** ausgeführt.
Die vollständige Teststrategie steht in [`testing.md`](testing.md).

## 8. Profil `geotools-probe`

```sh
./mvnw -Pgeotools-probe verify
```

Erwartet: **388 Tests** (384 Standard + 4 GeoTools). Die gezielte Probe
`-pl gretl-geotools-probe -am verify` umfasst 21 Tests (17 API + 4 GeoTools).
GeoTools 35.2 bleibt eine gesonderte Testprobe, kein öffentlicher Task und kein
Bestandteil der Scriptdistribution.

## 9. Plugins, Versionen und Buildstruktur

| Modul | Rolle | Standardbuild |
|---|---|---|
| `gretl-api` | JDK-only SPI | ja |
| `gretl-runner` | sequenzieller Runner | ja |
| `gretl-groovy` | Groovy-6-DSL | ja |
| `gretl-i18n` | englischer Quellkatalog, deutscher Katalog | ja |
| `gretl-logging` | Events, Redaction, Report | ja |
| `gretl-basic-tasks` | HTTP/Curl/Gzip/Extract/SQL/Db2Db/DuckDB | ja |
| `gretl-interlis-tasks` | gretl-next CSV/JSON/GPKG/SHP + INTERLIS-Validierung | ja |
| `gretl-core-tasks` | XSLT, FTP, S3, Saxon, Commons Net, AWS SDK | ja |
| `gretl-ilivalidator` | reale INTERLIS-Validierung (B) | ja |
| `gretl-cli` | CLI/Config/Hilfe/Orchestrierung | ja |
| `examples/file-size-extension` | separat gebautes Erweiterungs-JAR | ja |
| `gretl-dist` | Scriptdistribution/Assembly | ja |
| `gretl-geotools-probe` | GeoTools-Testprobe | nur Profil |

Zentrale Versionen: Groovy 6.0.0 (`groovy`, `groovy-json`), SLF4J 2.0.20,
Logback 1.6.5, Commons Compress 1.27.1, pgJDBC 42.7.14, H2 2.3.232 (nur Test),
ilivalidator 1.15.0 / ehibasics 1.4.1, Saxon 11.4, xmlresolver 4.4.3,
Commons Net 3.6, AWS SDK v2 2.15.64 (URL-Connection-Client), DuckDB 1.5.2.0,
iox-wkf 2.0.1-20250417.084407-2, iox-formats 1.0.3, ili2c 5.6.8, iox-ili 1.24.4,
Apache POI 5.2.5, Jackson 2.17.2,
Testcontainers 1.21.4 (nur `backing-services`), Floci 1.10.0 (nur Test),
JUnit 5.14.4, GeoTools 35.2 (nur Profil).

Zusätzliche Plugins: `maven-assembly-plugin` 3.7.1 für die Distribution,
`maven-failsafe-plugin` 3.6.0 für die Distributions-IT. Kein Groovy-
Produktcompiler, kein Shade-Plugin, keine globale Fachdependency in API/Runner.

## 10. Dependency-/Grenzprüfung

```sh
./mvnw -pl gretl-api dependency:tree -Dscope=compile
./mvnw -pl gretl-runner dependency:tree -Dscope=compile
./mvnw -pl gretl-cli dependency:tree
./mvnw -pl gretl-basic-tasks dependency:tree
./mvnw -pl gretl-core-tasks dependency:tree
jdeps --multi-release 25 --ignore-missing-deps -summary \
  gretl-runner/target/gretl-runner-0.1.0-SNAPSHOT.jar
jdeps --multi-release 25 --ignore-missing-deps -summary \
  gretl-basic-tasks/target/gretl-basic-tasks-0.1.0-SNAPSHOT.jar
```

Die Tests prüfen zusätzlich API-/Runner-Importgrenzen, das Fehlen von
Publisher-/XtfToGpkg-Symbolen, den standardmässigen Taskkatalog und das
Erweiterungs-JAR im isolierten Distributionslauf.

## 11. Bekannte Warnungen und Grenzen

- Testmodule ohne `src/main/java` erzeugen harmlose
  Compiler-Zustandswarnungen zu `target/classes`.
- Die Distributionsassembly kann den internen `gretl-dist`-Projektartefakt-
  Hinweis ausgeben; das ZIP enthält dennoch Launcher, Runtime, Tasks, Beispiele
  und Erweiterungsbereich.
- Das Profil `backing-services` benötigt Docker und lädt die gepinnten
  PostGIS-/FTP-Images; ohne Docker schlägt es bewusst fehl. Der Standard-`verify`
  bleibt Docker-frei.
- `inventory.json` und `SHA256SUMS` sind vorhanden. Die Prüfsummen sind keine
  Signatur; `sourceRevision` ist im Working-Tree-Build kein Commit. Der
  Report-Inventarhash bleibt ein Katalogfingerabdruck, kein Hash aller JARs.
- Heartbeat, Progress-Koaleszenz, taskgebundene MDC-Anreicherung und ein
  SIGTERM-/Flush-IT sind offene Logging-Arbeiten.
- Standardtests nutzen H2; der echte PostgreSQL-Lauf ist Teil des
  Docker-backed `backing-services`-Gates. Commit-Unbekanntheit nach
  Verbindungsverlust ist nicht fault-injiziert.
- DuckDB bringt native Bibliotheken mit. Das Job-JAR erlaubt deshalb einzelne
  Einträge bis 256 MiB; die JDBC-Nutzung erzeugt unter JDK 25 eine
  Native-Access-Warnung, die den Lauf nicht beeinflusst.
- M5 enthält keine EHI-spezifische Feldbrücke, keinen Massen-Sampling-Test und
  keine separate Validator-JVM.
- Der M6-Packager löst im ersten Profil keine Maven-Koordinaten auf, sondern
  bündelt den bereitgestellten, qualifizierten Runtime-Klassenpfad. Die
  Deskriptor-Dependencies werden gegen die enthaltenen Artefaktnamen geprüft.
  Das JAR enthält daher den vollständigen Standardkatalog statt einer minimalen
  Aufgabenauswahl.
- Das Job-JAR wird mit einem eigenen, getesteten Java-Merger statt mit einem
  Maven-Shade-Mojo gebaut; Service-, Groovy- und Lizenz-Merges sowie
  Klassen-/Ressourcenkollisionen sind implementiert. Publisher, XtfToGpkg und
  Native Image sind nicht Bestandteil von M6.

## 12. M5-Abnahme und M6-Grenzen

Die kurze Anwenderabnahme wurde mit P1/P2/P3 durchgeführt; alle vier TC-U-Fälle
sind bestanden, einschliesslich der Re-Tests für TC-U02 und TC-U03. Das
Protokoll steht in [`m5-user-acceptance.md`](m5-user-acceptance.md). Der
M6-Packager ist die optionale Lieferstufe C; Publisher, XtfToGpkg und Native
Image bleiben spätere, getrennte Aufträge. Die M5- und M6-Berichte halten die
jeweiligen Grenzen fest.

## 13. Snapshot-Veröffentlichung

Die produktiven Maven-Artefakte und die Scriptdistribution werden unter
`0.1.0-SNAPSHOT` nach `https://jars.interlis.guru/snapshots` veröffentlicht.
Die vollständige Anleitung steht in
[`publish-snapshots.md`](publish-snapshots.md).

Der GitHub-Actions-Workflow `.github/workflows/publish-snapshots.yml` heisst
**publish artefacts** und läuft bei jedem Push sowie manuell über
`workflow_dispatch`. Er führt zuerst
`./mvnw -B -Pgeotools-probe clean verify` aus und deployt anschliessend mit
`-DskipTests -DskipITs`; die Repository-Secrets `MAVEN_USERNAME` und
`MAVEN_PASSWORD` werden nur im Deploy-Schritt injiziert.

Lokal:

```sh
export MAVEN_USERNAME='...'
export MAVEN_PASSWORD='...'
./scripts/publish-snapshots.sh
```

Das Skript nutzt `docs/implementation/publish-settings.xml` mit
Environment-Platzhaltern und das Maven-Profil `publish-snapshots`. Die
Distribution wird als `gretl-dist` mit den Classifiers `bin` (ZIP) und
`bin-tar-gz` (TAR.GZ) angehängt.

## Coverage und Artefaktjobs (Verbesserungsplan Phase 1)

JaCoCo 0.8.14 ist für Java-25-Bytecode gepinnt ([offizieller Versionsnachweis](https://www.jacoco.org/jacoco/trunk/doc/changes.html)). Das Plugin instrumentiert die
Surefire-/Failsafe-JVMs und erzeugt bei `verify` Line-/Branch-Berichte unter
`<modul>/target/site/jacoco/`. Das testseitige Integrationsmodul aggregiert die
verfügbaren Reactorbeiträge unter `gretl-integration-tests/target/site/jacoco-aggregate/`.
Die Compile-Abhängigkeiten dieses ausschliesslichen Testmoduls erlauben dabei
die Zuordnung der Produktklassen und Quellen; sie ändern keine Produktabhängigkeit.

Separat gestartete Launcher-, Packager- und `java -jar`-Prozesse sind bewusst
**nicht instrumentiert**. Ihre fachlichen Assertions sind Ausführungsnachweise,
aber kein gemessener Anteil der JaCoCo-Abdeckung. Testzahlen und Coverage werden
getrennt ausgewiesen; es gibt keine globale Prozenthürde.

Das Integrationsmodul ist einmal im normalen Reactor enthalten und hängt für die
Buildreihenfolge explizit von `gretl-dist` ab. Standardmässig führt Failsafe dort
`*JobIT` aus (derzeit die lokalen `*LocalJobIT`-Klassen); `backing-services` erweitert die Auswahl auf sämtliche `*IT`.
Damit bleiben lokale Artefaktjobs Docker-frei und Diensttests ausdrücklich opt-in.
Die beiden vollständigen Prüfwege bleiben:

```sh
./mvnw -B clean verify
./mvnw -B -Pgeotools-probe,backing-services clean verify
```

Die JAR-Szenarien liegen unter `gretl-integration-tests/src/test/resources/jobs/`.
`ArtifactJobRunner` kopiert jedes Szenario frisch, ruft den regulären Packager der
gebauten Distribution auf und startet `java -jar` ohne Testclasspath. Prozessdaten,
SHA-256, Report, Logs und Outputs bleiben unter `target/job-evidence/` erhalten.
Vor einem erneuten `clean` müssen benötigte Nachweise gesichert werden.
