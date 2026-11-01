# TST1 – unabhängiger SCRIPT-Prozessweg

Auftrags- und Phasenbasis: `520b59be12f7966be46d0eaaca046c6ee3dc04f7`.
Arbeitszustand: Entwicklung abgeschlossen, Kandidatengates und unabhängiger Review noch fällig. Kein Phasen-PASS.

## Baseline und Vorher-Gegenfall

JDK 25.0.2, Maven-Wrapper 3.10.0, macOS ARM64; Docker Desktop mit
Linux-ARM64-Daemon, PostgreSQL/PostGIS hier unter AMD64-Emulation.

- `./mvnw -B clean verify`: Exit 0, 674 Tests, 0 Failures/Errors/Skips.
  Die zu diesem Zeitpunkt neu angelegte Statusdatei macht die
  Distributionsprovenienz dirty; dies ist eine Entwicklungsbaseline,
  kein Kandidatengate. Die Tests liefen noch mit den kompilierten Basisklassen.
- `./mvnw -B -Pgeotools-probe,backing-services clean verify`: Exit 0 im
  separaten sauberen Basisworktree, 730 Tests, 0 Failures/Errors/Skips.
  `python3 scripts/audit-job-evidence.py --full --source
  520b59be12f7966be46d0eaaca046c6ee3dc04f7`: Exit 0; alle 44 geforderten
  Standardoperationen positiv JAR, zusätzlich zwei Extension-Operationen.
- Derselbe Default-Runner am Basisstand lehnt einen gültigen externen
  Quellenjob wegen seines unbenutzten kaputten Deskriptors bereits bei
  `prepare` ab: `PACKAGE_DESCRIPTOR_INVALID`; Probe-Exit 17.
  Rohbeleg: `preflight/descriptor-red.log` im privaten Evidenzverzeichnis.
- OpenCode 2.2.5 aus der Desktop-App, privates `run --standalone`, direkter
  Provider `deepseek/deepseek-flash`; Bereitschaftsprüfung Exit 0.
  Effektiver Modellkatalog: API `https://api.deepseek.com`, kanonisches
  Modell `deepseek/deepseek-v4.1-flash-1017`. Offizielle Zuordnung am
  01.11.2026 erneut geprüft. PATH enthält weiterhin eine andere V1-CLI;
  die Orchestrierung verwendet ausschliesslich den absoluten V2-Pfad.
- Docker-Bind-Mount des Checkouts read-only getestet, Exit 0.

Private Rohbelege liegen unter
`/Users/stefan/sources/gretl-script-first-evidence/`; die Baselineberichte
wurden vor dem nächsten `clean` erhalten.

## Implementierung und gezieltes Testdelta

Der vorhandene `ArtifactJobRunner` bleibt der gemeinsame Runner. Der Default
ist SCRIPT; die versiegelten Launchvarianten binden den Modus an den
vorbereiteten Start. Explizite Datei-/Projekteingaben und RUN/CHECK/PLAN/Hilfe
ersetzen `scriptInput` und Startbooleans. `ScriptExecutionAdapter` erzeugt
Argumentvektoren für den wirklichen Launcher, `JarExecutionAdapter` übernimmt
alle bisherige Paketlogik und deren Assertions, `ImageExecutionAdapter`
behält seinen externen SCRIPT-Einstieg. Es gibt keine neue Produktschnittstelle.

`CandidateDistribution` prüft die tatsächlichen Manifestbytes, Inventar und
Launcher. `JobInputSnapshot` inventarisiert Rohbytes einschliesslich
verschachtelter Ressourcen, ohne Groovy-/Settingsparser. Nur der
Root-Paketdeskriptor ist von dieser gemeinsamen fachlichen Identität
ausgenommen. `JobExecutionEvidence` schreibt atomare getrennte Input-,
Runtime-, JAR-/Image- und Prozessmetadaten. Prozessumgebung und Wartezeiten
bleiben kontrolliert; ein Force-kill hat nur begrenzte Warteaufrufe.

| Betroffene Methoden/Varianten | Bisher | TST1-Zuordnung und erhaltene Assertions |
| --- | --- | --- |
| Bestehende Fachtests über `run/prepare(Request)` | JAR-Default | COMMON, SCRIPT-Default; dieselben Fixturequellen, Testdaten und Orakel; explizite JAR-Regression bleibt Pflicht. |
| `ValidatorLocalJobIT#validatesThroughPublicDsl`, alle acht Varianten | JAR-Default auch im erwarteten Modus | COMMON; erwarteter tatsächlich konfigurierter Modus, alle Fachdiagnosen unverändert. |
| `CancellationLocalJobIT`, alle Booleanvarianten | JAR vorbereiten, Boolean beim Start | Explizite SCRIPT-/JAR-Vorbereitung; Checkpoints, Report-, Signal-, Cleanup- und Timeoutassertions unverändert. |
| `PlaintextSecretLocalJobIT`, beide Varianten | JAR vorbereiten, Boolean beim Start | Explizite SCRIPT-/JAR-Vorbereitung; rohe Produktlogs, SQLite und alle Secretassertions erhalten. |
| `RuntimeSafetyLocalJobIT`, sämtliche Varianten | JAR vorbereiten, Boolean beim Start | Explizite SCRIPT-/JAR-Vorbereitung; Rechte, Ursachen, Retention und Diagnoseassertions erhalten. |
| `ProjectArtifactLocalJobIT`, Script-/JAR-Vergleiche | Script erst nach Paketvorbereitung | Quellenvergleich bereitet SCRIPT vor; Paket-/Manipulationsfälle ausdrücklich JAR, alle Inventar-/Quell-/Ressourcenassertions erhalten. |
| `LibraryScopeLocalJobIT`, beide Startwege | Vor beiden Starts Jobpaketierung | Script mit eigener geprüfter Extensiondistribution; JAR-Paketassertions im JAR-Zweig erhalten, identische Logging-Orakel. |
| `SelectedPackageLocalJobIT`, `SelectedPackageSupport` | Explizite JAR-Qualifikation | PACKAGE_CONTRACT, weiterhin JAR; tatsächliche manipulierte/isolierte JAR-Bytes separat erfasst. |
| `ProjectScriptLocalJobIT` | Bereits echter Quellenprozess | SCRIPT_CONTRACT unverändert erhalten; keine neue Paketabhängigkeit. |
| `ImageExecutionAdapterTest` | RecordingEngine-Komponentenprüfung | COMPONENT; mit dem eindeutigen Launchmodell migriert, keine echte IMAGE-Qualifikation behauptet. |
| Neue `ArtifactJobRunnerTest` und `ScriptProcessLocalJobIT` | Nicht vorhanden | COMPONENT bzw. SCRIPT_CONTRACT: ungültige Modi, defensive Configkopien, Byteinventar, Symlinks, kaputte Deskriptoren, geänderte Quellen, Unicode, externe ZIP-Installation, frühe Fehler, Controls, grosse Streams und Fehlstart. |

Der Paketcache bindet Quelle, Deskriptor und Distributionsmanifest ein und
verwendet eigenständige Kopien. Der Audit prüft den gewählten Modus und die
wirklichen Start-/Input-/Runtimebytes; der vollständige Szenariokatalog und
die vollständige Audit-Härtung bleiben TST2 vorbehalten.

Die verschachtelten Launchrecords bilden die entworfenen Typen ohne neue
Datei pro Record ab. `Result.mode()` behält für bestehende Evidenzkonsumenten
ein Textlabel; dieses wird ausschliesslich aus dem vorbereiteten Launch-Enum
abgeleitet. Ein Startboolean oder späterer Propertywechsel kann es nicht ändern.

Der kleine lokale OpenCode-Starter prüft Revisionen, Kriterienmengen,
Prozessabschluss und strukturierte Urteile. Seine synthetischen Tests sind
keine unabhängigen Modellreviews.

## Entwicklungsläufe und noch fällige Gates

Die erste SCRIPT-Entwicklungsgesamtprüfung deckte alte JAR-Defaulterwartungen
und die bisher nur im JAR gesetzte JVM-Locale auf. Der Teststartkontext wurde
angeglichen; keine fachlichen Assertions wurden abgeschwächt. Ein zusätzlicher
neuer Ausgabeprobefall übergab zunächst einen nicht deklarierten Parameter;
seine Jobdeklaration wurde korrigiert.

- Neue externe Quellenprozessproben ausserhalb des Checkouts: Exit 0,
  einschliesslich der ZIP-Installation und der JAR-Cache-/Distributionsmutation.
- Korrigierter Entwicklungs-Standardlauf: 685 Tests, 0 Failures/Errors/Skips,
  Maven Exit 0. Der anschliessende Audit scheiterte korrekt am noch nicht
  zurückkopierten Inputmanifest eines isolierten JAR-Laufs; der Kopierpfad
  wurde korrigiert. Diese Runde gilt ausdrücklich nicht als bestandenes
  vollständiges Gate.
- `node --test tests/script-first-testing/review-phase.test.mjs`: 21 Tests,
  0 Failures/Skips; technische Negativproben gegen Fehlannahme von Reviews.
- Dokumentationsgenerator, 73 Cleanup-Werkzeugtests, Linkprüfung
  (197 Ziele, keine defekten Links) und Quarto-Website-Render durchgeführt.

Die zuletzt abgeschlossenen vollständigen Entwicklungsgates:

| Befehl | Exit | Tests / Failures / Errors / Skips | Audit |
| --- | --- | --- | --- |
| `DOCKER_HOST=unix:///tmp/gretl-intentionally-unavailable-docker.sock ./mvnw -B clean verify` | 0 | 686 / 0 / 0 / 0 | `python3 scripts/audit-job-evidence.py --mode SCRIPT`, Exit 0 |
| `./mvnw -B clean verify -Dgretl.test.executionMode=JAR` | 0 | 686 / 0 / 0 / 0 | `python3 scripts/audit-job-evidence.py --mode JAR`, Exit 0 |
| `./mvnw -B -Pgeotools-probe,backing-services clean verify` | 0 | 742 / 0 / 0 / 0 | `python3 scripts/audit-job-evidence.py --mode SCRIPT --full`, Exit 0 |

Die vollständige SCRIPT-Prüfung besitzt positive fachliche Nachweise für alle
44 geforderten Standardoperationen; die zusätzliche private Logging-Operation
wird getrennt davon gezählt. Die JAR/local-Prüfung behauptet keine vollständige
Dienstoperationsabdeckung. Alle diese Läufe sind Entwicklungsnachweise mit
`sourceRevision` der Auftragsbasis und `sourceDirty=true`, keine festen
Kandidatengates. Ihre XML-Berichte, tatsächlichen Pakete, Distributionen,
Inputinventare und Jobausgaben sind vor dem nächsten `clean` gesichert.

Die abschliessende Quellenprobe verwendet einen privaten PATH ausserhalb der
Inputwurzel mit ausschliesslich `dirname`. Der tatsächlich ausgeführte Job
prüft selbst, dass dort weder Maven noch Gradle existiert. Damit hängt der
Nachweis nicht von zufälligen Programmen unter `/usr/bin` ab. Seine sechs
Prozessfälle bestehen im vollständigen frischen Reactor. Eine zusätzliche
Direktprobe kollidierte mit einem parallelen `clean`; dieser Lauf ist als
ungültiger Entwicklungsversuch gesondert erhalten und zählt nicht als Gate.

Die Dokumentationschecks bestehen: Generator, 73 Werkzeugtests, 197 lokale
Linkziele ohne Fehler und Quarto-Render. Die private OpenCode-Probe mit zwei
Dateianhängen ist ebenfalls mit Exit 0 und abgeschlossenem Sessionereignis
beendet; beide Dateien wurden erkannt.

Noch fällig: lokaler Kandidatencommit, frische feste Kandidatengates und
unabhängiger TST1-Review mit neuer Gegenprobe. Der tatsächliche Kandidaten-SHA
wird nach dem Commit in der externen Übergabe festgehalten. TST2 ist noch
nicht freigegeben.


## Erster fester Kandidat und eigene Nacharbeit

Am ersten Kandidaten `3e60341597904094383f5394e544ecb125c184fd`
sind Standard (686 Tests), JAR/local (686 Tests), SCRIPT/full (742 Tests),
alle drei Audits sowie die Orchestrierungs- und Dokumentationschecks frisch
mit Exit 0 abgeschlossen; überall 0 Failures/Errors/Skips und saubere
Commitprovenienz. Die komplette äussere Gatesitzung ist beendet.

Die eigene Durchsicht fand dennoch eine bei der Migration verschobene
Prüfbedeutung: Der bisherige No-Toplevel-/No-Report-Check nach JAR-Vorbereitung
prüfte nun nur die SCRIPT-Vorbereitung. Beide Checks stehen zusätzlich wieder
ausdrücklich nach der Schema-3-JAR-Paketierung. Das ist eine Wiederherstellung
des Testvertrags, kein behaupteter neuer Produktfehler. Der erste Kandidat
wird deshalb nicht zur unabhängigen Abnahme eingereicht. Der neue Kandidat
erhält erneut sämtliche frischen Pflichtgates; die Phasenbasis bleibt unverändert.
