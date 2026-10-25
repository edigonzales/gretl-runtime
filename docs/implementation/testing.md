# Teststrategie und Backing-Services

Diese Seite beschreibt, wie der GRETL-Runtime-Build Backing-Services testet.
Sie ergänzt [`build.md`](build.md) und die Migrationsmatrix
[`../migration/core-tasks.md`](../migration/core-tasks.md).

## Zwei Testebenen

Der Standardbuild bleibt bewusst Docker-frei. Echte Backing-Services laufen in
einem separaten, explizit aktivierten Profil.

| Ebene | Docker | Werkzeuge | Befehl |
|---|---|---|---|
| Unit und Komponente | nein | H2, JDK-`HttpServer`, `MockFtpServer`, lokale Dateien | `./mvnw verify` |
| Backing-Service-Integration | ja | Testcontainers, PostgreSQL/PostGIS, FTP, S3/Floci | `./mvnw -Pbacking-services verify` |
| GeoTools-Probe | nein | separates Profil | `./mvnw -Pgeotools-probe verify` |

`./mvnw verify` bleibt der schnelle Entwickler- und Standard-CI-Build.
`backing-services` ist kein Ersatz für die Unit-Tests, sondern die
Contract-/End-to-End-Ebene für Dienste, die sich nicht glaubwürdig durch H2
oder einen In-Process-Mock abbilden lassen.

## Aktuelle Abdeckung

| Service | Standardbuild | Backing-Service-Gate |
|---|---|---|
| PostgreSQL | H2 in `SqlExecutorOperationTest` und `Db2DbOperationTest` | `PostgresSqlExecutorIT` und `Db2DbPostgresIT` mit echtem PostgreSQL 16 + PostGIS |
| PostGIS | `Db2Db`-Geometrietest mit PostGIS-Funktionen | `FormatPostgresIT` für Shapefile/GPKG/CSV/JSON |
| FTP | `MockFtpServer` in `FtpOperationTest` | `FtpTaskFamilyIT` mit `delfer/alpine-ftp-server` |
| HTTP/Curl | JDK-`HttpServer` | derzeit kein Container nötig; Host-Tests decken den Netzwerkpfad ab |
| S3 | keine Unit-Server; Builder-/Pfadtests | `S3TaskFamilyIT` mit Floci/Testcontainers |
| XSLT/Gzip | lokale Temp-Dateien | kein Backing-Service |
| INTERLIS-Validierung | `IliValidatorOperationTest`, `InterlisValidatorTest` mit Modellen/CSV/JSON/GPKG/SHP | kein Backing-Service |
| DuckDB | `DuckDbSqlExecutorOperationTest` und CLI-Test, Datei/In-Memory, CSV/GPKG/Parquet/XLSX | `DuckDbFederationPostgresIT` mit PostgreSQL und DuckDB-Extensions |
| CSV/JSON/Excel | `InterlisTaskProviderTest`, `NativeShapefileTest`, `Gpkg2ShpEngineTest`, `InterlisValidatorTest` | `FormatPostgresIT` (CSV, JSON, GPKG, SHP) |
| Geo-Dateien | nativer Shapefile-/DBF-Code in `NativeShapefileTest` | `FormatPostgresIT` (SHP/GPKG Import/Export) |
| ili2db/AV/DXF | `Ili2DbFileTest` (DuckDB/GPKG), `AvOperationsTest` (Av2ch/Av2geobau/Gpkg2Dxf), Options-/Inventar-Tests | `FormatPostgresIT` (Ili2pg-Lebenszyklus) |

Der reale PostgreSQL-Nachweis läuft über `PostgresSqlExecutorIT`,
`Db2DbPostgresIT`, `DuckDbFederationPostgresIT`, `FormatPostgresIT` und
`PackagedSqlJobPostgresIT` im Profil `backing-services`.


### Zusätzliche Docker-backed Szenarien

Die 28 Tests in `gretl-integration-tests` sind bewusst breiter als reine
Happy-Path-Aufrufe:

- S3: verschachtelte und Unicode-Schlüssel, Überschreibschutz ohne
  Temp-Reste, Bucket-Kopie mit Sonderzeichen/Metadaten, 8-MiB-Objekt.
- FTP: Löschen aller Dateien und leeres Verzeichnis, binäre Bytes,
  Überschreibschutz.
- Db2Db: getrennte Quell-/Zielverbindungen und Rollback über mehrere
  Parameter-Sets.
- PostgreSQL: Script/Prepared/Rollback und echte PostGIS-Geometrie.
- DuckDB: PostgreSQL-Quelle/-Ziel, `append`/`truncate`/`replace`.
- CSV/JSON/GPKG/SHP: Import/Export und INTERLIS-Validierung gegen PostgreSQL/PostGIS.
- Packaging: gebautes Job-JAR läuft als eigener Prozess gegen PostgreSQL.
  Der Test setzt den Distributionsbuild voraus; im `backing-services`-Reaktor
  wird `gretl-dist` vorher gebaut.

Die 10 Failsafe-Tests in `gretl-dist` kommen separat hinzu; sie prüfen
Distribution, Launcher, Job-JAR-Paketierung und lokale Integrationen ohne
Docker. Die Bezeichnung „28 Integrationstests“ meint daher ausschließlich das
Profil `backing-services`.


## Integrationstests ausführen

```sh
# Standard: Docker-frei
./mvnw verify

# Backing-Services: Docker muss laufen
./mvnw -Pbacking-services verify

# Nur die Integrationstests des Moduls (mit Reaktorabhängigkeiten)
./mvnw -Pbacking-services -pl gretl-integration-tests -am verify
```

Das Profil `backing-services` bindet das test-only Modul
`gretl-integration-tests` in den Reactor ein. Es startet die Container, führt
die Failsafe-Tests (`*IT`) aus und beendet die Container danach. Fehlt Docker,
schlägt das Profil mit einem klaren Fehler fehl; es gibt kein stilles Skip.

## Fixtures und Isolation

### PostgreSQL/PostGIS

- Image: `postgis/postgis:16-3.4` mit gepinntem Digest.
- Eine Containerinstanz pro Test-JVM; pro Test ein eigenes Schema
  `gretl_<token>` und `currentSchema=<schema>,public`.
- Das Schema wird im `finally`/`AfterEach` mit `DROP SCHEMA ... CASCADE`
  entfernt.
- Die Erweiterung `postgis` wird beim Start einmalig installiert. Damit steht
  derselbe Container später auch für `Ili2pg*` und Geodaten-Tasks bereit.

### FTP

- Image: `docker.io/delfer/alpine-ftp-server` mit gepinntem Digest.
- Dynamischer Control-Port und fester Passive-Port pro Testlauf, damit
  Passive-Mode-Datenverbindungen deterministisch sind.
- Der Image-Entrypoint initialisiert den Benutzer und startet vsftpd mit
  `background=NO` direkt unter dem Initprozess. So bleibt die Lebensdauer
  an den Servermaster gebunden. Nach abgewiesenem Login prüft der Test
  einen gültigen Zugriff auf denselben Server.
- Pro Test ein Lease-Verzeichnis unter `/home/gretl/<token>`; Cleanup löscht
  Dateien und Verzeichnis rekursiv.
- `MockFtpServer` bleibt für schnelle Protokoll-/Fehlerfälle im Standardbuild.

### S3

- Floci `io.floci:testcontainers-floci:1.10.0` mit `floci/floci:latest`.
- Source-/Target-Buckets pro Lease; Cleanup aller Objekte und Buckets.
- Der Test prüft Upload/Download/Delete/Bucket2Bucket, ACL, Content-Type,
  Metadaten und Secret-Redaction über echte Groovy-Jobs.
- Die fehlende Digest-Override-API von Floci 1.10.0 wird als Contract
  dokumentiert; kein falsches „digest-gepinnt“-Versprechen.

## Ausführungspfad

Die Integrationstests starten echte Groovy-Jobs über die öffentliche CLI
(`GretlCli`) mit einer generierten `runtime.json`. Damit werden nicht nur
Handler, sondern auch DSL, Konfiguration, Secret-Auflösung, Arbeitsverzeichnis,
Reporting und Exit-Code geprüft.

- Secrets werden als Datei-Secretquellen in das temporäre Testverzeichnis
  geschrieben und über `secret('alias')` gebunden.
- Die Tests prüfen, dass Passwörter nicht in stdout, stderr oder Report
  erscheinen.
- Die Assertions laufen über unabhängige Clients (JDBC bzw. FTP-Client), nicht
  über denselben Code wie der Task.

## CI

Der Publish-Workflow führt beide Ebenen aus:

```sh
./mvnw -B -Pgeotools-probe,backing-services clean verify
```

Damit ist der Docker-backed Gate vor der Snapshot-Publikation verpflichtend.
Der schnelle lokale Build bleibt davon unberührt. Kombiniert sind das
**416 Tests** (384 Standard + 4 GeoTools + 28 Backing-Services).

## Grenzen

- Der Standardbuild testet weiterhin keine echte PostgreSQL-/PostGIS-Dialektik;
  dafür ist das Profil zuständig.
- Der Enforcer-Guard `ban-geotools` und der ZIP-Test in `DistributionIT`
  sichern die GeoTools-Abgrenzung; das Testprofil `geotools-probe` ist die
  einzige Ausnahme.
- Der ili2pg-Lebenszyklus (Schema, Import, Export, Replace, Update, Validate,
  Delete) läuft gegen den echten PostgreSQL/PostGIS-Container.
- `DuckDbSqlExecutor` deckt den SQL-Kern und die Föderation ab; ein
  PostGIS-Geometrie-Mapping für PostgreSQL-Quellen ist noch nicht separat
  getestet.
- `CsvValidator`, `JsonValidator`, `GpkgValidator` und `ShpValidator` nutzen
  ilivalidator und IOX-Reader gegen INTERLIS-Modelle. Die Testfixtures liegen
  unter `gretl-interlis-tasks/src/test/resources`.
- Shapefile-I/O nutzt den nativen gretl-next-Code; die Hop-Regeln für
  DBF-NULLs und Encoding sind in `ShapefileIoxReader`/`DbfEncoding` umgesetzt
  und durch `NativeShapefileTest`/`InterlisValidatorTest` abgedeckt.
- 2D-Multipart, Polygon-Löcher, MultiPoint und MultiLine sind in
  `NativeShapefileTest` und `FormatPostgresIT` getestet. Z/M-Shapefile-
  Geometrien und Z/M-GeoPackage-Importe werden explizit abgelehnt.
- Welle 7 deckt den ili2pg-Lebenszyklus, ili2gpkg-/ili2duckdb-Importe,
  Av2ch, Av2geobau und Gpkg2Dxf ab. Die ili2db-Optionsfläche ist als
  DSL-Feldfläche mit Defaults abgebildet; der Packager-Test sichert die
  bevorzugte iox-ili-Modellklasse bei av2geobau.
- HTTP-Container werden erst benötigt, wenn Jobs aus einem Runtime-Image gegen
  den Fixture-Host laufen; die aktuellen Host-Tests nutzen den JDK-`HttpServer`.
- Die Integrationstests laufen sequenziell (`forkCount=1`), damit geteilte
  Container und feste FTP-Passive-Ports stabil bleiben.
- Testcontainers- und Image-Versionen werden zentral gepflegt; Image-Digests
  sind im Code sichtbar und dürfen nicht stillschweigend auf `latest`
  zurückfallen (Ausnahme: dokumentiertes Floci-Contract).

## Folgeplan: neue Artefaktgrenze und Messgrenzen

Der Folgeplan unter `improvement-plan/` erweitert die oben beschriebene
historische CLI-Komponentenprüfung um echte `java -jar`-Szenarien.
`ArtifactJobRunner` paketiert kanonische Jobs über den aktuellen Distributions-
Packager und startet sie in separaten Prozessen. `ValidatorLocalJobIT` und
`CancellationLocalJobIT` laufen im normalen Reactor; `DatabaseArtifactIT` prüft
zusätzlich eine PostgreSQL-Taskkette, Abbruch/Rollback und die ausdrücklich
nicht unterstützte direkte PostGIS-Geometrieübernahme durch DuckDB.

Die vorhandenen In-Process-Prüfungen bleiben Komponentenprüfungen und werden
nicht als JAR-Artefaktnachweise gezählt. Die Originalzuordnung mit Quellcommit,
Methoden, Assertionstexten und Fixture-Prüfsummen steht in
[improvement-plan/original-scenarios.md](improvement-plan/original-scenarios.md).
Der vollständige fachliche Port und die 44er-Abdeckung sind Phase-2-Gates;
Inventarisierung allein gilt nicht als Migrationserfolg.

JaCoCo misst Test-JVMs. Nicht instrumentierte Job-/Launcherprozesse stehen
separat als fachliche Ausführungsnachweise im Bericht. Eine Testanzahl ist
kein Coverage-Prozentsatz. Details und Pfade: [build.md](build.md).
