# M3-Prüfbericht: CLI, allgemeine Tasks und Stufe A

> Historischer Meilensteinbericht: Aussagen und Modulnamen gelten für den hier
> dokumentierten Commit. Aktueller Zuschnitt: [Modulgrenzen](module-boundaries.md).

> **Historischer Bericht.** Dieser Bericht dokumentiert den Stand von M3
> (`0.1.0-M3`, 263 Standard-/267 Profil-Tests). M4 ergänzt die reale
> `IliValidator`-Integration; aktuell ist der [M4-Bericht](m4-report.md).

**Stand:** 18. Oktober 2026  
**Basis:** Spezifikation 4.1, Kapitel 04, 06, 07, 09 und 16  
**Projektversion:** `0.1.0-M3`  
**Status:** technischer A-Kandidat umgesetzt; Stufe B/C und die öffentliche
Anwenderfreigabe nach M5 sind nicht begonnen.

## 1. Auftrag und Abgrenzung

M3 sollte aus dem M2-Groovy-Adapter einen startfertigen A-Kandidaten machen:
CLI, Runtimekonfiguration, Parameter- und Secretquellen, Workspace und
Runbericht, eine verwaltete Loggingpipeline, die Tasks `HttpDownload`,
`Extract` und `SqlExecutor` sowie eine Scriptdistribution mit Erweiterungsbereich
und Beispielen.

Nicht Gegenstand waren `IliValidator` (B), Publisher, XtfToGpkg, der optionale
Job-Fat-JAR-Packager (C), Native Image und eine OpenTelemetry-Backend-
instrumentierung. Diese Grenzen bleiben unverändert.

## 2. Lieferumfang

Neu sind vier Produktmodule:

| Modul | Rolle | Quellen/Klassen |
|---|---|---|
| `gretl-logging` | JSON-Ereignisse, Redaction, Text-/JSON-Konsolen, JSONL-Datei, Runbericht, Logback-Bridge | 16/16 |
| `gretl-basic-tasks` | `HttpDownload`, `Extract`, `SqlExecutor` mit Provider | 12/12 |
| `gretl-cli` | Argumente, Runtimekonfiguration, Hilfe, Parameter, Secrets, Pfade, Runorchestrierung | 9/9 |
| `gretl-dist` | Launcher, Inventar, Beispiele, Assembly und Distributions-IT | 0/0 plus Test |

Die Projektversion ist `0.1.0-M3`. `gretl-api` bleibt JDK-only;
`gretl-runner` bleibt frei von Groovy, Logging-Backend und Fachbibliotheken.

## 3. CLI und Runtimekonfiguration

### 3.1 Befehle

- `gretl --help` und `gretl --version` antworten vor Job-, Provider- und
  Groovy-Laden.
- `gretl operations` liest nur Providerdefinitionen und zeigt Symbol, ID,
  Beschreibung und Herkunft.
- `gretl operations <Symbol>` zeigt Eingabefelder, Typen, Pflichtstatus,
  Defaults, erlaubte Werte, Ergebnisfelder und Beispiel.
- `gretl run job.groovy --help` lädt die Definition, zeigt Parameter, Typen,
  Defaults, Ziele und Tasks, führt aber keine Taskclosure und keine
  Parameterwertauflösung aus.
- `gretl check` prüft Definition und ausgewählte Ziele; fehlende
  Pflichtparameter bleiben offen.
- `gretl plan` zeigt Ziele und ausgewählte Taskreihenfolge.
- `gretl run` führt den Job aus und schreibt Ereignisdatei und Runbericht.
- `gretl package` ist bewusst nicht implementiert (Lieferstufe C) und endet
  mit Code 2.

### 3.2 Optionen

`--target`, `--config`, `--params-file`, `--param`, `--work-dir`, `--report`,
`--timeout`, `--log-format`, `--log-level` und `--keep-work` sind implementiert.
Unbekannte Optionen, fehlende Werte, doppelte `--param`-Namen und nicht
anwendbare Optionen für `check`/`plan` scheitern vor Taskarbeit mit Code 2.
`--param` trennt am ersten `=`; `name=` ist ein ausdrücklich leerer Text.

### 3.3 Runtimekonfiguration

`RuntimeConfig` liest striktes UTF-8-JSON mit doppelten Schlüsseln als Fehler.
Unterstützt werden `schemaVersion`, `runtime`, `logging`, `params`, `secrets`
und `taskConfig`. Unbekannte Corefelder, falsche Typen, ungültige Intervalle,
`required` plus `defaultValue` auf Parameterebene und widersprüchliche
Secretquellen scheitern mit Code 2.

Pfadbasen:

- Jobdatei und allgemeine CLI-Dateien: ursprüngliches Start-CWD,
- `workRoot`, `reportRoot` und Secretdatei: Ordner der Runtimekonfiguration,
- `file(relative)`: Jobroot,
- `ctx.path(relative)`: Start-CWD,
- `workPath(relative)`: privater Task-/Invocation-Workspace,
- deklarierter `path`-Parameter: Start-CWD.

Ohne `--config` gelten die OPS-013-Defaults: `gretl-work` unter
`java.io.tmpdir`, `gretl-reports` unter dem Start-CWD, `keepWork=on-failure`,
Text/INFO, Ereignisdatei aktiv, kein Timeout. Der Abschlussbericht heisst
`<runId>.json`, die Ereignisdatei `<runId>.events.jsonl`.

### 3.4 Secrets

Environment- und Dateiquellen sind implementiert, aber nie gleichzeitig pro
Alias. Dateien werden als UTF-8 mit maximal 64 KiB und ohne NUL gelesen; ein
abschliessendes LF/CRLF wird entfernt. Fehlende Quellen nennen Alias und Quelle,
aber keinen Wert. `ctx.secret(alias).reveal()` und `ctx.sensitive(value)`
registrieren Werte im rungebundenen `SecretRegistry`; dieselbe Registry wird
vom Logger/Report-Redactor verwendet.

## 4. Logging und Runbericht

### 4.1 Kanäle

- Textkonsolenformat mit Zeitstempel, Level, Eventname, Task-/Invocation-
  Identität, Nachricht, Phase, Outcome, Dauer und begrenztem Fehlergraph.
- JSON-Konsolenformat mit genau einem vollständigen JSON-Objekt pro physischer
  Zeile, inklusive `schema_version`, `timestamp`, `sequence`, `event_id`,
  `command_id`, `resource`, `attributes`, `source` und `error`/`error_ref`.
- Begrenzte JSONL-Ereignisdatei mit 0600-Rechten, 16-KiB-Default pro Event,
  128-MiB-Datei und 256-KiB-Terminalreserve. Bei Erreichen der Grenze werden
  normale Ereignisse gestoppt, `gretl.logging.truncated` geschrieben und
  Terminalereignisse weiterhin versucht.
- Abschliessender JSON-Runbericht mit `identity`, `source`, `execution`,
  `errors`, `diagnostics`, `logging` und `telemetry.mode=none`.
- Atomarer Berichtsersatz über eine temporäre Datei; 0600-Rechte.
- Optionales `GRETL_TERMINATION_MESSAGE_PATH` mit höchstens 3 KiB sicherer
  Kurzmeldung.

### 4.2 Redaction

Der gemeinsame `Redactor` maskiert alle registrierten Secret-/Sensitive-Werte
vor Text-/JSON-Encoding und vor dem Runbericht. Sensible Feldnamen wie
`password`, `authorization`, `cookie`, `token`, `credential` und `secret`
werden defensiv maskiert. HTTP-Adapter loggen nur Methode, Origin, Status,
Bytes und Phase, keine Query, Header, Cookies oder Body. SQL-Adapter loggen
Alias, Scriptname, Eintrag, Phase, SQLSTATE/Vendorcode und Hash, aber keine
SQL-Text-/Parameterwerte.

### 4.3 Bibliotheksbrücke

`LogbackBridge` installiert genau einen Logback-Provider und leitet
Bibliothekslogs als `gretl.library.log` in die gemeinsame Pipeline. Die
JUL→SLF4J-Brücke ist installiert. `ProviderInventory` prüft genau einen
SLF4J-Provider und den Logback-Kontext vor dem Lauf. GRETL-Lifecycle- und
Tasklogs hängen nicht vom Logback-Provider ab.

## 5. Allgemeine Tasks

### 5.1 HttpDownload

Implementiert: URI-Konvertierung und Schema-Prüfung, GET/POST, Header mit Text
oder `SecretValue`, UTF-8-Body nur für POST, `maxBytes=1 GiB`, deaktivierte
Redirects, kein automatischer Retry, temporäre Datei und atomare
Zielveröffentlichung, kein Überschreiben, SHA-256, Status-/Byte-/Dauerprüfung
und sichere Fehlerdiagnose.

Nicht implementiert: automatische Redirect-Weitergabe, automatische
Wiederaufnahme, TLS-Sonderkonfiguration und eine generische Proxy-API.

### 5.2 Extract

Implementiert: ZIP/GZIP mit explizitem Modus, `maxExpandedBytes=4 GiB`,
`maxEntries=10'000`, Traversal-/Absolutpfad-/Backslash-Schutz,
Symlink-Erkennung, Kollisionsprüfung (auch case-insensitiv), keine
Überschreibung, sortierte ZIP-Dateiliste und korrekte `file`/`files`/
`directory`/`bytes`-Ergebnisse.

### 5.3 SqlExecutor

Implementiert: PostgreSQL-Alias aus `taskConfig`, vollständige UTF-8-Scripts
ohne Regex-Splitting, PreparedStatements mit den dokumentierten Binder-Typen,
Transaktion mit einmaligem Commit bzw. Rollback, ResultSet-Verarbeitung,
`SqlResult` mit Updatezahlen und sichere SQLSTATE-/Vendorcode-Diagnosen.

Ein echter PostgreSQL-17-Lauf wurde mit lokalem Container durchgeführt:
mehrere Statements in einem Script, Semikolon im String, Dollar-Quote,
Apostroph/null als PreparedStatement sowie Rollback nach dem zweiten Fehler
bestanden. Der Standardbuild verwendet H2 als lokale Testdatenbank; der
PostgreSQL-Lauf ist als optionaler `PostgresLiveIT` dokumentiert.

> **Hinweis:** Der hier dokumentierte `PostgresLiveIT` wurde später entfernt.
> Aktueller Nachweis ist `PostgresSqlExecutorIT` im `backing-services`-Profil.

## 6. Distribution, Erweiterungen und Beispiele

`gretl-dist` erzeugt ein ZIP/TAR.GZ mit:

- `bin/gretl` (Shelllauncher mit `exec java` und gemeinsamem Klassenpfad),
- `lib/runtime` (CLI, Groovy, Logging, Runner, API, Logback, Groovy-JSON),
- `lib/tasks` (Standard-Task-JAR),
- `lib/extensions` (dokumentierter Erweiterungsbereich),
- `inventory.json`, `README.md` und ausführbaren Beispielen.

Die Distributions-IT entpackt das erzeugte ZIP, startet `--version`,
`operations`, lädt ein separat gebautes FileSize-JAR aus `lib/extensions` und
führt einen echten HTTP-Download-/Extract-Job aus. Der Erweiterungsbereich
funktioniert ohne Runnerneubuild.

Beispiele:

- `examples/download-extract` (HTTP + ZIP + Parameter + `result`),
- `examples/sql` (PostgreSQL-Script),
- `examples/token-catalog` (Action, `ctx.run`, Token, `JsonSlurper`, Katalog,
  späte Download-URL, `ctx.sensitive`, HTTP + Extract).

## 7. Ausgeführte Nachweise

```sh
./mvnw -o clean verify
./mvnw -o -Pgeotools-probe verify
./mvnw -o -pl gretl-dist -am verify
./mvnw -Pgeotools-probe verify -Dmaven.repo.local=/tmp/m3-fresh-final2
./mvnw -o -pl gretl-basic-tasks -am test \
  -Dtest=PostgresLiveIT \
  -Dgretl.postgres.url=jdbc:postgresql://127.0.0.1:55432/gretl \
  -Dgretl.postgres.user=gretl \
  -Dgretl.postgres.password=gretl
```

| Prüfung | Ergebnis |
|---|---|
| Standard-`verify` | **263 Tests**, 0 Fehler, `BUILD SUCCESS` |
| GeoTools-Profil | **267 Tests**, `BUILD SUCCESS` |
| Distributions-IT | 2 Tests, Launcher/Extension/HTTP/Extract |
| Frisches lokales Maven-Repository | **267 Tests**, `BUILD SUCCESS` |
| Live PostgreSQL 17 | 2 Tests, Script/Dollar-Quote/Prepared/Rollback |
| M2-Groovy | 49 Tests |
| Logging | 12 Tests |
| Basic Tasks | 20 Tests |
| CLI | 32 Tests |

Standard-Testverteilung:

| Modul | Tests |
|---|---:|
| `gretl-api` | 17 |
| `gretl-runner` | 115 |
| `gretl-groovy` | 49 |
| `gretl-logging` | 12 |
| `gretl-basic-tasks` | 20 |
| `gretl-cli` | 32 |
| `gretl-m0-qualification` | 16 |
| `gretl-dist` | 2 |
| **gesamt** | **263** |

## 8. Abdeckung der A-Fälle

### 8.1 Allgemeine Runtime

TC-R01 bis TC-R10 sind aus M1 vorhanden; `check`/`plan`-Top-Level-Markierung
wurde in M3 ergänzt. TC-D01 bis TC-D17 sind aus M2 vorhanden; TC-D01
(Token/Katalog/Download) ist jetzt als echter HTTP-/Groovy-Job ausgeführt.
TC-E01 bis TC-E15 sind aus M1 vorhanden; M3 ergänzt Timeout/Work-Retention.

### 8.2 Fachoperationen

| Fall | Status | Nachweis |
|---|---|---|
| TC-O01 HTTP GET/POST | **bestanden** | lokaler Fixture-Server, Header/Body/Status/Datei |
| TC-O02 HTTP-Fehler/Limit/Redirect | **bestanden** | 500, `maxBytes`, Redirect, kein Retry |
| TC-O03 ZIP/GZIP | **bestanden** | bekannte Inhalte, Streamabschluss |
| TC-O04 Traversal/Symlink/Kollision/Grenzen | **bestanden** | Sentinel unverändert, Symlink-Entry abgelehnt |
| TC-O05 SQL-Scripts mit Semikolon/Dollar-Quote | **bestanden** | PostgreSQL-17-Lauf |
| TC-O06 PreparedStatements/Apostroph/null | **bestanden** | H2 und PostgreSQL |
| TC-O07 Rollback zweites Statement | **bestanden** | H2 und PostgreSQL |
| TC-O08 Connection-/Commitdiagnose | **teilweise** | Connectionfehler getestet; Commit-Unbekanntheit nicht fault-injiziert |

### 8.3 CLI/Parameter/Report

| Fall | Status | Nachweis |
|---|---|---|
| TC-C01 Parameterpriorität | **bestanden** | Runtime/Datei/CLI, erstes `=`, leerer Text |
| TC-C02 doppelte Keys/fehlendes `=`/Nichtstring | **bestanden** | `Json`, `ParameterFile`, CLI |
| TC-C03 Pfadbasen | **bestanden** | Jobroot/Start-CWD/Configordner/WorkPath |
| TC-C04 globale Hilfe/Version | **bestanden** | ohne Job-/Providerladen |
| TC-C05 Secrets/Token | **bestanden** | statisch/dynamisch, Log/Report maskiert |
| TC-C06 Workdir/Retention | **bestanden** | Erfolg/Fehler/Immer, externe Dateien unberührt |
| TC-C07 Distribution ohne Buildwerkzeug | **bestanden** | ZIP-IT mit `bin/gretl` |
| TC-C08 Runbericht/Quelle/Zeile | **bestanden** | Status, Fehler, `bad-config.groovy:<Zeile>` |
| TC-C09 Start ohne Config | **bestanden** | Default-Reportroot und Bericht |
| TC-C10 Pflicht-/Typfehler | **bestanden** | fehlend, boolean, integer, path, unbekannt |
| TC-C11 Deklarationsfehler | **bestanden** | M2 plus RuntimeConfig |
| TC-C12 gemeinsame Runtimeparams | **bestanden** | unbekannter Default ignoriert, explizit unbekannt scheitert |
| TC-C13 Jobhilfe | **bestanden** | Parameter/Ziele, keine Taskclosure |
| TC-C14 operations/FileSize | **bestanden** | Katalog und Erweiterungs-JAR in Distribution |

### 8.4 Logging

| Fall | Status | Anmerkung |
|---|---|---|
| TC-L01 Text/JSON-Identität | **bestanden** | gemeinsame Event-/Sequenzbasis |
| TC-L02 stdout/stderr/frühe JSON-Fehler | **bestanden** | CLI-Tests |
| TC-L03 IDs/Sequenz | **bestanden** | Eventdatei/Konsole |
| TC-L04 Lifecycle | **bestanden** | Runner-Ereignisse plus CLI |
| TC-L05 gefangener Fehler/Diagnostic | **bestanden** | M1/M2 |
| TC-L06 Causegraph | **teilweise** | Graph/Redaction vorhanden; Senkenfilter-Eskalation nicht vollständig getestet |
| TC-L07 Secretfilter | **bestanden** | HTTP, dynamischer Token, Report/Events |
| TC-L08 Record-/Dateigrenzen | **bestanden** | 16 KiB/128 MiB, Terminalreserve |
| TC-L09 MDC-Restoration | **nicht umgesetzt** | keine taskgebundene Logback-MDC-Instrumentierung |
| TC-L10 Provider-/Brückenprüfung | **teilweise** | ProviderInventory/JUL-Bridge vorhanden; Mehrfachprovider-Fault nicht getestet |
| TC-L11 HTTP-Details | **teilweise** | sichere Origin/Methode/Status/Bytes/Phase; kein eigener Dauerfeldsatz |
| TC-L12 SQL-Details | **teilweise** | Alias/Script/Index/SQLSTATE/Hash; Serverposition nicht kartiert |
| TC-L15 Progress-Sampling | **nicht umgesetzt** | Adapter melden Progress, aber ohne Koaleszenz/Samplinggrenze |
| TC-L16 Heartbeat | **nicht umgesetzt** | kein runeigener Heartbeat-Timer |
| TC-L17 Dateigrenze | **bestanden** | Truncation-Marker und Terminalereignis |
| TC-L18 Senke degradiert | **teilweise** | Konsole fehlerhaft getestet; Dateisenke nach Initialisierung nicht |
| TC-L19 WARN-Konsole/INFO-Datei | **bestanden** | CLI-Test |
| TC-L20 SIGTERM/Flush | **nicht umgesetzt** | Shutdown-Hook setzt Cancel, aber kein Signal-/Flush-IT |
| TC-L21 K8s/Jenkins-Metadaten | **teilweise** | Termination Message; keine K8s-Ressourcenattribute |
| TC-L22 Script-/Ressourcenhash | **teilweise** | Script-SHA im Report; Inventarhash ist Katalogfingerabdruck |
| TC-L23 Job-JAR | **nicht M3** | Lieferstufe C |
| TC-L24 OTel | **nicht umgesetzt** | `telemetry.mode=none` |

## 9. Bewusste Grenzen und Restrisiken

- **Stufe B/C fehlen:** kein `IliValidator`, kein Publisher, kein XtfToGpkg,
  kein Job-Fat-JAR und kein Native Image.
- **Keine öffentliche Anwenderfreigabe:** TC-U01 bis TC-U04 und die
  Beobachtung mit Fachanwendern bleiben M5.
- **Heartbeat/Progress/MDC:** siehe TC-L15/L16/L09. Die Logs sind wahrheitsgemäss
  als unvollständig gekennzeichnet; es wird keine Plattforminstrumentierung
  vorgetäuscht.
- **SQL-Commit-Unbekanntheit:** nicht fault-injiziert; ein Verbindungsverlust
  während des Commits bleibt eine dokumentierte Grenze.
- **HTTP-Abbruch:** Limit/Status/Redirect sind getestet; ein harter
  Transportabbruch mit laufendem Stream ist nicht separat fault-injiziert.
- **Symlink-Härtung:** Traversal/Symlink im Archiv getestet; bestehende
  Symlinks in externen Zielpfaden und `workPath` sind noch nicht vollständig
  gegen alle Plattformfälle gehärtet.
- **Distribution:** `inventory.json` beschreibt Module/Tasks; ein aggregiertes
  `SHA256SUMS` wird noch nicht im ZIP erzeugt. Der Report-Inventarhash ist ein
  Katalogfingerabdruck, kein Hash aller JARs.
- **PostgreSQL:** Der Live-Lauf ist optional und nicht Teil des
  Standard-`verify`; Standardtests verwenden H2. Die Produktionstauglichkeit
  weiterer Treiberoptionen ist nicht zugesagt.
- **Secrets in fremden Exceptiontexten:** Die Adapter erzeugen sichere
  Top-Level-Meldungen; generische Fremdursachen können weiterhin rohe
  Bibliotheksdetails enthalten. Die Redaction ersetzt registrierte Werte, ist
  aber keine vollständige DLP-Garantie.
- **Keine JSON-Schemadatei:** Runtimekonfiguration und Parameterdatei werden
  durch strikte Parser validiert; der Runbericht wird strikt geschrieben, aber
  nicht gegen eine separate veröffentlichte Schemaressource geprüft.

## 10. Fazit und nächster Schritt

Der A-Kandidat ist technisch lauffähig: CLI, Konfiguration, Secrets, Logging,
Runbericht, HTTP/Archiv/SQL, Distribution und Erweiterungs-JAR sind implementiert
und getestet. Die Standardprüfung besteht mit 263 Tests, das GeoTools-Profil mit
267 Tests. Die Live-PostgreSQL- und Distributionsnachweise wurden zusätzlich
ausgeführt.

Der nächste Meilenstein ist **M4**: die echte `IliValidator`-Integration mit
lokalen Modellen/Transfers, Fehlerdiagnosen und Ressourcenfreigabe. Die
öffentliche Anwenderfreigabe folgt in M5; M4 wurde in diesem Auftrag nicht
begonnen.
