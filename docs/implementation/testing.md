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
| PostGIS | `Db2Db`-Geometrietest mit PostGIS-Funktionen | Container ist vorbereitet; echte PostGIS-Tasktests folgen mit `Ili2pg*`/Geodaten |
| FTP | `MockFtpServer` in `FtpOperationTest` | `FtpTaskFamilyIT` mit `delfer/alpine-ftp-server` |
| HTTP/Curl | JDK-`HttpServer` | derzeit kein Container nötig; Host-Tests decken den Netzwerkpfad ab |
| S3 | keine Unit-Server; Builder-/Pfadtests | `S3TaskFamilyIT` mit Floci/Testcontainers |
| XSLT/Gzip | lokale Temp-Dateien | kein Backing-Service |
| INTERLIS-Validierung | lokale Modelle/XTF | kein Backing-Service |
| DuckDB | `DuckDbSqlExecutorOperationTest` und CLI-Test, Datei/In-Memory | kein Docker; Föderation/Exporte noch nicht migriert |

Der reale PostgreSQL-Nachweis läuft ausschließlich über `PostgresSqlExecutorIT`
und `Db2DbPostgresIT` im Profil `backing-services`.

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
**361 Tests** (349 Standard + 4 GeoTools + 8 Backing-Services).

## Grenzen

- Der Standardbuild testet weiterhin keine echte PostgreSQL-/PostGIS-Dialektik;
  dafür ist das Profil zuständig.
- PostGIS-Tasktests existieren erst mit den Geodaten-/Ili2pg-Wellen; der
  PostGIS-Container steht bereits bereit.
- `DuckDbSqlExecutor` deckt den SQL-Kern ab. Föderierte Quellen/Ziele und
  Exporte aus gretl-next sind noch nicht Bestandteil der Runtime-Migration.
- HTTP-Container werden erst benötigt, wenn Jobs aus einem Runtime-Image gegen
  den Fixture-Host laufen; die aktuellen Host-Tests nutzen den JDK-`HttpServer`.
- Die Integrationstests laufen sequenziell (`forkCount=1`), damit geteilte
  Container und feste FTP-Passive-Ports stabil bleiben.
- Testcontainers- und Image-Versionen werden zentral gepflegt; Image-Digests
  sind im Code sichtbar und dürfen nicht stillschweigend auf `latest`
  zurückfallen (Ausnahme: dokumentiertes Floci-Contract).
