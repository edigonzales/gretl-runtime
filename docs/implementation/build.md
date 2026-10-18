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
   Kollisionsprüfung, 4 Tests,
7. **gretl-basic-tasks** — HTTP, ZIP/GZIP, SQL, Provider, 23 Tests,
8. **gretl-cli** — Hilfe, Plan, Parameter, Secrets, Pfade, Timeout, Reports,
   Distribution-nahe Integration, 44 Tests,
9. **gretl-ilivalidator** — reale lokale INTERLIS-Validierung, 3 Tests,
10. **gretl-job-runtime** — JAR-Einstieg, Ressourcenmaterialisierung,
11. **examples/file-size-extension** — separat gebautes Erweiterungs-JAR,
12. **gretl-dist** — ZIP/TAR.GZ, Launcher, Inventar/Prüfsummen,
    Extension-/HTTP-/IliValidator-/Token-/Action-Integration sowie Job-JAR-Paket
    und -Start, 1 Surefire- und 7 Failsafe-Tests.

Erwartet: **285 Tests**, 0 Fehler, `BUILD SUCCESS`.

### Artefakte

| Artefakt | Ort |
|---|---|
| API-JAR | `gretl-api/target/gretl-api-0.1.0-SNAPSHOT.jar` |
| Runner-JAR | `gretl-runner/target/gretl-runner-0.1.0-SNAPSHOT.jar` |
| Groovy-Adapter | `gretl-groovy/target/gretl-groovy-0.1.0-SNAPSHOT.jar` |
| Logging | `gretl-logging/target/gretl-logging-0.1.0-SNAPSHOT.jar` |
| Lokalisierung | `gretl-i18n/target/gretl-i18n-0.1.0-SNAPSHOT.jar` |
| Standard-Tasks | `gretl-basic-tasks/target/gretl-basic-tasks-0.1.0-SNAPSHOT.jar` |
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
lib/tasks/       gretl-basic-tasks, gretl-ilivalidator
lib/extensions/  eigene Task-JARs und kompatible Dependencies
examples/        Download/Extract, SQL, IliValidator, Token/Katalog, Action/NO_DATA
inventory.json   Module, Tasks, Artefakte, Dependencyversionen
SHA256SUMS       SHA-256 aller Distributionsdateien mit relativen Pfaden
```

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

## 7. Optionaler Live-PostgreSQL-Nachweis

```sh
docker run --rm -d --name gretl-pg \
  -e POSTGRES_PASSWORD=gretl -e POSTGRES_USER=gretl -e POSTGRES_DB=gretl \
  -p 55432:5432 postgres:17-alpine

./mvnw -pl gretl-basic-tasks -am test \
  -Dtest=PostgresLiveIT \
  -Dgretl.postgres.url=jdbc:postgresql://127.0.0.1:55432/gretl \
  -Dgretl.postgres.user=gretl \
  -Dgretl.postgres.password=gretl

docker stop gretl-pg
```

Der Lauf prüft mehrere Statements in einem Script, Semikolon im String,
Dollar-Quote, Prepared-Apostroph/null und Rollback nach dem zweiten Fehler.
`PostgresLiveIT` ist kein Bestandteil des Standard-`verify`.

## 8. Profil `geotools-probe`

```sh
./mvnw -Pgeotools-probe verify
```

Erwartet: **289 Tests** (285 Standard + 4 GeoTools). Die gezielte Probe
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
| `gretl-basic-tasks` | HTTP/Extract/SQL | ja |
| `gretl-ilivalidator` | reale INTERLIS-Validierung (B) | ja |
| `gretl-cli` | CLI/Config/Hilfe/Orchestrierung | ja |
| `examples/file-size-extension` | separat gebautes Erweiterungs-JAR | ja |
| `gretl-dist` | Scriptdistribution/Assembly | ja |
| `gretl-geotools-probe` | GeoTools-Testprobe | nur Profil |

Zentrale Versionen: Groovy 6.0.0 (`groovy`, `groovy-json`), SLF4J 2.0.20,
Logback 1.6.5, Commons Compress 1.27.1, pgJDBC 42.7.14, H2 2.3.232 (nur Test),
ilivalidator 1.15.0 / ehibasics 1.4.1, JUnit 5.14.4, GeoTools 35.2 (nur Profil).

Zusätzliche Plugins: `maven-assembly-plugin` 3.7.1 für die Distribution,
`maven-failsafe-plugin` 3.6.0 für die Distributions-IT. Kein Groovy-
Produktcompiler, kein Shade-Plugin, keine globale Fachdependency in API/Runner.

## 10. Dependency-/Grenzprüfung

```sh
./mvnw -pl gretl-api dependency:tree -Dscope=compile
./mvnw -pl gretl-runner dependency:tree -Dscope=compile
./mvnw -pl gretl-cli dependency:tree
./mvnw -pl gretl-basic-tasks dependency:tree
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
- `inventory.json` und `SHA256SUMS` sind vorhanden. Die Prüfsummen sind keine
  Signatur; `sourceRevision` ist im Working-Tree-Build kein Commit. Der
  Report-Inventarhash bleibt ein Katalogfingerabdruck, kein Hash aller JARs.
- Heartbeat, Progress-Koaleszenz, taskgebundene MDC-Anreicherung und ein
  SIGTERM-/Flush-IT sind offene Logging-Arbeiten.
- PostgreSQL-Live ist optional; Standardtests nutzen H2. Commit-Unbekanntheit
  nach Verbindungsverlust ist nicht fault-injiziert.
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
