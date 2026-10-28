# Phase 1 – Independent Review

- Phase base SHA: `5bc9d367fb3ea3f1e8643c43d0894e8d6eb1bd10`.
- Reviewed candidate SHA: `f1f1074c1dd0b78d3456bc918bca7661d4f978f0`.
- Reviewer/Model/Session: unabhängiger Codex-Review-Agent B, eigene Sitzung `/root/independent_phase1`. Die Sitzungsanweisung bezeichnet den Agenten als GPT-6-basiert; eine genauere konfigurierte Modellkennung wird nicht offengelegt und hier nicht geraten. B hat diese Implementierung und As Phasenberichte nicht geschrieben.
- Reviewzeitpunkt UTC: 2026-10-28T19:40:41.868202+00:00.
- Eigener separater detached Worktree: `/Users/stefan/sources/gretl-runtime-phase-1-review-1`. Dort bestätigte `git rev-parse HEAD` vor und nach der Prüfung exakt die Kandidaten-SHA. `git symbolic-ref -q HEAD` lieferte Exit 1, passend zu detached HEAD. Der getrackte Arbeitsbaum blieb sauber; keine Produkt-, Test-, Fixture- oder Spezifikationsdatei wurde geändert.
- Vollständiger geprüfter Diff: `5bc9d367fb3ea3f1e8643c43d0894e8d6eb1bd10..f1f1074c1dd0b78d3456bc918bca7661d4f978f0`, alle 21 Dateien, 1401 hinzugefügte Zeilen. Kein verkürzter HEAD~1-Vergleich.
- Externe Evidenzwurzel E: `/Users/stefan/sources/gretl-runtime-phase-1-evidence-20261028/independent-review-evidence/`. Dieser Bericht liegt ausserhalb des Worktrees und ausserhalb von E; A kann ihn unverändert archivieren.

## Identität, Normen und vollständiger Umfang

Beide vollständigen Commit-IDs wurden mit `git cat-file -e <SHA>^{commit}` geprüft (je Exit 0); `git merge-base --is-ancestor 5bc9d367fb3ea3f1e8643c43d0894e8d6eb1bd10 f1f1074c1dd0b78d3456bc918bca7661d4f978f0` lieferte Exit 0. B erzeugte mit `git worktree add --detach /Users/stefan/sources/gretl-runtime-phase-1-review-1 f1f1074c1dd0b78d3456bc918bca7661d4f978f0` seinen eigenen Checkout (Exit 0), ausgehend vom ursprünglichen Repository. Danach wurde bei jedem Shell-Aufruf der Reviewworktree ausdrücklich als workdir gesetzt. Der gemeinsame Default-CWD wurde nicht gewechselt.

Gelesen wurden Repository-AGENTS, `docs/spec/AGENTS.md`, die vollständige Subprojekt-Spezifikation 1.1 einschliesslich §§7–9 und 12, die dort genannten Runtime-/DSL-/Paketierungs-/API-/Kernklassen-/Loggingverträge 03/04/12/14/15/16 sowie `module-boundaries.md`, `testing.md` und `job-artifact-tests.md`. Phasenbericht, Evidence-Index und Handoff wurden unmittelbar gelesen und mit den realen Dateien abgeglichen. Die vorherige archivierte Review betrifft `1ff17246caff404f75303dcc31d2f9814e4bc2aa`; der Diff von dort zur Phasenbasis enthält ausschliesslich `phase-0-review.md`.

Der komplette Git-Diff ist zusätzlich unverändert als E/`full-phase.diff` gesichert, SHA-256 `d4fb93ee08674095020051c55c2d27b0f96a78fb5db6bbf4f01f5202dcd51263`. Die neuen Produktklassen, Tests, Katalogergänzungen, Beispiele und Dokumentationsänderungen wurden selbst geprüft. Alle geänderten Dateien (repository-relativ):

- `README.md`
- `docs/implementation/project-subprojects/README.md`
- `docs/implementation/project-subprojects/phase-1-evidence.json`
- `docs/implementation/project-subprojects/phase-1-implementation.md`
- `examples/project-subprojects/README.md`
- `examples/project-subprojects/checks/local/input.txt`
- `examples/project-subprojects/checks/local/job.groovy`
- `examples/project-subprojects/settings.gretl`
- `examples/project-subprojects/source/input.txt`
- `examples/project-subprojects/source/job.groovy`
- `gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/project/GretlProject.java`
- `gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/project/GretlProjectParser.java`
- `gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/project/ProjectEntry.java`
- `gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/project/ProjectException.java`
- `gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/project/ProjectPath.java`
- `gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/project/ProjectSourceNames.java`
- `gretl-groovy/src/test/java/ch/so/agi/gretl/runtime/groovy/project/GretlProjectParserTest.java`
- `gretl-groovy/src/test/java/ch/so/agi/gretl/runtime/groovy/project/ProjectPathTest.java`
- `gretl-groovy/src/test/java/ch/so/agi/gretl/runtime/groovy/project/ProjectSourceNamesTest.java`
- `gretl-i18n/src/main/resources/ch/so/agi/gretl/i18n/messages.properties`
- `gretl-i18n/src/main/resources/ch/so/agi/gretl/i18n/messages_de.properties`

Der getrackte Evidence-Index nennt die bekannte Phasenbasis, die vollständige siebenzeilige Testfallmap, reale Entwicklungsfehler und die vor dem Commit ausgeführten Prüfungen. Er behauptet keine selbstreferenzierende Kandidaten-SHA. Die 27 späteren PRJ-IDs sind ausdrücklich als nicht ausgeführt markiert.

## Eigene Ausführung und Herkunft der Nachweise

Alle folgenden Produktprüfungen liefen im oben genannten Reviewworktree. JDK: Eclipse Temurin `25.0.2+10-LTS`; Maven `3.10.0` über den unveränderten Wrapper; echte Groovy-Version `6.0.0`; UTF-8; macOS `27.0.1` aarch64; Locale en_US, Europe/Zurich. Eigene Versionsausgaben, Git-Kommandos, Exitcodes und CWD stehen in E/`identity-and-handoff-verification.json`.

| Eigener Befehl | Exit | Beleg |
| --- | ---: | --- |
| `./mvnw -B clean verify` | 0 | E/`console.log`, `build-start.json`, `result.json`, `reports/`; UTC 2026-10-28T19:30:35.026093+00:00 bis 2026-10-28T19:38:01.329152+00:00, rund 7:26 Minuten |
| `python3 scripts/audit-job-evidence.py --source f1f1074c1dd0b78d3456bc918bca7661d4f978f0` | 0 | E/`audit.log`, `result.json`, `qualification/summary.json`, `qualification/jar-executions.json` |
| `git diff --check 5bc9d367fb3ea3f1e8643c43d0894e8d6eb1bd10 f1f1074c1dd0b78d3456bc918bca7661d4f978f0` | 0 | Keine Ausgabe/Whitespacefehler |
| `javac -cp <exakter Surefire-Classpath> E/IndependentParserProbe.java` und `java -cp E:<derselbe Classpath> IndependentParserProbe E/probe-cases-final` | 0 / 0 | Vollständige tatsächlich verwendete argv, UTC und CWD in E/`independent-probes.json`; Quelle und Konsolenlog separat gesichert |
| Eigene Python-Prüfung aller Handoff- und A-Archiv-Checksummen | 0 | E/`identity-and-handoff-verification.json`; 3366 Dateien je Vor-/Nachtestarchiv, keine Abweichung |
| `python3 /Users/stefan/sources/gretl-runtime-phase-1-evidence-20261028/independent-review-evidence/archive-own-evidence.py` | 0 | Eigener Audit, aktive JUnit-Auswertung, selektive Archivierung, JAR-/Outputstichproben und Manifest |

Der standardmässige Reactor wurde ohne Skip-Flags oder selektive Testauswahl ausgeführt. Die 112 aktiven JUnit-XML enthalten insgesamt **621 Tests, 0 Failures, 0 Errors, 0 Skips**, davon 17 neue Fälle in den drei Projekt-Testklassen. Die aktiven Root-POM-Module wurden eigenständig ausgewertet; keine alten GeoTools-Reports wurden hinzugezählt:

| Modul | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| gretl-api | 17 | 0 | 0 | 0 |
| gretl-runner | 185 | 0 | 0 | 0 |
| gretl-groovy | 66 | 0 | 0 | 0 |
| gretl-i18n | 4 | 0 | 0 | 0 |
| gretl-logging | 38 | 0 | 0 | 0 |
| gretl-packager | 12 | 0 | 0 | 0 |
| gretl-database-tasks | 21 | 0 | 0 | 0 |
| gretl-io-tasks | 54 | 0 | 0 | 0 |
| gretl-cli | 55 | 0 | 0 | 0 |
| gretl-job-runtime | 24 | 0 | 0 | 0 |
| gretl-ilivalidator | 3 | 0 | 0 | 0 |
| gretl-interlis-tasks | 34 | 0 | 0 | 0 |
| examples/file-size-extension | 0 | 0 | 0 | 0 |
| gretl-dist | 13 | 0 | 0 | 0 |
| gretl-integration-tests | 95 | 0 | 0 | 0 |

Der erste Versuch, die zusätzliche externe Java-Gegenprobe zu verfassen, hatte einen eigenen Java-Quote-Fehler (`javac` Exit 1); dies steht in E/`independent-probes-authoring-error.*` und ist kein Produktfehler. Danach kompilierte die Probe erfolgreich. Ihr zunächst strikter Lauf scheiterte reproduzierbar an der fehlenden Modulimport-Originalzeile (`java` Exit 1); E/`module-import-position-repro.*` bleibt erhalten. Der nachfolgende vollständige Lauf dokumentiert an dieser Stelle ausdrücklich den beobachteten Wert 0 und prüft weitere 79 Assertions erfolgreich. Dieser nachfolgende Exit 0 hebt das unten aufgeführte Finding nicht auf.

As Handoff ist ein fremder Nachweis und wurde nicht als eigene Ausführung ausgegeben. Seine SHA-256 ist `cb47e0eb2caea3d0cea349a90be78e05b3e7ce78f395ae4efcfe5ffd5d17e6fa`. Alle referenzierten Datei-Hashes stimmen. Die vollständigen A-Manifeste wurden neu gehasht: `before-standard/SHA256SUMS.json` = `a4c36f9b37e8b36835510e37dce8f3cbe7e8131d2080e31a52505cb67d3e0bf4`; `after-standard/SHA256SUMS.json` = `6f0aa2ff3a0498ff3e7925452954b70c58c27169cfa6fec7b7cd84fe2dab21ac`, jeweils 3366 Dateien und null Hashabweichungen. Der Vorlauf ist korrekt als dirty auf der Phasenbasis dokumentiert; der Nachlauf ist an die saubere Kandidaten-SHA gebunden.

## Pflicht-Testfallmatrix und zusätzliche Gegenproben

| ID | Outcome des definierten Orakels | Selbst geprüftes Verhalten |
| --- | --- | --- |
| PRJ-001 | PASS | Root zuerst, danach kanonische Includes in Eingabereihenfolge; kein rekursiver Scan/impliziter Elternjob; wiederholte Parserausgabe stabil; Defaultpositionen und immutable Sammlungen. Eigene Probe ergänzt bewusst umgekehrte `z`, `a:b`-Reihenfolge mit vorhandenen unselektierten Jobs. |
| PRJ-002 | PASS | Zwei Includes ohne Root; verschachtelte `b/c/job.groovy`; kein erforderlicher `b/job.groovy`; untergeordnete Settings werden nicht ausgeführt. |
| PRJ-003 | PASS | Leere/ungültige Includes, doppelte kanonische Includes und Direktiven, fehlende Dateien mit Codes und Originalposition; eigene mehrzeilige Duplikatprobe bestätigt Zeile 2. |
| PRJ-004 | PASS | Ausführbare Settings werden verworfen, Marker bleibt abwesend; vorhandene Suite und eigene Probe umfassen Dateiaktionen, println/System.exit, Variablen, GStrings, ASTTest(CONVERSION), Klassen/Traits/Enums, synchronized/assert, Annotationen und Imports. Das zusätzliche Originalpositionsfinding bei Modulimports wird gesondert bewertet. |
| PRJ-005 | PASS | `:a:b` und `:a_b` bleiben unterschiedliche Werte; vollständiger SHA-256 in deterministischen gültigen Namen; echte Groovy-6-Klassengenerierung für Root und beide Varianten. Die eigene Probe instanziiert drei unter diesen Namen erzeugte harmlose Skriptklassen und bestätigt jeweils deren unterschiedlichen Body-Rückgabewert. |
| PRJ-006 | PASS | Relative Rootargumente, verschachtelte Pfade, fehlende/nicht reguläre Dateien, Root-Escape sowie direkte/intermediäre/interne/externe/dangling Symlinks werden geprüft; striktes UTF-8. Eigene Probe bestätigt exakt 262144 Bytes mit `ä` als gültig, 262145 Bytes als Fehler und ungültige UTF-8-Surrogatbytes als Fehler. |
| PRJ-007 | PASS | Defaults nur absolute Syntax; unbekanntes syntaktisch gültiges `:absent` wird ohne Taskexistenzprüfung erhalten; Jobtoplevel mit absichtlichem Fehler/Exit wird nicht geladen oder ausgeführt; Positionen bleiben für spätere Registrierung erhalten. |

Die sieben eng definierten Tabellenorakel bestehen. Daneben gilt die allgemeine verbindliche Anforderung der Originalzeile für Settingsfehler; genau diese zusätzliche Pflichtprüfung schlägt für Groovy-6-Modulimports fehl. Grüne bestehende Tests ersetzen diesen negativen Befund nicht.

## Architektur, Umfang und Rückwärtskompatibilität

Die Produktionsänderungen liegen ausschliesslich in sechs kleinen Klassen unter `gretl-groovy/.../project` und additiven i18n-Meldungen. Keine POM-/Dependency-/Plugin-/Profil-/Wrapper-/CI-Änderung, kein neues Modul und kein neuer Modulzyklus. Maven und die bestehende Enforcergrenze laufen im eigenen Reactor erfolgreich.

`ProjectPath` ist IO-frei, immutable, kanonisiert nur den führenden Doppelpunkt und validiert zentral sämtliche Segmente. `ProjectEntry` und `GretlProject` normalisieren absolute Pfade und kopieren Listen/Map defensiv. Die zusätzliche immutable Map `defaultTargetSources` mit erhaltenem Vierargument-Konstruktor ist eine begründete additive Abweichung von der vorgeschlagenen Recordsignatur: Sie hält die geforderten Defaultpositionen bis zur späteren Existenzprüfung vor.

Der Parser liest begrenzt, dekodiert UTF-8 strikt und lehnt Symlinks an Root, Settings, Jobdateien und innerhalb inkludierter Verzeichnisketten ab. Anschliessend werden ausschliesslich `SourceUnit.parse` und `convert` verwendet, niemals `run`, `evaluate`, Settingsinstanziierung oder ausführbarer Bytecode. Die AST-Allowlist ist klein und strukturell; keine zweite DSL-/Sandboxplattform. Das Ausschalten der Job-Basisklasse betrifft nur die frische Settingskonfiguration. Globale Registries und Taskresultate existieren hier nicht. Die Quellnamensbildung benutzt die ganze kanonische Identität.

Es gibt keine Änderung an `GroovyJobLoader`, `JobDslModel`, `GretlScript`, `GroovyContextFacade`, SourceLocations, API, Runner, CLI, JobRuntime, JobSource, Packager, Taskmodulen oder Logging. Kein geteilter Builder/ProjectTaskIndex, kein Directorydispatch, kein Schema 3 und keine Phase-2-Ausführung wurde vorgezogen. Das Beispiel wird ausdrücklich als zwei bestehende Einzeljobs dokumentiert und im echten Loader/Runner geprüft; die beiden unterschiedlichen lokalen UTF-8-Inhalte werden fachlich verglichen. Vorhandene Tests oder Assertions wurden nicht abgeschwächt oder entfernt.

Die bestehende Einzeldatei-/Schema-1/2-/JAR-Kompatibilität wird durch den unveränderten vollständigen Reactor einschliesslich CLI-, Groovy-, Runner-, Packager-, Distributions- und lokalen JAR-Prozesstests bestätigt. Die eigens gelesenen `IoLocalJobIT`, `SelectedPackageLocalJobIT`, `SelectedPackageSupport` und `PackageArtifactAssertions` prüfen ausserhalb der Jobprozesse echte Bytes, XTF-Felder, Datenbank-/XLSX-Werte, Provider/Services/Inventare und isolierte JAR-Starts; sie sind keine blossen --help-/Exitcode-Nachweise.

## Provenienz, Telemetrie und tatsächliche JAR-Belege

Der eigene Audit bestätigt die saubere Distribution auf exakt `f1f1074c1dd0b78d3456bc918bca7661d4f978f0`, prüft Distributionsdateien und jeden tatsächlich gestarteten JAR-Hash erneut und gleicht Report-/Prozesscodes ab. Er findet **82 echte JAR- und 14 Script-Ausführungen**, keinen IMAGE-Lauf. Der Standardlauf behauptet keine vollständige positive Dienstqualifikation aller 44 Operationen. Die Test-JVM-Coverage wird von nicht instrumentierten Produktprozessen getrennt gehalten.

E enthält eigene Logs, 112 JUnit-XML, Audit-/Provenienz-JSON, tatsächliche `job.jar`, Reports, redigierte stdout/stderr-/Eventdateien und ausgewählte unabhängige Outputs. Rohkonfiguration, Secretdateien und pauschale Workspaces wurden nicht kopiert. E/`SHA256SUMS.json` umfasst 1440 ausgewählte Dateien, SHA-256 **`962d2b6065dc493f5868b779e1f5d829cbeda052a91d4eaf0bb9a45626eecaeb`**. Die ausgeführten Kopier-/Hash-/Stichprobenregeln sind in E/`archive-own-evidence.py` einsehbar.

B hat zusätzlich zum Audit die folgenden realen Artefakte selbst geöffnet und Ergebnisse gelesen:

- IO-Extract-JAR `7260b918de49a732435654bade1fc3a4f72af39ef77c6f5cb6dc50946a666e68`: interne Jobmetadaten Schema 1, Dependency-Inventar Schema 2, tatsächliche Einstiegsklasse vorhanden; entpackte `nested/payload.txt` bytegleich zur kanonischen Eingabe.
- Minimaler Erfolgs-/Fehlerjob, in beiden Starts dasselbe unveränderte JAR `4c1142cba2093c5412d3158454eb811b35daae144a5b533dcb5a4a31869d0b6f`: Erfolg schreibt exakt `minimal package ü\n`, Fehler liefert Code 10 mit `selected-package-business-failure`; beide schreiben `cleaned`. Beide isolierten Starts haben gelöschte Quellkopie, fremdes CWD, leeres Maven-HOME und PATH `/gretl-no-build-tools` nachgewiesen. Metadaten/Inventar/Einstiegsklasse wurden direkt aus dem jeweiligen JAR geprüft.

Die zugehörigen konkreten Szenarioverzeichnisse, Codes, Hashes und Assertions stehen in E/`result.json` unter `independentJarSpotChecks`. Die Phasenänderung fügt keine Run-/Task-Telemetrie hinzu; die direkten Parser-Negativproben erzeugen keine Jobausführung und keine Markerdatei. Bestehende Fehler-/Cancellation-/Cleanup- und Quellenpositionsregressionen liefen vollständig mit.

## Finding

**MAJOR – M1: Verbotene Groovy-6-Modulimports verlieren die verpflichtende Originalzeile.**

- Datei: `gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/project/GretlProjectParser.java:99` (positionsloser Modulimport wird unmittelbar an `unsupported` weitergereicht; die gemeinsame `location(..., ASTNode)`-Konversion bei Zeile 256 übernimmt daraus 0).
- Reproduktion im Kandidaten: reguläre `job.groovy` im Root anlegen und folgende `settings.gretl` parsen:

```groovy
// heading
import module java.base; include 'a'
```

- Beobachtung: `PROJECT_SETTINGS_UNSUPPORTED` wird korrekt und ohne Nebenwirkung geworfen. `source.file()` nennt die ursprüngliche Settingsdatei; **`source.line()==0` und `source.column()==0`**. Die Meldung enthält deshalb keine `:2`-Position. Erwartung ist die tatsächliche Originalzeile **2**, möglichst mit Spalte.
- Unabhängiger Nachweis: E/`module-import-position-repro.log` und `.json` enthalten die strikte zusätzliche Gegenprobe (`javac` Exit 0, `java` Exit 1 bei der fehlenden Zeilenassertion). E/`IndependentParserProbe.java`, `independent-probes.log` und `.json` dokumentieren denselben beobachteten Sonderfall explizit neben den anderen erfolgreich geprüften Negativfällen. Die erzeugten kleinen synthetischen Quellen bleiben unter E/`probe-cases*/` erhalten.
- Vertrag: Subprojekt-Spezifikation §3.3 verlangt Dateipfad, Zeile und Fehlercode; §4.1 verlangt Zurückweisung verbotener AST-Knoten samt SourceLocation. Die allgemeine Forderung einer konkreten Originalquelle aus §7.1 und die neue Formatdokumentation versprechen ebenfalls Originalpositionen. Diese Information ist hier aus dem tatsächlichen Quelltext bestimmbar. Ein unveränderter Groovy-AST-Knoten ohne Position rechtfertigt nicht das Weglassen der vertraglich versprochenen Originalzeile.
- Schwerebegründung: Die Settings-Sicherheitsentscheidung und die erlaubte Sprache bleiben korrekt. Das Finding ist **keine** nachgewiesene Codeausführung oder Pfadumgehung. Es verletzt jedoch eine ausdrückliche Phase-1-Diagnosepflicht und ist damit keine rein kosmetische Schreibweisenfrage. Nach §9.4 genügt der grüne Standardlauf nicht zur Freigabe eines tatsächlich unerfüllten Pflichtvertrags.
- Erforderliche Korrektur durch A: Die echte Position des verbotenen Modulimports bewahren bzw. aus der Parser-/Lexermetadatenherkunft zuordnen; die strikte AST-Ablehnung erhalten. Eine gezielte negative Regression muss bei vorangestellten Kommentaren/Leerzeilen die tatsächliche Originalzeile/-spalte prüfen und weiterhin jede Ausführung ausschliessen. Danach neuer Kandidat, vollständiger Vor-/Nachtest und neue unabhängige Review derselben Phasenbasis. B nimmt keinen Fix vor.

Weitere CRITICAL-/MAJOR-Befunde wurden im beschriebenen Umfang nicht gefunden.

## Nicht unabhängig qualifizierte Grenzen

Der vollständige Aufruf `./mvnw -B -Pgeotools-probe,backing-services clean verify` wurde in dieser Phase von B nicht erneut ausgeführt; er ist erst Gate 3 des neuen Features und wird hier nicht als eigener Erfolg behauptet. Historische Full-Archive aus Phase 0 ändern diese Aussage nicht. Kein echter IMAGE-, Windows-/Linux-, Native-, Leer-Authoringcache- oder Produktionslauf.

PRJ-101–115 und PRJ-201–212 sowie geteilter Builder, Taskindex, Cross-Project-Resultate/-Parameter/-Cleanup/-file, Directory-CLI, Projektquellhash und Projekt-JAR sind spätere Phasen und wurden weder implementiert noch als aktuell fertig bewertet. Daraus wird kein zusätzlicher Phase-1-Fehler konstruiert. Pfadprüfungen sind keine allgemeine Dateisystemsandbox und keine atomare Transaktion gegen gleichzeitig ausgetauschte Verzeichnisstrukturen. Runtime-/Jobcode bleibt vertrauenswürdiger ausführbarer Code.

## Verdict

**FAIL**

Der Kandidat erfüllt die sieben konkreten Parser-/Value-Object-Orakel, die harte Settings-/Symlink-Ablehnung und den eigenen vollständigen Reactor samt Audit. Das reproduzierte MAJOR-Finding M1 verletzt dennoch die verbindliche Originalzeilenpflicht für eine verbotene Settings-Anweisung. Deshalb keine Phase-1-Freigabe dieser SHA. A muss ausschliesslich Phase 1 korrigieren und einen neuen festen Kandidaten unter derselben Phasenbasis prüfen lassen; Phase 2 bleibt ausserhalb des Auftrags.
